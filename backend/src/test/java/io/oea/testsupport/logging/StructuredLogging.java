package io.oea.testsupport.logging;

/**
 * Spring Boot keeps the selected structured log format as a system property after the
 * application closes; tests that select a format remove it again, so that later applications
 * in the same JVM log plain text.
 */
public final class StructuredLogging {

	/** System property set by Spring Boot from {@code logging.structured.format.console}. */
	public static final String CONSOLE_FORMAT_PROPERTY = "CONSOLE_LOG_STRUCTURED_FORMAT";

	private StructuredLogging() {
	}

	public static void reset() {
		System.clearProperty(CONSOLE_FORMAT_PROPERTY);
	}

}
