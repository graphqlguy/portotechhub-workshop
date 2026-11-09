# Chapter 2: Relationships and the N+1 problem

> **User story** · As a vet, I want to see each patient with its owner and its visit history, and the visits on my own list, so that I know a pet before it walks in.

The clinic's data is a graph: owners have pets, pets have visits, and each visit has a vet.

## 1. Add pets to Owner

> **In REST** · The pets of an owner would be a second request, `GET /owners/{id}/pets`, or part of every owner the server returns. In GraphQL, they are a field of `Owner`, and the client asks for them in the same query when it needs them.

The `Owner` entity already has its pets: a lazy `@OneToMany` list with a `getPets()` getter. Add a `pets` field to `Owner`, and two new types below it:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Owner {
    # ...existing fields...
    pets: [Pet!]!
}

"An animal that an owner has registered with the clinic."
type Pet {
    id: ID!
    name: String!
    birthDate: String
    type: PetType!
}

"A kind of pet the clinic treats, such as cat or dog."
type PetType {
    id: ID!
    name: String!
}
```

`birthDate` is a `String` for now. Chapter 3 gives dates a type of their own.

Restart the application and run a nested query in GraphiQL:

```graphql
query {
  owner(id: 11) {
    firstName
    lastName
    pets {
      name
      birthDate
      type {
        name
      }
    }
  }
}
```

```json
{
  "data": {
    "owner": {
      "firstName": "Porto",
      "lastName": "Tech",
      "pets": [
        { "name": "Ronaldo", "birthDate": "2026-01-01", "type": { "name": "dog" } },
        { "name": "Rosa", "birthDate": "2025-06-14", "type": { "name": "cat" } }
      ]
    }
  }
}
```

It works without any Java. Refresh the frontend and open an owner: the owner page now lists the pets.

### Why does this work?

GraphQL needs a resolver for every field in a query: a piece of code that produces its value. Without one of ours, the engine uses its **default resolver**, which looks at the parent object and calls the getter with the field's name. `owner` returns an `Owner` entity, the entity has `getPets()`, so `pets` resolves by itself, and so do `name`, `birthDate` and `type` on each `Pet`. Every field of chapter 1 resolved the same way.

The pets are a lazy relationship: Hibernate reads them from the database when `getPets()` is called. The starting point keeps the persistence context open for the whole HTTP request (`spring.jpa.open-in-view=true` in `application.properties`), so the getter can still load them while GraphQL builds the response.

A field needs code of your own only when the parent object cannot answer for it.

## 2. The other direction: the visits of a vet

> **In REST** · The visits of a vet would get their own endpoint, `GET /vets/{id}/visits`, with a controller method behind it. In GraphQL, they are a field of `Vet`, and a controller method resolves that field.

In Java, the relationship belongs to `Visit`, which has a `getVet()`, and `Vet` lacks a `getVisits()`. Add the field and the `Visit` type to the schema anyway:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Vet {
    # ...existing fields...
    "The visits this vet has seen or will see, newest first."
    visits: [Visit!]!
}

"A pet's visit to the clinic, in the past or booked for later."
type Visit {
    id: ID!
    date: String!
    description: String!
}
```

Restart. The startup report already shows the gap:

```text
GraphQL schema inspection:
	Unmapped fields: {Vet=[visits]}
```

Run the query anyway:

```graphql
query {
  vet(id: 3) {
    lastName
    visits {
      date
      description
    }
  }
}
```

The answer is the error from chapter 1, `The field at path '/vet/visits' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value`, with `"data": {"vet": null}`. The default resolver did not find a `getVisits()`, so it returned `null`. `visits` promises a list, so GraphQL sets its parent to `null` instead: the nearest field that may be `null` is `vet`.

