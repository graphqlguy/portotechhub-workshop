# Chapter 4: Curated operations

> **User story** · As the clinic manager, I want the AI assistants the staff already use to work with our API through the operations our frontend already sends, so that they can look up owners and book visits by asking in their own words.

The Model Context Protocol (MCP) is an open standard for connecting AI applications to the systems they work with. A server publishes **tools**, and the AI application hands them to its model, which picks a tool and its arguments. In this chapter, [GATool](https://github.com/api-agent-bridge/gatool) publishes the frontend's operation files as MCP tools. Each file is an operation written for one question and described for its reader, so each tool selects what its question needs.

By the end of this chapter, you will:

- Know how an MCP server, its tools and an AI application fit together
- Publish operation files as MCP tools with GATool
- List and call the tools with the MCP Inspector
- Know what an operation file decides about its tool
- Turn exceptions into errors a model can act on

## 1. MCP in brief

| Term | Meaning |
|---|---|
| MCP server | Publishes tools at an endpoint. Ours will be `http://localhost:8080/mcp`. |
| Tool | A name, a description and a JSON Schema for its arguments. The model reads all three to choose a tool. |
| MCP client | The part of an AI application that connects to a server, lists its tools, and calls the one the model picks. The MCP Inspector is a client for trying a server by hand. |
| `tools/list`, `tools/call` | The two requests this chapter uses. They are JSON-RPC messages, sent with HTTP POST to the endpoint (the **Streamable HTTP** transport). |

GATool turns each operation file into one tool. The tool's arguments are the operation's variables, its description comes from the file's comments and from the schema descriptions of chapters 1 to 3, and a call runs the operation inside our application.

## 2. The frontend's operations

The frontend sends only the operations in two folders, one `.graphql` file each:

| Folder | Holds |
|---|---|
| `src/main/resources/operations` | The queries and mutations of the pages: 5 in `queries/` and 5 in `mutations/` |
| `src/main/resources/operations-ui` | What the frontend alone needs: the schema check, the two summaries of chapter 1, and the owner's pets and the vet's visits of chapter 2 |

Open one of them:

`src/main/resources/operations/queries/findOwners.graphql`

```graphql
# Finds owners by the start of their last name, by their city, or both, and returns one page of them with their id and address.
# The last name and the city match in upper or lower case, and leaving both out lists every owner.
# Pages count from 0. To answer a question about all owners, read every page.
# The getOwnerDetails tool gives the telephone, the pets and the visits of one owner.
query FindOwners($lastName: String, $city: String, $page: Int = 0, $size: Int = 10, $sortBy: OwnerSortField = LAST_NAME, $order: SortOrder = ASC) {
  owners(filter: {lastName: $lastName, city: $city}, page: $page, size: $size, sort: {field: $sortBy, order: $order}) {
    totalElements
    totalPages
    currentPage
    hasNext
    hasPrevious
    content {
      id
      firstName
      lastName
      address
      city
    }
  }
}
```

The `#` lines say what the operation is for and which operation to call next, written the way the schema's descriptions are. The selection leaves `telephone` out, since the list of owners does not show it. Somebody decided what the answer to this question holds, and the tool inherits that decision: an agent gets the same small answer the frontend gets.

## 3. Add GATool

Add the starter next to the GraphQL starter:

`pom.xml`

```xml
<dependency>
  <groupId>io.gatool</groupId>
  <artifactId>gatool-mcp-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

GATool needs Java 21 or newer, which is why the workshop asks for it.

> [!NOTE]
> With Gradle, add `implementation 'io.gatool:gatool-mcp-spring-boot-starter:0.1.0'` to `build.gradle`.

Add an MCP section below the `# GraphQL` section:

`src/main/resources/application.properties`

```properties
# MCP
# GATool serves the frontend's operations as MCP tools at /mcp, one tool for each file
gatool.mcp.operations.locations=classpath*:operations/
# An MCP client connects without signing in, which suits a laptop. A deployed server secures /mcp.
gatool.mcp.security.unsafe.allow-mcp-calls-without-authentication=true
```

| Property | Meaning |
|---|---|
| `operations.locations` | A folder. GATool reads every `.graphql` file in it and in its subfolders, and publishes each one as a tool. `operations-ui` stays out, since its files serve the frontend alone. |
| `allow-mcp-calls-without-authentication` | GATool secures the endpoint by default. Without this line, startup stops with *GATool secures the MCP endpoint with Spring Security, which is missing from this application, so every caller that reaches the port would call every tool on it.* `/graphql` has been open the same way since chapter 1, and a deployed server puts Spring Security in front of both. |

## 4. Restart and read the log

Restart. GATool checks every file against the schema, then lists the tools:

```text
GATool runs each document inside this application.
GATool validates every operation file against the schema this application serves.
GATool serves 10 MCP tools:
  addOwner from file:.../target/classes/operations/mutations/addOwner.graphql (about 189 tokens)
  addPet from file:.../target/classes/operations/mutations/addPet.graphql (about 278 tokens)
  addVisit from file:.../target/classes/operations/mutations/addVisit.graphql (about 323 tokens)
  updateOwner from file:.../target/classes/operations/mutations/updateOwner.graphql (about 185 tokens)
  updatePet from file:.../target/classes/operations/mutations/updatePet.graphql (about 195 tokens)
  findOwners from file:.../target/classes/operations/queries/findOwners.graphql (about 344 tokens)
  getOwnerDetails from file:.../target/classes/operations/queries/getOwnerDetails.graphql (about 127 tokens)
  getVetDetails from file:.../target/classes/operations/queries/getVetDetails.graphql (about 126 tokens)
  listPetTypes from file:.../target/classes/operations/queries/listPetTypes.graphql (about 92 tokens)
  listVets from file:.../target/classes/operations/queries/listVets.graphql (about 107 tokens)
  about 1966 tokens in all, which every agent that lists these tools reads before it calls one
```

| Line | Meaning |
|---|---|
| `runs each document inside this application` | A tool call goes straight to the GraphQL engine, in the same application, without an HTTP request to `/graphql`. |
| `about 189 tokens` | The size of the tool's name, description and JSON Schema. An AI application sends every tool to its model with every question, so these tokens are paid on every request. |

The log also warns that the MCP endpoint answers anyone who can reach your laptop, as the property above allows.

A file that asks for a field the schema lacks stops the application at startup, and the message names the file and the line:

```text
GATool read the operation files and found 1 problem:
    file:.../target/classes/operations/queries/listPetTypes.graphql fails validation against the schema:
  - Validation error (FieldUndefined@[petTypes/nickname]) : Field 'nickname' in type 'PetType' is undefined (line 6, column 5)
```

## 5. Call the tools from the MCP Inspector

In a new terminal, start the MCP Inspector with our server's address:

```bash
npx -y @modelcontextprotocol/inspector@2.9.0 --web --transport http --server-url http://localhost:8080/mcp
```

The Inspector opens in your browser, with one server card, `localhost:8080`.

1. Flip the switch on the card. It says **Connected**, and the **Messages** panel on the right lists the requests the Inspector sent, `INITIALIZE` and `TOOLS/LIST` among them.
2. Open the **Tools** tab at the top. It lists the 10 tools.
3. Select `findOwners`. The description is the four `#` lines of the file. The arguments are the variables, with their defaults, and `sortBy` and `order` are lists to choose from, filled with the values of their enums. `lastName` reads *Keeps the owners whose last name starts with this text*: the variable fills `OwnerFilter.lastName`, and GATool takes that field's description from the schema.
4. Type `Madison` in **city** and click **Execute Tool**:

```json
{
  "data": {
    "owners": {
      "totalElements": 4,
      "totalPages": 1,
      "currentPage": 0,
      "hasNext": false,
      "hasPrevious": false,
      "content": [
        { "id": "8", "firstName": "Maria", "lastName": "Escobito", "address": "345 Maple St.", "city": "Madison" },
        { "id": "1", "firstName": "George", "lastName": "Franklin", "address": "110 W. Liberty St.", "city": "Madison" },
        { "id": "5", "firstName": "Peter", "lastName": "McTavish", "address": "2387 S. Fair Way", "city": "Madison" },
        { "id": "9", "firstName": "David", "lastName": "Schroeder", "address": "2749 Blackhawk Trail", "city": "Madison" }
      ]
    }
  }
}
```

Each part of a tool comes from the file:

| Part of the tool | Comes from |
|---|---|
| Name | The operation's name in camel case: `FindOwners` becomes `findOwners` |
| Description | The `#` lines right above the operation |
| Arguments | The variables, with their types and defaults. Each one's description is the schema's description of the argument or input field the variable fills. |
| Answer | The selection |
| **READ-ONLY**, **IDEMPOTENT**, **DESTRUCTIVE** | The operation type: GATool marks every query **READ-ONLY** and **IDEMPOTENT**, and every mutation **DESTRUCTIVE** |

Run `listVets`: every vet with their specialties, in 574 characters, because the file selects only the names and the specialties.

The dates have a type as well. Click the arrow ↕ on the `TOOLS/LIST` entry in the **Messages** panel, and find `addPet` in the response. Its `birthDate` is a string in the `date` format, which GATool took from the `@specifiedBy` URL of chapter 3, so a model and a client can check a date before they call:

```json
"birthDate": {
  "type": "string",
  "format": "date",
  "description": "The day the pet was born, written as 2024-05-01, today or earlier. An RFC-3339 compliant Full Date Scalar"
}
```

## 6. Errors a model can act on

Run `getVetDetails` with `99` as the **id**. The Inspector shows a **Tool Error**:

```json
{
  "data": { "vet": null },
  "errors": [
    {
      "message": "INTERNAL_ERROR for c2494fb8-5c77-fb02-9dc0-d23080b8431b",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["vet"],
      "extensions": { "classification": "INTERNAL_ERROR" }
    }
  ]
}
```

This is the answer of chapter 1's second exercise: GraphQL sends an exception it does not know as `INTERNAL_ERROR`, and the reason stays in the log. A person can read the log. A model reads only the answer, and `INTERNAL_ERROR` leaves it to guess whether to call again, try another id, or stop.

The services throw two exceptions of their own:

| Exception | When | Holds |
|---|---|---|
| `EntityNotFoundException` | An unknown id | The entity and the id |
| `InvalidInputException` | Input that breaks a rule of the clinic | One message per field |

**Create a new class** named `GraphQLExceptionHandlers` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLExceptionHandlers.java`

```java
package org.springframework.samples.petclinic.system;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
class GraphQLExceptionHandlers {

	@GraphQlExceptionHandler
	GraphQLError notFound(EntityNotFoundException ex, DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
			.errorType(ErrorType.NOT_FOUND)
			.message(ex.getMessage())
			.build();
	}

	@GraphQlExceptionHandler
	List<GraphQLError> invalidInput(InvalidInputException ex, DataFetchingEnvironment environment) {
		return ex.getFieldErrors()
			.entrySet()
			.stream()
			.map((error) -> GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.BAD_REQUEST)
				.message(error.getValue())
				.extensions(Map.of("field", error.getKey()))
				.build())
			.toList();
	}

}
```

| Element | Meaning |
|---|---|
| `@ControllerAdvice` | A bean whose handlers apply to every controller, as in Spring MVC. |
| `@GraphQlExceptionHandler` | Handles the exception type of the method's first parameter, from any `@QueryMapping`, `@MutationMapping` or `@SchemaMapping` method. |
| `GraphqlErrorBuilder.newError(environment)` | Starts an error with the path and the location of the field that failed. |
| `ErrorType.NOT_FOUND` | The classification. Spring for GraphQL defines `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND` and `INTERNAL_ERROR`. |
| `extensions` | A map for anything else a client needs. Ours names the input field. |
| `List<GraphQLError>` | One failure can produce several errors: here, one per broken field. |

The schema can now say what an error means, in the descriptions an agent reads. Change the description of `vet`, and the description of `Mutation`:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...

    "One vet. The vets query gives the ids of vets. An id that no vet has returns a NOT_FOUND error."
    vet(id: ID!): Vet
}
```

