package org.springframework.samples.petclinic.porto;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.Fixtures;
import org.springframework.samples.petclinic.upstream.UpstreamClock;

import static org.assertj.core.api.Assertions.assertThat;

class PortoBusesTests {

	private final Fixtures fixtures = new Fixtures().answer("type=Vehicle", "porto-buses-fastest.json");

	private final PortoBuses buses = new PortoBuses("Porto Digital", "https://broker", this.fixtures.client(),
			JsonMapper.builder().build(), new UpstreamClock("2026-11-05T19:50:00Z"), (name) -> null);

	@Test
	void fastestFirstWithKmh() {
		List<PortoBus> fastest = this.buses.now(null, true, null, 1000, 3);

		assertThat(fastest.get(0).line()).isEqualTo("904");
		assertThat(fastest.get(0).speedMetresPerSecond()).isEqualTo(15.8);
		assertThat(fastest.get(0).speedKilometresPerHour()).isEqualTo(56.9);
	}

	@Test
	void theWindowIsTheLastFiveMinutesAndFastestFirstSortsBySpeed() {
		assertThat(this.buses.uri(null, true, null, 1000, 3).toString())
			.contains("q=observationDateTime%3E2026-11-05T19:45:00Z;vehicleType%3D%3Dbus")
			.contains("orderBy=!speed");
		assertThat(this.buses.uri("500", false, null, 1000, 3).toString()).contains(";annotations%3D%3Dstcp:route:500");
	}

}
