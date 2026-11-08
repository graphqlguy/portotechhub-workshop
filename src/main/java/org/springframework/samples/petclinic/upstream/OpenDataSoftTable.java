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
 * The datasets of an OpenDataSoft portal that the schema exposes, as a generator wrote
 * them from the portal's metadata into a JSON file next to the schema. Each dataset
 * becomes a root field whose arguments filter its records, and which returns a page with
 * the number of matching records and the first of them.
 *
 * @param datasets the datasets, one for each root field
 */
public record OpenDataSoftTable(List<Dataset> datasets) {

	/**
	 * One dataset.
	 *
	 * @param field the root field
	 * @param dataset the portal's id of the dataset, such as
	 * <code>atendimentos-em-urgencia-triagem-manchester</code>
	 * @param type the GraphQL type of a record
	 * @param filters the arguments that filter the records
	 * @param orderBy the order of the records, such as <code>tempo desc</code>, or null
	 * for the portal's order
	 * @param columns the fields of the type, each with the column it reads and how its
	 * value is converted
	 */
	public record Dataset(String field, String dataset, String type, List<Filter> filters, String orderBy,
			Map<String, FieldSource> columns) {

		public Dataset {
			filters = (filters != null) ? filters : List.of();
			columns = (columns != null) ? columns : Map.of();
		}

	}

	/**
	 * An argument that filters the records of a dataset.
	 *
	 * @param name the argument, such as <code>region</code>
	 * @param column the column, such as <code>regiao</code>
	 * @param kind what the column holds, which decides how the value is written
	 * @param op the comparison, such as <code>=</code> or <code>&gt;=</code>
	 */
	public record Filter(String name, String column, Kind kind, String op) {
	}

	/**
	 * What a filtered column holds.
	 */
	public enum Kind {

		/**
		 * Text, compared in double quotes.
		 */
		@JsonProperty("text")
		TEXT,

		/**
		 * A month, written <code>2026-08</code>, compared as a date.
		 */
		@JsonProperty("month")
		MONTH,

		/**
		 * A day, written <code>2026-08-15</code>, compared as a date.
		 */
		@JsonProperty("date")
		DATE,

		/**
		 * A number.
		 */
		@JsonProperty("number")
		NUMBER,

		/**
		 * Text that contains the words, in any case.
		 */
		@JsonProperty("search")
		SEARCH

	}

	/**
	 * Reads the datasets from the JSON file that the generator wrote.
	 */
	public static OpenDataSoftTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, OpenDataSoftTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the datasets in " + resource, ex);
		}
	}

	/**
	 * Registers an {@link OpenDataSoftFetcher} for each dataset, and a fetcher for each
	 * field of its type that reads its column.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the address of the portal, such as
	 * <code>https://transparencia.sns.gov.pt</code>
	 * @param client the client that calls the portal
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper) {
		wiring.type("Query", (type) -> {
			datasets.forEach((dataset) -> type.dataFetcher(dataset.field(),
					new OpenDataSoftFetcher(upstream, baseUrl, client, dataset, mapper)));
			return type;
		});
		datasets.forEach((dataset) -> wiring.type(dataset.type(), (type) -> {
			dataset.columns()
				.forEach((field, source) -> type.dataFetcher(field,
						(environment) -> source.read(environment.getSource())));
			return type;
		}));
	}

}
