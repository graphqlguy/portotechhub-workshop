package org.springframework.samples.petclinic.ropi;

/**
 * A park or garden of Porto, with its distance from a place.
 *
 * @param name the name, such as Parque das Virtudes
 * @param address the street, number, postal code and town
 * @param distanceInMetres the distance from the place, in a straight line
 * @param latitude where it is
 * @param longitude where it is
 */
public record RopiPark(String name, String address, int distanceInMetres, double latitude, double longitude) {
}
