package org.springframework.samples.petclinic.upstream;

import java.nio.charset.StandardCharsets;

import org.hamcrest.Matchers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A client whose requests are answered with fixtures recorded from the public services,
 * in <code>src/test/resources/upstream</code>.
 */
public final class Fixtures {

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(this.builder)
		.ignoreExpectOrder(true)
		.build();

	/**
	 * Answers every request whose URL contains a text with a fixture.
	 */
	public Fixtures answer(String urlPart, String fixture) {
		this.server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString(urlPart)))
			.andRespond(withSuccess(read(fixture), MediaType.APPLICATION_JSON));
		return this;
	}

	public RestClient client() {
		return this.builder.build();
	}

	public MockRestServiceServer server() {
		return this.server;
	}

	public static String read(String fixture) {
		try {
			return new ClassPathResource("upstream/" + fixture).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

}
