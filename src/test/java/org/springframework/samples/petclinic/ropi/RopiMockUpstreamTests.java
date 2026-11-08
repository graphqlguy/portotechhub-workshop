package org.springframework.samples.petclinic.ropi;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class RopiMockUpstreamTests extends MockUpstreamTests {

	@Test
	void parqueDasVirtudesIsNearestTheAlfandega() {
		this.graphQl.document("{ ropiNearestParks(place: \"Alfândega\", first: 3) { name distanceInMetres } }")
			.execute()
			.path("ropiNearestParks[0].name")
			.entity(String.class)
			.isEqualTo("Parque das Virtudes")
			.path("ropiNearestParks[0].distanceInMetres")
			.entity(Integer.class)
			.isEqualTo(257);
	}

}
