package org.springframework.samples.petclinic.sns;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;

class SnsEmergencyVisitsTests {

	private final SnsEmergencyVisits visits = new SnsEmergencyVisits("SNS", "https://transparencia.sns.gov.pt",
			new Fixtures().answer("atendimentos-em-urgencia-triagem-manchester", "sns-emergency-2026-07-08-norte.json")
				.client(),
			JsonMapper.builder().build());

	@Test
	void augustIsAugustMinusJuly() {
		List<SnsHospitalEmergencyVisits> august = this.visits.inMonth("2026-08", "Região de Saúde Norte");

		assertThat(august).hasSize(14);
		assertThat(august.get(0).hospital()).contains("São João");
		assertThat(august.get(0).total()).isEqualTo(21063);
		assertThat(august.get(0).month()).isEqualTo("2026-08");
	}

	@Test
	void theRegionIsFoundByItsWords() {
		assertThat(this.visits.uri("2026-08", "Norte").toString()).contains("search(regiao,%20%22Norte%22)");
	}

	@Test
	void theQueryAsksForTheMonthAndTheOneBefore() {
		assertThat(this.visits.uri("2026-08", null).toString()).contains("date'2026-07'").contains("date'2026-08'");
		assertThat(this.visits.uri("2026-01", null).toString()).contains("date'2026-01'")
			.doesNotContain("date'2025-12'");
	}

}
