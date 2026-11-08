package org.springframework.samples.petclinic.upstream;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The time the application works with: the system clock, or a fixed moment when
 * <code>petclinic.now</code> is set. The <code>mock</code> profile sets it to the moment
 * the answers were recorded, so a question about tomorrow or right now asks the recorded
 * answers on any later day.
 */
@Component
public class UpstreamClock {

	/**
	 * The clinic is in Porto, which keeps the time of Lisbon.
	 */
	public static final ZoneId PORTO = ZoneId.of("Europe/Lisbon");

	private final Clock clock;

	public UpstreamClock(@Value("${petclinic.now:}") String now) {
		this.clock = now.isBlank() ? Clock.system(PORTO) : Clock.fixed(Instant.parse(now), PORTO);
	}

	/**
	 * The clock, in the time zone of Porto.
	 */
	public Clock clock() {
		return this.clock;
	}

	/**
	 * Today in Porto.
	 */
	public LocalDate today() {
		return LocalDate.now(this.clock);
	}

}
