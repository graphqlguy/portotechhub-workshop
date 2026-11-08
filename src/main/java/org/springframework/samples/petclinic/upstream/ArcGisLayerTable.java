package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.schema.idl.RuntimeWiring;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.Resource;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.web.client.RestClient;

/**
 * The layers of an ArcGIS server that the schema exposes, as a generator wrote them from
 * the server's own description of each layer into a JSON file next to the schema. Each
 * layer becomes a root field whose arguments filter the records, and which returns a page
 * with the number of matching records and the first of them.
 *
 * @param layers the layers, one for each root field
 */
public record ArcGisLayerTable(List<Layer> layers) {

	/**
	 * One layer.
	 *
	 * @param field the root field
	 * @param path the path of the layer on the server, such as
	 * <code>/Dashboard/Praias_dashboard/MapServer/0</code>
	 * @param type the GraphQL type of a record
	 * @param filters the arguments that filter the records
	 * @param attributes the fields of the type, each with the attribute of the layer it
	 * reads and how its value is converted
	 * @param orderBy the attributes the records are sorted by, such as
	 * <code>nome_praia</code>, or null for the server's order
	 */
	public record Layer(String field, String path, String type, List<Filter> filters,
			Map<String, FieldSource> attributes, String orderBy) {

		public Layer {
			filters = (filters != null) ? filters : List.of();
			attributes = (attributes != null) ? attributes : Map.of();
		}

	}

	/**
	 * An argument that filters the records of a layer.
	 *
	 * @param name the argument, such as <code>municipality</code>
	 * @param attribute the attribute of the layer, such as <code>concelho</code>
	 * @param kind how the value is compared
	 * @param upperCase true for an attribute the layer writes in capitals, such as
	 * <code>PORTO</code>
	 */
	public record Filter(String name, String attribute, Kind kind, Boolean upperCase) {
	}

	/**
	 * How the value of a filter is compared with the attribute.
	 */
	public enum Kind {

		/**
		 * The attribute equals the text.
		 */
		@JsonProperty("equals")
		EQUALS,

		/**
		 * The attribute contains the text, ignoring case.
		 */
		@JsonProperty("contains")
		CONTAINS,

		/**
		 * The attribute equals the number.
		 */
		@JsonProperty("number")
		NUMBER

	}

	/**
	 * Reads the layers from the JSON file that the generator wrote.
	 */
	public static ArcGisLayerTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, ArcGisLayerTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the layers in " + resource, ex);
		}
	}

	/**
	 * Registers an {@link ArcGisLayerFetcher} for each layer, and a fetcher for each
	 * field of its type that reads its attribute.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the address of the server's services, such as
	 * <code>https://sniambgeoogc.apambiente.pt/getogc/rest/services</code>
	 * @param client the client that calls the server
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper) {
		wiring.type("Query", (type) -> {
			layers.forEach((layer) -> type.dataFetcher(layer.field(),
					new ArcGisLayerFetcher(upstream, baseUrl, client, layer, mapper)));
			return type;
		});
		layers.forEach((layer) -> wiring.type(layer.type(), (type) -> {
			layer.attributes()
				.forEach((field, source) -> type.dataFetcher(field,
						(environment) -> source.read(environment.getSource())));
			return type;
		}));
	}

}
