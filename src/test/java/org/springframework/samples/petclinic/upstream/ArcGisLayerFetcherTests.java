package org.springframework.samples.petclinic.upstream;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable.Filter;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable.Kind;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable.Layer;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArcGisLayerFetcherTests {

	private static final String BASE = "https://sniambgeoogc.apambiente.pt/getogc/rest/services";

	private static final Layer BEACHES = new Layer("apaBathingWaters", "/Dashboard/Praias_dashboard/MapServer/0",
			"ApaBathingWater", List.of(new Filter("municipality", "concelho", Kind.EQUALS, true),
					new Filter("name", "nome_praia", Kind.CONTAINS, false)),
			attributes(), null);

	private static Map<String, FieldSource> attributes() {
		Map<String, FieldSource> attributes = new LinkedHashMap<>();
		attributes.put("name", new FieldSource("nome_praia", null));
		attributes.put("blueFlag", new FieldSource("bandeira_azul", ValueConversion.YES_NO));
		return attributes;
	}

	private final JsonMapper mapper = JsonMapper.builder().build();

	private MockRestServiceServer server;

	private ArcGisLayerFetcher fetcher;

	@BeforeEach
	void bindServer() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.fetcher = new ArcGisLayerFetcher("APA", BASE, builder.build(), BEACHES, this.mapper);
	}

	@Test
	void whereQuotesText() {
		assertThat(this.fetcher.where(Map.of("municipality", "O'Porto"))).isEqualTo("concelho = 'O''PORTO'");
		assertThat(this.fetcher.where(Map.of("name", "Leme"))).isEqualTo("UPPER(nome_praia) LIKE '%LEME%'");
		assertThat(this.fetcher.where(Map.of())).isEqualTo("1=1");
	}

	@Test
	void countOnlyWhenTotalCountSelected() throws Exception {
		this.server.expect(requestTo(org.hamcrest.Matchers.containsString("returnCountOnly=true")))
			.andRespond(withSuccess(fixture("arcgis-beaches-porto-count.json"), MediaType.APPLICATION_JSON));

		Map<String, Object> page = this.fetcher.page(Map.of("municipality", "Porto"), true, false);

		assertThat(page).containsEntry("totalCount", 10).containsEntry("items", null);
		this.server.verify();
	}

	@Test
	void itemsNeverAskForGeometry() throws Exception {
		this.server
			.expect(requestTo(
					org.hamcrest.Matchers.startsWith(BASE + "/Dashboard/Praias_dashboard/MapServer/0/query?")))
			.andExpect(queryParam("returnGeometry", "false"))
			.andExpect(queryParam("resultRecordCount", "20"))
			.andExpect(queryParam("outFields", "nome_praia,bandeira_azul"))
			.andRespond(withSuccess(fixture("arcgis-beaches-porto.json"), MediaType.APPLICATION_JSON));

		Map<String, Object> page = this.fetcher.page(Map.of("municipality", "Porto"), false, true);

		assertThat((List<?>) page.get("items")).hasSize(2);
		this.server.verify();
	}

	@Test
	void codedValuesBecomeBooleans() {
		assertThat(BEACHES.attributes().get("blueFlag").read(Map.of("bandeira_azul", 1))).isEqualTo(true);
		assertThat(BEACHES.attributes().get("blueFlag").read(Map.of("bandeira_azul", 0))).isEqualTo(false);
	}

	@Test
	void anErrorInTheAnswerIsBadRequest() throws Exception {
		this.server.expect(requestTo(org.hamcrest.Matchers.containsString("/query?")))
			.andRespond(withSuccess(fixture("arcgis-error.json"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> this.fetcher.page(Map.of(), false, true)).isInstanceOf(InvalidArgumentException.class)
			.hasMessage("APA rejected the arguments: Failed to execute query.");
	}

	private static String fixture(String name) throws Exception {
		return new ClassPathResource("upstream/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

}
