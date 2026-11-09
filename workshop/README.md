# Workshop guide

Each chapter is one file: numbered steps with the complete code to copy. Chapters 1, 3 and 5 end with an exercise, with its solution folded below it. Start with [the setup page](00-setup.md).

## How it works

The repository you cloned is the **starting point**: Spring Petclinic with its services, its data and a React frontend ready. The GraphQL API is what we add, step by step. After each chapter, a checkpoint branch holds the code as it should be, exercises included: `checkpoint-01-graphql-api` after chapter 1, `checkpoint-02-relationships` after chapter 2, and so on. The [repository's README](../README.md#branches) lists them all.

The frontend checks the backend's schema at startup and shows only the pages the API can serve. Each chapter lights up another part of it: change the schema, restart the backend, refresh the browser.

## Part 1: GraphQL

1. [Your first GraphQL service](01-your-first-graphql-service.md): the dependency, GraphiQL, your first schema and its descriptions, queries with arguments, paging and filtering, deprecation
2. [Relationships and the N+1 problem](02-relationships-and-the-n-plus-1-problem.md): nested fields, default field resolution, `@SchemaMapping`, the N+1 problem and `@BatchMapping`
3. [Mutations, input types and a custom scalar](03-mutations-input-types-and-a-custom-scalar.md): a `Date` scalar, the `Mutation` type, input types, variables and partial updates

## Part 2: agents

4. [Curated operations](04-curated-operations.md): MCP in brief, GATool, the MCP Inspector, the frontend's operation files as tools, errors a model can act on, a model choosing the tools in MCPJam
5. [An assistant inside the application](05-an-assistant-inside-the-application.md): Spring AI's `ChatClient` with the operation files as in-process tools, checking what the model did, errors a model can act on, hosted models, conversation memory
6. [Dynamic tools](06-dynamic-tools.md): `searchSchema`, `introspectType` and `executeGraphql`, a system prompt that says when to write a query, a schema search by meaning with an embedding model, the checks on a query the model wrote
7. [A large schema built from Portuguese open data](07-a-large-schema-built-from-portuguese-open-data.md): nine public services of Portugal, Porto and the EU in one schema of about 158,000 tokens, recorded answers for working offline, prepared questions, and what a field needs in its description

## Appendix

- [A model calls the tools on your laptop](appendix-a-model-calls-the-tools.md): chapter 4's tools from a chat, with ollmcp and Ollama in the terminal, or with Claude Desktop through mcp-remote
