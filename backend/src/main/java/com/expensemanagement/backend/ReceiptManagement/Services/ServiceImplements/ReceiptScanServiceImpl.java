package com.expensemanagement.backend.ReceiptManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.AiUnavailableException;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.Ai.AiVocabulary;
import com.expensemanagement.backend.Ai.DeepSeekClient;
import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoRequests.ReceiptScanRequests;
import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoResponses.ReceiptScanResponses;
import com.expensemanagement.backend.ReceiptManagement.Services.ServiceInterface.ReceiptScanService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Turns "here is a photo" into "here are the expenses on it".
 *
 * <p>Two properties matter more than anything else in this class:
 *
 * <ul>
 *   <li><strong>The image is never stored.</strong> The bytes exist only as a local variable for
 *       the length of one request: decoded, re-encoded into a {@code data:} URL, posted, and
 *       released. Nothing is written to disk, no entity holds it, and it is not logged.</li>
 *   <li><strong>The picture's format is normalised, not trusted.</strong> The client's base64 may
 *       carry a data-URL prefix, line breaks or missing padding, and its declared MIME type may
 *       be wrong. The bytes are decoded leniently, the real format is sniffed from the magic
 *       number, and the URL sent onward is re-encoded canonically - so a labelling difference
 *       between app, server and model cannot break the hand-off.</li>
 * </ul>
 */
@Service
public class ReceiptScanServiceImpl implements ReceiptScanService
{
    /** Far above a compressed phone photo, low enough that a bad request cannot exhaust the heap. */
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic");

    private final UserCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DeepSeekClient aiClient;
    private final ObjectMapper objectMapper;

    public ReceiptScanServiceImpl(
            UserCategoryRepository categoryRepository,
            UserRepository userRepository,
            DeepSeekClient aiClient,
            ObjectMapper objectMapper)
    {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptScanResponses.Scan scan(UUID userId, ReceiptScanRequests.Scan request)
    {
        byte[] bytes = decode(request.imageBase64());

        if (bytes.length == 0)
        {
            throw new InvalidRequestException("The picture is empty.");
        }
        if (bytes.length > MAX_IMAGE_BYTES)
        {
            throw new InvalidRequestException(
                    "That picture is too large. Take it again at a smaller size.");
        }

        String mimeType = resolveMimeType(request.mimeType(), bytes);

        // The caller's own categories - which already include the ones copied from the platform
        // defaults at sign-up - so the model answers in the vocabulary the picker will show.
        List<UserCategory> categories = categoryRepository.findAllByUserIdOrderByNameAsc(userId);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No such user."));
        String currency = owner.getBaseCurrency() == null
                ? Money.DEFAULT_CURRENCY
                : owner.getBaseCurrency();

        String prompt = buildPrompt(categories, currency);
        String answer = aiClient.readImage(prompt, toDataUrl(mimeType, bytes));

        List<String> categoryNames = categories.stream().map(UserCategory::getName).toList();

        return new ReceiptScanResponses.Scan(parseExpenses(answer, categoryNames));
    }

    // ------------------------------------------------------------------
    // The picture
    // ------------------------------------------------------------------

    /**
     * Decodes base64 that may arrive as a bare blob or as a data URL, with or without padding.
     *
     * <p>Leniency is the point: the client and the server disagreeing about padding or a prefix
     * must not read as "that photo is broken".
     */
    private static byte[] decode(String raw)
    {
        String value = raw == null ? "" : raw.trim();

        int marker = value.indexOf("base64,");
        if (marker >= 0)
        {
            value = value.substring(marker + "base64,".length());
        }
        value = value.replaceAll("\\s", "");

        if (value.isEmpty())
        {
            throw new InvalidRequestException("The picture is empty.");
        }

        // A missing tail is common when the string was copied or trimmed; restore it.
        int remainder = value.length() % 4;
        if (remainder == 2)
        {
            value = value + "==";
        }
        else if (remainder == 3)
        {
            value = value + "=";
        }

        try
        {
            return Base64.getDecoder().decode(value);
        }
        catch (IllegalArgumentException e)
        {
            throw new InvalidRequestException("The picture could not be decoded. Try taking it again.");
        }
    }

    /** The real format, read from the bytes; the declared type is only a fallback. */
    private static String resolveMimeType(String declared, byte[] bytes)
    {
        String sniffed = sniff(bytes);
        if (sniffed != null)
        {
            return sniffed;
        }

        String normalised = declared == null ? "" : declared.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalised))
        {
            normalised = "image/jpeg";
        }
        if (ALLOWED_MIME.contains(normalised))
        {
            return normalised;
        }

