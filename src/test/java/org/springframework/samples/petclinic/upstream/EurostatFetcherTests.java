package org.springframework.samples.petclinic.upstream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.samples.petclinic.upstream.EurostatTable.Dataset;
import org.springframework.samples.petclinic.upstream.EurostatTable.Dimension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EurostatFetcherTests {

	private static final String BASE = "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data";

	private static final Dataset PRICES = new Dataset(
			"eurostatConsumerPriceChange", "prc_hicp_minr", List.of(new Dimension("countries", "geo", true),
					new Dimension("category", "coicop18", false), new Dimension("month", "time", false)),
			Map.of("unit", "RCH_A"));

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void flattensJsonStat() throws Exception {
		Object answer = this.mapper.readValue(new ClassPathResource("upstream/eurostat-hicp-pet-services.json")
			.getContentAsString(StandardCharsets.UTF_8), Object.class);

		List<Map<String, Object>> rows = EurostatFetcher.flatten(answer);

		assertThat(rows).hasSize(2);
		assertThat(rows).anySatisfy((row) -> assertThat(row).containsEntry("country", "PT")
			.containsEntry("countryName", "Portugal")
			.containsEntry("time", "2026-09")
			.containsEntry("value", 4.1));
		assertThat(rows)
			.anySatisfy((row) -> assertThat(row).containsEntry("country", "EU27_2020").containsEntry("value", 3.3));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> categories = (List<Map<String, Object>>) rows.get(0).get("categories");
		assertThat(categories).anySatisfy((category) -> assertThat(category).containsEntry("code", "CP0945")
			.containsEntry("label", "Veterinary and other services for pets (S)"));
	}

	@Test
	void listArgumentRepeatsTheParameter() {
		String uri = fetcher().uri(Map.of("countries", List.of("PT", "EU27_2020"), "month", "2026-09")).toString();

		assertThat(uri).startsWith(BASE + "/prc_hicp_minr?")
			.contains("geo=PT&geo=EU27_2020")
			.contains("time=2026-09")
			.contains("unit=RCH_A")
			.contains("lang=en");
	}

	@Test
	void countryNamesBecomeCodes() {
		String uri = fetcher().uri(Map.of("countries", List.of("Portugal", "European Union", "spain"))).toString();

		assertThat(uri).contains("geo=PT&geo=EU27_2020&geo=ES");
	}

	@Test
	void latestPeriodsSendsLastTimePeriod() {
		assertThat(fetcher().uri(Map.of("latestPeriods", 3)).toString()).contains("lastTimePeriod=3");
	}

	@Test
	void aMonthWithLatestPeriodsIsBadRequest() {
		assertThatThrownBy(() -> fetcher().uri(Map.of("month", "2026-11", "latestPeriods", 1)))
			.isInstanceOf(InvalidArgumentException.class)
			.hasMessage("Give month or latestPeriods: month asks for one month, and latestPeriods for the newest months.");
	}

	private EurostatFetcher fetcher() {
		return new EurostatFetcher("Eurostat", BASE, RestClient.create(), PRICES, this.mapper);
	}

}
