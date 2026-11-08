package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.schema.idl.RuntimeWiring;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The indicators of Statistics Portugal (INE) that the schema exposes, as a generator
 * wrote them into a JSON file next to the schema. Each indicator becomes a root field
 * whose arguments choose the period and the categories of its dimensions, and which
 * returns one observation for each combination INE sends.
 *
 * @param places the root field that finds the code of a place by its name
 * @param indicators the indicators, one for each root field
 */
public record IneIndicatorTable(Places places, List<Indicator> indicators) {

	/**
	 * The root field that finds the codes of places, read from the metadata of one
	 * indicator whose dimension lists every place.
	 *
	 * @param field the root field
	 * @param varcd the indicator whose metadata lists the places
	 * @param dim the number of the dimension that holds the places, such as
	 * <code>2</code>
	 */
	public record Places(String field, String varcd, String dim) {
	}

	/**
	 * One indicator.
	 *
	 * @param field the root field
	 * @param varcd INE's code of the indicator, such as <code>0008273</code>
	 * @param period whether the indicator has a value for each year or for each month
	 * @param dimensions the arguments that choose a category of a dimension other than
	 * the period
	 */
	public record Indicator(String field, String varcd, Period period, List<Dimension> dimensions) {

		public Indicator {
			dimensions = (dimensions != null) ? dimensions : List.of();
		}

	}

	/**
	 * How often an indicator has a value.
	 */
	public enum Period {

		/**
		 * One value a year: the field takes a <code>year</code> argument.
		 */
		@JsonProperty("year")
		YEAR,

		/**
		 * One value a month: the field takes a <code>month</code> argument.
		 */
		@JsonProperty("month")
		MONTH

	}

	/**
	 * An argument that chooses a category of a dimension.
	 *
	 * @param name the argument, such as <code>place</code>
	 * @param dim INE's parameter, such as <code>Dim2</code>
	 * @param defaultValue the category sent when the argument is left out, such as
	 * <code>T</code> for the total. With null, the request leaves the dimension out, and INE answers every category.
	 */
	public record Dimension(String name, String dim, @JsonProperty("default") String defaultValue) {
	}

	/**
	 * Reads the indicators from the JSON file that the generator wrote.
	 */
	public static IneIndicatorTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, IneIndicatorTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the indicators in " + resource, ex);
		}
	}

	/**
	 * Registers an {@link IneIndicatorFetcher} for each indicator, and the field that
	 * finds places.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl INE's address, such as <code>https://www.ine.pt</code>
	 * @param client the client that calls INE
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper) {
		Supplier<Object> metadata = (places != null) ? new Once<>(() -> UpstreamGet.json(upstream, client, mapper,
				UriComponentsBuilder.fromUriString(baseUrl)
					.path("/ine/json_indicador/pindicaMeta.jsp")
					.queryParam("varcd", places.varcd())
					.queryParam("lang", "EN")
					.build()
					.toUri(),
				(answer) -> "the metadata")) : null;
		wiring.type("Query", (type) -> {
			Map<String, Map<String, List<List<String>>>> categories = categories(mapper);
			indicators
				.forEach((indicator) -> type.dataFetcher(indicator.field(), new IneIndicatorFetcher(upstream, baseUrl,
						client, indicator, mapper, metadata, categories.getOrDefault(indicator.varcd(), Map.of()))));
			if (places != null) {
				type.dataFetcher(places.field(),
						(environment) -> places(metadata.get(), places.dim(), environment.getArgument("name")));
			}
			return type;
		});
	}

	/**
	 * The codes and names of the categories of every indicator, by dimension, from
	 * <code>ine/categories.json</code>, which the generator wrote from INE's metadata: a
	 * list of categories for each dimension, and each indicator naming its lists.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, List<List<String>>>> categories(JsonMapper mapper) {
		ClassPathResource resource = new ClassPathResource("ine/categories.json");
		if (!resource.exists()) {
			return Map.of();
		}
		try (InputStream json = resource.getInputStream()) {
			Map<String, Object> file = mapper.readValue(json, Map.class);
			Map<String, List<List<String>>> lists = (Map<String, List<List<String>>>) file.get("lists");
			Map<String, Map<String, List<List<String>>>> byIndicator = new HashMap<>();
			((Map<String, Map<String, String>>) file.get("indicators")).forEach((varcd, dims) -> {
				Map<String, List<List<String>>> byDim = new HashMap<>();
				dims.forEach((dim, list) -> byDim.put(dim, lists.get(list)));
				byIndicator.put(varcd, byDim);
			});
			return byIndicator;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the categories in " + resource, ex);
		}
	}

	/**
	 * The categories of a dimension whose name contains a text, ignoring accents and
	 * case, the exact names first.
	 * @param metadata INE's answer for the metadata of an indicator
	 * @param dim the number of the dimension, such as <code>2</code>
	 * @param name the text, such as <code>porto</code>
	 * @return a list of places, each with a <code>code</code>, a <code>name</code> and a
	 * <code>level</code>
	 */
	static List<Map<String, Object>> places(Object metadata, String dim, String name) {
		String wanted = plain(name);
		List<Map<String, Object>> found = new ArrayList<>();
		Object categories = ((Map<?, ?>) ((Map<?, ?>) ((List<?>) metadata).get(0)).get("Dimensoes"))
			.get("Categoria_Dim");
		for (Object group : (List<?>) categories) {
			((Map<?, ?>) group).forEach((key, values) -> {
				if (!String.valueOf(key).startsWith("Dim_Num" + dim + "_")) {
					return;
				}
				for (Object value : (List<?>) values) {
					Map<?, ?> category = (Map<?, ?>) value;
					String label = String.valueOf(category.get("categ_dsg"));
					if (plain(label).contains(wanted)) {
						found.add(Map.of("code", String.valueOf(category.get("categ_cod")), "name", label, "level",
								String.valueOf(category.get("categ_nivel"))));
					}
				}
			});
		}
		found.sort(
				Comparator.comparing((Map<String, Object> place) -> !plain((String) place.get("name")).equals(wanted)));
		return found;
	}

	private static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.strip();
	}

	/**
	 * A value computed at its first use and kept.
	 */
	private static final class Once<T> implements Supplier<T> {

		private final Supplier<T> supplier;

		private volatile T value;

		Once(Supplier<T> supplier) {
			this.supplier = supplier;
		}

		@Override
		public T get() {
			T result = this.value;
			if (result == null) {
				result = this.supplier.get();
				this.value = result;
			}
			return result;
		}

	}

}
