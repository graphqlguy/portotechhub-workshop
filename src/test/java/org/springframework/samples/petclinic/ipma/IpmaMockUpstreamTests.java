package org.springframework.samples.petclinic.ipma;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class IpmaMockUpstreamTests extends MockUpstreamTests {

	@Test
	void tomorrowInPortoBringsRain() {
		this.graphQl
			.document("{ ipmaCityForecast(location: 1131200) { days { date maxTemperature rainProbability } } }")
			.execute()
			.path("ipmaCityForecast.days[1].date")
			.entity(String.class)
			.isEqualTo("2026-11-06")
			.path("ipmaCityForecast.days[1].maxTemperature")
			.entity(Double.class)
			.isEqualTo(17.1)
			.path("ipmaCityForecast.days[1].rainProbability")
			.entity(Double.class)
			.isEqualTo(100.0);
	}

	@Test
	void otherArgumentsGetTheFirstRecording() {
		// Lisbon's forecast was not recorded, so the mock answers with Porto's
		this.graphQl.document("{ ipmaCityForecast(location: 1110600) { days { date } } }")
			.execute()
			.path("ipmaCityForecast.days")
			.entityList(Object.class)
			.hasSize(5);
	}

	@Test
	void theStrongestEarthquakeWasFeltInVinhais() {
		this.graphQl.document(
				"{ ipmaEarthquakes(area: MAINLAND_AND_MADEIRA) { earthquakes { magnitude region sensed feltAt } } }")
			.execute()
			.path("ipmaEarthquakes.earthquakes[?(@.magnitude == '4.2')].feltAt")
			.entityList(String.class)
			.containsExactly("Vinhais (Bragança)");
	}

}
