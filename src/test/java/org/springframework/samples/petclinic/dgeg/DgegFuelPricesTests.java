package org.springframework.samples.petclinic.dgeg;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.Fixtures;
import org.springframework.samples.petclinic.upstream.GeoPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DgegFuelPricesTests {

	private final DgegFuelPrices prices = new DgegFuelPrices("DGEG", "https://precoscombustiveis.dgeg.gov.pt",
			new Fixtures().answer("GetDistritos", "dgeg-districts.json")
				.answer("idsTiposComb=3201", "dgeg-petrol-porto-district.json")
				.answer("idsTiposComb=2101", "dgeg-diesel-porto-district.json")
				.client(),
			JsonMapper.builder().build(), (name) -> new GeoPoint(41.14321055, -8.621461985));

	@Test
	void cheapestPetrolInPortoDistrict() {
		List<DgegFuelPrice> cheapest = this.prices.cheapest(DgegFuel.PETROL_95, "Porto", null, null, null, null, 3);

		assertThat(cheapest.get(0).station()).isEqualTo("PLENERGY - VILA NOVA DE GAIA III");
		assertThat(cheapest.get(0).priceEurosPerLitre()).isEqualTo(1.869);
		assertThat(cheapest.get(0).updatedAt()).isEqualTo("2026-11-03 17:05");
		assertThat(cheapest.get(0).source()).contains("DGEG");
	}

	@Test
	void cheapestDieselWithin3km() {
		List<DgegFuelPrice> cheapest = this.prices.cheapest(DgegFuel.DIESEL, null, "Alfândega do Porto", null, null,
				3.0, 3);

		assertThat(cheapest.get(0).station()).isEqualTo("AUCHAN Canidelo");
		assertThat(cheapest.get(0).priceEurosPerLitre()).isEqualTo(2.149);
		assertThat(cheapest.get(0).distanceKilometres()).isCloseTo(2.5, within(0.1));
	}

}
