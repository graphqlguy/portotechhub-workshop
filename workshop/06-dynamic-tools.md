# Chapter 6: Dynamic tools

> **User story** · As the clinic manager, I want to ask the assistant any question about the clinic's data, such as whether the clinic has a surgeon, so that I get the answer without waiting for a new operation file.

Each operation file answers a question we planned for, and every new question needs a new file and a new release. With three other tools from GATool, the model writes its own query: it searches the schema for the fields it needs, reads a type when it needs more details, and runs the query. In this chapter, MCP clients and the assistant get those three tools alone.

By the end of this chapter, you will:

- Know what an embedding is, and why the search uses one
- Serve the three tools to MCP clients and to the assistant
- Follow the model's three steps in the MCP Inspector
- Know which checks apply to a query that the model wrote

## 1. The three tools

| Tool | Takes | Returns |
|---|---|---|
| `searchSchema` | A question in plain words | The fields that match it best, each with its declaration and the root field that reaches it |
| `introspectType` | The name of a type | The type's fields, with their arguments and descriptions |
| `executeGraphql` | A query and its variables | The result, as an operation file's tool returns it |

The search ranks **schema coordinates**: a coordinate names one field of one type, such as `Vet.visits`. One search returns at most 2000 tokens, however large the schema is, so the model reads only the part of the schema it needs, and a schema too large to read whole stays usable.

Here the three tools replace the operation tools, so you can follow the model's steps on their own. `executeGraphql` runs queries only, so from this chapter on, MCP clients and the assistant can only read the clinic's data.

## 2. Search by meaning

A question often uses other words than the schema: a user asks for doctors, and the schema says vet. An **embedding model** turns a text into a vector, a list of numbers, and texts with a similar meaning get vectors that point the same way.

Ollama serves an HTTP API on port 11434 for the models it runs. It runs on its own, apart from our application: on macOS and Windows the Ollama app starts it, and on Linux the installer sets it up as a service. Spring AI calls its `/api/embed` endpoint for every vector GATool needs: the schema's fields at startup, and each question. You can send the same request for one word with `curl`, on macOS, Linux or Git Bash. PowerShell handles the quotes differently, so you might need to adjust the command there.

```bash
curl -s http://localhost:11434/api/embed -d '{"model": "qwen3-graphql-embedder", "input": "doctors"}'
```

```json
{"model":"qwen3-graphql-embedder","embeddings":[[0.015182828,-0.02370876,-0.0013484629,0.09673668,...
```

The vector holds 1,024 numbers. Two vectors are compared by the angle between them, the **cosine similarity**: 1 means the same direction, and 0 means the texts are unrelated. We compared `doctors` with the first sentence of three descriptions in our schema:

| Text | Describes | Similarity to `doctors` |
|---|---|---|
| Every vet of the clinic, with their specialties. | `Query.vets` | 0.58 |
| The kinds of pet the clinic treats, sorted by name. | `Query.petTypes` | 0.31 |
| Owners, one page at a time, sorted by last name unless the sort argument says otherwise. | `Query.owners` | 0.09 |

`searchSchema` works the same way. At startup, GATool asks the embedding model for the vector of every field a query can reach: the root fields, such as `Query.vets`, and the fields of the types behind them, such as `Vet.visits`. GATool embeds the nested fields too, because a question often asks about a detail, such as a vet's visits, and each result names the root field the query starts from. GATool writes each field as a declaration with its description, and caches the vectors on disk. For each question, it ranks the fields by their similarity to the question's vector and returns the best ones within 2000 tokens.

The model the setup pulled is Qwen3-Embedding-0.6B, fine-tuned on 4,788 pairs of a question and the field that answers it, because a schema is camelCase names and type signatures, while most embedding models learn from prose. Its model card gives the right field among the first 50 results for 63.6% of the 816 questions of the GraphQL AI Working Group's benchmark, against 59.8% for OpenAI's `text-embedding-3-large` and 58.5% for the model before the fine-tune.

## 3. Serve the three tools

In `application.properties`, point both operation locations at a folder that does not exist. The `optional:` prefix lets Spring accept a missing folder, so GATool reads zero operation files and leaves the files where they are. Replace the first two lines below `# MCP`:

