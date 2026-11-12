package com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureSource;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction;
import com.expensemanagement.backend.CaptureManagement.Parsers.ReceiptTextParser;
import com.expensemanagement.backend.CaptureManagement.Repositories.CapturedTransactionRepository;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.ReceiptOcrProvider;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.OperationNotAllowedException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.Shared.CurrencyPolicy;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('USER')")
public class ReceiptCaptureService
{
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    public static final int OCR_MAX_BYTES = 950_000;
    private final ReceiptOcrProvider provider;
    private final CapturedTransactionRepository captureRepository;
    private final UserRepository userRepository;
    private final ReceiptTextParser parser = new ReceiptTextParser();

    public ReceiptCaptureService(ReceiptOcrProvider provider, CapturedTransactionRepository captureRepository,
                                 UserRepository userRepository)
    {
        this.provider = provider;
        this.captureRepository = captureRepository;
        this.userRepository = userRepository;
    }

    public CaptureResponses.Receipt capture(UUID userId, byte[] image, String contentType)
    {
        if (image.length == 0)
        {
            throw new InvalidRequestException("Choose a receipt image to upload");
        }
        if (image.length > MAX_UPLOAD_BYTES)
        {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Receipt images must be 5 MB or smaller");
        }
        if (!matchesSignature(image, contentType))
        {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a JPEG, PNG or WebP image");
        }
        if (image.length > OCR_MAX_BYTES)
        {
            image = shrink(image);
            contentType = "image/jpeg";
        }
        ReceiptTextParser.ParsedReceipt parsed = parser.parse(provider.extractText(image, contentType));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String currency = CurrencyPolicy.resolve(null, user.getBaseCurrency());
        CapturedTransaction capture = new CapturedTransaction();
        capture.setUser(user);
        capture.setSource(CaptureSource.RECEIPT);
        capture.setStatus(CaptureStatus.PENDING);
        capture.setAmount(parsed.amount());
        capture.setCurrency(currency);
        capture.setTransactionDate(parsed.date());
        capture.setMerchant(parsed.merchant());
        capture.setConfidence(parsed.confidence());
        CapturedTransaction saved = captureRepository.save(capture);
        Money amount = parsed.amount() == null ? null : Money.of(parsed.amount(), currency);
        return new CaptureResponses.Receipt(saved.getId(),
                new CaptureResponses.Extracted(amount, parsed.date(), parsed.merchant()), parsed.confidence());
    }

    private static boolean matchesSignature(byte[] image, String contentType)
    {
        if ("image/jpeg".equals(contentType))
        {
            return image.length >= 3 && image[0] == (byte) 0xff && image[1] == (byte) 0xd8
                    && image[2] == (byte) 0xff;
        }
        if ("image/png".equals(contentType))
        {
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return image.length >= 8 && Arrays.equals(signature, Arrays.copyOf(image, 8));
        }
        if ("image/webp".equals(contentType))
        {
            return image.length >= 12 && image[0] == 'R' && image[1] == 'I' && image[2] == 'F'
                    && image[3] == 'F' && image[8] == 'W' && image[9] == 'E' && image[10] == 'B' && image[11] == 'P';
        }
        return false;
    }

    private static byte[] shrink(byte[] image)
    {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
            {
                throw cannotResize();
            }
            ImageReader reader = readers.next();
            BufferedImage source;
            try
            {
                reader.setInput(input);
                // Check dimensions before allocating the raster for an untrusted compressed image.
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000)
                {
                    throw cannotResize();
                }
                source = reader.read(0);
            }
            finally
            {
                reader.dispose();
            }
            double factor = Math.min(1.0, 1600.0 / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
            int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try
            {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, Color.WHITE, null);
            }
            finally
            {
                graphics.dispose();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try
            {
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                for (float quality : new float[]{0.85f, 0.65f, 0.45f, 0.25f})
                {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes))
                    {
                        writer.setOutput(output);
                        parameters.setCompressionQuality(quality);
                        writer.write(null, new IIOImage(target, null, null), parameters);
                    }
                    if (bytes.size() <= OCR_MAX_BYTES)
                    {
                        return bytes.toByteArray();
                    }
                }
            }
            finally
            {
                writer.dispose();
            }
        }
        catch (IOException ex)
        {
            throw cannotResize();
        }
        throw cannotResize();
    }

    private static OperationNotAllowedException cannotResize()
    {
        return new OperationNotAllowedException("Receipt image cannot be resized for OCR; use a smaller JPEG or PNG");
    }
}
