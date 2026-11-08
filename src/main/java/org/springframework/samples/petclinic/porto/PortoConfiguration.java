package org.springframework.samples.petclinic.porto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.NgsiEntityTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.samples.petclinic.upstream.UpstreamClock;
import org.springframework.web.client.RestClient;

/**
 * The fields of Porto Digital's urban platform, a FIWARE context broker: one for each
 * entity type in <code>porto/ngsi.json</code>, which a generator wrote from samples of
 * each type, and the hand-written fields of {@link PortoGraphQLController}.
 */
@Configuration(proxyBeanMethods = false)
class PortoConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "Porto Digital's urban platform";

	private final String url;

	private final RestClient client;

	PortoConfiguration(UpstreamClients clients, @Value("${petclinic.upstream.porto.url}") String url) {
		this.url = url;
		this.client = clients.builder().build();
	}

	@Bean
	PortoPlaces portoPlaces(UpstreamClients clients) {
		return new PortoPlaces(UPSTREAM, this.url, this.client, clients.mapper());
	}

	@Bean
	PortoBuses portoBuses(UpstreamClients clients, UpstreamClock clock, PortoPlaces places) {
		return new PortoBuses(UPSTREAM, this.url, this.client, clients.mapper(), clock, places);
	}

	@Bean
	RuntimeWiringConfigurer portoEntities(UpstreamClients clients, UpstreamClock clock, PortoPlaces places) {
		NgsiEntityTable table = NgsiEntityTable.read(new ClassPathResource("porto/ngsi.json"), clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, this.url, this.client, clients.mapper(), clock, places);
	}

}
