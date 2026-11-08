package org.springframework.samples.petclinic.turismo;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class TurismoMockUpstreamTests extends MockUpstreamTests {

	@Test
	void portoHasAlmostTenThousandLocalLodgingUnits() {
		this.graphQl.document("{ turismoLocalLodging(municipality: \"Porto\", first: 1) { totalCount } }")
			.execute()
			.path("turismoLocalLodging.totalCount")
			.entity(Integer.class)
			.isEqualTo(9942);
	}

}
