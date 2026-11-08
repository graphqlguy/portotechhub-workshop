package org.springframework.samples.petclinic.sns;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.OpenDataSoftTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the transparency portal of the Portuguese National Health Service (SNS):
 * one for each dataset in <code>sns/opendatasoft.json</code>, which a generator wrote
 * from the portal's metadata, and the emergency visits of one month, which
 * {@link SnsEmergencyVisits} computes.
 */
@Configuration(proxyBeanMethods = false)
class SnsConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The transparency portal of the SNS";

	private final String url;

	private final RestClient client;

	SnsConfiguration(UpstreamClients clients, @Value("${petclinic.upstream.sns.url}") String url) {
		this.url = url;
		this.client = clients.builder().build();
	}

	@Bean
	SnsEmergencyVisits snsEmergencyVisits(UpstreamClients clients) {
		return new SnsEmergencyVisits(UPSTREAM, this.url, this.client, clients.mapper());
	}

	@Bean
	RuntimeWiringConfigurer snsDatasets(UpstreamClients clients) {
		OpenDataSoftTable table = OpenDataSoftTable.read(new ClassPathResource("sns/opendatasoft.json"),
				clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, this.url, this.client, clients.mapper());
	}

}
