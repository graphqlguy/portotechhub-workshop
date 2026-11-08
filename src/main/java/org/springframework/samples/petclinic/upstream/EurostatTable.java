package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import graphql.schema.idl.RuntimeWiring;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

/**
 * The datasets of Eurostat that the schema exposes, as a generator wrote them into a JSON
 * file next to the schema. Each dataset becomes a root field whose arguments filter its
 * dimensions, and which returns one observation for each value Eurostat sends.
 *
 * @param datasets the datasets, one for each root field
 */
public record EurostatTable(List<Dataset> datasets) {

	/**
	 * One dataset.
	 *
	 * @param field the root field
	 * @param code Eurostat's code of the dataset, such as <code>prc_hicp_minr</code>
	 * @param dimensions the arguments that filter a dimension
	 * @param fixed parameters every request sends, such as the unit
	 */
	public record Dataset(String field, String code, List<Dimension> dimensions, Map<String, String> fixed) {

		public Dataset {
			dimensions = (dimensions != null) ? dimensions : List.of();
			fixed = (fixed != null) ? fixed : Map.of();
		}

	}

	/**
	 * An argument that filters a dimension.
	 *
	 * @param name the argument, such as <code>countries</code>
	 * @param dim Eurostat's dimension, such as <code>geo</code>
	 * @param list whether the argument takes several codes, which the request repeats
	 */
	public record Dimension(String name, String dim, boolean list) {
	}

	/**
	 * Reads the datasets from the JSON file that the generator wrote.
	 */
	public static EurostatTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, EurostatTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the datasets in " + resource, ex);
		}
	}

	/**
	 * Registers a {@link EurostatFetcher} for each dataset.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the URL the dataset codes follow
	 * @param client the client that calls Eurostat
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper) {
		wiring.type("Query", (type) -> {
			datasets.forEach((dataset) -> type.dataFetcher(dataset.field(),
					new EurostatFetcher(upstream, baseUrl, client, dataset, mapper)));
			return type;
		});
	}

}