A **schema mapping** fixes it: a controller method that resolves one field of one type. `VisitService.findByVet` is ready. Replace the whole `VetGraphQLController` with this version, which adds the `VisitService` and the new method:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.samples.petclinic.owner.visit.Visit;
import org.springframework.samples.petclinic.owner.visit.VisitService;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQLController {

	private final VetService vetService;

	private final VisitService visitService;

	VetGraphQLController(VetService vetService, VisitService visitService) {
		this.vetService = vetService;
		this.visitService = visitService;
	}

	@QueryMapping
	List<Vet> vets() {
		return vetService.findAll();
	}

	@QueryMapping
	Vet vet(@Argument Integer id) {
		return vetService.findById(id);
	}

	@SchemaMapping
	List<Visit> visits(Vet vet) {
		return visitService.findByVet(vet);
	}

}
```

| Element | Meaning |
|---|---|
| `@SchemaMapping` | Binds the method to a field. By convention, the method name is the field (`visits`), and the parameter's type is the type the field belongs to (`Vet`). |
| `Vet vet` | The parent object, as the resolver above it produced it. GraphQL hands it over, so that we can resolve the child field. |

Restart and run the query again: Linda Douglas has 2 visits. `@QueryMapping`, from chapter 1, is a shorthand for `@SchemaMapping` on the `Query` type.

Refresh the frontend and open a vet from the **Veterinarians** page: the vet page now lists the visits.

## 3. The hidden cost: N+1

Our resolver runs once per vet. Ask for all six vets and their visits, and it runs six times, each time with its own database query. One query for the list, plus N queries for the nested field: this is the **N+1 problem**, the classic performance trap of GraphQL APIs.

> **In REST** · A client has the same problem one level up: `GET /vets`, then `GET /vets/{id}/visits` for each vet, is 1 + N HTTP requests. GraphQL turns them into one request, and the 1 + N moves into the server, where we can fix it.

On a laptop with an in-memory database, seven queries finish in milliseconds, but on a production database, they would probably mean a large delay. The starting point has two switches in `application.properties` that make the cost visible:

`src/main/resources/application.properties`

```properties
spring.jpa.show-sql=true
```

```properties
petclinic.demo.latency=1s
```

The first prints every SQL statement. The second makes every service method pause for one second before it touches the database, as a round trip to a remote database would.

With both on, this query shows the cost:

```graphql
query {
  vets {
    lastName
    visits {
      date
    }
  }
}
```

- The response takes about **seven seconds**: one paused service call for the vets, then six more, one per vet, for `visits`.
- The console shows six statements that differ only in the vet's id:

```text
Hibernate: select v1_0.id,v1_0.visit_date,v1_0.description,v1_0.pet_id,v1_0.vet_id from visits v1_0 where v1_0.vet_id=? order by v1_0.visit_date desc,v1_0.id desc
```

The first run also shows the statements that read the vets and their specialties. After that, Petclinic serves the vets from a cache.

Each call of the `visits` resolver sees a single vet, so it cannot tell that its six calls could have been a single `where vet_id in (...)` query. With 100 vets and a real database at 5 milliseconds per round trip, that is half a second of waiting for one page, and every further level of nesting multiplies it.

## 4. The fix: batch loading with @BatchMapping

The fix is to collect **all** the vets of the current request first, then load their visits in one call. GraphQL calls this pattern a **DataLoader**, and Spring for GraphQL offers it as `@BatchMapping`. The schema stays the same.

The starting point has the repository code: `VisitRepository.findByVetIds` loads the visits of many vets with `where visit.vet.id in :vetIds`, and `VisitService.findVisitsByVetIds` groups them into a `Map<Integer, List<Visit>>` keyed by vet id.

Replace the `@SchemaMapping` method from step 2 with this one, and replace the `SchemaMapping` import with the three new ones:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.BatchMapping;

@BatchMapping
Map<Vet, List<Visit>> visits(List<Vet> vets) {
	List<Integer> vetIds = vets.stream().map(Vet::getId).toList();
	Map<Integer, List<Visit>> visitsByVetId = visitService.findVisitsByVetIds(vetIds);
	return vets.stream()
		.collect(Collectors.toMap((vet) -> vet, (vet) -> visitsByVetId.getOrDefault(vet.getId(), List.of())));
}
```

Compare the two signatures:

| Step 2: `@SchemaMapping` | Step 4: `@BatchMapping` |
|---|---|
| `List<Visit> visits(Vet vet)` | `Map<Vet, List<Visit>> visits(List<Vet> vets)` |
| Called once per vet | Called once per request |
| Returns one vet's visits | Returns every vet's visits, keyed by vet |

