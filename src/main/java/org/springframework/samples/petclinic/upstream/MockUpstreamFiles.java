package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileSystemUtils;

/**
 * A copy of the recorded answers in a temporary directory, for the server that answers in
 * place of the public services. The server reads its files from a directory, and the
 * recordings are on the classpath, which is a nested jar when the application runs with
 * <code>java -jar</code>. The copy is deleted when the application stops.
 *
 * @param directory the temporary directory, with the <code>mappings</code> and
 * <code>__files</code> folders of the recordings
 */
public record MockUpstreamFiles(Path directory) implements AutoCloseable {

	/**
	 * Copies every file under a classpath location into a new temporary directory.
	 * @param location the location, such as <code>mock-upstreams</code>
	 */
	public static MockUpstreamFiles copy(String location) {
		try {
			Path directory = Files.createTempDirectory("mock-upstreams");
			for (Resource resource : new PathMatchingResourcePatternResolver()
				.getResources("classpath*:" + location + "/**/*")) {
				if (!resource.isReadable()) {
					continue;
				}
				String url = resource.getURL().toString();
				Path target = directory.resolve(url.substring(url.lastIndexOf(location + "/") + location.length() + 1));
				Files.createDirectories(target.getParent());
				try (InputStream in = resource.getInputStream()) {
					Files.copy(in, target);
				}
			}
			return new MockUpstreamFiles(directory);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot copy the recordings in " + location, ex);
		}
	}

	@Override
	public void close() {
		try {
			FileSystemUtils.deleteRecursively(this.directory);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
