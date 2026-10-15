package io.oea.testsupport.containers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/** Random key-encryption key files for tests (REQ-188); never a development key. */
public final class OeaTestKeys {

	private static Path shared;

	private OeaTestKeys() {
	}

	/** Key file shared by all application contexts of the test JVM. */
	public static synchronized String sharedKeyFile() {
		if (shared == null) {
			shared = newKeyFile();
		}
		return shared.toString();
	}

	public static Path newKeyFile() {
		try {
			byte[] key = new byte[32];
			new SecureRandom().nextBytes(key);
			Path file = Files.createTempFile("oea-kek", ".key");
			Files.writeString(file, Base64.getEncoder().encodeToString(key) + "\n");
			return file;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
