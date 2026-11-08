package org.springframework.samples.petclinic.upstream;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockUpstreamFilesTests {

	@Test
	void copiesTheRecordingsOutOfTheClasspath() throws Exception {
		Path directory;
		try (MockUpstreamFiles files = MockUpstreamFiles.copy("mock-upstreams")) {
			directory = files.directory();
			assertThat(directory.resolve("mappings/ipma.json")).exists();
			try (var bodies = Files.list(directory.resolve("__files/ipma"))) {
				assertThat(bodies).isNotEmpty();
			}
		}
		assertThat(directory).doesNotExist();
	}

}
