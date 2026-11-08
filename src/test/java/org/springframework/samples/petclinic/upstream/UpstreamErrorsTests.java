package org.springframework.samples.petclinic.upstream;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A public service that cannot be reached: its field answers an error that names the
 * service, and the other fields of the same query still answer.
 */
@SpringBootTest(properties = "petclinic.upstream.ine.url=http://localhost:1")
@AutoConfigureGraphQlTester
class UpstreamErrorsTests {

	@Autowired
	private GraphQlTester graphQl;

	@Test
	void unreachableServiceNamesItself() {
		this.graphQl.document("{ inePopulation(year: 2023, place: \"11A1312\") { value } vets { lastName } }")
			.execute()
			.errors()
			.satisfy((errors) -> assertThat(errors).singleElement()
				.satisfies((error) -> assertThat(error.getMessage()).contains("INE").contains("unavailable")))
			.path("vets")
			.entityList(Object.class)
			.hasSizeGreaterThan(0);
	}

}