```graphql
"""
Changes the clinic's data.
A value that breaks a rule returns one BAD_REQUEST error for each field, and the data stays as it was.
An id that does not exist returns a NOT_FOUND error.
"""
type Mutation {
```

Restart, reload the Inspector and connect, and run `getVetDetails` with `99` again:

```json
{
  "data": { "vet": null },
  "errors": [
    {
      "message": "Vet with id 99 does not exist",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["vet"],
      "extensions": { "classification": "NOT_FOUND" }
    }
  ]
}
```

Run `addOwner` with an empty **lastName** and `123` as the **telephone**. Each broken field gets its own `BAD_REQUEST` error, which names the field, and the frontend's forms show the same messages next to their fields:

```json
{
  "data": null,
  "errors": [
    {
      "message": "must not be blank",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["addOwner"],
      "extensions": { "field": "lastName", "classification": "BAD_REQUEST" }
    },
    {
      "message": "Telephone must be a 10-digit number",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["addOwner"],
      "extensions": { "field": "telephone", "classification": "BAD_REQUEST" }
    }
  ]
}
```

## Exercise: one call less

Porto Tech asks for an x-ray for their cat Rosa, with a vet who does radiology. With the tools, the booking takes four calls: `findOwners` for the owner's id, `getOwnerDetails` for the pet's id, `listVets` for the vet's id, and `addVisit`. Change `findOwners.graphql` so that the booking takes three, and say so in its description. Then book the x-ray in the Inspector.

