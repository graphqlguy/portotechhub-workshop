package org.springframework.samples.petclinic.upstream;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.NgsiEntityTable.Entity;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class NgsiEntityFetcherTests {

	private static final String BASE = "https://broker.fiware.urbanplatform.portodigital.pt";

	private static final Entity AIR = new Entity("portoAirQuality", "AirQualityObserved", "PortoAirQuality",
			"dateObserved", Map.of("station", new FieldSource("name", null), "street",
					new FieldSource("address/streetAddress", null), "pm10", new FieldSource("pm10", null)));

	private static final Entity BUSES = new Entity("portoVehicles", "Vehicle", "PortoVehicle", "observationDateTime",
			Map.of());

	private final UpstreamClock clock = new UpstreamClock("2026-11-05T19:50:00Z");

	@Test
	void nearSendsGeorel() {
		String uri = fetcher(AIR, (name) -> null)
			.uri(Map.of("latitude", 41.14321, "longitude", -8.62146, "radiusMetres", 1000))
			.toString();

		assertThat(uri).startsWith(BASE + "/v2/entities?type=AirQualityObserved&options=keyValues")
			.contains("&georel=near;maxDistance:1000&geometry=point&coords=41.14321,-8.62146");
	}

	@Test
	void nearAPlaceNameAsksTheResolver() {
		String uri = fetcher(AIR, (name) -> "Alfândega do Porto".equals(name) ? new GeoPoint(41.14321, -8.62146) : null)
			.uri(Map.of("near", "Alfândega do Porto"))
			.toString();

		assertThat(uri).contains("&georel=near;maxDistance:1000&geometry=point&coords=41.14321,-8.62146");
	}

	@Test
	void nearPortoMeansTheWholeCity() {
		PlaceResolver places = new PlaceResolver() {

			@Override
			public GeoPoint resolve(String name) {
				// PortoPlaces finds a business whose name starts with Porto
				return new GeoPoint(41.14718532, -8.62569521);
			}

			@Override
			public boolean isWholeCity(String name) {
				return "Porto".equals(name);
			}

		};

		String uri = fetcher(AIR, places).uri(Map.of("near", "Porto")).toString();

		assertThat(uri).doesNotContain("georel").doesNotContain("coords");
	}

	@Test
	void attrsAskForTheAttributesTheTypeReads() {
		String uri = fetcher(AIR, (name) -> null).uri(Map.of()).toString();

		assertThat(uri).contains("&attrs=address,dateObserved,name,pm10");
	}

	@Test
	void sinceMinutesUsesTheClock() {
		String uri = fetcher(BUSES, (name) -> null).uri(Map.of("sinceMinutes", 5)).toString();

		assertThat(uri).contains("&q=observationDateTime%3E2026-11-05T19:45:00Z");
	}

	@Test
	void firstSendsLimit() {
		assertThat(fetcher(BUSES, (name) -> null).uri(Map.of()).toString()).contains("&limit=20");
		assertThat(fetcher(BUSES, (name) -> null).uri(Map.of("first", 5000)).toString()).contains("&limit=1000");
	}

	private NgsiEntityFetcher fetcher(Entity entity, PlaceResolver places) {
		return new NgsiEntityFetcher("Porto Digital", BASE, RestClient.create(), entity, JsonMapper.builder().build(),
				this.clock, places);
	}

}
