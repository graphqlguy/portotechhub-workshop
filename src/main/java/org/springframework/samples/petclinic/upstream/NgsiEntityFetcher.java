package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.NgsiEntityTable.Entity;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves the root field of one entity type of a FIWARE context broker, with NGSI v2's
 * <code>/v2/entities</code>. A place, given by its name or by its latitude and longitude,
 * asks for the entities within a radius of it, nearest first. A number of minutes asks
 * for the entities that reported within them, measured by {@link UpstreamClock}, so the
 * recorded answers match under the <code>mock</code> profile.
 */
public class NgsiEntityFetcher implements DataFetcher<List<?>> {

	private static final int DEFAULT_FIRST = 20;

	private static final int MAX_FIRST = 1000;

	private static final int DEFAULT_RADIUS = 1000;

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final Entity entity;

	private final JsonMapper mapper;

	private final UpstreamClock clock;

	private final PlaceResolver places;

	public NgsiEntityFetcher(String upstream, String baseUrl, RestClient client, Entity entity, JsonMapper mapper,
			UpstreamClock clock, PlaceResolver places) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.entity = entity;
		this.mapper = mapper;
		this.clock = clock;
		this.places = places;
	}

	@Override
	public List<?> get(DataFetchingEnvironment environment) {
		Object answer = UpstreamGet.json(upstream, client, mapper, uri(environment.getArguments()),
				(json) -> (json instanceof List<?> list) ? list.size() + " entities" : "an answer");
		return (answer instanceof List<?> list) ? list : List.of();
	}

	URI uri(Map<String, Object> arguments) {
		int first = Math.min(MAX_FIRST, ((Number) arguments.getOrDefault("first", DEFAULT_FIRST)).intValue());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
			.path("/v2/entities")
			.queryParam("type", entity.entityType())
			.queryParam("options", "keyValues")
			.queryParam("limit", first);
		String attrs = attrs();
		if (!attrs.isEmpty()) {
			builder.queryParam("attrs", attrs);
		}
		GeoPoint point = point(arguments);
		if (point != null) {
			int radius = ((Number) arguments.getOrDefault("radiusMetres", DEFAULT_RADIUS)).intValue();
			builder.queryParam("georel", "near;maxDistance:" + radius)
				.queryParam("geometry", "point")
				.queryParam("coords", point.latitude() + "," + point.longitude());
		}
		if (entity.timeAttribute() != null && arguments.get("sinceMinutes") instanceof Number minutes) {
			Instant since = clock.clock().instant().minus(minutes.longValue(), ChronoUnit.MINUTES);
			builder.queryParam("q", entity.timeAttribute() + ">" + since);
		}
		return builder.encode().build().toUri();
	}

	/**
	 * The attributes the type reads, and the time attribute, so the broker leaves out the
	 * others, such as the contacts of a point of interest.
	 */
	private String attrs() {
		Set<String> keys = new TreeSet<>();
		entity.attributes()
			.values()
			.forEach((source) -> Arrays.stream(source.key().split("\\|"))
				.map((key) -> key.split("/")[0])
				.forEach(keys::add));
		if (entity.timeAttribute() != null) {
			keys.add(entity.timeAttribute());
		}
		return String.join(",", keys);
	}

	private GeoPoint point(Map<String, Object> arguments) {
		return PlaceResolver.point(places, (arguments.get("near") instanceof String name) ? name : null,
				(arguments.get("latitude") instanceof Number latitude) ? latitude : null,
				(arguments.get("longitude") instanceof Number longitude) ? longitude : null);
	}

}