        throw new InvalidRequestException(
                "That file is not a supported picture. Use a JPEG, PNG, WebP or HEIC image.");
    }

    private static String sniff(byte[] b)
    {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
        {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
        {
            return "image/png";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P')
        {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p')
        {
            String brand = new String(b, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf"))
            {
                return "image/heic";
            }
        }
        return null;
    }

    /** Canonical base64 of the decoded bytes, so what goes out is always well-formed. */
    private static String toDataUrl(String mimeType, byte[] bytes)
    {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    // ------------------------------------------------------------------
    // The instruction
    // ------------------------------------------------------------------

    private static String buildPrompt(List<UserCategory> categories, String currency)
    {
        StringBuilder list = new StringBuilder();
        if (categories.isEmpty())
        {
            list.append("- (the account has no categories yet)");
        }
        else
        {
            for (UserCategory category : categories)
            {
                list.append("- ").append(category.getName());
                if (category.getDescription() != null && !category.getDescription().isBlank())
                {
                    list.append(" (").append(category.getDescription().trim()).append(')');
                }
                list.append('\n');
            }
        }

        return """
                You extract expenses from a photo of a receipt, an invoice or a payment confirmation.

                The user's categories are:
                %s

                Rules:
                - The currency is %s. Amounts are numbers in that currency, without symbols or separators.
                - Today is %s. Use it as the date when the receipt does not show one.
                - Put one object per distinct charge. A single receipt with several lines may produce several expenses; do not merge different items into one total.
                - "category" MUST be exactly one of the category names above. If none fits, use the closest one; never invent a name and never use null unless the list is empty.
                - "description" is a short label such as the shop name or the item, at most 60 characters.
                - Amounts are positive numbers. Ignore totals, subtotals, tax lines and change due.

                Answer with JSON only, in this exact shape:
                {"expenses":[{"description":"...","amount":1234.5,"date":"YYYY-MM-DD","category":"..."}]}

                If the photo does not contain a readable expense, answer {"expenses":[]}.
                """.formatted(list, currency, LocalDate.now());
    }

    // ------------------------------------------------------------------
    // The answer
    // ------------------------------------------------------------------

    private List<ReceiptScanResponses.Expense> parseExpenses(String answer, List<String> categoryNames)
    {
        JsonNode root = readJsonObject(answer);
        JsonNode array = root.path("expenses");

        if (!array.isArray())
        {
            throw new AiUnavailableException("The AI answer was not in the expected shape.");
        }

        List<ReceiptScanResponses.Expense> expenses = new ArrayList<>();

        for (JsonNode node : array)
        {
            BigDecimal amount = asAmount(node.path("amount"));
            if (amount == null)
            {
                continue;
            }

            String description = node.path("description").asText("").trim();
            if (description.length() > 255)
            {
                description = description.substring(0, 255);
            }

            // Resolved against the caller's own list, so a near miss or a null still arrives as
            // a real choice and the review opens with a category already selected.
            String category = AiVocabulary.resolveCategory(
                    node.path("category").asText("").trim(), categoryNames);

            expenses.add(new ReceiptScanResponses.Expense(
                    description,
                    amount,
                    asDate(node.path("date").asText("")),
                    category));
        }

        return expenses;
    }

    /** The model may wrap its JSON in prose or a fenced block; take the outermost object. */
    private JsonNode readJsonObject(String answer)
    {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');

        if (start < 0 || end <= start)
        {
            throw new AiUnavailableException("The AI answer was not in the expected shape.");
        }

        try
        {
            return objectMapper.readTree(answer.substring(start, end + 1));
        }
        catch (Exception e)
        {
            throw new AiUnavailableException("The AI answer could not be read.", e);
        }
    }

    private static BigDecimal asAmount(JsonNode node)
    {
        BigDecimal value;

        if (node.isNumber())
        {
            value = node.decimalValue();
        }
        else
        {
            String text = node.asText("").replaceAll("[^0-9.\\-]", "");
            if (text.isEmpty())
            {
                return null;
            }
            try
            {
                value = new BigDecimal(text);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        return value.signum() > 0 ? value : null;
    }

    private static LocalDate asDate(String value)
    {
        String text = value == null ? "" : value.trim();
        if (text.length() > 10)
        {
            text = text.substring(0, 10);
        }

        try
        {
            return text.isEmpty() ? LocalDate.now() : LocalDate.parse(text);
        }
        catch (DateTimeParseException e)
        {
            return LocalDate.now();
        }
    }
}
