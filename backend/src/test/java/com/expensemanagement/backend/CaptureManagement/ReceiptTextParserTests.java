package com.expensemanagement.backend.CaptureManagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.expensemanagement.backend.CaptureManagement.Parsers.ReceiptTextParser;
import com.expensemanagement.backend.CaptureManagement.Parsers.ReceiptTextParser.ParsedReceipt;

class ReceiptTextParserTests {

    private final ReceiptTextParser parser = new ReceiptTextParser();

    @Test
    void parsesCompleteEnglishReceipt() {
        ParsedReceipt receipt = parser.parse("""
                Corner Market
                DATE: 12/05/2024
                Bread 3.00
                Milk 2.00
                TOTAL 5.00
                """);

        assertThat(receipt.amount()).isEqualTo(new BigDecimal("5.00"));
        assertThat(receipt.date()).isEqualTo(LocalDate.of(2024, 5, 12));
        assertThat(receipt.merchant()).isEqualTo("Corner Market");
        assertThat(receipt.confidence()).isEqualTo(1.0);
    }

    @Test
    void parsesFrenchNetAPayerInFcfa() {
        ParsedReceipt receipt = parser.parse("""
                RECU
                Épicerie du Centre
                DATE: 12/05/2024
                NET A PAYER 12 500 FCFA
                """);

        assertThat(receipt.amount()).isEqualTo(new BigDecimal("12500.00"));
        assertThat(receipt.date()).isEqualTo(LocalDate.of(2024, 5, 12));
        assertThat(receipt.merchant()).isEqualTo("Épicerie du Centre");
        assertThat(receipt.confidence()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({
            "'12.500', 12500.00",
            "'12,500.00', 12500.00",
            "'12500,50', 12500.50"
    })
    void parsesAmountSeparatorVariants(String amount, BigDecimal expected) {
        assertThat(parser.parse("TOTAL " + amount + " FCFA").amount()).isEqualTo(expected);
    }

    @Test
    void selectsLastTotalLineRatherThanLargestAmount() {
        ParsedReceipt receipt = parser.parse("TOTAL 999.00\nMONTANT TTC 60.00\nAMOUNT DUE 42.50");

        assertThat(receipt.amount()).isEqualTo(new BigDecimal("42.50"));
    }

    @Test
    void unreadableLastTotalDoesNotFallBackToEarlierTotal() {
        assertThat(parser.parse("TOTAL 60.00\nTOTAL TTC unreadable").amount()).isNull();
    }

    @Test
    void doesNotGuessAmountWithoutTotalKeyword() {
        assertThat(parser.parse("Market\nItem 12500\nCash 99999\nChange 2500").amount()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12/05/2024", "12-05-2024", "12.05.2024", "12/05/24", "2024-05-12"})
    void parsesSupportedDateFormats(String date) {
        assertThat(parser.parse("DATE: " + date).date()).isEqualTo(LocalDate.of(2024, 5, 12));
    }

    @Test
    void choosesFirstValidDateInTextOrderNotEarliestCalendarDate() {
        ParsedReceipt receipt = parser.parse("DATE: 31/02/2024\nDATE: 2024-05-20\nDATE: 03/01/2020");

        assertThat(receipt.date()).isEqualTo(LocalDate.of(2024, 5, 20));
    }

    @Test
    void ignoresFutureDatesAndContinuesToValidPastDate() {
        String future = LocalDate.now().plusDays(1).toString();

        assertThat(parser.parse("DATE: " + future).date()).isNull();
        assertThat(parser.parse("DATE: " + future + "\nDATE: 12/05/2024").date())
                .isEqualTo(LocalDate.of(2024, 5, 12));
    }

    @Test
    void skipsReceiptHeadersPhonesNumbersAndTotalWhenFindingMerchant() {
        ParsedReceipt receipt = parser.parse("""
                RECU
                FACTURE 123
                TICKET
                TEL: +237 677 123 456
                DATE: 12/05/2024
                +237 699 888 777
                123456789
                TOTAL 10
                  Épicerie du Centre
                """);

        assertThat(receipt.merchant()).isEqualTo("Épicerie du Centre");
    }

    @Test
    void capsMerchantAtOneHundredCharacters() {
        String merchant = "Marché " + "x".repeat(120);

        assertThat(parser.parse(merchant).merchant()).hasSize(100).isEqualTo(merchant.substring(0, 100));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void emptyInputHasNoFieldsAndZeroConfidence(String text) {
        ParsedReceipt receipt = parser.parse(text);

        assertThat(receipt.amount()).isNull();
        assertThat(receipt.date()).isNull();
        assertThat(receipt.merchant()).isNull();
        assertThat(receipt.confidence()).isZero();
    }
}
