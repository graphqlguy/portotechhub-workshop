package org.springframework.samples.petclinic.eurostat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.EurostatTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of Eurostat, the statistical office of the European Union, one for each
 * dataset in <code>eurostat/datasets.json</code>, which a generator wrote from Eurostat's
 * structure definitions.
 */
@Configuration(proxyBeanMethods = false)
class EurostatConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "Eurostat";

	@Bean
	RuntimeWiringConfigurer eurostatDatasets(UpstreamClients clients,
			@Value("${petclinic.upstream.eurostat.url}") String url) {
		RestClient client = clients.builder().build();
		EurostatTable table = EurostatTable.read(new ClassPathResource("eurostat/datasets.json"), clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, url, client, clients.mapper());
	}

}