`src/main/resources/application.properties` (excerpt)

```properties
# GATool serves the three dynamic tools below alone at /mcp: this folder does not exist, so GATool
# skips the operation files, which the frontend still sends from operations/
gatool.mcp.operations.locations=optional:classpath*:none/
```

Add a Dynamic tools section below the MCP section:

```properties
# Dynamic tools
# Three tools, with which the model writes its own GraphQL: searchSchema, introspectType
# and executeGraphql. MCP clients and the assistant both get them.
gatool.dev.experimental.generate-tools=dynamic-three-step
# searchSchema ranks the schema's fields by meaning, with the embedding model pulled in the setup
gatool.dev.experimental.dynamic-operations.search-backend=embedding
spring.ai.ollama.embedding.model=qwen3-graphql-embedder
```

`spring.ai.ollama.embedding.model` names the model that Spring AI asks Ollama for, the name that `ollama cp` gave it in the setup. Ollama loads the model into memory at the first request that names it, and unloads it after 5 minutes without requests: `ollama ps` lists the models it holds.

Replace the first two lines below `# Chat`:

```properties
# The assistant in the frontend gets the same three tools, inside the application
gatool.in-process.operations.locations=optional:classpath*:none/
```

Restart:

```text
WARN ... i.g.b.a.GAToolAutoConfiguration          : GATool serves zero MCP tools. These locations were read: optional:classpath*:none/. An operation file there becomes a tool, and gatool.mcp.operations.locations names the folders.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool is embedding 41 schema coordinates, which is one call to your embedding model for each of them. The result is cached, so later starts read it.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool cached them in /var/folders/.../T/gatool-<you>/vectors/<hash>.vectors.
WARN ... i.g.b.a.GAToolAutoConfiguration          : gatool.dev.experimental.generate-tools is dynamic-three-step, so a model can search your schema and run GraphQL it wrote against your API. ... Turn this off before you deploy.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 41 schema coordinates with EMBEDDING and returning up to 2000 tokens of results per call.
```

GATool warns that it serves zero MCP tools, which is what the empty folder is for, and that a model can now run queries it wrote itself: step 6 shows the checks that still apply. The 41 coordinates are the fields a query can reach: 5 on `Query`, and 36 on the types behind them, such as `Owner`, `Pet` and `Visit`. The mutations and the deprecated `ownersAll` stay out of the search. On our laptop, embedding them took under a second, and the next start reads them from the cache.

> [!NOTE]
> The embedding model runs in Ollama. With a hosted model from chapter 5, add `gatool.dev.experimental.dynamic-operations.search-backend=bm25` to its `application-<name>.properties`: BM25 ranks the fields by their words, and runs without a model. With the Mistral or OpenAI starter, the application holds two embedding models, and GATool stops at startup and asks which one to use. The tests run without Ollama, so `src/test/resources/config/application.properties` sets `bm25` for them.

## 4. Follow the three steps in the Inspector

Reload the Inspector's page and flip the switch on the card. If you stopped the Inspector, start it with the command of chapter 4. The **Tools** tab lists three tools.

Run `searchSchema` with:

```json
{"question": "Does the clinic have a surgeon?"}
```

```text
Query.vets:
"""Every vet of the clinic, with their specialties."""
vets: [Vet!]!  # on type Query

Visit.vet:
reach it from Query.vets -> Vet.visits
"""The vet who sees the pet, or null when the visit was booked without one, as the Petclinic web pages do."""
vet: Vet  # on type Visit
...
```

The answer lists all 41 coordinates, best first, because the whole schema fits in 2000 tokens. A field below the root comes with the path that reaches it, such as `Query.vets -> Vet.visits` for `Visit.vet`. `Vet.specialties` and `Specialty.name` come third and fourth, so this result already holds every field the query needs. To see a whole type at once, read it with `introspectType`:

```json
{"name": "Specialty"}
```

```graphql
"""An area of veterinary medicine that a vet specializes in, such as surgery."""
type Specialty {
  id: ID!
  name: String!
}
```

Run `executeGraphql` with:

```json
{"document": "{ vets { firstName lastName specialties { name } } }"}
```

