package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.time.Duration;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * One GET of JSON from a public service, for the fetchers of the table kinds that are not
 * plain REST operations. It logs the request with a summary of the answer, turns a
 * <code>404 Not Found</code> into null, a <code>400 Bad Request</code> or a
 * <code>422 Unprocessable Content</code> into an error that repeats the reason the
 * service gave, and any other failure into an error that names the service.
 */
public final class UpstreamGet {

	private static final Log logger = LogFactory.getLog(UpstreamGet.class);

	private static final int MAX_REASON_LENGTH = 500;

	private UpstreamGet() {
	}

	/**
	 * Reads the answer at a URI as JSON: maps, lists and plain values.
	 * @param upstream the name of the service, in words a client can read
	 * @param client the client that calls the service
	 * @param mapper the mapper that reads the JSON of the answer
	 * @param uri the request
	 * @param summary what the log says about the answer, such as "3 rows"
	 */
	public static Object json(String upstream, RestClient client, JsonMapper mapper, URI uri,
			Function<Object, String> summary) {
		long started = System.nanoTime();
		ResponseEntity<String> answer = RestUpstreams.call(upstream, () -> read(upstream, client, uri));
		String body = answer.getBody();
		Object json = null;
		if (body != null && !body.isBlank()) {
			try {
				json = mapper.readValue(body, Object.class);
			}
			catch (JacksonException ex) {
				throw new UpstreamException(upstream, "The answer of %s is not JSON".formatted(uri.getPath()));
			}
		}
		logger.info("REST GET %s returned %s (%d ms)".formatted(uri,
				(json != null) ? summary.apply(json) : "nothing (" + answer.getStatusCode() + ")",
				Duration.ofNanos(System.nanoTime() - started).toMillis()));
		return json;
	}

	private static ResponseEntity<String> read(String upstream, RestClient client, URI uri) {
		try {
			return client.get().uri(uri).retrieve().toEntity(String.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return ResponseEntity.notFound().build();
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode().value() != 400 && ex.getStatusCode().value() != 422) {
				throw ex;
			}
			logger.info("REST GET %s was rejected: %s".formatted(uri, reason(ex)));
			throw new InvalidArgumentException("%s rejected the arguments: %s".formatted(upstream, reason(ex)));
		}
	}

	private static String reason(HttpClientErrorException ex) {
		String text = ex.getResponseBodyAsString().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
		return (text.length() > MAX_REASON_LENGTH) ? text.substring(0, MAX_REASON_LENGTH) + "..." : text;
	}

}
