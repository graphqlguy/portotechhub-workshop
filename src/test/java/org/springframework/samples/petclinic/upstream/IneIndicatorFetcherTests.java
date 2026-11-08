package org.springframework.samples.petclinic.upstream;

import java.util.List;
import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Dimension;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Indicator;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Period;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IneIndicatorFetcherTests {

	private static final String BASE = "https://www.ine.pt";

	private static final Indicator POPULATION = new Indicator("inePopulation", "0008273", Period.YEAR,
			List.of(new Dimension("place", "Dim2", "PT"), new Dimension("sex", "Dim3", "T"),
					new Dimension("ageGroup", "Dim4", "T")));

	private static final Indicator PRICES = new Indicator("ineConsumerPriceChange", "0014647", Period.MONTH,
			List.of(new Dimension("place", "Dim2", "PT"), new Dimension("category", "Dim3", "T")));

	private final JsonMapper mapper = JsonMapper.builder().build();

	private MockRestServiceServer server;

	private RestClient client;

	@BeforeEach
	void bindServer() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.client = builder.build();
	}

	@Test
	void flattensDados() throws Exception {
		this.server.expect(requestTo(BASE
				+ "/ine/json_indicador/pindica.jsp?op=2&varcd=0008273&lang=EN&Dim1=S7A2023&Dim2=11A1312&Dim3=T&Dim4=T"))
			.andRespond(withSuccess(fixture("ine-population-porto-2023.json"), MediaType.APPLICATION_JSON));

		List<Map<String, Object>> rows = fetcher(POPULATION).get(arguments(Map.of("year", 2023, "place", "11A1312")));

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("period", "2023")
			.containsEntry("placeCode", "11A1312")
			.containsEntry("place", "Porto")
			.containsEntry("value", 267236.0)
			.containsEntry("valueText", "267 236");
	}

	@Test
	void yearSendsS7A() {
		assertThat(fetcher(POPULATION).uri(Map.of("year", 2023)).toString()).contains("&Dim1=S7A2023&");
	}

	@Test
	void monthSendsS3A() {
		assertThat(fetcher(PRICES).uri(Map.of("month", "2026-09")).toString()).contains("&Dim1=S3A202609&");
	}

	@Test
	void allPeriodsSendsT() {
		assertThat(fetcher(POPULATION).uri(Map.of("allPeriods", true)).toString()).contains("&Dim1=T&");
	}

	@Test
	void noPeriodSendsNoDim1() {
		assertThat(fetcher(POPULATION).uri(Map.of()).toString()).doesNotContain("Dim1=")
			.contains("&Dim2=PT&Dim3=T&Dim4=T");
	}

	@Test
	void invalidCodeIsBadRequest() throws Exception {
		this.server
			.expect(requestTo(
					BASE + "/ine/json_indicador/pindica.jsp?op=2&varcd=0008273&lang=EN&Dim2=X" + "&Dim3=T&Dim4=T"))
			.andRespond(withSuccess(fixture("ine-invalid-code.json"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> fetcher(POPULATION).get(arguments(Map.of("place", "X"))))
			.isInstanceOf(InvalidArgumentException.class)
			.hasMessage(
					"INE rejected the arguments: Code(s) in Dim2 not valid for the indicator. Dim2 is the argument place.");
	}

	@Test
	void aYearINEDoesNotHaveSaysToLeaveItOut() throws Exception {
		this.server
			.expect(requestTo(BASE
					+ "/ine/json_indicador/pindica.jsp?op=2&varcd=0008273&lang=EN&Dim1=S7A2026&Dim2=PT&Dim3=T&Dim4=T"))
			.andRespond(withSuccess(fixture("ine-invalid-period.json"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> fetcher(POPULATION).get(arguments(Map.of("year", 2026))))
			.isInstanceOf(InvalidArgumentException.class)
			.hasMessage("INE rejected the arguments: Code(s) in Dim1 not valid for the indicator. "
					+ "Dim1 is the year: leave it out for the latest year.");
	}

	@Test
	void aMonthINEDoesNotHaveSaysToLeaveItOut() throws Exception {
		this.server
			.expect(requestTo(
					BASE + "/ine/json_indicador/pindica.jsp?op=2&varcd=0014647&lang=EN&Dim1=S3A202611&Dim2=PT&Dim3=T"))
			.andRespond(withSuccess(fixture("ine-invalid-period.json"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> fetcher(PRICES).get(arguments(Map.of("month", "2026-11"))))
			.isInstanceOf(InvalidArgumentException.class)
			.hasMessageEndingWith("Dim1 is the month: leave it out for the latest month.");
	}

	@Test
	void placesFindsPortoByName() throws Exception {
		List<Map<String, Object>> places = IneIndicatorTable
			.places(this.mapper.readValue(fixture("ine-meta-0008273.json"), Object.class), "2", "porto");

		assertThat(places)
			.anySatisfy((place) -> assertThat(place).containsEntry("code", "11A1312").containsEntry("name", "Porto"));
	}

	@Test
	void aPlaceNameBecomesItsCode() throws Exception {
		Object metadata = this.mapper.readValue(fixture("ine-meta-0008273.json"), Object.class);
		IneIndicatorFetcher fetcher = new IneIndicatorFetcher("INE", BASE, this.client, POPULATION, this.mapper,
				() -> metadata);

		assertThat(fetcher.uri(Map.of("year", 2023, "place", "Porto")).toString()).contains("&Dim2=11A1312&");
		assertThat(fetcher.uri(Map.of("year", 2023, "place", "porto")).toString()).contains("&Dim2=11A1312&");
		assertThat(fetcher.uri(Map.of("year", 2023, "place", "11A1312")).toString()).contains("&Dim2=11A1312&");
		assertThat(fetcher.uri(Map.of("year", 2023, "place", "PT")).toString()).contains("&Dim2=PT&");
	}

	private static final Indicator IMPORTS = new Indicator("ineImports", "0005719", Period.YEAR,
			List.of(new Dimension("place", "Dim2", null), new Dimension("importedProduct", "Dim3", "T")));

	private static final Map<String, List<List<String>>> PRODUCTS = Map.of("Dim3",
			List.of(List.of("T", "Total"), List.of("2309", "Preparations of a kind used in animal feeding"),
					List.of("230910", "Dog or cat food, put up for retail sale")));

	@Test
	void aCategoryNameBecomesItsCode() {
		IneIndicatorFetcher fetcher = new IneIndicatorFetcher("INE", BASE, this.client, IMPORTS, this.mapper, null,
				PRODUCTS);

		assertThat(fetcher.uri(Map.of("year", 2025, "importedProduct", "dog and cat food")).toString())
			.contains("&Dim3=230910");
		assertThat(fetcher.uri(Map.of("year", 2025, "importedProduct", "230910")).toString()).contains("&Dim3=230910");
	}

	@Test
	void allMeansTheTotal() {
		assertThat(fetcher(POPULATION).uri(Map.of("year", 2023, "sex", "all", "ageGroup", "Total")).toString())
			.contains("&Dim3=T&Dim4=T");
	}

	@Test
	void anUnknownCategoryNameIsBadRequest() {
		IneIndicatorFetcher fetcher = new IneIndicatorFetcher("INE", BASE, this.client, IMPORTS, this.mapper, null,
				PRODUCTS);

		assertThatThrownBy(() -> fetcher.uri(Map.of("importedProduct", "spaceships")))
			.isInstanceOf(InvalidArgumentException.class)
			.hasMessageContaining("importedProduct");
	}

	private IneIndicatorFetcher fetcher(Indicator indicator) {
		return new IneIndicatorFetcher("INE", BASE, this.client, indicator, this.mapper);
	}

	private static DataFetchingEnvironment arguments(Map<String, Object> arguments) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}

	private static String fixture(String name) throws Exception {
		return new ClassPathResource("upstream/" + name).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

}
