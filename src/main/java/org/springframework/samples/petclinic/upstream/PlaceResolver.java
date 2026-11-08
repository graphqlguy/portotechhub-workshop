package org.springframework.samples.petclinic.upstream;

/**
 * Finds a place by its name, for the fields that take a place to search near.
 */
@FunctionalInterface
public interface PlaceResolver {

	/**
	 * The point of a place, such as the Alfândega do Porto.
	 * @param name the name of the place
	 * @return the point, never null
	 * @throws InvalidArgumentException when no place has the name
	 */
	GeoPoint resolve(String name);

	/**
	 * Whether a name is the whole city, such as Porto itself, for which a field searches
	 * everywhere instead of near one point.
	 */
	default boolean isWholeCity(String name) {
		return false;
	}

	/**
	 * The point a field searches near: the place with the name, or the latitude and
	 * longitude, or null when the arguments give neither or name the whole city.
	 */
	static GeoPoint point(PlaceResolver places, String name, Number latitude, Number longitude) {
		if (name != null && !name.isBlank()) {
			return places.isWholeCity(name) ? null : places.resolve(name);
		}
		if (latitude != null && longitude != null) {
			return new GeoPoint(latitude.doubleValue(), longitude.doubleValue());
		}
		return null;
	}

}
