package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.ArcGisLayerTable.Filter;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable.Layer;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves the root field of one ArcGIS layer. The filter arguments become the
 * <code>where</code> clause of the layer's query. The field returns a page: its
 * <code>totalCount</code> comes from a request that asks for the count alone, and its
 * <code>items</code> from a request for the first records, each sent only when the query
 * selects it. The records leave out their geometry, which would make the answers large.
 */
public class ArcGisLayerFetcher implements DataFetcher<Map<String, Object>> {

	private static final int DEFAULT_FIRST = 20;

	private static final int MAX_FIRST = 200;

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final Layer layer;

	private final JsonMapper mapper;

	public ArcGisLayerFetcher(String upstream, String baseUrl, RestClient client, Layer layer, JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.layer = layer;
		this.mapper = mapper;
	}

	@Override
	public Map<String, Object> get(DataFetchingEnvironment environment) {
		return page(environment.getArguments(), environment.getSelectionSet().contains("totalCount"),
				environment.getSelectionSet().contains("items"));
	}

	/**
	 * The page of a query, with the count when it is asked for, and the records when they
	 * are asked for.
	 */
	Map<String, Object> page(Map<String, Object> arguments, boolean count, boolean items) {
		Map<String, Object> page = new HashMap<>();
		String where = where(arguments);
		if (count) {
			Object answer = query(UriComponentsBuilder.fromUriString(baseUrl)
				.path(layer.path() + "/query")
				.queryParam("where", where)
				.queryParam("returnCountOnly", "true")
				.queryParam("f", "json"));
			page.put("totalCount", (answer instanceof Map<?, ?> map) ? map.get("count") : null);
		}
		else {
			page.put("totalCount", null);
		}
		if (items) {
			int first = Math.min(MAX_FIRST, ((Number) arguments.getOrDefault("first", DEFAULT_FIRST)).intValue());
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
				.path(layer.path() + "/query")
				.queryParam("where", where)
				.queryParam("outFields",
						layer.attributes()
							.values()
							.stream()
							.map(FieldSource::key)
							.distinct()
							.collect(Collectors.joining(",")))
				.queryParam("returnGeometry", "false")
				.queryParam("resultRecordCount", first)
				.queryParam("f", "json");
			if (layer.orderBy() != null) {
				builder.queryParam("orderByFields", layer.orderBy());
			}
			Object answer = query(builder);
			List<Object> records = new ArrayList<>();
			if (answer instanceof Map<?, ?> map && map.get("features") instanceof List<?> features) {
				features.forEach((feature) -> records.add(((Map<?, ?>) feature).get("attributes")));
			}
			page.put("items", records);
		}
		else {
			page.put("items", null);
		}
		return page;
	}

	/**
	 * The <code>where</code> clause of the filters given, with each text in single quotes
	 * and its own single quotes doubled.
	 */
	String where(Map<String, Object> arguments) {
		List<String> conditions = new ArrayList<>();
		for (Filter filter : layer.filters()) {
			Object value = arguments.get(filter.name());
			if (value == null) {
				continue;
			}
			String text = String.valueOf(value);
			switch (filter.kind()) {
				case NUMBER -> conditions.add("%s = %s".formatted(filter.attribute(),
						Double.valueOf(text).toString().replaceAll("\\.0$", "")));
				case CONTAINS -> conditions.add(
						"UPPER(%s) LIKE '%%%s%%'".formatted(filter.attribute(), quoted(text.toUpperCase(Locale.ROOT))));
				case EQUALS -> conditions.add("%s = '%s'".formatted(filter.attribute(),
						quoted(Boolean.TRUE.equals(filter.upperCase()) ? text.toUpperCase(Locale.ROOT) : text)));
			}
		}
		return conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private Object query(UriComponentsBuilder builder) {
		URI uri = builder.encode().build().toUri();
		Object answer = UpstreamGet.json(upstream, client, mapper, uri, ArcGisLayerFetcher::summary);
		if (answer instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error) {
			throw new InvalidArgumentException(
					"%s rejected the arguments: %s".formatted(upstream, error.get("message")));
		}
		return answer;
	}

	private static String summary(Object answer) {
		if (answer instanceof Map<?, ?> map) {
			if (map.get("features") instanceof List<?> features) {
				return features.size() + " records";
			}
			if (map.containsKey("count")) {
				return "a count of " + map.get("count");
			}
		}
		return "an answer";
	}

}
