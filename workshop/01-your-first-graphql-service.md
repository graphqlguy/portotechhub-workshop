# Chapter 1: Your first GraphQL service

> **User story** · As the clinic's receptionist, I want to look up owners by last name or city, a page at a time, so that I can find a client while they wait at the desk.

GraphQL lets a client ask for exactly the fields it needs.

## 1. Add the dependency

Spring for GraphQL runs GraphQL Java, the engine that checks and executes queries, and one starter brings in both. Add it to the `<dependencies>` section, next to `spring-boot-starter-webmvc`:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

> [!NOTE]
> If you build with Gradle, add `implementation 'org.springframework.boot:spring-boot-starter-graphql'` to `build.gradle` instead.

## 2. Turn on GraphiQL

GraphiQL is an in-browser IDE for GraphQL: a query editor with autocomplete and the API's documentation. It is off by default. Add these two lines below the `# Web` section:

`src/main/resources/application.properties`

```properties
# GraphQL
spring.graphql.graphiql.enabled=true
```

## 3. Write your first schema

> **In REST** · We would add an endpoint, `GET /owners`, that returns every owner as JSON. In GraphQL, the same request is a field of the `Query` type, `ownersAll`, and we declare it in the schema below.

This is schema-first development: we describe the API in the GraphQL Schema Definition Language (SDL), and only then implement it. Spring reads the `.graphqls` files under `src/main/resources/graphql/`, so **create a folder named `graphql`** under `src/main/resources`, and in it **a file named `schema.graphqls`** with one query and one type:

`src/main/resources/graphql/schema.graphqls`

```graphql
# The entry point of our API. This line is a comment:
# comments start with # and are only for people reading this file.
"""
Reads the clinic's data.
An id identifies an object within its type, so an owner and a vet can have the same id.
"""
type Query {
    "Every owner of the clinic, in one list, without filters."
    ownersAll: [Owner!]!
}

"A client of the clinic, who owns one or more pets."
# We extend the Owner type as the workshop goes on
type Owner {
    id: ID!
    firstName: String!
    lastName: String!
    address: String!
    city: String!
    "The owner's telephone number as ten digits, for example 2221234567."
    telephone: String!
}
```

| Syntax | Meaning |
|---|---|
| `type Query` | The entry point. Every field of `Query` is a query a client can send. Every schema **must** have a `Query` type with at least one field; mutations and subscriptions are optional. |
| `[Owner!]!` | A list of owners. The outer `!` makes the list itself non-null, and the inner `!` makes each entry non-null. |
| `ID!` | An identifier, sent as a string. The `!` means a value is always there. |
| `String!` | Text that is always there. A field without `!` may be `null`. |

### Descriptions and comments

SDL has two kinds of text, for different readers:

| Syntax | Who reads it | Use it for |
|---|---|---|
| `"..."`, or `"""..."""` for several lines | **Clients.** A description is part of the schema: introspection returns it, and GraphiQL shows it. | What a client of the API needs to know |
| `# ...` | **People reading the file**, as long as the definition below it has a description | Notes for whoever edits the schema |

> [!IMPORTANT]
> When a type or a field lacks a description, GraphQL Java takes the comment lines directly above it as its description, and sends them to every client. That is why `Query` has a description here: the comment above it then stays in the file.

## 4. Start the server and explore

Start the application:

```bash
./mvnw spring-boot:run
```

Among the startup lines, Spring for GraphQL reports how the schema matches the Java code, and where the endpoint is:

```text
GraphQL schema inspection:
	Unmapped fields: {Query=[ownersAll]}
	Unmapped registrations: {}
	Unmapped arguments: {}
...
GraphQL endpoint HTTP POST /graphql
```

`Unmapped fields` lists the schema fields that Java does not answer yet. `ownersAll` is there, because we write its Java in step 5.

