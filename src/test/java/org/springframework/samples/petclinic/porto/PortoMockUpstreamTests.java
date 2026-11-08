package org.springframework.samples.petclinic.porto;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class PortoMockUpstreamTests extends MockUpstreamTests {

	@Test
	void theFastestBusRanLine803() {
		this.graphQl.document("{ portoBusesNow(fastestFirst: true, first: 3) { line speedKilometresPerHour } }")
			.execute()
			.path("portoBusesNow[0].line")
			.entity(String.class)
			.isEqualTo("803")
			.path("portoBusesNow[0].speedKilometresPerHour")
			.entity(Double.class)
			.isEqualTo(55.1);
	}

	@Test
	void airQualityStationsReportedInTheLastHour() {
		this.graphQl.document("{ portoAirQuality(sinceMinutes: 60, first: 20) { station pm10 } }")
			.execute()
			.path("portoAirQuality[*].station")
			.entityList(String.class)
			.contains("Pólo Asprela", "Av. da Boavista / Av. do Parque");
	}

	@Test
	void theAlfandegaIsAPlace() {
		this.graphQl.document("{ portoPlace(name: \"alfandega do porto\") { name latitude longitude } }")
			.execute()
			.path("portoPlace.name")
			.entity(String.class)
			.isEqualTo("Alfândega do Porto");
	}

}
