package org.springframework.samples.petclinic.porto;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * The hand-written fields of Porto Digital's urban platform: a place by its name, and the
 * buses reporting now.
 */
@Controller
class PortoGraphQLController {

	private final PortoPlaces places;

	private final PortoBuses buses;

	PortoGraphQLController(PortoPlaces places, PortoBuses buses) {
		this.places = places;
		this.buses = buses;
	}

	@QueryMapping
	PortoPlace portoPlace(@Argument String name) {
		return this.places.find(name);
	}

	@QueryMapping
	List<PortoBus> portoBusesNow(@Argument String line, @Argument boolean fastestFirst, @Argument String near,
			@Argument int radiusMetres, @Argument int first) {
		return this.buses.now(line, fastestFirst, near, radiusMetres, first);
	}

}
