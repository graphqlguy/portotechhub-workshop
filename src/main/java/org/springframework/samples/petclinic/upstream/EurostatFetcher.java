package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.EurostatTable.Dataset;
import org.springframework.samples.petclinic.upstream.EurostatTable.Dimension;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves the root field of one Eurostat dataset. Each dimension argument becomes a
 * parameter, repeated for each code of a list, a country given by its name becomes its
 * code, and <code>latestPeriods</code> asks for the latest periods alone. Eurostat
 * answers in JSON-stat: a map from positions to values, and for each dimension the
 * position of each code. The fetcher turns it into one observation for each value, with
 * its country, its time and its other categories.
 */
public class EurostatFetcher implements DataFetcher<List<Map<String, Object>>> {

	/**
	 * The codes of the European Union, its countries and their neighbours in Eurostat's
	 * tables, by their names in English and in Portuguese, without accents.
	 */
	private static final Map<String, String> COUNTRIES = Map.ofEntries(Map.entry("european union", "EU27_2020"),
			Map.entry("eu", "EU27_2020"), Map.entry("uniao europeia", "EU27_2020"), Map.entry("euro area", "EA20"),
			Map.entry("portugal", "PT"), Map.entry("spain", "ES"), Map.entry("espanha", "ES"),
			Map.entry("france", "FR"), Map.entry("franca", "FR"), Map.entry("germany", "DE"),
			Map.entry("alemanha", "DE"), Map.entry("italy", "IT"), Map.entry("italia", "IT"),
			Map.entry("netherlands", "NL"), Map.entry("paises baixos", "NL"), Map.entry("belgium", "BE"),
			Map.entry("belgica", "BE"), Map.entry("luxembourg", "LU"), Map.entry("luxemburgo", "LU"),
			Map.entry("ireland", "IE"), Map.entry("irlanda", "IE"), Map.entry("austria", "AT"),
			Map.entry("greece", "EL"), Map.entry("grecia", "EL"), Map.entry("poland", "PL"), Map.entry("polonia", "PL"),
			Map.entry("czechia", "CZ"), Map.entry("chequia", "CZ"), Map.entry("slovakia", "SK"),
			Map.entry("eslovaquia", "SK"), Map.entry("slovenia", "SI"), Map.entry("eslovenia", "SI"),
			Map.entry("hungary", "HU"), Map.entry("hungria", "HU"), Map.entry("romania", "RO"),
			Map.entry("romenia", "RO"), Map.entry("bulgaria", "BG"), Map.entry("croatia", "HR"),
			Map.entry("croacia", "HR"), Map.entry("denmark", "DK"), Map.entry("dinamarca", "DK"),
			Map.entry("sweden", "SE"), Map.entry("suecia", "SE"), Map.entry("finland", "FI"),
			Map.entry("finlandia", "FI"), Map.entry("estonia", "EE"), Map.entry("latvia", "LV"),
			Map.entry("letonia", "LV"), Map.entry("lithuania", "LT"), Map.entry("lituania", "LT"),
			Map.entry("cyprus", "CY"), Map.entry("chipre", "CY"), Map.entry("malta", "MT"), Map.entry("norway", "NO"),
			Map.entry("noruega", "NO"), Map.entry("switzerland", "CH"), Map.entry("suica", "CH"),
			Map.entry("iceland", "IS"), Map.entry("islandia", "IS"));

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final Dataset dataset;

	private final JsonMapper mapper;

	public EurostatFetcher(String upstream, String baseUrl, RestClient client, Dataset dataset, JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.dataset = dataset;
		this.mapper = mapper;
	}

	@Override
	public List<Map<String, Object>> get(DataFetchingEnvironment environment) {
		Object answer = UpstreamGet.json(upstream, client, mapper, uri(environment.getArguments()),
				(json) -> (json instanceof Map<?, ?> map && map.get("value") instanceof Map<?, ?> values)
						? values.size() + " values" : "no values");
		return flatten(answer);
	}

