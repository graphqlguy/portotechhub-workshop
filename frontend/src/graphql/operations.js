import { Kind, parse, print, visit } from 'graphql'

// Every operation the frontend sends lives as a .graphql file with the backend, in
// src/main/resources. The files in operations/ are the ones an agent can use as tools
// from chapter 4 on; the files in operations-ui/ serve the frontend alone.
const files = {
  ...import.meta.glob('../../../src/main/resources/operations/**/*.graphql', {
    query: '?raw',
    import: 'default',
    eager: true,
  }),
  ...import.meta.glob('../../../src/main/resources/operations-ui/**/*.graphql', {
    query: '?raw',
    import: 'default',
    eager: true,
  }),
}

// GATool reads @gatool(...) on an operation. The GraphQL API does not declare that
// directive, so it comes out before the frontend validates or sends a document.
function withoutGatoolDirective(document) {
  return visit(document, {
    Directive(node) {
      return node.name.value === 'gatool' ? null : undefined
    },
  })
}

export async function loadOperations() {
  const operations = {}
  for (const [path, text] of Object.entries(files)) {
    const name = path.split('/').pop().replace(/\.graphql$/, '')
    const document = withoutGatoolDirective(parse(text))
    const definition = document.definitions.find((d) => d.kind === Kind.OPERATION_DEFINITION)
    operations[name] = {
      name,
      document,
      query: print(document),
      operationName: definition?.name?.value,
      file: path.slice(path.indexOf('src/main/resources/')),
    }
  }
  return operations
}
