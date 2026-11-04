# Chapter 5: An assistant inside the application

> **User story** · As a receptionist, I want to ask the clinic's assistant in my own words, so that I can find owners and book visits without clicking through the pages.

In chapter 4, an AI application outside ours called our tools over MCP. In this chapter, the AI application is ours: a chat in the frontend, answered by a model in Ollama through [Spring AI](https://docs.spring.io/spring-ai/reference/), with chapter 4's operation files as its tools. The tools run inside the application.

By the end of this chapter, you will:

- Give a Spring AI `ChatClient` the operation files as in-process tools
- Run the chat on a model in Ollama, or on a hosted model
- Check what the assistant did, and give it errors it can act on

## 1. How a question becomes an answer

| Step | What happens |
|---|---|
| 1 | The frontend sends the question to `POST /api/chat`. |
| 2 | Spring AI's `ChatClient` sends the model a system prompt, the question and the 10 tools. |
| 3 | The model answers, or asks for a tool with its arguments. |
| 4 | Spring AI runs the tool: GATool runs the tool's operation file in our GraphQL engine. |
| 5 | The model reads the tool's result, then asks for another tool or answers. |

The frontend has had the chat panel since the starting point. It shows the panel once `GET /api/chat` answers `405 Method Not Allowed`, which tells it that the endpoint exists.

## 2. Add Spring AI

Add two starters next to GATool's:

`pom.xml`

```xml
<dependency>
  <groupId>io.gatool</groupId>
  <artifactId>gatool-in-process-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-ollama</artifactId>
  <version>2.0.1</version>
</dependency>
```

| Starter | Brings |
|---|---|
| `gatool-in-process-spring-boot-starter` | A `GAToolCallbacks` bean, which hands the tools to a `ChatClient`. These tools stay off the MCP server, which serves its own. |
| `spring-ai-starter-model-ollama` | A `ChatClient.Builder` for a model in Ollama. GATool 0.1.0 is built with Spring AI 2.0.1. |

> [!NOTE]
> With Gradle, add `implementation 'io.gatool:gatool-in-process-spring-boot-starter:0.1.0'` and `implementation 'org.springframework.ai:spring-ai-starter-model-ollama:2.0.1'` to `build.gradle`.

If Ollama cannot run on your laptop, do step 8 now, and from then on start the backend the way it says.

## 3. Configure the model

Add a Chat section below the MCP section:

`src/main/resources/application.properties`

```properties
# Chat
# The assistant in the frontend gets the same operation files as tools, inside the application
gatool.in-process.operations.locations=classpath*:operations/
# The chat model in Ollama, pulled in the setup with: ollama pull qwen3:8b
spring.ai.ollama.chat.model=qwen3:8b
# The model reasons before it answers: slower, and its bookings come out right more often
spring.ai.ollama.chat.think=true
# The model reads the tools, the question and the tool results within this many tokens.
# Ollama picks a size from your memory when this is unset, so set it for the same result on every laptop.
spring.ai.ollama.chat.num-ctx=8192
# One call to the model, so the chat reports at once when Ollama is not running. By default
# Spring AI tries a failed call 10 more times, with pauses that grow to 3 minutes.
spring.ai.retry.max-attempts=0
# The calls to the model go through the JDK's HTTP client. Reactor Netty, which Spring AI also
# brings, logs an ERROR about DNS on macOS at its first call.
spring.http.clients.imperative.factory=jdk
# Logs the name of each tool the model calls
logging.level.org.springframework.ai.model.tool=DEBUG
```

| Property | Meaning |
|---|---|
| `gatool.in-process.operations.locations` | The operation files of the in-process tools. It is separate from the MCP server's setting, so the application's own assistant can get other tools. |
| `spring.ai.ollama.chat.model` | The model from the setup. Ollama must be running: `ollama list` answers when it is. |
| `spring.ai.ollama.chat.think` | `qwen3:8b` can reason before it answers. Step 6 shows what that costs and what it buys. |
| `spring.ai.ollama.chat.num-ctx` | Everything the model reads for one answer must fit in this many tokens. In our runs, a booking used up to about 4,500, the model's reasoning included. With 8192, `qwen3:8b` takes 5.8 GB of memory. |

## 4. The chat endpoint

**Create a new class** named `ChatController` in a new package, `org.springframework.samples.petclinic.chat`:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java`

```java
package org.springframework.samples.petclinic.chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools) {
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider()).build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question) {
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read and change the clinic's data.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
		return chatClient.prompt().system(system).user(question).call().content();
	}

	// The chat panel shows this text, for example when Ollama is not running
	@ExceptionHandler
	ResponseEntity<String> modelFailed(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The model did not answer: " + ex.getMessage());
	}

}
```

| Element | Meaning |
|---|---|
| `ChatClient.Builder` | Spring AI's builder, set up for the model of step 3. |
| `defaultTools(tools.toolCallbackProvider())` | Gives every prompt of this client the 10 in-process tools. |
| The system prompt | What the model needs and cannot know: its role, and today's date, which goes with every question. |
| `call().content()` | Sends the prompt, runs every tool the model asks for, and returns the model's last answer as text. |
| `@PostMapping` | The frontend sends the question as plain text. A `GET` answers `405`. |
| `@ExceptionHandler` | When the model cannot be reached, the chat shows one line, such as `The model did not answer: I/O error on POST request for "http://localhost:11434/api/chat": Connection refused`. |

## 5. Ask the assistant

Restart. After the MCP tools, GATool lists the in-process tools:

```text
GATool serves 10 in-process tools:
  addOwner from file:.../target/classes/operations/mutations/addOwner.graphql (about 164 tokens)
  addPet from file:.../target/classes/operations/mutations/addPet.graphql (about 254 tokens)
  addVisit from file:.../target/classes/operations/mutations/addVisit.graphql (about 299 tokens)
  updateOwner from file:.../target/classes/operations/mutations/updateOwner.graphql (about 160 tokens)
  updatePet from file:.../target/classes/operations/mutations/updatePet.graphql (about 170 tokens)
  findOwners from file:.../target/classes/operations/queries/findOwners.graphql (about 318 tokens)
  getOwnerDetails from file:.../target/classes/operations/queries/getOwnerDetails.graphql (about 102 tokens)
  getVetDetails from file:.../target/classes/operations/queries/getVetDetails.graphql (about 102 tokens)
  listPetTypes from file:.../target/classes/operations/queries/listPetTypes.graphql (about 67 tokens)
  listVets from file:.../target/classes/operations/queries/listVets.graphql (about 82 tokens)
  about 1718 tokens in all, which every agent that lists these tools reads before it calls one