	/**
	 * The request for the arguments. Eurostat rejects a time together with
	 * <code>lastTimePeriod</code> in its own parameter names, so a period with
	 * <code>latestPeriods</code> is rejected here in the field's.
	 */
	URI uri(Map<String, Object> arguments) {
		if (arguments.get("latestPeriods") != null) {
			dataset.dimensions()
				.stream()
				.filter((dimension) -> "time".equals(dimension.dim()) && arguments.get(dimension.name()) != null)
				.findFirst()
				.ifPresent((time) -> {
					throw new InvalidArgumentException(
							"Give %1$s or latestPeriods: %1$s asks for one %1$s, and latestPeriods for the newest %1$ss."
								.formatted(time.name()));
				});
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path("/" + dataset.code());
		for (Dimension dimension : dataset.dimensions()) {
			Object value = arguments.get(dimension.name());
			if (value == null) {
				continue;
			}
			for (Object code : (value instanceof List<?> list) ? list : List.of(value)) {
				builder.queryParam(dimension.dim(),
						"geo".equals(dimension.dim()) ? countryCode(String.valueOf(code)) : code);
			}
		}
		dataset.fixed().forEach(builder::queryParam);
		if (arguments.get("latestPeriods") instanceof Number periods) {
			builder.queryParam("lastTimePeriod", periods.intValue());
		}
		builder.queryParam("lang", "en");
		return builder.encode().build().toUri();
	}

	/**
	 * Eurostat's code of a country given by its English or Portuguese name, such as
	 * <code>PT</code> for Portugal, or the text itself when it is a code already.
	 */
	static String countryCode(String country) {
		String name = Normalizer.normalize(country, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.strip();
		return COUNTRIES.getOrDefault(name, country);
	}

	/**
	 * Turns a JSON-stat answer into one observation for each value. The positions count
	 * through the dimensions in the order of <code>id</code>, the last one fastest.
	 */
	static List<Map<String, Object>> flatten(Object answer) {
		List<Map<String, Object>> rows = new ArrayList<>();
		if (!(answer instanceof Map<?, ?> stat) || !(stat.get("value") instanceof Map<?, ?> values)) {
			return rows;
		}
		List<?> ids = (List<?>) stat.get("id");
		List<?> sizes = (List<?>) stat.get("size");
		Map<?, ?> dimensions = (Map<?, ?>) stat.get("dimension");
		List<String[]> codes = new ArrayList<>();
		for (Object id : ids) {
			Map<?, ?> index = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) dimensions.get(id)).get("category")).get("index");
			String[] byPosition = new String[index.size()];
			index.forEach((code, position) -> byPosition[((Number) position).intValue()] = String.valueOf(code));
			codes.add(byPosition);
		}
		values.forEach((key, value) -> {
			long position = Long.parseLong(String.valueOf(key));
			String[] coordinates = new String[ids.size()];
			for (int i = ids.size() - 1; i >= 0; i--) {
				int size = ((Number) sizes.get(i)).intValue();
				coordinates[i] = codes.get(i)[(int) (position % size)];
				position /= size;
			}
			rows.add(observation(ids, dimensions, coordinates, value));
		});
		return rows;
	}

	private static Map<String, Object> observation(List<?> ids, Map<?, ?> dimensions, String[] coordinates,
			Object value) {
		Map<String, Object> row = new LinkedHashMap<>();
		List<Map<String, Object>> categories = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			String id = String.valueOf(ids.get(i));
			Map<?, ?> dimension = (Map<?, ?>) dimensions.get(id);
			Map<?, ?> labels = (Map<?, ?>) ((Map<?, ?>) dimension.get("category")).get("label");
			Object label = (labels != null) ? labels.get(coordinates[i]) : null;
			switch (id) {
				case "geo" -> {
					row.put("country", coordinates[i]);
					row.put("countryName", label);
				}
				case "time" -> row.put("time", coordinates[i]);
				default -> {
					Map<String, Object> category = new LinkedHashMap<>();
					category.put("dimension", dimension.get("label"));
					category.put("code", coordinates[i]);
					category.put("label", label);
					categories.add(category);
				}
			}
		}
		row.put("categories", categories);
		row.put("value", ValueConversion.NUMBER.apply(value));
		return row;
	}

}
