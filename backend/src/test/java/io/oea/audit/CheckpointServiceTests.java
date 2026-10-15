package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.oea.TestcontainersConfiguration;
import io.oea.audit.verify.Checkpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec audit/hash-chain: Periodic checkpoint (ADR-033 §6). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CheckpointServiceTests {

	@Autowired
	private CheckpointService checkpoints;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void checkpointIsAppendedAndLoggedInTheVerifierFormat(CapturedOutput output, @TempDir Path dir) throws IOException {
		AppendedLink link = this.checkpoints.checkpoint();

		assertThat(this.jdbc.queryForObject("SELECT event_type FROM audit.events WHERE id = ?", String.class, link.id()))
			.isEqualTo("audit.checkpoint");
		Path log = dir.resolve("app.log");
		Files.writeString(log, output.getOut());
		List<Checkpoint> read = Checkpoint.read(log);
		assertThat(read).anySatisfy((checkpoint) -> {
			assertThat(checkpoint.chainSeq()).isEqualTo(link.chainSeq());
			assertThat(HexFormat.of().formatHex(checkpoint.recordHash()))
				.isEqualTo(HexFormat.of().formatHex(link.recordHash()));
		});
	}

	@Test
	void checkpointsAreWrittenWhenTheIntervalElapsesAndAtShutdown() {
		AuditWriter writer = mock(AuditWriter.class);
		given(writer.append(any())).willReturn(new AppendedLink(1, 1, new byte[32]));
		CheckpointService service = CheckpointService.withUncheckedInterval(writer, Duration.ofMillis(50));
		service.start();
		verify(writer, timeout(2000).atLeast(2)).append(any());
		service.stop();
		assertThat(service.isRunning()).isFalse();
		ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
		verify(writer, atLeast(3)).append(events.capture());
		assertThat(events.getAllValues()).allSatisfy((event) -> {
			assertThat(event.eventType()).isEqualTo("audit.checkpoint");
			assertThat(event.source()).isEqualTo(AuditEvent.Source.JOB);
			assertThat(event.actor().type()).isEqualTo("system");
		});
	}

	@Test
	void intervalOutsideTheAllowedRangeIsRejected() {
		AuditWriter writer = mock(AuditWriter.class);
		assertThatIllegalArgumentException().isThrownBy(() -> new CheckpointService(writer, Duration.ofMinutes(4)))
			.withMessageContaining("between 5 and 1440 minutes");
		assertThatIllegalArgumentException().isThrownBy(() -> new CheckpointService(writer, Duration.ofMinutes(1441)));
	}

}
