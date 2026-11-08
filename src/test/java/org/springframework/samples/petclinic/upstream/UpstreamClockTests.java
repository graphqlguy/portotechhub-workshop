package org.springframework.samples.petclinic.upstream;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamClockTests {

	@Test
	void mockClockIsPinned() {
		UpstreamClock clock = new UpstreamClock("2026-11-05T19:50:00Z");

		assertThat(clock.clock().instant()).isEqualTo(Instant.parse("2026-11-05T19:50:00Z"));
	}

	@Test
	void blankMeansTheSystemClock() {
		Instant before = Instant.now();

		Instant now = new UpstreamClock("").clock().instant();

		assertThat(now).isBetween(before, Instant.now());
	}

	@Test
	void todayIsTheDayInLisbon() {
		// 23:30 UTC on 5 November is still 5 November in Lisbon, which is on UTC in
		// winter
		assertThat(new UpstreamClock("2026-11-05T23:30:00Z").today()).hasToString("2026-11-05");
		// 23:30 UTC on 5 June is already 6 June in Lisbon, which is on UTC+1 in summer
		assertThat(new UpstreamClock("2026-06-05T23:30:00Z").today()).hasToString("2026-06-06");
	}

}
