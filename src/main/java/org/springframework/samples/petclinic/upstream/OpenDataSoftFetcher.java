package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.OpenDataSoftTable.Dataset;
import org.springframework.samples.petclinic.upstream.OpenDataSoftTable.Filter;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves the root field of one OpenDataSoft dataset, from the portal's records endpoint.
 * The filter arguments become the <code>where</code> clause, in the portal's query
 * language, and the page holds the portal's <code>total_count</code> and the first
 * records, at most 100, which is the portal's limit.
 */
public class OpenDataSoftFetcher implements DataFetcher<Map<String, Object>> {

	private static final int DEFAULT_FIRST = 20;

	private static final int MAX_FIRST = 100;

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final Dataset dataset;

	private final JsonMapper mapper;

	public OpenDataSoftFetcher(String upstream, String baseUrl, RestClient client, Dataset dataset, JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.dataset = dataset;
		this.mapper = mapper;
	}

	@Override
	public Map<String, Object> get(DataFetchingEnvironment environment) {
		return page(environment.getArguments());
	}

	Map<String, Object> page(Map<String, Object> arguments) {
		int first = Math.min(MAX_FIRST, ((Number) arguments.getOrDefault("first", DEFAULT_FIRST)).intValue());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
			.path("/api/explore/v2.1/catalog/datasets/" + dataset.dataset() + "/records");
		String where = where(arguments);
		if (where != null) {
			builder.queryParam("where", where);
		}
		if (dataset.orderBy() != null) {
			builder.queryParam("order_by", dataset.orderBy());
		}
		builder.queryParam("limit", first);
		URI uri = builder.encode().build().toUri();
		Object answer = UpstreamGet.json(upstream, client, mapper, uri,
				(json) -> (json instanceof Map<?, ?> map) ? "%s of %s records"
					.formatted((map.get("results") instanceof List<?> list) ? list.size() : 0, map.get("total_count"))
						: "an answer");
		Map<String, Object> page = new HashMap<>();
		page.put("totalCount", (answer instanceof Map<?, ?> map) ? map.get("total_count") : null);
		page.put("items", (answer instanceof Map<?, ?> map && map.get("results") instanceof List<?> results) ? results
				: new ArrayList<>());
		return page;
	}

	/**
	 * The <code>where</code> clause of the filters given, or null for a call without filters.
	 */
	String where(Map<String, Object> arguments) {
		List<String> conditions = new ArrayList<>();
		for (Filter filter : dataset.filters()) {
			Object value = arguments.get(filter.name());
			if (value == null) {
				continue;
			}
			String text = String.valueOf(value).replace("\"", "\\\"");
			String op = (filter.op() != null) ? filter.op() : "=";
			conditions.add(switch (filter.kind()) {
				case TEXT -> "%s %s \"%s\"".formatted(filter.column(), op, text);
				case MONTH, DATE -> "%s %s date'%s'".formatted(filter.column(), op, text);
				case NUMBER ->
					"%s %s %s".formatted(filter.column(), op, Double.valueOf(text).toString().replaceAll("\\.0$", ""));
				case SEARCH -> "search(%s, \"%s\")".formatted(filter.column(), text);
			});
		}
		return conditions.isEmpty() ? null : String.join(" and ", conditions);
	}

}
