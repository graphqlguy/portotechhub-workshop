package org.springframework.samples.petclinic.porto;

/**
 * A bus of STCP, Porto's bus company, as it last reported to Porto Digital's urban
 * platform.
 *
 * @param line the line it runs, such as 500
 * @param vehicle the number of the bus in STCP's fleet
 * @param speedMetresPerSecond the speed it reported, in metres per second
 * @param speedKilometresPerHour the same speed in kilometres per hour
 * @param latitude where it was
 * @param longitude where it was
 * @param observedAt when it reported, in UTC
 */
public record PortoBus(String line, String vehicle, Double speedMetresPerSecond, Double speedKilometresPerHour,
		Double latitude, Double longitude, String observedAt) {
}
