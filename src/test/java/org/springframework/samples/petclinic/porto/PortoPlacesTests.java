package org.springframework.samples.petclinic.porto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.Fixtures;
import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.samples.petclinic.upstream.PlaceResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortoPlacesTests {

	private final PortoPlaces places = new PortoPlaces("Porto Digital", "https://broker",
			new Fixtures().answer("type=PointOfInterest", "porto-points-of-interest.json").client(),
			JsonMapper.builder().build());

	@Test
	void accentsAndCaseIgnored() {
		assertThat(this.places.resolve("alfandega do porto")).isEqualTo(new GeoPoint(41.14321055, -8.621461985));
		assertThat(this.places.resolve("Alfândega do Porto")).isEqualTo(new GeoPoint(41.14321055, -8.621461985));
	}

	@Test
	void theStartOfANameFindsThePortugueseNameFirst() {
		assertThat(this.places.resolve("Alfândega")).isEqualTo(new GeoPoint(41.14321055, -8.621461985));
	}

	@Test
	void theCityItselfIsTheWholeCity() {
		assertThat(PlaceResolver.point(this.places, "Porto", null, null)).isNull();
		assertThat(PlaceResolver.point(this.places, "porto city", null, null)).isNull();
		assertThat(PlaceResolver.point(this.places, "Alfândega do Porto", null, null)).isNotNull();
	}

	@Test
	void anotherMunicipalityIsOutsidePorto() {
		assertThatThrownBy(() -> this.places.resolve("Braga")).isInstanceOf(InvalidArgumentException.class)
			.hasMessageContaining("outside Porto");
		assertThatThrownBy(() -> this.places.resolve("Lisbon")).isInstanceOf(InvalidArgumentException.class);
		assertThatThrownBy(() -> this.places.resolve("faro")).isInstanceOf(InvalidArgumentException.class);
	}

	@Test
	void aPlaceNamedAfterATownStillResolves() {
		assertThat(this.places.find("Jardim de Teófilo Braga").name()).isEqualTo("Jardim de Teófilo Braga");
	}

	@Test
	void unknownPlaceIsBadRequest() {
		assertThatThrownBy(() -> this.places.resolve("Nowhere at all")).isInstanceOf(InvalidArgumentException.class)
			.hasMessageContaining("No place in Porto");
	}

}
