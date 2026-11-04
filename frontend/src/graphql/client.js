export function requestBody(operation, variables = {}) {
  return {
    query: operation.query,
    operationName: operation.operationName,
    variables,
  }
}

// Sends one operation and returns { data, errors }, as a GraphQL response does.
export async function execute(operation, variables = {}) {
  let response
  try {
    response = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/graphql-response+json, application/json',
      },
      body: JSON.stringify(requestBody(operation, variables)),
    })
  } catch (error) {
    return { errors: [{ message: `The backend did not answer: ${error.message}` }] }
  }
  const text = await response.text()
  try {
    return JSON.parse(text)
  } catch {
    return { errors: [{ message: `The backend answered HTTP ${response.status} without a GraphQL response` }] }
  }
}
