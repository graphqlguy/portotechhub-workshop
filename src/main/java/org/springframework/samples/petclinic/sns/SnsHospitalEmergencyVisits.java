package org.springframework.samples.petclinic.sns;

/**
 * The visits to the emergency department of one public hospital in one month, by the
 * colour of the Manchester triage, from red, the most urgent, to blue and white.
 *
 * @param hospital the hospital, or the local health unit it belongs to
 * @param region the health region
 * @param month the month, such as 2026-08
 * @param total every visit of the month
 * @param red visits triaged red: immediate
 * @param orange visits triaged orange: very urgent
 * @param yellow visits triaged yellow: urgent
 * @param green visits triaged green: standard
 * @param blue visits triaged blue: not urgent
 * @param white visits triaged white: not an emergency, such as a planned treatment
 * @param notTriaged visits without a triage
 */
public record SnsHospitalEmergencyVisits(String hospital, String region, String month, int total, int red, int orange,
		int yellow, int green, int blue, int white, int notTriaged) {
}
