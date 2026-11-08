package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Dimension;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Indicator;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable.Period;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Serves the root field of one INE indicator. The arguments become INE's parameters: a
 * year becomes <code>Dim1=S7A2023</code>, a month <code>Dim1=S3A202609</code>, every
 * period <code>Dim1=T</code>, and without any of them INE answers the latest period. Each
 * dimension argument becomes its parameter, or its default when it is left out, and a
 * place given by its name, such as Porto, becomes its code. INE's answer, a map from each
 * period to its rows, becomes a flat list of observations.
 */
public class IneIndicatorFetcher implements DataFetcher<List<Map<String, Object>>> {

	private static final String PLACE = "place";

	/**
	 * An INE code: capitals and digits, such as <code>PT</code> or <code>11A1312</code>.
	 * Anything else is the name of a place.
	 */
	private static final Pattern CODE = Pattern.compile("[A-Z0-9_]+");

	/**
	 * A dimension in INE's reason for rejecting the arguments, such as <code>Dim1</code>.
	 */
	private static final Pattern DIMENSION = Pattern.compile("Dim\\d+");

	private static final Set<String> TOTALS = Set.of("all", "total", "todos", "todas", "every", "any");

	private static final Set<String> STOP_WORDS = Set.of("and", "or", "of", "the", "for", "a", "an", "in", "e", "ou",
			"de", "do", "da", "dos", "das", "para");

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final Indicator indicator;

	private final JsonMapper mapper;

	private final Supplier<Object> places;

	private final Map<String, List<List<String>>> categories;

	public IneIndicatorFetcher(String upstream, String baseUrl, RestClient client, Indicator indicator,
			JsonMapper mapper) {
		this(upstream, baseUrl, client, indicator, mapper, null, Map.of());
	}

	public IneIndicatorFetcher(String upstream, String baseUrl, RestClient client, Indicator indicator,
			JsonMapper mapper, Supplier<Object> places) {
		this(upstream, baseUrl, client, indicator, mapper, places, Map.of());
	}

