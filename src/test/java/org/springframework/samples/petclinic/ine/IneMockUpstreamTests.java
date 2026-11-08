package org.springframework.samples.petclinic.ine;

import org.junit.jupiter.api.Test;

import org.springframework.graphql.execution.ErrorType;
import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

import static org.assertj.core.api.Assertions.assertThat;

class IneMockUpstreamTests extends MockUpstreamTests {

	@Test
	void portoHadAbout267000PeopleIn2023() {
		value("inePopulation(year: 2023, place: \"11A1312\")", 267236.0);
	}

	@Test
	void portoHas105PetClinicSites() {
		value("ineEstablishments(year: 2024, place: \"11A1312\", activity: \"75000\")", 105.0);
	}

	@Test
	void portoRecorded36CrimesAgainstPetsIn2025() {
		value("ineCrimes(year: 2025, place: \"11A1312\", crimeCategory: \"10\")", 36.0);
	}

	@Test
	void aPetClinicVisitCost4PercentMoreThanAYearBefore() {
		value("ineConsumerPriceChange(month: \"2026-09\", priceCategory: \"0945\")", 4.08);
	}

	@Test
	void spainSellsPortugalMostOfItsDogAndCatFood() {
		this.graphQl.document("{ ineImports(year: 2025, importedProduct: \"230910\") { placeCode value } }")
			.execute()
			.path("ineImports[?(@.placeCode == 'ES')].value")
			.entityList(Double.class)
			.containsExactly(1.63093227E8);
	}

	@Test
	void everyPeriodWithoutItsPeriodIsBadRequest() {
		this.graphQl
			.document("{ ineEnterprises(allPeriods: true, place: \"11A1312\", businessActivity: \"75000\") { value } }")
			.execute()
			.errors()
			.satisfy((errors) -> assertThat(errors).singleElement().satisfies((error) -> {
				assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				assertThat(error.getMessage()).isEqualTo("With allPeriods, the answer has one observation for each "
						+ "period, so select period to know which period each value is for.");
			}));
	}

	@Test
	void everyPeriodWithItsPeriodAnswersEachYear() {
		this.graphQl
			.document("{ ineEnterprises(allPeriods: true, place: \"11A1312\", businessActivity: \"75000\") "
					+ "{ period value } }")
			.execute()
			.path("ineEnterprises[*].period")
			.entityList(String.class)
			.containsExactly("2023", "2024");
	}

	private void value(String field, double expected) {
		this.graphQl.document("{ " + field + " { value } }")
			.execute()
			.path(field.substring(0, field.indexOf('(')) + "[0].value")
			.entity(Double.class)
			.isEqualTo(expected);
	}

}
