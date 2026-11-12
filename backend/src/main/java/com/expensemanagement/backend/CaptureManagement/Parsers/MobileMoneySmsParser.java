package com.expensemanagement.backend.CaptureManagement.Parsers;

import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyProvider;
import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyTransactionType;
import com.expensemanagement.backend.Exceptions.UnsupportedMessageException;
import com.expensemanagement.backend.Shared.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an MTN MoMo or Orange Money SMS and extracts the transaction it describes (S6-TECH-2).
 *
 * <p>The operators reword their messages from time to time and send them in French or English,
 * so this does not match whole templates. It looks for each field on its own, by the labels the
 * messages use ("Frais", "Solde", "ID de transaction", ...), which keeps working when the sentence
 * around a field changes.
 *
 * <p>The rule throughout is <b>blank, not guessed</b>: a field that cannot be read with confidence
 * is returned as null, and the user fills it in on the review screen. Only two things are
 * required - the provider and the amount - because without them there is nothing to review.
 *
 * <p>Matching is done on a copy of the text with accents removed ("reçu" becomes "recu") that has
 * exactly the same length, so a position found in the copy is also valid in the original. That
 * is how names keep their accents in the result.
 */
@Component
public class MobileMoneySmsParser
{
    /** Longest text accepted; a long concatenated SMS is about 900 characters. */
    public static final int MAX_LENGTH = 2000;

    /** What the parser read. Every field except provider, type and amount can be null. */
    public record ParsedSms(
            MobileMoneyProvider provider,
            MobileMoneyTransactionType type,
            Money amount,
            Money fee,
            String counterparty,
            String reference,
            LocalDate date,
            LocalTime time)
    {
    }

    // ----- provider -------------------------------------------------------------------------

    private static final Pattern ORANGE = Pattern.compile("(?i:\\borange\\b)|\\bOM\\b");
    private static final Pattern MTN = Pattern.compile("(?i)\\bmtn\\b|\\bmomo\\b|\\by['’]?ello\\b|\\bmobile money\\b");

    // ----- transaction type -----------------------------------------------------------------
    // Checked in this order. Money coming in is checked first so that "Transfert de 5000 FCFA
    // recu de ..." is recognised as received rather than as an outgoing transfer.

    private static final Pattern RECEIVED = Pattern.compile(
            "(?i)\\b(vous avez recu|recu de|recu un|transfert recu|you have received|received from|credite|credited"
                    + "|depot|deposit|cash[ -]?in)\\b");
    private static final Pattern WITHDRAWAL = Pattern.compile("(?i)\\b(retrait|withdraw\\w*|cash[ -]?out)\\b");
    private static final Pattern PAYMENT = Pattern.compile(
            "(?i)\\b(paiement|payment|paye|paid|achat|purchase|facture|bill|marchand|merchant)\\b");
    private static final Pattern TRANSFER = Pattern.compile(
            "(?i)\\b(transfert|transfer\\w*|envoye|envoi|sent|send)\\b");

    // ----- money ----------------------------------------------------------------------------

    /**
     * An amount: "5000", "5 000", "5.000", "5,000", "5000.50", "5 000,50". A group of exactly
     * three digits after a separator is a thousands group; one or two digits are decimals.
     */
    private static final String NUMBER = "(\\d{1,3}(?:[ .,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    private static final String CURRENCY = "(F ?CFA|XAF|CFA)";

