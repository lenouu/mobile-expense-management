package com.expensemanagement.backend.CaptureManagement.Parsers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReceiptTextParser
{
    private static final Pattern TOTAL = Pattern.compile(
            "\\b(?:TOTAL(?:\\s+TTC)?|MONTANT(?:\\s+TTC)?|NET\\s+A\\s+PAYER|AMOUNT\\s+DUE)\\b");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\d.,-])(\\d{1,3}(?:[ .,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)(?![\\d.,])");
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(?:(\\d{4})-(\\d{2})-(\\d{2})|(\\d{1,2})([/.-])(\\d{1,2})\\5(\\d{4}|\\d{2}))(?!\\d)");
    private static final Pattern MERCHANT_JUNK = Pattern.compile(
            "\\b(?:RECU|FACTURE|TICKET|TEL(?:EPHONE)?|DATE)\\b");

    public record ParsedReceipt(BigDecimal amount, LocalDate date, String merchant)
    {
        public double confidence()
        {
            return (amount == null ? 0 : 0.5) + (date == null ? 0 : 0.25) + (merchant == null ? 0 : 0.25);
        }
    }

    public ParsedReceipt parse(String text)
    {
        if (text == null || text.isBlank())
        {
            return new ParsedReceipt(null, null, null);
        }
        String folded = MobileMoneySmsParser.fold(text).toUpperCase(Locale.ROOT);
        BigDecimal amount = null;
        for (String line : folded.split("\\R"))
        {
            Matcher total = TOTAL.matcher(line);
            if (total.find())
            {
                amount = null;
                Matcher number = NUMBER.matcher(line.substring(total.end()));
                if (number.find())
                {
                    BigDecimal candidate = MobileMoneySmsParser.toNumber(number.group(1));
                    if (candidate != null && candidate.signum() > 0
                            && candidate.compareTo(new BigDecimal("9999999999.99")) <= 0)
                    {
                        amount = candidate.setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
        }
        return new ParsedReceipt(amount, readDate(folded), readMerchant(text));
    }

    private static LocalDate readDate(String text)
    {
        Matcher matcher = DATE.matcher(text);
        while (matcher.find())
        {
            try
            {
                boolean iso = matcher.group(1) != null;
                String yearText = iso ? matcher.group(1) : matcher.group(7);
                int year = Integer.parseInt(yearText.length() == 2 ? "20" + yearText : yearText);
                int month = Integer.parseInt(iso ? matcher.group(2) : matcher.group(6));
                int day = Integer.parseInt(iso ? matcher.group(3) : matcher.group(4));
                LocalDate date = LocalDate.of(year, month, day);
                if (!date.isAfter(LocalDate.now()))
                {
                    return date;
                }
            }
            catch (DateTimeException ignored)
            {
            }
        }
        return null;
    }

    private static String readMerchant(String text)
    {
        for (String line : text.split("\\R"))
        {
            String trimmed = line.strip();
            String folded = MobileMoneySmsParser.fold(trimmed).toUpperCase(Locale.ROOT);
            if (trimmed.codePoints().filter(Character::isLetter).count() >= 3
                    && !MERCHANT_JUNK.matcher(folded).find() && !TOTAL.matcher(folded).find())
            {
                return trimmed.substring(0, Math.min(100, trimmed.length()));
            }
        }
        return null;
    }
}
