package org.springframework.samples.petclinic.upstream;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.upstream.OpenDataSoftTable.Dataset;
import org.springframework.samples.petclinic.upstream.OpenDataSoftTable.Filter;
import org.springframework.samples.petclinic.upstream.OpenDataSoftTable.Kind;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenDataSoftFetcherTests {

	private static final String BASE = "https://transparencia.sns.gov.pt";

	private static final Dataset EMERGENCY = new Dataset("snsEmergencyVisitsByTriage",
			"atendimentos-em-urgencia-triagem-manchester", "SnsEmergencyVisitsByTriage",
			List.of(new Filter("region", "regiao", Kind.TEXT, "="), new Filter("from", "tempo", Kind.MONTH, ">="),
					new Filter("to", "tempo", Kind.MONTH, "<=")),
			"tempo desc", Map.of());

	private final JsonMapper mapper = JsonMapper.builder().build();

	private MockRestServiceServer server;

	private OpenDataSoftFetcher fetcher;

	@BeforeEach
	void bindServer() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.fetcher = new OpenDataSoftFetcher("SNS", BASE, builder.build(), EMERGENCY, this.mapper);
	}

	@Test
	void whereJoinsFilters() {
		assertThat(this.fetcher.where(Map.of("region", "Região de Saúde Norte", "from", "2026-07")))
			.isEqualTo("regiao = \"Região de Saúde Norte\" and tempo >= date'2026-07'");
		assertThat(this.fetcher.where(Map.of())).isNull();
	}

	@Test
	void limitCapsAt100() throws Exception {
		this.server
			.expect(requestTo(Matchers.startsWith(
					BASE + "/api/explore/v2.1/catalog/datasets/atendimentos-em-urgencia-triagem-manchester/records?")))
			.andExpect(queryParam("limit", "100"))
			.andExpect(queryParam("order_by", "tempo%20desc"))
			.andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

		this.fetcher.page(Map.of("first", 500));

		this.server.verify();
	}

	@Test
	void totalCountFromTotalCount() throws Exception {
		this.server.expect(requestTo(Matchers.containsString("/records?")))
			.andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

		Map<String, Object> page = this.fetcher.page(Map.of("region", "Região de Saúde Norte", "from", "2026-07"));

		assertThat(page).containsEntry("totalCount", 28);
		assertThat((List<?>) page.get("items")).hasSize(2);
	}

	private static String fixture() throws Exception {
		return new ClassPathResource("upstream/opendatasoft-sns-emergency.json")
			.getContentAsString(StandardCharsets.UTF_8);
	}

}
