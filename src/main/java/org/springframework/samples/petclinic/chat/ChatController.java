package org.springframework.samples.petclinic.chat;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;
import jakarta.servlet.http.HttpSession;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.upstream.UpstreamClock;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	private final UpstreamClock clock;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools, ChatMemory memory, UpstreamClock clock) {
		this.clock = clock;
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider())
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
			.build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question, HttpSession session) {
		String system = """
				You are the assistant of a veterinary clinic in Porto, Portugal. Use the tools to answer:
				find the fields with searchSchema, use introspectType if you need more details about a type,
				and run the query with executeGraphql.
				Besides the clinic's owners, pets, visits and vets, the API reads public services of Portugal,
				Porto and the European Union: the weather, the sea and earthquakes (IPMA), beaches, water and the
				environment (APA), hospitals and health (SNS), statistics such as population, businesses, prices,
				crimes and trade (INE and Eurostat), tourism and local lodging (Turismo de Portugal), Porto's buses,
				air quality, parks and places (Porto Digital), and fuel prices (DGEG).
				For a question on any of these topics, search the schema before you decide the API cannot answer it.
				When an answer comes from a public service, name the service.
				Today is %s. Answer briefly.""".formatted(this.clock.today().format(DAY));
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
