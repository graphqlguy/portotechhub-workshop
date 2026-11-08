package org.springframework.samples.petclinic.turismo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.ArcGisLayerTable;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of Turismo de Portugal, from the layers of its open data ArcGIS server, one
 * for each layer in <code>turismo/arcgis.json</code>, which a generator wrote from the
 * server's description of each layer.
 */
@Configuration(proxyBeanMethods = false)
class TurismoConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "Turismo de Portugal";

	@Bean
	RuntimeWiringConfigurer turismoLayers(UpstreamClients clients,
			@Value("${petclinic.upstream.turismo.url}") String url) {
		RestClient client = clients.builder().build();
		ArcGisLayerTable table = ArcGisLayerTable.read(new ClassPathResource("turismo/arcgis.json"), clients.mapper());
		return (wiring) -> table.register(wiring, UPSTREAM, url, client, clients.mapper());
	}

}