```

Refresh the frontend. The home page lists *Chapter 5 Clinic assistant*, and every page has an **Ask the assistant** button at the bottom right. Open it and ask:

```text
Which vets work at the clinic, and what are their specialties?
```

The first answer takes longer, while Ollama loads the model. The backend's log shows the tool the model chose:

```text
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: listVets
```

The answer lists the six vets with their specialties, in about 6 seconds once the model is loaded. Models write Markdown, and the panel renders it.

## 6. Check what the assistant did

Ask:

```text
Book a check-up for Leo, the cat of George Franklin, with Helen Leary next Wednesday.
```

Then open George Franklin on the **Find owners** page, and look for the date and the vet under Leo's visits. We asked the same question with three different days, 5 times each, on Wednesday 2026-11-04, with `think` on and off:

| Asked | `think=true` | `think=false` |
|---|---|---|
| "tomorrow" | Thursday 2026-11-05 every time, and once without the vet | Thursday 2026-11-05 every time, always without the vet |
| "next Wednesday" | 2026-11-11 every time, and once without the vet | Monday 2026-11-09 every time |
| "next Monday" | 2026-11-09 every time | Sunday 2026-11-08 every time |
| Time for one booking | 21 to 54 seconds | about 1 second |

To see the second column yourself, set `spring.ai.ollama.chat.think=false`, restart, and ask again. With `think` on, the model works out the date before it calls a tool, which takes time. With `think` off, it answers at once, and turning a weekday into a date is arithmetic that it gets wrong. A booking without a vet came each time the model skipped `listVets`: `vetId` is optional, so `addVisit` booked the visit without one. With `think` on, the answer said so; with `think` off, it reported a plain success. The owner's page shows every booking, so an assistant's changes are there to check, whichever model makes them.

Names trip a small model up as well. We asked 19 times for *a check-up for Rosa, the cat of Porto Tech, with Helen Leary tomorrow*. In 7 runs the model searched for an owner whose last name is `Porto Tech`, got an empty page, and stopped. In one more, it registered a second Porto Tech with a second Rosa, and booked the visit for her. Only 4 runs booked Rosa with Helen Leary. With an owner whose name reads as a first and a last name, such as George Franklin, the search found the owner every time.

## 7. Errors the model can act on

With `think` off, the model guesses ids before it looks them up. In our runs it called `addVisit` with `petId: 123`, and got chapter 4's error:

```json
{
  "errors": [
    {
      "message": "Pet with id 123 does not exist",
      "path": ["addVisit"],
      "extensions": { "classification": "NOT_FOUND" }
    }
  ],
  "data": null
}
```

The model then tried `getOwnerDetails` with another guessed id, which returned `null`, and called `findOwners` with the last name `Tech` and the city `Porto`. It found Rosa's id, 15, and booked her visit, without the vet. A guessed id that belongs to another pet passes every check: in two runs, the model guessed Leo's id, booked Leo, and answered that Rosa was booked. Only a person who looks at the owner's page finds that mistake.

## 8. Use a hosted model instead of Ollama

Spring AI has a starter for each provider. A Maven profile adds one, and runs the application with the Spring profile of the same name. Add three profiles at the top of the `<profiles>` section:

`pom.xml`

```xml
<!-- A hosted chat model in place of Ollama, for example: ./mvnw spring-boot:run -Pmistral
     Each profile adds the model's starter, and runs the application with the Spring
     profile of the same name, which application-mistral.properties configures. -->
