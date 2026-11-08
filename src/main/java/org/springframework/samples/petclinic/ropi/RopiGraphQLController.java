package org.springframework.samples.petclinic.ropi;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * The parks of Porto nearest a place.
 */
@Controller
class RopiGraphQLController {

	private final RopiParks parks;

	RopiGraphQLController(RopiParks parks) {
		this.parks = parks;
	}

	@QueryMapping
	List<RopiPark> ropiNearestParks(@Argument String place, @Argument Double latitude, @Argument Double longitude,
			@Argument int first) {
		return this.parks.nearest(place, latitude, longitude, first);
	}

}
