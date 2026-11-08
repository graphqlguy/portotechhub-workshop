package org.springframework.samples.petclinic.ipma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of IPMA, the Portuguese Institute for Sea and Atmosphere: forecasts,
 * warnings, earthquakes and observations, one for each file in
 * <code>ipma/operations.json</code>, which a generator wrote from samples of IPMA's open
 * data files.
 */
@Configuration(proxyBeanMethods = false)
class IpmaConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "IPMA, the Portuguese Institute for Sea and Atmosphere";

	@Bean
	RuntimeWiringConfigurer ipmaOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.ipma.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "ipma/operations.json", UPSTREAM, url, client);
	}

}