<details>
<summary>Show solution</summary>

`findOwners` returns owners, so it can return each owner's pets too. Add `pets` to the selection. In the description, the first line now names the pets, and the last line leaves the telephone and the visits to `getOwnerDetails`:

`src/main/resources/operations/queries/findOwners.graphql`

```graphql
# Finds owners by the start of their last name, by their city, or both, and returns one page of them with their id, address and pets.
# The last name and the city match in upper or lower case, and leaving both out lists every owner.
# Pages count from 0. To answer a question about all owners, read every page.
# The getOwnerDetails tool gives the telephone and the visits of one owner.
query FindOwners($lastName: String, $city: String, $page: Int = 0, $size: Int = 10, $sortBy: OwnerSortField = LAST_NAME, $order: SortOrder = ASC) {
  owners(filter: {lastName: $lastName, city: $city}, page: $page, size: $size, sort: {field: $sortBy, order: $order}) {
    totalElements
    totalPages
    currentPage
    hasNext
    hasPrevious
    content {
      id
      firstName
      lastName
      address
      city
      pets {
        id
        name
        type {
          name
        }
      }
    }
  }
}
```

Restart, so that the tool follows the new file. The frontend reads the new file when you reload it, and its **Find owners** page shows the same owners as before, since it ignores the pets.

1. `findOwners` with `Tech` as the **lastName** returns Porto Tech with Ronaldo (id `14`) and Rosa (id `15`).
2. `listVets` shows that Helen Leary (id `2`) and Henry Stevens (id `5`) do radiology.
3. `addVisit` books it for tomorrow, since the date is left out:

```json
{
  "petId": 15,
  "vetId": 2,
  "description": "x-ray"
}
```

The arguments are the file's variables, so they arrive one by one, and the file's operation builds the mutation's `input` object from them.

</details>

## Recap

- MCP lets an AI application call the tools a server publishes, and GATool publishes GraphQL operations as tools.
- `gatool.mcp.operations.locations` publishes every operation file in a folder as a tool, and GATool checks each file against the schema at startup.
- The file decides the tool: its name, its description from the `#` lines, its arguments from the variables, and its answer from the selection.
- `@GraphQlExceptionHandler` turns our exceptions into `NOT_FOUND` and `BAD_REQUEST` errors, which a model can act on, and the descriptions say what each one means.
- The agent's tools and the frontend use the same files, so a change to a file changes both, after a restart.

Chapter 5 gives these tools to an assistant inside the application, in a chat on the frontend.
