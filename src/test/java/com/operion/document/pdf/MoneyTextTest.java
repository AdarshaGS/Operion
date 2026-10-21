package com.operion.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTextTest {

	@Test
	void rupeesUseIndianGroupingAndLakhCroreWords() {
		assertThat(MoneyText.format(new BigDecimal("12345678.5"), "INR")).isEqualTo("₹ 1,23,45,678.50");
		assertThat(MoneyText.format(new BigDecimal("999"), "INR")).isEqualTo("₹ 999.00");
		assertThat(MoneyText.inWords(new BigDecimal("12345678.50"), "INR"))
				.isEqualTo("Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight and Fifty Paise Only");
		assertThat(MoneyText.inWords(new BigDecimal("4500"), "INR")).isEqualTo("Rupees Four Thousand Five Hundred Only");
		assertThat(MoneyText.inWords(BigDecimal.ZERO, null)).isEqualTo("Rupees Zero Only");
	}

	@Test
	void otherCurrenciesUseInternationalGroupingAndTheIsoCode() {
		assertThat(MoneyText.format(new BigDecimal("1234567.25"), "USD")).isEqualTo("$ 1,234,567.25");
		assertThat(MoneyText.inWords(new BigDecimal("1234567.25"), "usd"))
				.isEqualTo("One Million Two Hundred Thirty Four Thousand Five Hundred Sixty Seven and 25/100 USD Only");
	}
}
