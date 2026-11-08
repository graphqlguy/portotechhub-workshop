package org.springframework.samples.petclinic.eurostat;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class EurostatMockUpstreamTests extends MockUpstreamTests {

	@Test
	void petClinicPricesRoseFasterInPortugalThanInTheEu() {
		this.graphQl.document(
				"{ eurostatConsumerPriceChange(countries: [\"PT\", \"EU27_2020\"], category: \"CP0945\", month: \"2026-09\") { country value } }")
			.execute()
			.path("eurostatConsumerPriceChange[?(@.country == 'PT')].value")
			.entityList(Double.class)
			.containsExactly(4.1)
			.path("eurostatConsumerPriceChange[?(@.country == 'EU27_2020')].value")
			.entityList(Double.class)
			.containsExactly(3.3);
	}

}