> [!NOTE]
> If startup fails with `Port 8080 was already in use`, another program holds the port. See [changing the port](../README.md#changing-the-port), and use your port in place of `8080` in the addresses below.

Open GraphiQL at [http://localhost:8080/graphiql](http://localhost:8080/graphiql) and click the book icon at the top left to open the documentation explorer. It shows `ownersAll` and `Owner` with their descriptions, and leaves out the `#` comments. GraphiQL read all of it from the API: a client can ask a GraphQL API for its schema, which is called **introspection**.

> [!NOTE]
> The browser downloads GraphiQL's code from the `esm.sh` CDN. If the page stays on **Loading...**, check in this order:
>
> 1. **Can the browser reach the CDN?** Open [https://esm.sh/graphiql@5.2.1/dist/style.css](https://esm.sh/graphiql@5.2.1/dist/style.css) in the same browser. If it fails, a VPN, a company proxy, a DNS filter or the venue Wi-Fi is blocking it.
> 2. **Browser extensions.** Ad and script blockers can block `esm.sh`. Try a private window, where most extensions are off.
> 3. **An old browser.** GraphiQL loads its code with import maps, which need Chrome 89, Firefox 108 or Safari 16.4 or newer.
>
> If the page still stays on **Loading...**, send the queries of this workshop from the Postman desktop app to `http://localhost:8080/graphql`, as [Create GraphQL requests in Postman](https://learning.postman.com/docs/use/send-requests/protocols/graphql/graphql-client-first-request/) shows.

Now run this query in the editor, with the ▶ button:

```graphql
query {
  ownersAll {
    id
    lastName
  }
}
```

The answer is an error:

```json
{
  "errors": [
    {
      "message": "The field at path '/ownersAll' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value. ...",
      "path": ["ownersAll"],
      "extensions": { "classification": "NullValueInNonNullableField" }
    }
  ],
  "data": null
}
```

The error is expected: the schema promises a non-null list, and GraphQL checks that promise on every response.

## 5. Implement the controller

`OwnerService` already reads the owners from the database. We add a controller whose methods Spring for GraphQL maps to schema fields.

**Create a new Java class** named `OwnerGraphQLController` in the `org.springframework.samples.petclinic.owner` package, next to `OwnerService`. The name differs from `OwnerController`, which serves the Petclinic web pages.

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class OwnerGraphQLController {

	private final OwnerService ownerService;

	OwnerGraphQLController(OwnerService ownerService) {
		this.ownerService = ownerService;
	}

	@QueryMapping
	List<Owner> ownersAll() {
		return ownerService.findAll();
	}

}
```

| Element | Meaning |
|---|---|
| `@Controller` | A Spring bean. Spring for GraphQL looks for handler methods in it. |
| `@QueryMapping` | Binds the method to the `Query` field with the same name, `ownersAll`. |
| The constructor | Spring passes in the `OwnerService` bean. |

Restart the application: stop it with `Ctrl+C`, and run `./mvnw spring-boot:run` again. The startup report now says `Unmapped fields: {}`. Run the query again, and the answer holds all 11 owners.

Remove `id` from the query, or add `firstName` and `city`, and run it again. The answer always has exactly the fields you asked for: in GraphQL, **the client decides the shape of the response**.

> **In REST** · `GET /owners` returns the fields its server chose, the same ones to every client.

## 6. Fetch a single owner

> **In REST** · One owner is `GET /owners/{id}`, and an unknown id answers `404 Not Found`. In GraphQL, the id is an argument of the `owner` field.

> [!NOTE]
> From here on, the code shows only **what changes**: `# ...existing queries...` stands for what is already in the file. Add the new lines to the existing `Query` type, and keep the rest of the block.

Schema first:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...

    "One owner, or null when no owner has the id. The owners query gives the ids of owners."
    owner(id: ID!): Owner
}
```

The type is `Owner`, without `!`. When no owner has the id, the field is `null`, and the rest of the response is unaffected.

Then Java, in the same controller:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import org.springframework.graphql.data.method.annotation.Argument;

@QueryMapping
Owner owner(@Argument Integer id) {
	return ownerService.findById(id);
}
```

`@Argument` binds the GraphQL argument to the method parameter of the same name, and converts the `ID` to an `Integer` on the way. Restart and try it:

```graphql
query {
  owner(id: 11) {
    firstName
    lastName
    address
    city
  }
}
```

```json
{
  "data": {
    "owner": {
      "firstName": "Porto",
      "lastName": "Tech",
      "address": "Alfândega do Porto, Rua Nova da Alfândega",
      "city": "Porto"
    }
  }
}
```

Try `owner(id: 999)` as well. The answer is `{"data": {"owner": null}}`, as the schema allows.

## 7. Evolve the API: filtering, sorting and paging

`ownersAll` returns every owner in one response, so the response and the memory it takes grow with every new client. We add a query with paging and filtering, and since clients may already use `ownersAll`, we keep it and mark it as deprecated.

> **In REST** · Paging, filtering and sorting go in query parameters, such as `GET /owners?city=Madison&sort=firstName,desc&page=0&size=2`, and a changed endpoint often gets a new version, such as `/v2/owners`. In GraphQL, they are arguments of a new field, and `@deprecated` marks the old one.

Change the `ownersAll` line:

`src/main/resources/graphql/schema.graphqls`

```graphql
    ownersAll: [Owner!]! @deprecated(reason: "Unbounded. Use 'owners' instead.")
```

Add the new query to the `Query` type. A description goes right before the argument it describes, so the arguments stand one per line:

```graphql
type Query {
    # ...existing queries...

    """
    Owners, one page at a time, sorted by last name unless the sort argument says otherwise.
    A page past the last one holds an empty list.
    """
    owners(
        "Narrows the list by the start of the last name and by city. Leave it out to list every owner."
        filter: OwnerFilter
        "The page to return. Pages count from 0."
        page: Int! = 0
        "The number of owners on a page."
        size: Int! = 10
        sort: OwnerSort
    ): OwnerPage!
}
```

Add the types it uses at the end of the file. These are new types, outside `Query`:

```graphql
enum OwnerSortField {
    LAST_NAME
    FIRST_NAME
    CITY
}

enum SortOrder {
    ASC
    DESC
}

input OwnerFilter {
    "Keeps the owners whose last name starts with this text. It matches in upper or lower case."
    lastName: String
    "Keeps the owners who live in this city. It matches in upper or lower case."
    city: String
}

input OwnerSort {
    field: OwnerSortField
    order: SortOrder
}

"One page of owners. Pages count from 0."
type OwnerPage {
    content: [Owner!]!
    totalElements: Int!
    totalPages: Int!
    currentPage: Int!
    size: Int!
    isFirst: Boolean!
    isLast: Boolean!
    hasNext: Boolean!
    hasPrevious: Boolean!
}
```

| Syntax | Meaning |
|---|---|
| `enum` | A closed set of values. The engine refuses any other value before your code runs. |
| `input` | A type for arguments. An `input` serves only as an argument, and a `type` only as a result: GraphQL keeps the two directions apart. |
| `page: Int! = 0` | A default value. A client may leave `page` out and gets `0`, but it cannot send `null`. |
| `@deprecated` | The field keeps working, and every tool marks it as deprecated, with the reason. |

The Java side is one method. The `OwnerFilter`, `OwnerSort` and `OwnerPage` records are already in the starting point, and Spring for GraphQL builds the records from the input objects by matching their field names. Add the method to the controller:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
@QueryMapping
OwnerPage owners(@Argument OwnerFilter filter, @Argument int page, @Argument int size, @Argument OwnerSort sort) {
	return ownerService.findOwners(filter, page, size, sort);
}
```

Restart, open the documentation explorer again, and look at `ownersAll`: GraphiQL now marks it as deprecated and shows our reason. Try the new query:

```graphql
query {
  owners(
    filter: { city: "Madison" }
    sort: { field: FIRST_NAME, order: DESC }
    page: 0
    size: 2
  ) {
    totalElements
    totalPages
    content {
      firstName
      lastName
    }
  }
}
```

Four owners live in Madison, so there are 2 pages of 2, starting with Peter McTavish. Then try `owners(page: null)`. The engine refuses it during validation, before any Java runs:

```text
Validation error (WrongType@[owners]) : argument 'page' with value 'NullValue{}' must not be null
```

## 8. Start the frontend

The repository has a React frontend that reads our schema when it starts and turns on the pages the API can serve. In a second terminal, from the project folder:

```bash
cd frontend
npm install    # the first time only
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The home page lists what the API can do so far, and **Find owners** is live: filtering by last name and city, sorting and paging, all through the `owners` query you just wrote. Click an owner to open their page.

> [!TIP]
> Each time you extend the schema from now on: restart the backend, then refresh the browser. The frontend reads the schema again and turns on whatever is new.

## Exercises

The **Veterinarians** page needs two queries, and the later chapters use both. `VetService` and the `Vet` and `Specialty` entities are ready. Write the first query yourself, schema and then controller, and paste the solution of the second.

### Exercise 1: list the vets

Add a `vets` query that returns every vet. A vet has an `id`, a `firstName`, a `lastName` and a list of `specialties`, and a specialty has an `id` and a `name`. Use `VetService.findAll()`, in a new `VetGraphQLController` in the `org.springframework.samples.petclinic.vet` package (`VetController` already serves the web pages). When it works, the frontend shows a **Veterinarians** link.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...

    "Every vet of the clinic, with their specialties."
    vets: [Vet!]!
}

"A veterinarian who works at the clinic."
type Vet {
    id: ID!
    firstName: String!
    lastName: String!
    specialties: [Specialty!]!
}

"An area of veterinary medicine that a vet specializes in, such as surgery."
type Specialty {
    id: ID!
    name: String!
}
```

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQLController {

	private final VetService vetService;

	VetGraphQLController(VetService vetService) {
		this.vetService = vetService;
	}

	@QueryMapping
	List<Vet> vets() {
		return vetService.findAll();
	}

}
```

`specialties` works without Java code: GraphQL calls `Vet.getSpecialties()`, the getter with the field's name. Chapter 2 explains how that works.

</details>

### Exercise 2: fetch a single vet

Paste the solution: it adds a `vet(id: ID!)` query, and clicking a vet in the frontend then opens their page. `VetService.findById` throws an `EntityNotFoundException` for an unknown id, so query `vet(id: 99)` and look at what GraphQL makes of the exception, in the response and in the application's log. Chapter 4 turns it into a clear error.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...

    "One vet. The vets query gives the ids of vets."
    vet(id: ID!): Vet
}
```

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
import org.springframework.graphql.data.method.annotation.Argument;

@QueryMapping
Vet vet(@Argument Integer id) {
	return vetService.findById(id);
}
```

For `vet(id: 99)` the response hides the exception and gives an id to look up in the log:

```json
{
  "errors": [
    {
      "message": "INTERNAL_ERROR for e93e1918-0779-72f3-d7f1-7c70b0d5fe22",
      "path": ["vet"],
      "extensions": { "classification": "INTERNAL_ERROR" }
    }
  ],
  "data": { "vet": null }
}
```

The log has the details: `Unresolved EntityNotFoundException for executionId e93e1918-...`, followed by `Vet with id 99 does not exist`. GraphQL sends an exception it does not know as `INTERNAL_ERROR`, so the details stay in the log.

</details>

## Recap

- One dependency and one property gave us a GraphQL endpoint and GraphiQL.
- The schema is the contract: we wrote SDL first, every time, and implemented it second.
- `@QueryMapping` binds a `Query` field to a controller method, and `@Argument` binds its arguments.
- The client chooses the fields, and the response has exactly those.
- An API grows in place: `@deprecated` and a better query, without a `/v2`.
