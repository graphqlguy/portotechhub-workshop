package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import graphql.schema.idl.RuntimeWiring;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.Resource;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.web.client.RestClient;

/**
 * The entity types of a FIWARE context broker that the schema exposes, as a generator
 * wrote them from samples of each type into a JSON file next to the schema. Each type
 * becomes a root field whose arguments ask for the entities near a point, or those that
 * reported in the last minutes.
 *
 * @param entities the entity types, one for each root field
 */
public record NgsiEntityTable(List<Entity> entities) {

	/**
	 * One entity type.
	 *
	 * @param field the root field
	 * @param entityType the broker's type, such as <code>AirQualityObserved</code>
	 * @param type the GraphQL type of an entity
	 * @param timeAttribute the attribute that holds when the entity reported, such as
	 * <code>dateObserved</code>, or null for a type without one
	 * @param attributes the fields of the type, each with the attribute it reads and how
	 * its value is converted
	 */
	public record Entity(String field, String entityType, String type, String timeAttribute,
			Map<String, FieldSource> attributes) {

		public Entity {
			attributes = (attributes != null) ? attributes : Map.of();
		}

	}

	/**
	 * Reads the entity types from the JSON file that the generator wrote.
	 */
	public static NgsiEntityTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, NgsiEntityTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the entity types in " + resource, ex);
		}
	}

	/**
	 * Registers an {@link NgsiEntityFetcher} for each entity type, and a fetcher for each
	 * field of its type that reads its attribute.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the address of the broker
	 * @param client the client that calls the broker
	 * @param mapper the mapper that reads the JSON of the answers
	 * @param clock the clock that says what the last minutes are
	 * @param places finds the point of a place given by its name
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper, UpstreamClock clock, PlaceResolver places) {
		wiring.type("Query", (type) -> {
			entities.forEach((entity) -> type.dataFetcher(entity.field(),
					new NgsiEntityFetcher(upstream, baseUrl, client, entity, mapper, clock, places)));
			return type;
		});
		entities.forEach((entity) -> wiring.type(entity.type(), (type) -> {
			entity.attributes()
				.forEach((field, source) -> type.dataFetcher(field,
						(environment) -> source.read(environment.getSource())));
			return type;
		}));
	}

}
