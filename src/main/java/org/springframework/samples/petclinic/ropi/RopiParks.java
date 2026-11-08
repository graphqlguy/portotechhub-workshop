package org.springframework.samples.petclinic.ropi;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamGet;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The parks and gardens of Porto nearest a place, from the category "Parques e Jardins"
 * of ROPI, the official register of points of interest of Porto Digital. The list is read
 * once a day, and Java computes the distances.
 */
public class RopiParks {

	private static final Duration KEPT_FOR = Duration.ofDays(1);

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final JsonMapper mapper;

	private final PlaceResolver places;

	private volatile List<Park> parks;

	private volatile Instant readAt;

	public RopiParks(String upstream, String baseUrl, RestClient client, JsonMapper mapper, PlaceResolver places) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.mapper = mapper;
		this.places = places;
	}

	/**
	 * The parks nearest a place, given by its name or by its latitude and longitude.
	 * @throws InvalidArgumentException when the arguments leave out the place
	 */
	public List<RopiPark> nearest(String place, Number latitude, Number longitude, int first) {
		GeoPoint point = PlaceResolver.point(places, place, latitude, longitude);
		if (point == null) {
			throw new InvalidArgumentException(
					"Give a place, or a latitude and a longitude, to find the parks near it.");
		}
		return parks().stream()
			.map((park) -> new RopiPark(park.name(), park.address(), point.distanceInMetres(park.point()),
					park.point().latitude(), park.point().longitude()))
			.sorted(Comparator.comparingInt(RopiPark::distanceInMetres))
			.limit(Math.max(1, first))
			.toList();
	}

	private List<Park> parks() {
		List<Park> read = this.parks;
		if (read == null || this.readAt.plus(KEPT_FOR).isBefore(Instant.now())) {
			URI uri = UriComponentsBuilder.fromUriString(baseUrl)
				.path("/CitySDK/pois")
				.queryParam("category", "Parques e Jardins")
				.queryParam("limit", 100)
				.encode()
				.build()
				.toUri();
			Object answer = UpstreamGet.json(upstream, client, mapper, uri,
					(json) -> (json instanceof Map<?, ?> map && map.get("points_of_interest") instanceof List<?> list)
							? list.size() + " parks" : "an answer");
			read = (answer instanceof Map<?, ?> map && map.get("points_of_interest") instanceof List<?> list)
					? list.stream().flatMap(RopiParks::park).toList() : List.of();
			this.parks = read;
			this.readAt = Instant.now();
		}
		return read;
	}

	private static Stream<Park> park(Object poi) {
		if (!(poi instanceof Map<?, ?> map) || !(map.get("location") instanceof Map<?, ?> location)
				|| !(location.get("point") instanceof List<?> points) || points.isEmpty()) {
			return Stream.empty();
		}
		Object point = ((Map<?, ?>) points.get(0)).get("Point");
		if (!(point instanceof Map<?, ?> geometry) || !(geometry.get("posList") instanceof String posList)) {
			return Stream.empty();
		}
		String[] position = posList.strip().split("\\s+");
		return Stream.of(new Park(label(map), address(location),
				new GeoPoint(Double.parseDouble(position[0]), Double.parseDouble(position[1]))));
	}

	/**
	 * The name in Portuguese, from the labels in several languages.
	 */
	private static String label(Map<?, ?> poi) {
		if (poi.get("label") instanceof List<?> labels) {
			for (Object label : labels) {
				if (label instanceof Map<?, ?> map && "pt-PT".equals(map.get("lang"))) {
					return String.valueOf(map.get("value")).strip();
				}
			}
			if (!labels.isEmpty() && labels.get(0) instanceof Map<?, ?> first) {
				return String.valueOf(first.get("value")).strip();
			}
		}
		return null;
	}

	/**
	 * The address, from the <code>ADR</code> line of the vCard that ROPI keeps for each
	 * point: post box, number, street, town, region, postal code and country.
	 */
	private static String address(Map<?, ?> location) {
		if (!(location.get("address") instanceof Map<?, ?> address)
				|| !(address.get("value") instanceof String vcard)) {
			return null;
		}
		for (String line : vcard.split("\\r?\\n")) {
			if (line.startsWith("ADR")) {
				String[] parts = line.substring(line.indexOf(':') + 1).split(";", -1);
				List<String> written = new ArrayList<>();
				if (parts.length > 2 && !parts[2].isBlank()) {
					written.add(parts[2].strip() + ((parts[1].isBlank()) ? "" : ", " + parts[1].strip()));
				}
				if (parts.length > 5 && !parts[5].isBlank()) {
					written.add(parts[5].strip()
							+ ((parts.length > 3 && !parts[3].isBlank()) ? " " + parts[3].strip() : ""));
				}
				return written.isEmpty() ? null : String.join(", ", written);
			}
		}
		return null;
	}

	private record Park(String name, String address, GeoPoint point) {
	}

}