Spring registers a DataLoader for the field: GraphQL collects every `Vet` that needs `visits`, hands the whole batch to our method in one call, and gives each vet its list from the map. A vet missing from the map gets the `getOrDefault` value, an empty list.

Restart and run the `vets` query from step 3: the answer is the same as with `@SchemaMapping`. With the two switches on:

- The response time drops from about **seven seconds to two**: one paused call for the vets, and one for all their visits.
- The console shows one statement for the visits, with all six ids: `... from visits v1_0 where v1_0.vet_id in (?,?,?,?,?,?) order by ...`
- The application log has one line for the batch: `Batch fetching visits for 6 vets`.

### The same trap behind a getter

Lazy relationships that GraphQL reads through getters, such as `Owner.pets`, have the same problem: one query per owner. Petclinic's `application.properties` already sets `spring.jpa.properties.hibernate.default_batch_fetch_size=16`, so Hibernate loads the pets of up to 16 owners in one statement. With the SQL log on, this query shows it:

```graphql
query {
  owners(size: 10) {
    content {
      lastName
      pets {
        name
      }
    }
  }
}
```

The console shows three statements: the page of owners, its count, and one `... from pets p1_0 ... where p1_0.owner_id in (?,?,...)` for the pets of all ten. `@BatchMapping` batches the resolvers we write, and Hibernate batches the lazy relationships behind the getters.

> [!NOTE]
> Hibernate's batch fetching works here because `spring.jpa.open-in-view=true` keeps the persistence context open while GraphQL calls the getters. With it set to `false`, as many applications run, the same query fails with a `LazyInitializationException`. Batch fetching also takes one statement for every 16 owners, so a page of 100 owners needs 7 statements for their pets. A `@BatchMapping` for each lazy relationship fixes both: it works with open-in-view off, and loads the field for every parent of the request in one call. The workshop keeps the getters to save time.

If you turned the switches on, set them back to `false` and `0s`, so the next chapters run at full speed.

## 5. Follow the graph both ways

So far an owner lists their pets, and a vet lists their visits. The vet page also needs the pet of each visit and the pet's owner, and the owner page needs each pet's visits and the vet who saw it. A field in the other direction lets a client follow those links in one query, where a REST client would send one more request per visit.

The starting point gave `Pet` a `getOwner()` and a `getVisits()`, and `Visit` a `getPet()` and a `getVet()`, so all four fields resolve through getters. Add them:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Pet {
    # ...existing fields...
    owner: Owner!
    "The pet's visits to the clinic, oldest first."
    visits: [Visit!]!
}

type Visit {
    # ...existing fields...
    pet: Pet!
    "The vet who sees the pet, or null when the visit was booked without one, as the Petclinic web pages do."
    vet: Vet
}
```

`vet` may be `null`, because the Petclinic web pages book visits without a vet.

Restart, and follow the graph from a vet to the owners of their patients:

```graphql
query {
  vet(id: 3) {
    lastName
    visits {
      date
      description
      pet {
        name
        owner {
          firstName
          lastName
        }
      }
    }
  }
}
```

The first visit is for Rosa, Porto Tech's cat, who had a wound stitched after a fight. Refresh the frontend and open a vet again: each visit now shows its pet and the pet's owner. Open an owner as well: each pet now lists its visits and the vet who saw it.

A client can follow these fields as deep as it likes, `owner { pets { owner { pets ... } } }`, and the server answers every level. A public API limits how deep a query may go, for example with GraphQL Java's `MaxQueryDepthInstrumentation`.

## Recap

- A field whose parent has a matching getter resolves by itself, through the default resolver.
- `@SchemaMapping` resolves a field that the parent cannot answer, one parent at a time.
- One call per parent is the N+1 problem: invisible on a laptop, and slow in production.
- `@BatchMapping` makes one call per request, and the schema stays the same.
- Hibernate's batch fetching does the same for lazy relationships behind getters, as long as open-in-view keeps the persistence context open.
