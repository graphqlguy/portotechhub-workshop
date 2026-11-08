package org.springframework.samples.petclinic.ine;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.IneIndicatorTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of Statistics Portugal (INE), one for each indicator in
 * <code>ine/indicators.json</code>, which a generator wrote from INE's metadata.
 */
@Configuration(proxyBeanMethods = false)
class IneConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "INE, Statistics Portugal";

	@Bean
	RuntimeWiringConfigurer ineIndicators(UpstreamClients clients, @Value("${petclinic.upstream.ine.url}") String url) {
		// INE takes up to half a minute for a large indicator
		RestClient client = clients.builder(Duration.ofSeconds(60)).build();
		IneIndicatorTable table = IneIndicatorTable.read(new ClassPathResource("ine/indicators.json"),
				clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, url, client, clients.mapper());
	}

}
