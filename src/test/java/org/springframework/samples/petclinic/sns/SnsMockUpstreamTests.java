package org.springframework.samples.petclinic.sns;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class SnsMockUpstreamTests extends MockUpstreamTests {

	@Test
	void saoJoaoWasTheBusiestInAugust() {
		this.graphQl.document(
				"{ snsEmergencyVisitsInMonth(month: \"2026-08\", region: \"Região de Saúde Norte\") { hospital total } }")
			.execute()
			.path("snsEmergencyVisitsInMonth[0].hospital")
			.entity(String.class)
			.isEqualTo("Unidade Local de Saúde de São João, EPE")
			.path("snsEmergencyVisitsInMonth[0].total")
			.entity(Integer.class)
			.isEqualTo(21063);
	}

}
