package org.springframework.samples.petclinic.porto;

/**
 * A place in Porto, from the points of interest of Porto Digital's urban platform.
 *
 * @param name the name in Portuguese, such as Alfândega do Porto
 * @param englishName the name in English, when the platform has one
 * @param latitude the latitude, such as 41.1432
 * @param longitude the longitude, such as -8.6215
 * @param address the street, postal code and town, when the platform has them
 */
public record PortoPlace(String name, String englishName, double latitude, double longitude, String address) {
}
