package org.springframework.samples.petclinic.apa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the Portuguese Environment Agency (APA), from the layers of its SNIAmb
 * ArcGIS server, one for each layer in <code>apa/arcgis.json</code>, which a generator
 * wrote from the server's description of each layer.
 */
@Configuration(proxyBeanMethods = false)
class ApaConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "APA, the Portuguese Environment Agency";

	@Bean
	RuntimeWiringConfigurer apaLayers(UpstreamClients clients, @Value("${petclinic.upstream.apa.url}") String url) {
		RestClient client = clients.builder().build();
		ArcGisLayerTable table = ArcGisLayerTable.read(new ClassPathResource("apa/arcgis.json"), clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, url, client, clients.mapper());
	}

}
