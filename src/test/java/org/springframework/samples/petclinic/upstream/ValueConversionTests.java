package org.springframework.samples.petclinic.upstream;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueConversionTests {

	@Test
	void readsAListOfObjectsWithOneKeyAsTexts() {
		Object value = List.of(Map.of("organism", "Educación"), Map.of("organism", "Salud"));

		assertThat(ValueConversion.TEXT_LIST.apply(value)).isEqualTo(List.of("Educación", "Salud"));
	}

	@Test
	void readsAnObjectWhoseFirstValueIsNullAsNull() {
		// some datasets send documents such as {"document": null} when a link is missing
		Map<String, Object> missing = new LinkedHashMap<>();
		missing.put("document", null);

		assertThat(ValueConversion.TEXT_LIST.apply(List.of(missing, Map.of("document", "https://example.org/1"))))
			.isEqualTo(Arrays.asList(null, "https://example.org/1"));
	}

	@Test
	void readsATextInALanguage() {
		assertThat(ValueConversion.TEXT_LIST.apply(Map.of("_value", "Perros", "_lang", "es")))
			.isEqualTo(List.of("Perros"));
	}

	@Test
	void readsADecimalComma() {
		assertThat(ValueConversion.DECIMAL_COMMA.apply("1,459")).isEqualTo(1.459);
		assertThat(ValueConversion.DECIMAL_COMMA.apply("14.925.217,81")).isEqualTo(14925217.81);
		assertThat(ValueConversion.DECIMAL_COMMA.apply("36.72")).isEqualTo(36.72);
		assertThat(ValueConversion.DECIMAL_COMMA.apply("4,08")).isEqualTo(4.08);
	}

	@Test
	void readsAPortugueseYesOrNo() {
		assertThat(ValueConversion.YES_NO.apply("Sim")).isEqualTo(true);
		assertThat(ValueConversion.YES_NO.apply("Não")).isEqualTo(false);
		assertThat(ValueConversion.YES_NO.apply("NAO")).isEqualTo(false);
	}

	@Test
	void readsAPriceInEuros() {
		// DGEG writes prices as "1,869 €"
		assertThat(ValueConversion.EURO_PRICE.apply("1,869 €")).isEqualTo(1.869);
		assertThat(ValueConversion.EURO_PRICE.apply("2,149 €/litro")).isEqualTo(2.149);
	}

	@Test
	void readsAnIntegerWithSpacesBetweenThousands() {
		// INE writes "267 236" in its ind_string
		assertThat(ValueConversion.SPACED_INTEGER.apply("267 236")).isEqualTo(267236);
		assertThat(ValueConversion.SPACED_INTEGER.apply("97")).isEqualTo(97);
	}

	@Test
	void readsMillisecondsAsTheDayInLisbon() {
		// APA sends the start of the bathing season as milliseconds: midnight in Lisbon
		// in summer
		assertThat(ValueConversion.EPOCH_MILLIS_DATE.apply(1781308800000L)).isEqualTo(LocalDate.of(2026, 6, 13));
	}

	@Test
	void wrapsASingleObjectInAList() {
		Map<String, String> municipality = Map.of("codigo", "JA01290022");

		assertThat(ValueConversion.LIST.apply(municipality)).isEqualTo(List.of(municipality));
		assertThat(ValueConversion.LIST.apply(List.of(municipality))).isEqualTo(List.of(municipality));
	}

}
