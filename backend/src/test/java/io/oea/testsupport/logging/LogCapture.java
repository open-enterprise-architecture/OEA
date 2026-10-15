package io.oea.testsupport.logging;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Captures everything written to {@code System.out} and {@code System.err} while attached,
 * including the log output of an application started afterwards (Spring Boot reinitializes
 * the logging system, so appenders added before startup would be lost). Used for the log
 * scans of REQ-175 AC3.
 */
public final class LogCapture implements AutoCloseable {

	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	private final PrintStream originalErr = System.err;

	private LogCapture() {
		System.setOut(tee(this.originalOut));
		System.setErr(tee(this.originalErr));
	}

	public static LogCapture attach() {
		return new LogCapture();
	}

	public String text() {
		synchronized (this.buffer) {
			return this.buffer.toString(StandardCharsets.UTF_8);
		}
	}

	@Override
	public void close() {
		System.setOut(this.originalOut);
		System.setErr(this.originalErr);
	}

	private PrintStream tee(PrintStream original) {
		ByteArrayOutputStream capture = this.buffer;
		return new PrintStream(new OutputStream() {
			@Override
			public void write(int b) {
				original.write(b);
				synchronized (capture) {
					capture.write(b);
				}
			}

			@Override
			public void write(byte[] bytes, int offset, int length) {
				original.write(bytes, offset, length);
				synchronized (capture) {
					capture.write(bytes, offset, length);
				}
			}

			@Override
			public void flush() {
				original.flush();
			}
		}, true, StandardCharsets.UTF_8);
	}

}
