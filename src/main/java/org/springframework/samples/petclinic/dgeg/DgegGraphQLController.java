package org.springframework.samples.petclinic.dgeg;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * The cheapest fuel in a district or near a place.
 */
@Controller
class DgegGraphQLController {

	private final DgegFuelPrices prices;

	DgegGraphQLController(DgegFuelPrices prices) {
		this.prices = prices;
	}

	@QueryMapping
	List<DgegFuelPrice> dgegCheapestFuel(@Argument DgegFuel fuel, @Argument String district, @Argument String place,
			@Argument Double latitude, @Argument Double longitude, @Argument Double radiusKilometres,
			@Argument int first) {
		return this.prices.cheapest(fuel, district, place, latitude, longitude, radiusKilometres, first);
	}

}
