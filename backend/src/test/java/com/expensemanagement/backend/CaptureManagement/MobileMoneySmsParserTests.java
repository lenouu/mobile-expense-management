package com.expensemanagement.backend.CaptureManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyProvider;
import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyTransactionType;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser.ParsedSms;
import com.expensemanagement.backend.Exceptions.UnsupportedMessageException;

/**
 * S6-TECH-2: reading MTN MoMo and Orange Money SMS. Plain unit tests, no database.
 *
 * <p>The messages below follow the usual wording of each operator in French and English, with
 * made-up names, numbers and ids. When real messages from the team's phones are collected, add
 * each one here as its own test: that is how a format change gets noticed.
 */
class MobileMoneySmsParserTests {

	private final MobileMoneySmsParser parser = new MobileMoneySmsParser();

	@Test
	void mtnPaymentInFrench() {
		ParsedSms sms = parser.parse("Y'ello! Vous avez payé 5 000 FCFA à ENEO le 12/05/2026 à 10:22:33. Frais: 0 FCFA. "
				+ "Nouveau solde: 12 450 FCFA. ID de transaction: 8945123301.", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.MTN_MOMO);
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.PAYMENT);
		assertThat(sms.amount().amount()).isEqualByComparingTo("5000");
		assertThat(sms.amount().currency()).isEqualTo("XAF");
		assertThat(sms.fee().amount()).isEqualByComparingTo("0");
		assertThat(sms.counterparty()).isEqualTo("ENEO");
		assertThat(sms.reference()).isEqualTo("8945123301");
		assertThat(sms.date()).isEqualTo(LocalDate.of(2026, 5, 12));
		assertThat(sms.time()).isEqualTo(LocalTime.of(10, 22, 33));
	}

	@Test
	void mtnTransferInEnglishWithTheCurrencyBeforeTheAmount() {
		ParsedSms sms = parser.parse("MTN MoMo: You have sent XAF 15,000 to JOHN DOE (237677123456) on 2026-05-10 18:04:12. "
				+ "Fee: XAF 150. Your new balance: XAF 3,200. Financial Transaction Id: 7781234567.", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.MTN_MOMO);
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.TRANSFER);
		assertThat(sms.amount().amount()).isEqualByComparingTo("15000");
		assertThat(sms.fee().amount()).isEqualByComparingTo("150");
		assertThat(sms.counterparty()).isEqualTo("JOHN DOE (677123456)");
		assertThat(sms.reference()).isEqualTo("7781234567");
		assertThat(sms.date()).isEqualTo(LocalDate.of(2026, 5, 10));
		assertThat(sms.time()).isEqualTo(LocalTime.of(18, 4, 12));
	}

	@Test
	void orangeTransferKeepsAccentsInTheNameAndHasNoDate() {
		ParsedSms sms = parser.parse("Orange Money: Transfert de 20000 FCFA vers le 237699887766 ESSOMBA Hélène réussi. "
				+ "Frais: 200 FCFA. Nouveau solde: 5000 FCFA. ID Transaction: CI260511.1530.B98765.", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.ORANGE_MONEY);
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.TRANSFER);
		assertThat(sms.amount().amount()).isEqualByComparingTo("20000");
		assertThat(sms.fee().amount()).isEqualByComparingTo("200");
		assertThat(sms.counterparty()).isEqualTo("ESSOMBA Hélène (699887766)");
		assertThat(sms.reference()).isEqualTo("CI260511.1530.B98765");
		// Blank, not guessed: the message has no date, so none is invented.
		assertThat(sms.date()).isNull();
		assertThat(sms.time()).isNull();
	}

	@Test
	void orangeMerchantPaymentInEnglish() {
		ParsedSms sms = parser.parse("Orange Money. Payment of 7 500 FCFA to merchant SUPERMARCHE MAHIMA successful on "
				+ "09/05/2026 14:12. Transaction ID: MP260509.1412.C11223. New balance: 42 500 FCFA.", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.ORANGE_MONEY);
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.PAYMENT);
		assertThat(sms.amount().amount()).isEqualByComparingTo("7500");
		assertThat(sms.fee()).isNull();
		assertThat(sms.counterparty()).isEqualTo("SUPERMARCHE MAHIMA");
		assertThat(sms.reference()).isEqualTo("MP260509.1412.C11223");
		assertThat(sms.date()).isEqualTo(LocalDate.of(2026, 5, 9));
		assertThat(sms.time()).isEqualTo(LocalTime.of(14, 12));
	}

	@Test
	void orangeWithdrawalWithoutAReadableCounterparty() {
		ParsedSms sms = parser.parse("Retrait de 10 000 FCFA effectue avec succes chez l'agent le 08/05/2026 a 09:15. "
				+ "Frais: 300 FCFA. Solde: 1 250 FCFA. Orange Money. Ref: RT260508.0915.D44556", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.ORANGE_MONEY);
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.WITHDRAWAL);
		assertThat(sms.amount().amount()).isEqualByComparingTo("10000");
		assertThat(sms.fee().amount()).isEqualByComparingTo("300");
		assertThat(sms.counterparty()).isNull();
		assertThat(sms.reference()).isEqualTo("RT260508.0915.D44556");
		assertThat(sms.date()).isEqualTo(LocalDate.of(2026, 5, 8));
		assertThat(sms.time()).isEqualTo(LocalTime.of(9, 15));
	}

	@Test
	void feesAndTaxesAreAddedUpAndTheBalanceIsIgnored() {
		ParsedSms sms = parser.parse("MTN MoMo: Transfert de 50 000 FCFA a PAUL NGONO. Frais: 500 FCFA. Taxe: 100 FCFA. "
				+ "Nouveau solde: 999 999 FCFA.", null);

		assertThat(sms.amount().amount()).isEqualByComparingTo("50000");
		assertThat(sms.fee().amount()).isEqualByComparingTo("600");
		assertThat(sms.counterparty()).isEqualTo("PAUL NGONO");
	}

	@Test
	void anAmountDebitedFromTheBalanceIsTheTransactionAmount() {
		ParsedSms sms = parser.parse("Orange Money: votre solde a ete debite de 4 000 FCFA. Nouveau solde: 6 000 FCFA.", null);

		assertThat(sms.amount().amount()).isEqualByComparingTo("4000");
		// Nothing says what kind of transaction this was: left for the user to decide.
		assertThat(sms.type()).isEqualTo(MobileMoneyTransactionType.UNKNOWN);
	}

	@Test
	void aTimeJustBeforeAnAmountIsNotReadAsPartOfIt() {
		ParsedSms sms = parser.parse("MTN MoMo paiement le 12/05/2026 10:22 500 FCFA a CAMWATER", null);

		assertThat(sms.amount().amount()).isEqualByComparingTo("500");
	}

	@Test
	void whenBothServicesAreNamedTheFirstOneIsTheSender() {
		ParsedSms sms = parser.parse("MTN MoMo: Transfert de 3000 FCFA vers un compte Orange Money 237699000111.", null);

		assertThat(sms.provider()).isEqualTo(MobileMoneyProvider.MTN_MOMO);
	}

	@Test
	void theProviderTheUserPickedIsUsedWhenTheTextDoesNotNameOne() {
		String text = "Paiement de 3000 FCFA a CAMWATER. ID transaction: 1234567890";

		assertThatThrownBy(() -> parser.parse(text, null)).isInstanceOf(UnsupportedMessageException.class);
		assertThat(parser.parse(text, MobileMoneyProvider.ORANGE_MONEY).provider())
			.isEqualTo(MobileMoneyProvider.ORANGE_MONEY);
	}

	@Test
	void moneyReceivedIsRefused() {
		assertThatThrownBy(() -> parser.parse("MTN MoMo: Vous avez reçu 25 000 FCFA de PAUL NGONO (237677000111) "
				+ "le 07/05/2026. Nouveau solde: 30 000 FCFA. ID: 5566778899", null))
			.isInstanceOf(UnsupportedMessageException.class)
			.hasMessageContaining("income");
	}

	@Test
	void aMessageThatIsNotMobileMoneyIsRefused() {
		assertThatThrownBy(() -> parser.parse("Bonjour, votre colis de 2 articles arrive demain.", null))
			.isInstanceOf(UnsupportedMessageException.class)
			.hasMessageContaining("MTN MoMo or Orange Money");
	}

	@Test
	void aMessageWithoutAnAmountIsRefused() {
		assertThatThrownBy(() -> parser.parse("MTN MoMo: votre transaction a echoue. Reessayez plus tard.", null))
			.isInstanceOf(UnsupportedMessageException.class)
			.hasMessageContaining("No amount");
	}

	@Test
	void anImpossibleDateIsLeftBlank() {
		ParsedSms sms = parser.parse("Orange Money: Paiement de 1000 FCFA le 31/02/2026.", null);

		assertThat(sms.date()).isNull();
	}

	@Test
	void amountsAreReadInEveryUsualNotation() {
		assertThat(number("5 000")).isEqualByComparingTo("5000");
		assertThat(number("5.000")).isEqualByComparingTo("5000");
		assertThat(number("5,000")).isEqualByComparingTo("5000");
		assertThat(number("1.234.567")).isEqualByComparingTo("1234567");
		assertThat(number("5000.50")).isEqualByComparingTo("5000.50");
		assertThat(number("5 000,50")).isEqualByComparingTo("5000.50");
		assertThat(number("12,5")).isEqualByComparingTo("12.5");
	}

	private BigDecimal number(String amount) {
		return parser.parse("MTN MoMo paiement de " + amount + " FCFA", null).amount().amount();
	}

}
