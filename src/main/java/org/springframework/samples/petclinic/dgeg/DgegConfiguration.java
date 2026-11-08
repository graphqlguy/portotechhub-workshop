package org.springframework.samples.petclinic.dgeg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.samples.petclinic.upstream.PlaceResolver;
import org.springframework.samples.petclinic.upstream.UpstreamClients;

/**
 * The fields of DGEG's fuel prices.
 */
@Configuration(proxyBeanMethods = false)
class DgegConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "DGEG's fuel prices";

	@Bean
	DgegFuelPrices dgegFuelPrices(UpstreamClients clients, PlaceResolver places,
			@Value("${petclinic.upstream.dgeg.url}") String url) {
		return new DgegFuelPrices(UPSTREAM, url, clients.builder().build(), clients.mapper(), places);
	}

}
