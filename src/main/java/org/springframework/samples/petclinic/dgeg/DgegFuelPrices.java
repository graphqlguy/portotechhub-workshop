package org.springframework.samples.petclinic.dgeg;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.GeoPoint;
import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamGet;
import org.springframework.samples.petclinic.upstream.ValueConversion;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The cheapest fuel in a district, or within a radius of a place, from DGEG's portal of
 * fuel prices, Preços dos Combustíveis Online. Every filling station in Portugal must
 * report its prices to DGEG before they apply. The portal's API is the one its own pages
 * use, without documentation; its terms allow free reuse if the reuser names DGEG, gives
 * the time of the last update and reproduces the prices faithfully, so every price
 * carries its source and its update time. The stations of a district are kept for ten
 * minutes.
 */
public class DgegFuelPrices {

	static final String SOURCE = "DGEG, Preços dos Combustíveis Online (precoscombustiveis.dgeg.gov.pt)";

	private static final String DEFAULT_DISTRICT = "Porto";

	private static final double DEFAULT_RADIUS_KILOMETRES = 5;

	private static final Duration KEPT_FOR = Duration.ofMinutes(10);

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final JsonMapper mapper;

	private final PlaceResolver places;

	private final Map<String, Stations> stations = new ConcurrentHashMap<>();

	private volatile Map<String, Integer> districts;

	public DgegFuelPrices(String upstream, String baseUrl, RestClient client, JsonMapper mapper, PlaceResolver places) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.mapper = mapper;
		this.places = places;
	}

	/**
	 * The cheapest prices of a fuel, cheapest first, and nearest first among equal
	 * prices.
	 * @param fuel the fuel
	 * @param district the district, such as Porto or Lisboa; null for Porto
	 * @param place a place in Porto to search near, or null
	 * @param latitude the latitude to search near, or null
	 * @param longitude the longitude to search near, or null
	 * @param radiusKilometres the radius around the place; null for 5 km
	 * @param first how many prices to return
	 */
	public List<DgegFuelPrice> cheapest(DgegFuel fuel, String district, String place, Number latitude, Number longitude,
			Number radiusKilometres, int first) {
		GeoPoint point = PlaceResolver.point(places, place, latitude, longitude);
		double radius = (radiusKilometres != null) ? radiusKilometres.doubleValue() : DEFAULT_RADIUS_KILOMETRES;
		return stations(fuel, districtId((district != null) ? district : DEFAULT_DISTRICT)).stream()
			.map((station) -> price(station, point))
			.filter((price) -> price.priceEurosPerLitre() != null)
			.filter((price) -> point == null || price.distanceKilometres() <= radius)
			.sorted(Comparator.comparing(DgegFuelPrice::priceEurosPerLitre)
				.thenComparing((price) -> (price.distanceKilometres() != null) ? price.distanceKilometres() : 0))
			.limit(Math.max(1, first))
			.toList();
	}

	private static DgegFuelPrice price(Map<?, ?> station, GeoPoint point) {
		Double distance = null;
		if (point != null && station.get("Latitude") instanceof Number latitude
				&& station.get("Longitude") instanceof Number longitude) {
			distance = Math
				.round(point.distanceInMetres(new GeoPoint(latitude.doubleValue(), longitude.doubleValue())) / 100.0)
					/ 10.0;
		}
		return new DgegFuelPrice(text(station.get("Nome")), text(station.get("Marca")), text(station.get("Municipio")),
				text(station.get("Morada")), text(station.get("Combustivel")),
				(Double) ValueConversion.EURO_PRICE.apply(station.get("Preco")), text(station.get("DataAtualizacao")),
				(point != null) ? ((distance != null) ? distance : Double.MAX_VALUE) : null, SOURCE);
	}

	private static String text(Object value) {
		return (value != null) ? value.toString().strip() : null;
	}

	private List<Map<?, ?>> stations(DgegFuel fuel, int district) {
		String key = fuel.id() + "/" + district;
		Stations kept = this.stations.get(key);
		if (kept != null && kept.readAt().plus(KEPT_FOR).isAfter(Instant.now())) {
			return kept.stations();
		}
		URI uri = UriComponentsBuilder.fromUriString(baseUrl)
			.path("/api/PrecoComb/PesquisarPostos")
			.queryParam("idsTiposComb", fuel.id())
			.queryParam("idMarca", "")
			.queryParam("idTipoPosto", "")
			.queryParam("idDistrito", district)
			.queryParam("idsMunicipios", "")
			.queryParam("qtdPorPagina", 1000)
			.queryParam("pagina", 1)
			.encode()
			.build()
			.toUri();
		List<Map<?, ?>> read = results(UpstreamGet.json(upstream, client, mapper, uri, DgegFuelPrices::summary));
		this.stations.put(key, new Stations(read, Instant.now()));
		return read;
	}

	private int districtId(String district) {
		Map<String, Integer> ids = this.districts;
		if (ids == null) {
			URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/api/PrecoComb/GetDistritos").build().toUri();
			ids = results(UpstreamGet.json(upstream, client, mapper, uri, DgegFuelPrices::summary)).stream()
				.collect(Collectors.toMap((row) -> plain(String.valueOf(row.get("Descritivo"))),
						(row) -> ((Number) row.get("Id")).intValue(), (a, b) -> a));
			this.districts = ids;
		}
		Integer id = ids.get(plain(district));
		if (id == null) {
			throw new InvalidArgumentException("DGEG does not have a district called \"%s\". The districts are: %s."
				.formatted(district, ids.keySet().stream().sorted().collect(Collectors.joining(", "))));
		}
		return id;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<?, ?>> results(Object answer) {
		if (answer instanceof Map<?, ?> map && map.get("resultado") instanceof List<?> list) {
			return (List<Map<?, ?>>) list;
		}
		return List.of();
	}

	private static String summary(Object answer) {
		return results(answer).size() + " rows";
	}

	private static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.strip();
	}

	private record Stations(List<Map<?, ?>> stations, Instant readAt) {
	}

}