```json
{"data":{"vets":[{"firstName":"James","lastName":"Carter","specialties":[]},{"firstName":"Helen","lastName":"Leary","specialties":[{"name":"radiology"}]},{"firstName":"Linda","lastName":"Douglas","specialties":[{"name":"dentistry"},{"name":"surgery"}]},...
```

These are the calls a model makes, and it needs `introspectType` only when the search result leaves out a field it wants. Run `searchSchema` with these three questions to see the search by meaning. The last column shows what the same searches return with BM25, the search by words that the tests use:

| Question | By meaning | By words (BM25) |
|---|---|---|
| `doctors` | `Query.vets`, then `Vet.specialties` | *Nothing in this schema matched "doctors".* |
| `contact details` | `Owner.telephone`, then `Owner.address` | *Nothing in this schema matched "contact details".* |
| `Quantas consultas teve cada veterinário?` | `Query.vets`, then `Vet.visits` | *Nothing in this schema matched "Quantas consultas teve cada veterinário?".* |

## 5. Ask the assistant

The assistant can only read now, so the first line of chapter 5's system prompt changes, and says how to use the three tools. Change the start of the system prompt:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java` (excerpt)

```java
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read the clinic's data:
				find the fields with searchSchema, use introspectType if you need more details about a type,
				and run the query with executeGraphql.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
```

Restart, open the assistant in the frontend, and ask:

```text
Does the clinic have a surgeon?
```

The log shows the model's steps:

```text
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: searchSchema
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: executeGraphql
```

In our 10 runs with `qwen3:8b`, the model called `searchSchema` first every time, and `introspectType` in 2 of them. Nine runs ran a query and named the two surgeons, Linda Douglas and Rafael Ortega, one of them by id alone (*Vets 3 and 4 specialize in surgery*), within 11 to 41 seconds. The tenth answered yes after the search alone.

Now ask:

```text
What kinds of pets does the clinic treat?
```

In 2 of our 5 runs, the model ran a query and named the six kinds the clinic treats: birds, cats, dogs, hamsters, lizards and snakes. The other 3 stopped after `searchSchema` and answered from the schema, with a guess such as *dogs, cats, birds*. The operation file `listPetTypes` answers this question in one call.

A real application can serve both kinds of tools: the operation files to every caller, and the three tools to the callers it trusts with them, once the MCP endpoint has sign-in.

## 6. What a query from the model can do

`executeGraphql` runs a query the model wrote, so it passes through every check of the API, and GATool adds checks of its own. These are documents we sent with `executeGraphql` from the Inspector, and the answers they got:

| Document | Answer |
|---|---|
| `mutation { addVisit(input: {petId: 15, description: "x-ray"}) { id } }` | A **Tool Error**: *This server runs queries here, and that document is a mutation. gatool.dev.experimental.dynamic-operations.allow-mutations turns writing on, and it is an unsafe switch because the operation is one nobody reviewed.* |
| `{ __schema { types { name } } }` | A **Tool Error**: *That document selects __schema, and introspection runs through the schema tools here: search the schema for what you need, and read one type in full by its name. __typename inside a selection is fine.* |
| A query that nests 16 levels, `{ vets { visits { pet { owner { pets { visits { vet { visits { pet { owner { pets { visits { vet { visits { pet { name } } } } } } } } } } } } } } } }` | A **Tool Error**: *That document is larger than this server runs: it nests fields 16 levels deep, and the limit is 15. Select fewer fields, nest less deeply, or split the question into two operations.* |

GATool refuses mutations and introspection before the API sees the document, and its size limits of 15 levels, 500 fields and 30 aliases keep one query from reading the whole database. Every query that passes runs through our resolvers, as the operation files do.

## Recap

- `generate-tools=dynamic-three-step` publishes `searchSchema`, `introspectType` and `executeGraphql`, with which a model writes its own query, and an operation location that does not exist serves them alone.
- An embedding model turns a text into a vector, and the search ranks the fields by how close their vectors are to the question's, so `doctors` finds the vets.
- A small model can answer before it runs a query, where an operation file answers in one call.
- A query from the model runs only as a read, without introspection, and within the size limits.