    /**
     * "5000 FCFA" or "XAF 5000". The look-behind stops a match from starting inside a longer
     * number, a reference or a time such as "10:22".
     */
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?<![\\w.,:/])" + NUMBER + " ?" + CURRENCY + "\\b|\\b" + CURRENCY + " ?" + NUMBER + "(?![\\d])");

    private static final Pattern FEE_LABEL = Pattern.compile("(?i)\\b(frais|fees?|commission|charges?|tax[ea]?|tva)\\b");
    private static final Pattern BALANCE_LABEL = Pattern.compile("(?i)\\b(solde|balance)\\b");
    /** "Votre solde a ete debite de 5000 FCFA" names the balance but the amount is the debit. */
    private static final Pattern DEBIT_WORD = Pattern.compile("(?i)\\b(debite|debited|retire)\\b");

    /** How far before an amount its label may be ("Frais: 50 FCFA", "Nouveau solde est de 1 000 FCFA"). */
    private static final int LABEL_WINDOW = 40;

    // ----- reference ------------------------------------------------------------------------

    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)\\b(?:financial transaction id|transaction id|trans(?:action)?\\.? ?id|txn ?id"
                    + "|id (?:de (?:la )?)?transaction|id trans|numero de transaction|n[o°]? ?(?:de )?transaction"
                    + "|reference|ref\\b\\.?)"
                    + " ?[:#.]? ?([A-Z0-9][A-Z0-9.\\-]{3,})");

    // ----- date and time --------------------------------------------------------------------

    private static final String TIME = "(?: ?(?:a|at|,)? ?(\\d{1,2})[:h](\\d{2})(?::(\\d{2}))?)?";
    private static final Pattern ISO_DATE = Pattern.compile("(?i)\\b(\\d{4})-(\\d{2})-(\\d{2})(?:T|\\b)" + TIME);
    /** Day first, as written in Cameroon: 12/05/2026 or 12-05-26. */
    private static final Pattern DAY_FIRST_DATE = Pattern.compile(
            "(?i)(?<![\\d.])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?![\\d])" + TIME);

    // ----- counterparty ---------------------------------------------------------------------

    private static final String PHONE = "\\(?(?:\\+?237 ?)?(6\\d{8})\\)?";

    /**
     * The person or merchant after "à", "vers", "to", ... The keyword is case-insensitive, the
     * name is not: names are written in capitals ("JOHN DOE", "ENEO") or capitalised, and that is
     * what keeps the French "a été effectué" from being read as a name.
     */
    private static final Pattern COUNTERPARTY = Pattern.compile(
            "(?i:\\b(?:a|vers|to|chez|au|aupres de|marchand|merchant|beneficiaire|recipient)\\b) ?:? ?(?i:(?:le|the) )?"
                    + "(?:" + PHONE + " ?)?"
                    + "([A-Z][A-Za-z'\\-]*(?: [A-Z][A-Za-z'\\-]*)*)?"
                    + "(?: ?" + PHONE + ")?");

    /** Words that end a name: what usually follows it in these messages. */
    private static final Set<String> NAME_STOP_WORDS = Set.of(
            "le", "on", "the", "frais", "fee", "fees", "id", "ref", "reference", "solde", "nouveau", "new",
            "votre", "your", "transaction", "montant", "amount", "date", "est", "is", "a", "at", "reussi",
            "effectue", "successful", "pour", "for", "avec", "with");

    /** Capitalised words that are never a counterparty on their own. */
    private static final Set<String> NOT_A_NAME = Set.of(
            "fcfa", "xaf", "cfa", "mtn", "orange", "om", "momo", "mobile", "money", "yello", "y'ello",
            "mobile money", "orange money", "mtn mobile money", "mtn momo");

    /**
     * Reads an SMS.
     *
     * @param text the message as the user pasted it
     * @param hint the provider the user picked, or null to detect it from the text
     * @throws UnsupportedMessageException when the text is not a supported outgoing mobile money
     *         transaction, or its amount cannot be found
     */
    public ParsedSms parse(String text, MobileMoneyProvider hint)
    {
        if (text == null || text.isBlank())
        {
            throw new UnsupportedMessageException("Paste the text of a mobile money SMS.");
        }

        // Collapse whitespace first, on the original: from here on both strings have equal length.
        String original = text.replaceAll("\\s+", " ").trim();
        String folded = fold(original);

        MobileMoneyProvider provider = hint != null ? hint : detectProvider(folded);
        if (provider == null)
        {
            throw new UnsupportedMessageException(
                    "This does not look like an MTN MoMo or Orange Money message. Only those two are supported for now.");
        }

        if (RECEIVED.matcher(folded).find())
        {
            throw new UnsupportedMessageException(
                    "This message is for money you received, not money you spent. Add it as an income instead.");
        }

        Amounts amounts = readAmounts(folded);
        if (amounts.amount == null)
        {
            throw new UnsupportedMessageException(
                    "No amount was found in this message. Check that you pasted the whole SMS.");
        }

        LocalDate[] date = new LocalDate[1];
        LocalTime[] time = new LocalTime[1];
        readDateTime(folded, date, time);

        return new ParsedSms(
                provider,
                detectType(folded),
                Money.of(amounts.amount),
                amounts.fee == null ? null : Money.of(amounts.fee),
                readCounterparty(original, folded),
                readReference(folded),
                date[0],
                time[0]);
    }

    /**
     * Removes accents without changing the length: each character is replaced by its first
     * decomposed character, so "é" becomes "e" and "ç" becomes "c". Non-breaking spaces become
     * ordinary ones.
     */
    static String fold(String text)
    {
        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\u00A0' || c == '\u202F')
            {
                folded.append(' ');
                continue;
            }
            folded.append(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).charAt(0));
        }
        return folded.toString();
    }

    /**
     * When both names appear - an MTN message about a transfer to an Orange Money number, say -
     * the one mentioned first is the sender's own service.
     */
    private static MobileMoneyProvider detectProvider(String folded)
    {
        Matcher orange = ORANGE.matcher(folded);
        Matcher mtn = MTN.matcher(folded);
        int orangeAt = orange.find() ? orange.start() : Integer.MAX_VALUE;
        int mtnAt = mtn.find() ? mtn.start() : Integer.MAX_VALUE;

        if (orangeAt == Integer.MAX_VALUE && mtnAt == Integer.MAX_VALUE)
        {
            return null;
        }
        return orangeAt < mtnAt ? MobileMoneyProvider.ORANGE_MONEY : MobileMoneyProvider.MTN_MOMO;
    }

    private static MobileMoneyTransactionType detectType(String folded)
    {
        if (WITHDRAWAL.matcher(folded).find())
        {
            return MobileMoneyTransactionType.WITHDRAWAL;
        }
        if (PAYMENT.matcher(folded).find())
        {
            return MobileMoneyTransactionType.PAYMENT;
        }
        if (TRANSFER.matcher(folded).find())
        {
            return MobileMoneyTransactionType.TRANSFER;
        }
        return MobileMoneyTransactionType.UNKNOWN;
    }

    private record Amounts(BigDecimal amount, BigDecimal fee)
    {
    }

    /**
     * Sorts every amount in the message by the label just before it: after "Frais"/"Fee"/"Taxe"
     * it is a fee (several are added up), after "Solde"/"Balance" it is the balance and is
     * ignored, and the first one without a label is the transaction amount.
     *
     * <p>The label is looked for only between the previous amount and this one, so the "Frais"
     * of one amount is never taken as the label of the next.
     */
    private static Amounts readAmounts(String folded)
    {
        BigDecimal amount = null;
        BigDecimal fee = null;
        int previousEnd = 0;

        Matcher money = MONEY.matcher(folded);
        while (money.find())
        {
            String number = money.group(1) != null ? money.group(1) : money.group(4);
            BigDecimal value = toNumber(number);
            String before = folded.substring(Math.max(previousEnd, money.start() - LABEL_WINDOW), money.start());
            previousEnd = money.end();

            if (value == null)
            {
                continue;
            }
            if (BALANCE_LABEL.matcher(before).find() && !DEBIT_WORD.matcher(before).find())
            {
                continue;
            }
            if (FEE_LABEL.matcher(before).find())
            {
                fee = fee == null ? value : fee.add(value);
                continue;
            }
            if (amount == null && value.signum() > 0)
            {
                amount = value;
            }
        }
        return new Amounts(amount, fee);
    }

    /** "5 000" → 5000, "5.000" → 5000, "5 000,50" → 5000.50, "50.5" → 50.5. */
    static BigDecimal toNumber(String text)
    {
        String digits = text.replace(" ", "");
        String decimals = "";
        Matcher tail = Pattern.compile("[.,](\\d{1,2})$").matcher(digits);
        if (tail.find())
        {
            decimals = tail.group(1);
            digits = digits.substring(0, tail.start());
        }
        digits = digits.replace(".", "").replace(",", "");
        if (digits.isEmpty())
        {
            return null;
        }
        try
        {
            return new BigDecimal(decimals.isEmpty() ? digits : digits + "." + decimals);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static String readReference(String folded)
    {
        Matcher matcher = REFERENCE.matcher(folded);
        while (matcher.find())
        {
            // A trailing full stop ends the sentence, it is not part of the id.
            String reference = matcher.group(1).replaceAll("[.\\-]+$", "");
            // An id always has digits; this skips "Reference: Facture" and the like.
            if (reference.length() >= 4 && reference.chars().anyMatch(Character::isDigit))
            {
                return reference.length() > 64 ? reference.substring(0, 64) : reference;
            }
        }
        return null;
    }

    private static void readDateTime(String folded, LocalDate[] date, LocalTime[] time)
    {
        Matcher iso = ISO_DATE.matcher(folded);
        while (iso.find())
        {
            LocalDate found = toDate(iso.group(1), iso.group(2), iso.group(3));
            if (found != null)
            {
                date[0] = found;
                time[0] = toTime(iso.group(4), iso.group(5), iso.group(6));
                return;
            }
        }

        Matcher dayFirst = DAY_FIRST_DATE.matcher(folded);
        while (dayFirst.find())
        {
            String year = dayFirst.group(3).length() == 2 ? "20" + dayFirst.group(3) : dayFirst.group(3);
            LocalDate found = toDate(year, dayFirst.group(2), dayFirst.group(1));
            if (found != null)
            {
                date[0] = found;
                time[0] = toTime(dayFirst.group(4), dayFirst.group(5), dayFirst.group(6));
                return;
            }
        }
    }

    private static LocalDate toDate(String year, String month, String day)
    {
        try
        {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        }
        catch (DateTimeException | NumberFormatException e)
        {
            // 31/02 or a phone number fragment: not a date, keep looking.
            return null;
        }
    }

    private static LocalTime toTime(String hour, String minute, String second)
    {
        if (hour == null || minute == null)
        {
            return null;
        }
        try
        {
            return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute),
                    second == null ? 0 : Integer.parseInt(second));
        }
        catch (DateTimeException | NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * "JOHN DOE (677123456)", "ENEO" or just "677123456". The name is cut at the first word that
     * usually follows it ("le", "Frais", ...) and taken from the original text so it keeps its
     * accents.
     */
    private static String readCounterparty(String original, String folded)
    {
        Matcher matcher = COUNTERPARTY.matcher(folded);
        while (matcher.find())
        {
            String phone = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String name = null;
            if (matcher.group(2) != null)
            {
                name = trimName(original.substring(matcher.start(2), matcher.end(2)));
            }

            if (name == null && phone == null)
            {
                continue;
            }
            String counterparty = name == null ? phone : phone == null ? name : name + " (" + phone + ")";
            return counterparty.length() > 120 ? counterparty.substring(0, 120) : counterparty;
        }
        return null;
    }

    private static String trimName(String candidate)
    {
        List<String> words = List.of(candidate.split(" "));
        StringBuilder name = new StringBuilder();
        for (String word : words)
        {
            if (NAME_STOP_WORDS.contains(fold(word).toLowerCase(Locale.ROOT)))
            {
                break;
            }
            if (!name.isEmpty())
            {
                name.append(' ');
            }
            name.append(word);
        }
        String result = name.toString().trim();
        if (result.isEmpty() || NOT_A_NAME.contains(fold(result).toLowerCase(Locale.ROOT)))
        {
            return null;
        }
        return result;
    }
}
