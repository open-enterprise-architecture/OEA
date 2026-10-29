package io.oea.testsupport.logging;

import org.springframework.boot.logging.LoggingSystem;

/**
 * Spring Boot keeps the selected structured log format as a system property after the
 * application closes, and skips the logging setup of an application while another one of the
 * same JVM (e.g. a cached test context) has initialized it. Tests that select a format call
 * {@link #prepare()} before they start their application and {@link #reset()} afterwards.
 */
public final class StructuredLogging {

	/** System property set by Spring Boot from {@code logging.structured.format.console}. */
	public static final String CONSOLE_FORMAT_PROPERTY = "CONSOLE_LOG_STRUCTURED_FORMAT";

	private StructuredLogging() {
	}

	/** Lets the next application initialize the logging system with its own format. */
	public static void prepare() {
		LoggingSystem.get(StructuredLogging.class.getClassLoader()).cleanUp();
	}

	/** Back to plain text for later applications of the JVM. */
	public static void reset() {
		System.clearProperty(CONSOLE_FORMAT_PROPERTY);
		prepare();
	}

}
