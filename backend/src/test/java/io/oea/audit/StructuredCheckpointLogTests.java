package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oea.audit.verify.Checkpoint;
import io.oea.testsupport.logging.StructuredLogging;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Specs audit/hash-chain "Checkpoint in a JSON log" and "Checkpoints from a JSON log" (design
 * D3, D4 of s3-structured-logging-and-icons): real lines of the Spring Boot formatters.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredCheckpointLogTests {

	private static final byte[] HASH = HexFormat.of().parseHex("ab".repeat(32));

	@AfterEach
	void plainTextAgain() {
		StructuredLogging.reset();
	}

	@Test
	void plainTextLineIsUnchanged(CapturedOutput output) {
		String line = checkpointLine(null, output, 41);
		assertThat(line).contains("event=audit.checkpoint chainSeq=41 recordHash=" + "ab".repeat(32) + " at=")
			.doesNotStartWith("{");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ecs", "logstash", "gelf" })
	void jsonLineCarriesTheFieldsAndTheMessage(String format, CapturedOutput output, @TempDir Path dir)
			throws IOException {
		String line = checkpointLine(format, output, 42);
		JsonNode json = JsonMapper.shared().readTree(line);
		String prefix = "gelf".equals(format) ? "_" : "";
		assertThat(json.path(prefix + "event").asString()).isEqualTo("audit.checkpoint");
		assertThat(json.path(prefix + "chainSeq").asLong()).isEqualTo(42);
		assertThat(json.path(prefix + "recordHash").asString()).isEqualTo("ab".repeat(32));
		assertThat(json.path(prefix + "at").asString()).endsWith("Z");
		assertThat(line).contains("event=audit.checkpoint chainSeq=42 recordHash=");

		// the verifier reads the real line, also behind a collector prefix
		Path log = dir.resolve("backend.log");
		Files.writeString(log, line + "\n" + "backend-1  | " + line + "\n" + "{\"message\":\"other\"}\nnot a checkpoint\n");
		List<Checkpoint> read = Checkpoint.read(log);
		assertThat(read).hasSize(2).allSatisfy((checkpoint) -> {
			assertThat(checkpoint.chainSeq()).isEqualTo(42);
			assertThat(checkpoint.recordHash()).isEqualTo(HASH);
		});
	}

	/** Writes one checkpoint in an application whose console log uses the format ({@code null}: plain text). */
	private static String checkpointLine(String format, CapturedOutput output, long chainSeq) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(LoggingOnly.class).web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.logStartupInfo(false);
		if (format != null) {
			builder.properties("logging.structured.format.console=" + format);
		}
		StructuredLogging.prepare();
		try (ConfigurableApplicationContext context = builder.run()) {
			AuditWriter writer = mock(AuditWriter.class);
			when(writer.append(any(AuditEvent.class))).thenReturn(new AppendedLink(7, chainSeq, HASH));
			CheckpointService.withUncheckedInterval(writer, Duration.ofMinutes(60)).checkpoint();
		}
		return output.getOut()
			.lines()
			.filter((line) -> line.contains("chainSeq=" + chainSeq + " "))
			.reduce((first, second) -> second)
			.orElseThrow();
	}

	@Configuration(proxyBeanMethods = false)
	static class LoggingOnly {

	}

}
