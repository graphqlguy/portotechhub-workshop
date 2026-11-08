package org.springframework.samples.petclinic.ropi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamClients;

/**
 * The fields of ROPI, the official register of points of interest of Porto Digital.
 */
@Configuration(proxyBeanMethods = false)
class RopiConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "ROPI, Porto's register of points of interest";

	@Bean
	RopiParks ropiParks(UpstreamClients clients, PlaceResolver places,
			@Value("${petclinic.upstream.ropi.url}") String url) {
		return new RopiParks(UPSTREAM, url, clients.builder().build(), clients.mapper(), places);
	}

}