<profile>
  <id>mistral</id>
  <properties>
    <spring-boot.run.profiles>mistral</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-mistral-ai</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
<profile>
  <id>openai</id>
  <properties>
    <spring-boot.run.profiles>openai</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-openai</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
<profile>
  <id>anthropic</id>
  <properties>
    <spring-boot.run.profiles>anthropic</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-anthropic</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
```

Then create the properties file for your provider next to `application.properties`:

`src/main/resources/application-mistral.properties`

```properties
# ./mvnw spring-boot:run -Pmistral, with your key in the MISTRAL_API_KEY environment variable
spring.ai.model.chat=mistral
spring.ai.mistralai.api-key=${MISTRAL_API_KEY}
spring.ai.mistralai.chat.model=mistral-small-latest
```

`src/main/resources/application-openai.properties`

```properties
# ./mvnw spring-boot:run -Popenai, with your key in the OPENAI_API_KEY environment variable
spring.ai.model.chat=openai
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5-mini
```

`src/main/resources/application-anthropic.properties`

```properties
# ./mvnw spring-boot:run -Panthropic, with your key in the ANTHROPIC_API_KEY environment variable
spring.ai.model.chat=anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.model=claude-haiku-5-5
```

The Ollama starter stays in the project, so `spring.ai.model.chat` says which provider's model the `ChatClient` gets.

| Provider | Get a key at | Cost |
|---|---|---|
| Mistral | https://console.mistral.ai/api-keys: create an account and choose Free mode, then **Create new key**. Mistral shows the key once. | Free mode works without a credit card, with low rate limits. |
| OpenAI | https://platform.openai.com/api-keys | Paid |
| Anthropic | https://platform.claude.com/settings/keys | Paid |

Start the backend with the key in an environment variable:

```bash
export MISTRAL_API_KEY=your-key
./mvnw spring-boot:run -Pmistral
```

On Windows, set the key with `set MISTRAL_API_KEY=your-key` in Command Prompt or `$env:MISTRAL_API_KEY="your-key"` in PowerShell, then run `mvnw.cmd spring-boot:run -Pmistral`. The log says `The following 1 profile is active: "mistral"`. A wrong or missing key shows in the chat as `The model did not answer: HTTP 401 - {"detail":"Invalid API Key"}`. Keep the `-P` flag for the rest of the workshop.

## Exercise: a conversation

Ask one question after the other:

```text
Which pets does Carlos Estaban have?
```

```text
Book an x-ray for his cat tomorrow, with the vet Helen Leary.
```

The second answer shows that the model has lost the first: *The owner with last name "Leary" does not exist.* Each call sends the model one question. Give the assistant the conversation so far.

- Spring AI configures a `ChatMemory` bean, which keeps the last 20 messages of each conversation in memory.
- A `MessageChatMemoryAdvisor` adds a conversation's messages to each prompt, and stores the new ones.
- The frontend sends the session cookie with each question, so the HTTP session can name the conversation.

<details>
<summary>Show solution</summary>

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java`

```java
package org.springframework.samples.petclinic.chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;
import jakarta.servlet.http.HttpSession;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools, ChatMemory memory) {
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider())
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
			.build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question, HttpSession session) {
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read and change the clinic's data.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
		return chatClient.prompt()
			.system(system)
			.user(question)
			.advisors((advisor) -> advisor.param(ChatMemory.CONVERSATION_ID, session.getId()))
			.call()
			.content();
	}

	// The chat panel shows this text, for example when Ollama is not running
	@ExceptionHandler
	ResponseEntity<String> modelFailed(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The model did not answer: " + ex.getMessage());
	}

}
```

Restart and ask the two questions again:

> The x-ray for Sly (cat) is booked for 2026-11-05 with Dr. Helen Leary.

The memory lives in the backend, one conversation for each session, and a restart empties it. In all 3 of our runs with the memory, the model booked Sly with Helen Leary for tomorrow, and all 3 runs without it ended without a booking.

</details>

## Recap

- `gatool.in-process.operations.locations` gives the application's own `ChatClient` the operation files as tools, through the `GAToolCallbacks` bean.
- The model knows only what the prompt tells it, today's date included, and a small model gets dates and names wrong often enough that its changes need checking.
- An error message that says how to correct the call lets the model try again.
- A Maven profile switches the chat to Mistral, OpenAI or Anthropic.
- `ChatMemory` and `MessageChatMemoryAdvisor` give the model the conversation so far.

Chapter 6 gives the assistant three tools with which it writes its own queries.
