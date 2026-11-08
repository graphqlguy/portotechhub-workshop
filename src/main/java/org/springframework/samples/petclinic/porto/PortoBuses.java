package org.springframework.samples.petclinic.porto;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamClock;
import org.springframework.samples.petclinic.upstream.UpstreamGet;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The STCP buses that reported their position in the last five minutes, from the
 * <code>Vehicle</code> entities of Porto Digital's urban platform. Older entities are
 * buses that stopped reporting, so the window leaves them out. The platform sorts by
 * speed or by distance itself, and the speed it sends is in metres per second.
 */
public class PortoBuses {

	private static final int WINDOW_MINUTES = 5;

	private static final int MAX_FIRST = 1000;

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final JsonMapper mapper;

	private final UpstreamClock clock;

	private final PlaceResolver places;

	public PortoBuses(String upstream, String baseUrl, RestClient client, JsonMapper mapper, UpstreamClock clock,
			PlaceResolver places) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.mapper = mapper;
		this.clock = clock;
		this.places = places;
	}

	/**
	 * The buses reporting now.
	 * @param line only the buses of this line, such as 500, or null for every line
	 * @param fastestFirst true to sort them by speed, fastest first
	 * @param near only the buses within the radius of this place, nearest first, or null
	 * @param radiusMetres the radius around the place
	 * @param first how many buses to return
	 */
	public List<PortoBus> now(String line, boolean fastestFirst, String near, int radiusMetres, int first) {
		Object answer = UpstreamGet.json(upstream, client, mapper, uri(line, fastestFirst, near, radiusMetres, first),
				(json) -> (json instanceof List<?> list) ? list.size() + " buses" : "an answer");
		return (answer instanceof List<?> list) ? list.stream().flatMap(PortoBuses::bus).toList() : List.of();
	}

	URI uri(String line, boolean fastestFirst, String near, int radiusMetres, int first) {
		Instant since = clock.clock().instant().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
		String query = "observationDateTime>" + since + ";vehicleType==bus"
				+ ((line != null && !line.isBlank()) ? ";annotations==stcp:route:" + line.strip() : "");
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
			.path("/v2/entities")
			.queryParam("type", "Vehicle")
			.queryParam("options", "keyValues")
			.queryParam("limit", Math.min(MAX_FIRST, first))
			.queryParam("q", query);
		GeoPoint point = PlaceResolver.point(places, near, null, null);
		if (point != null) {
			builder.queryParam("georel", "near;maxDistance:" + radiusMetres)
				.queryParam("geometry", "point")
				.queryParam("coords", point.latitude() + "," + point.longitude());
		}
		if (fastestFirst) {
			builder.queryParam("orderBy", "!speed");
		}
		return builder.encode().build().toUri();
	}

	private static Stream<PortoBus> bus(Object entity) {
		if (!(entity instanceof Map<?, ?> map)) {
			return Stream.empty();
		}
		Double speed = (map.get("speed") instanceof Number number) ? number.doubleValue() : null;
		Double latitude = null;
		Double longitude = null;
		if (map.get("location") instanceof Map<?, ?> location && location.get("coordinates") instanceof List<?> point
				&& point.size() >= 2) {
			longitude = ((Number) point.get(0)).doubleValue();
			latitude = ((Number) point.get(1)).doubleValue();
		}
		return Stream.of(new PortoBus(line(map), (String) map.get("fleetVehicleId"), speed,
				(speed != null) ? Math.round(speed * 36) / 10.0 : null, latitude, longitude,
				(String) map.get("observationDateTime")));
	}

	/**
	 * The line, from the annotation <code>stcp:route:500</code>, or from the name, such
	 * as <code>STCP 500 3530</code>.
	 */
	private static String line(Map<?, ?> bus) {
		if (bus.get("annotations") instanceof List<?> annotations) {
			for (Object annotation : annotations) {
				if (annotation instanceof String text && text.startsWith("stcp:route:")) {
					return text.substring("stcp:route:".length());
				}
			}
		}
		String[] name = String.valueOf(bus.get("name")).split(" ");
		return (name.length >= 2) ? name[1] : null;
	}

}
