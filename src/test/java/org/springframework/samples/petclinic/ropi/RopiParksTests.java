package org.springframework.samples.petclinic.ropi;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.Fixtures;
import org.springframework.samples.petclinic.upstream.GeoPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RopiParksTests {

	private final RopiParks parks = new RopiParks("ROPI", "https://api.ropi.porto.digital",
			new Fixtures().answer("category=Parques", "ropi-parks.json").client(), JsonMapper.builder().build(),
			(name) -> new GeoPoint(41.14321055, -8.621461985));

	@Test
	void virtudesFirst() {
		List<RopiPark> nearest = this.parks.nearest("Alfândega do Porto", null, null, 3);

		assertThat(nearest).hasSize(3);
		assertThat(nearest.get(0).name()).isEqualTo("Parque das Virtudes");
		assertThat(nearest.get(0).distanceInMetres()).isCloseTo(257, within(5));
		assertThat(nearest.get(0).address()).contains("Porto");
	}

}
