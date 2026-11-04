/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.system.SortOrder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link OwnerService} against the seed data.
 */
@SpringBootTest
@Transactional
class OwnerServiceTests {

	@Autowired
	private OwnerService owners;

	@Test
	void findAllOrdersByLastName() {
		assertThat(owners.findAll()).hasSize(11)
			.extracting(Owner::getLastName)
			.startsWith("Black", "Coleman", "Davis");
	}

	@Test
	void findByIdReturnsNullForAnUnknownId() {
		assertThat(owners.findById(1).getLastName()).isEqualTo("Franklin");
		assertThat(owners.findById(999)).isNull();
	}

	@Test
	void findOwnersFiltersByLastNameAndCityInAnyCase() {
		OwnerPage davises = owners.findOwners(new OwnerFilter("davi", null), 0, 10, null);
		assertThat(davises.content()).extracting(Owner::getFirstName).containsExactly("Betty", "Harold");

		OwnerPage madison = owners.findOwners(new OwnerFilter(null, "MADISON"), 0, 10, null);
		assertThat(madison.totalElements()).isEqualTo(4);

		OwnerPage blank = owners.findOwners(new OwnerFilter(" ", ""), 0, 10, null);
		assertThat(blank.totalElements()).isEqualTo(11);
	}

	@Test
	void findOwnersMatchesTheCityWithOrWithoutAccents() {
		owners.addOwner(new AddOwnerInput("Ana", "Sousa", "Rua de Santa Maria 3", "Guimarães", "2531234567"));
		assertThat(owners.findOwners(new OwnerFilter(null, "Guimaraes"), 0, 10, null).content())
			.extracting(Owner::getLastName)
			.containsExactly("Sousa");
		assertThat(owners.findOwners(new OwnerFilter(null, "GUIMARÃES"), 0, 10, null).totalElements()).isEqualTo(1);

		owners.addOwner(new AddOwnerInput("Rui", "Costa", "Rua do Castelo 8", "Guimaraes", "2537654321"));
		assertThat(owners.findOwners(new OwnerFilter(null, "Guimarães"), 0, 10, null).totalElements()).isEqualTo(2);

		assertThat(owners.findOwners(new OwnerFilter(null, "Braga"), 0, 10, null).totalElements()).isZero();
	}

	@Test
	void findOwnersPagesAndSorts() {
		OwnerPage first = owners.findOwners(null, 0, 6, null);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(first.isFirst()).isTrue();
		assertThat(first.hasNext()).isTrue();

		OwnerPage last = owners.findOwners(null, 1, 6, null);
		assertThat(last.content()).hasSize(5);
		assertThat(last.isLast()).isTrue();
		assertThat(last.hasPrevious()).isTrue();

		OwnerPage byCity = owners.findOwners(null, 0, 1, new OwnerSort(OwnerSortField.CITY, SortOrder.DESC));
		assertThat(byCity.content().get(0).getCity()).isEqualTo("Windsor");
	}


	@Test
	void addOwner() {
		Owner owner = owners
			.addOwner(new AddOwnerInput("Ana", "Sousa", "Rua de Santa Catarina 3", "Porto", "2221234567"));
		assertThat(owner.getId()).isNotNull();
		assertThat(owners.findAll()).hasSize(12);
	}

	@Test
	void addOwnerRefusesATelephoneThatIsNotTenDigits() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> owners.addOwner(new AddOwnerInput("Ana", "", "Rua de Santa Catarina 3", "Porto", "123")))
			.satisfies((ex) -> assertThat(ex.getFieldErrors()).containsOnlyKeys("lastName", "telephone"));
	}

	@Test
	void updateOwnerChangesOnlyTheFieldsItSets() {
		Owner owner = owners.updateOwner(new UpdateOwnerInput(1, null, null, null, "Matosinhos", null));
		assertThat(owner.getCity()).isEqualTo("Matosinhos");
		assertThat(owner.getLastName()).isEqualTo("Franklin");
	}

	@Test
	void updateOwnerRefusesAnUnknownId() {
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> owners.updateOwner(new UpdateOwnerInput(999, "X", null, null, null, null)));
	}

}
