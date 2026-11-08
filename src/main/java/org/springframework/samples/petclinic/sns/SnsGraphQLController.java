package org.springframework.samples.petclinic.sns;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * The emergency visits of each public hospital in one month.
 */
@Controller
class SnsGraphQLController {

	private final SnsEmergencyVisits visits;

	SnsGraphQLController(SnsEmergencyVisits visits) {
		this.visits = visits;
	}

	@QueryMapping
	List<SnsHospitalEmergencyVisits> snsEmergencyVisitsInMonth(@Argument String month, @Argument String region) {
		return this.visits.inMonth(month, region);
	}

}
