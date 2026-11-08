package org.springframework.samples.petclinic.dgeg;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

class DgegMockUpstreamTests extends MockUpstreamTests {

	@Test
	void cheapestPetrolInThePortoDistrict() {
		this.graphQl.document(
				"{ dgegCheapestFuel(fuel: PETROL_95, district: \"Porto\", first: 3) { station priceEurosPerLitre updatedAt source } }")
			.execute()
			.path("dgegCheapestFuel[0].station")
			.entity(String.class)
			.isEqualTo("PLENERGY - VILA NOVA DE GAIA III")
			.path("dgegCheapestFuel[0].priceEurosPerLitre")
			.entity(Double.class)
			.isEqualTo(1.869);
	}

	@Test
	void cheapestDieselWithin3kmOfTheAlfandega() {
		this.graphQl.document(
				"{ dgegCheapestFuel(fuel: DIESEL, place: \"Alfândega\", radiusKilometres: 3, first: 3) { station priceEurosPerLitre distanceKilometres } }")
			.execute()
			.path("dgegCheapestFuel[0].station")
			.entity(String.class)
			.isEqualTo("AUCHAN Canidelo")
			.path("dgegCheapestFuel[0].priceEurosPerLitre")
			.entity(Double.class)
			.isEqualTo(2.149);
	}

}
