package org.springframework.samples.petclinic.apa;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class ApaMockUpstreamTests extends MockUpstreamTests {

	@Test
	void nineOfPortosTenBeachesHaveABlueFlag() {
		this.graphQl
			.document("{ apaBathingWaters(municipality: \"Porto\", first: 20) { totalCount items { name blueFlag } } }")
			.execute()
			.path("apaBathingWaters.totalCount")
			.entity(Integer.class)
			.isEqualTo(10)
			.path("apaBathingWaters.items[?(@.blueFlag == false)].name")
			.entityList(String.class)
			.containsExactly("Castelo do Queijo");
	}

}
