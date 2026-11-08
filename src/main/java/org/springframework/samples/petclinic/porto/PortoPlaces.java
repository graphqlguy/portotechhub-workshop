package org.springframework.samples.petclinic.porto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamGet;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Finds a place in Porto by its name, among the points of interest of Porto Digital's
 * urban platform, such as the Alfândega do Porto. The fields that search near a place use
 * it, so the model can name a place in place of a latitude and a longitude. The list is
 * read once a day.
 */
public class PortoPlaces implements PlaceResolver {

	private static final Duration KEPT_FOR = Duration.ofDays(1);

	/**
	 * The names of Porto itself, which ask for the whole city.
	 */
	private static final Set<String> CITY = Set.of("porto", "o porto", "porto city", "city of porto",
			"the city of porto", "cidade do porto", "porto, portugal", "porto portugal");

	/**
	 * The other municipalities of Portugal, such as Braga, whose names would otherwise
	 * match a place in Porto that is named after them, such as Jardim de Teófilo Braga.
	 */
	private static final Set<String> OTHER_MUNICIPALITIES = otherMunicipalities();

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final JsonMapper mapper;

	private volatile List<PortoPlace> places;

	private volatile Instant readAt;

	public PortoPlaces(String upstream, String baseUrl, RestClient client, JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.mapper = mapper;
	}

	@Override
	public boolean isWholeCity(String name) {
		return CITY.contains(plain(name));
	}

	@Override
	public GeoPoint resolve(String name) {
		PortoPlace place = find(name);
		return new GeoPoint(place.latitude(), place.longitude());
	}

	/**
	 * The place whose name matches best, ignoring accents and case: a name equal to the
	 * text first, then a Portuguese name that starts with it, then an English one, then a
	 * name that contains it, then an address that contains it. Among equals, the shortest
	 * name wins. The name of another municipality, such as Braga, is outside Porto unless a
	 * place has exactly that name.
	 * @throws InvalidArgumentException when no place matches, or for another municipality
	 */
	public PortoPlace find(String name) {
		String wanted = plain(name);
		List<PortoPlace> read = places();
		if (OTHER_MUNICIPALITIES.contains(wanted) && read.stream().allMatch((place) -> score(place, wanted) > 0)) {
			throw new InvalidArgumentException(
					"\"%s\" is a municipality outside Porto, and this field finds places in the city of Porto. For a place in Porto with that word in its name, give the place's full name."
						.formatted(name));
		}
		return read.stream()
			.map((place) -> new Match(place, score(place, wanted)))
			.filter((match) -> match.score() < Integer.MAX_VALUE)
			.min(Comparator.comparingInt(Match::score).thenComparingInt((match) -> match.place().name().length()))
			.map(Match::place)
			.orElseThrow(() -> new InvalidArgumentException(
					"No place in Porto matches \"%s\". Try the name of a street, a monument or a building, such as Alfândega do Porto."
						.formatted(name)));
	}

	private static int score(PortoPlace place, String wanted) {
		String portuguese = plain(place.name());
		String english = plain(Objects.requireNonNullElse(place.englishName(), ""));
		if (portuguese.equals(wanted) || english.equals(wanted)) {
			return 0;
		}
		if (portuguese.startsWith(wanted)) {
			return 1;
		}
		if (english.startsWith(wanted)) {
			return 2;
		}
		if (portuguese.contains(wanted)) {
			return 3;
		}
		if (english.contains(wanted)) {
			return 4;
		}
		if (place.address() != null && plain(place.address()).contains(wanted)) {
			return 5;
		}
		return Integer.MAX_VALUE;
	}

	private List<PortoPlace> places() {
		List<PortoPlace> read = this.places;
		if (read == null || this.readAt.plus(KEPT_FOR).isBefore(Instant.now())) {
			URI uri = UriComponentsBuilder.fromUriString(baseUrl)
				.path("/v2/entities")
				.queryParam("type", "PointOfInterest")
				.queryParam("options", "keyValues")
				.queryParam("limit", 1000)
				.queryParam("attrs", "name,name_pt,location,address")
				.encode()
				.build()
				.toUri();
			Object answer = UpstreamGet.json(upstream, client, mapper, uri,
					(json) -> (json instanceof List<?> list) ? list.size() + " places" : "an answer");
			read = (answer instanceof List<?> list) ? list.stream().flatMap(PortoPlaces::place).toList() : List.of();
			this.places = read;
			this.readAt = Instant.now();
		}
		return read;
	}

	private static Stream<PortoPlace> place(Object entity) {
		if (!(entity instanceof Map<?, ?> map) || !(map.get("location") instanceof Map<?, ?> location)
				|| !(location.get("coordinates") instanceof List<?> coordinates) || coordinates.size() < 2) {
			return Stream.empty();
		}
		String english = (String) map.get("name");
		String portuguese = (map.get("name_pt") instanceof String pt && !pt.isBlank()) ? pt.strip() : english;
		if (portuguese == null) {
			return Stream.empty();
		}
		return Stream.of(new PortoPlace(portuguese, english, ((Number) coordinates.get(1)).doubleValue(),
				((Number) coordinates.get(0)).doubleValue(), address(map.get("address"))));
	}

	private static String address(Object address) {
		if (!(address instanceof Map<?, ?> map)) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		Stream.of("streetAddress", "postalCode", "addressLocality")
			.map(map::get)
			.filter(String.class::isInstance)
			.map((part) -> ((String) part).strip().replaceAll(",$", ""))
			.filter((part) -> !part.isEmpty())
			.forEach(parts::add);
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private static Set<String> otherMunicipalities() {
		try (InputStream in = new ClassPathResource("porto/municipalities.txt").getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
				.filter((line) -> !line.isBlank() && !line.startsWith("#"))
				.map(PortoPlaces::plain)
				.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ")
			.strip();
	}

	private record Match(PortoPlace place, int score) {
	}

}