	/**
	 * @param places INE's metadata of the places, which turns a place given by its name
	 * into its code, or null when every place comes as a code
	 * @param categories the codes and names of the categories of each dimension, such as
	 * <code>Dim3</code>, which turn a category given by its name into its code
	 */
	public IneIndicatorFetcher(String upstream, String baseUrl, RestClient client, Indicator indicator,
			JsonMapper mapper, Supplier<Object> places, Map<String, List<List<String>>> categories) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.indicator = indicator;
		this.mapper = mapper;
		this.places = places;
		this.categories = (categories != null) ? categories : Map.of();
	}

	@Override
	public List<Map<String, Object>> get(DataFetchingEnvironment environment) {
		// an answer for every period without the period of each value cannot say which
		// year a value is for, and the model then guesses it
		if (Boolean.TRUE.equals(environment.getArgument("allPeriods"))
				&& !environment.getSelectionSet().contains("period")) {
			throw new InvalidArgumentException("With allPeriods, the answer has one observation for each period, "
					+ "so select period to know which period each value is for.");
		}
		Object answer = UpstreamGet.json(upstream, client, mapper, uri(environment.getArguments()),
				(json) -> "the indicator " + indicator.varcd());
		return flatten(answer);
	}

	URI uri(Map<String, Object> arguments) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
			.path("/ine/json_indicador/pindica.jsp")
			.queryParam("op", "2")
			.queryParam("varcd", indicator.varcd())
			.queryParam("lang", "EN");
		String period = period(arguments);
		if (period != null) {
			builder.queryParam("Dim1", period);
		}
		for (Dimension dimension : indicator.dimensions()) {
			Object value = arguments.get(dimension.name());
			if (value instanceof String text && TOTALS.contains(plain(text))) {
				// "all" or "total" asks for the total, which is the default of the
				// dimension
				value = null;
			}
			else if (value instanceof String name && !CODE.matcher(name).matches()) {
				value = PLACE.equals(dimension.name()) ? placeCode(name) : categoryCode(dimension, name);
			}
			Object sent = (value != null) ? value : dimension.defaultValue();
			if (sent != null) {
				builder.queryParam(dimension.dim(), sent);
			}
		}
		return builder.encode().build().toUri();
	}

	/**
	 * The code of a place given by its name, such as <code>11A1312</code> for Porto: the
	 * place whose name equals the text, ignoring accents and case, or else the first
	 * whose name contains it.
	 */
	private String placeCode(String name) {
		if (places == null) {
			return name;
		}
		List<Map<String, Object>> found = IneIndicatorTable.places(places.get(), "2", name);
		if (found.isEmpty()) {
			throw new InvalidArgumentException(
					"INE does not have a place called \"%s\". inePlaces finds the codes of places.".formatted(name));
		}
		return (String) found.get(0).get("code");
	}

	/**
	 * The code of a category given by its name, such as <code>230910</code> for dog and
	 * cat food: the category whose name has every word of the text, ignoring accents,
	 * case and words such as "and", the shortest name first.
	 */
	private String categoryCode(Dimension dimension, String name) {
		List<List<String>> known = categories.get(dimension.dim());
		if (known == null) {
			return name;
		}
		List<String> words = Arrays.stream(plain(name).split("[^\\p{L}\\p{N}]+"))
			.filter((word) -> !word.isEmpty() && !STOP_WORDS.contains(word))
			.map((word) -> word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1) : word)
			.toList();
		return known.stream().filter((category) -> {
			String label = plain(category.get(1));
			return !words.isEmpty() && words.stream().allMatch(label::contains);
		})
			.min(Comparator.comparingInt((List<String> category) -> category.get(1).length()))
			.map((category) -> category.get(0))
			.orElseThrow(() -> new InvalidArgumentException(
					"INE does not have a category of %s called \"%s\". Give its code, such as %s.".formatted(dimension.name(),
							name,
							known.stream()
								.limit(3)
								.map((category) -> category.get(0) + " for " + category.get(1))
								.collect(Collectors.joining(", ")))));
	}

	private static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.strip();
	}

	private String period(Map<String, Object> arguments) {
		if (Boolean.TRUE.equals(arguments.get("allPeriods"))) {
			return "T";
		}
		if (indicator.period() == Period.MONTH && arguments.get("month") instanceof String month) {
			return "S3A" + month.replace("-", "");
		}
		if (arguments.get("year") instanceof Number year) {
			return "S7A" + year.intValue();
		}
		return null;
	}

	/**
	 * Turns INE's answer into one observation for each row of each period, or into an
	 * error that repeats INE's reason when it rejected the arguments.
	 */
	List<Map<String, Object>> flatten(Object answer) {
		List<Map<String, Object>> rows = new ArrayList<>();
		if (!(answer instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> result)) {
			return rows;
		}
		rejected(result);
		if (!(result.get("Dados") instanceof Map<?, ?> data)) {
			return rows;
		}
		data.forEach((period, values) -> {
			for (Object value : (List<?>) values) {
				rows.add(observation(String.valueOf(period), (Map<?, ?>) value));
			}
		});
		return rows;
	}

	private void rejected(Map<?, ?> result) {
		if (result.get("Sucesso") instanceof Map<?, ?> success && success.get("Falso") instanceof List<?> failures
				&& !failures.isEmpty()) {
			String message = String.valueOf(((Map<?, ?>) failures.get(0)).get("Msg"));
			int english = message.indexOf("(EN) ");
			String reason = (english >= 0) ? message.substring(english + 5) : message;
			throw new InvalidArgumentException(
					"%s rejected the arguments: %s%s".formatted(upstream, reason, arguments(reason)));
		}
	}

	/**
	 * The argument behind each dimension that INE's reason names, such as " Dim1 is the
	 * year: leave it out for the latest year.", because INE names its dimensions and the
	 * model sent arguments.
	 */
	private String arguments(String reason) {
		StringBuilder text = new StringBuilder();
		DIMENSION.matcher(reason).results().map(MatchResult::group).distinct().forEach((dim) -> {
			if ("Dim1".equals(dim)) {
				String period = (indicator.period() == Period.MONTH) ? "month" : "year";
				text.append(" Dim1 is the %1$s: leave it out for the latest %1$s.".formatted(period));
			}
			else {
				indicator.dimensions()
					.stream()
					.filter((dimension) -> dimension.dim().equals(dim))
					.findFirst()
					.ifPresent((dimension) -> text.append(" %s is the argument %s.".formatted(dim, dimension.name())));
			}
		});
		return text.toString();
	}

	private static Map<String, Object> observation(String period, Map<?, ?> row) {
		Map<String, Object> observation = new LinkedHashMap<>();
		observation.put("period", period);
		observation.put("placeCode", row.get("geocod"));
		observation.put("place", row.get("geodsg"));
		List<Map<String, Object>> categories = new ArrayList<>();
		for (int dim = 3; row.containsKey("dim_" + dim); dim++) {
			Map<String, Object> category = new LinkedHashMap<>();
			category.put("dimension", dim);
			category.put("code", row.get("dim_" + dim));
			category.put("label", row.get("dim_" + dim + "_t"));
			categories.add(category);
		}
		observation.put("categoryCode", categories.isEmpty() ? null : categories.get(0).get("code"));
		observation.put("category", categories.isEmpty() ? null : categories.get(0).get("label"));
		observation.put("categories", categories);
		observation.put("value", ValueConversion.NUMBER.apply(row.get("valor")));
		observation.put("valueText", row.get("ind_string"));
		observation.put("note", row.get("sinal_conv_desc"));
		return observation;
	}

}
