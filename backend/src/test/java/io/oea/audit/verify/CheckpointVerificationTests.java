package io.oea.audit.verify;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.audit.ChainTampering;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec audit/hash-chain: Chain recomputed or backup restored after a checkpoint (REQ-174 AC5). */
class CheckpointVerificationTests {

	private OeaDatabase postgres;

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	@TempDir
	Path dir;

	@BeforeEach
	void chainWithFiveEvents() throws SQLException {
		this.postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(this.postgres, "oea_app")) {
			ChainTampering.appendEvents(app, "test.cp", 5);
		}
	}

	@AfterEach
	void stop() {
		this.postgres.stop();
	}

	@Test
	void consistentCheckpointIsAccepted() throws Exception {
		Path log = checkpointLog(4);
		assertThat(verify(log)).isZero();
		assertThat(output()).contains("1 checkpoint(s) consistent");
	}

	@Test
	void headBehindTheCheckpointIsReported() throws Exception {
		Path log = checkpointLog(6);
		// newest records deleted and the head rewritten, as after restoring an older backup
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			ChainTampering.truncateFrom(su, 5);
		}
		assertThat(verify(null)).as("chain alone looks intact").isZero();
		assertThat(verify(log)).isEqualTo(1);
		assertThat(output()).contains("database chain head 4 is behind checkpoint chain_seq 6");
	}

	@Test
	void recomputedChainDivergesFromTheCheckpoint() throws Exception {
		Path log = checkpointLog(5);
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			try (Statement statement = su.createStatement()) {
				statement.executeUpdate("UPDATE audit.events SET event_type = 'manipulated' WHERE chain_seq = 3");
			}
			ChainTampering.recomputeFrom(su, 3);
		}
		assertThat(verify(null)).as("recomputed chain looks intact").isZero();
		assertThat(verify(log)).isEqualTo(1);
		assertThat(output()).contains("chain diverges from checkpoint at chain_seq 5");
	}

	/** Spec "Checkpoints from a JSON log" (design D4 of s3-structured-logging-and-icons). */
	@Test
	void checkpointsFromJsonAndPlainTextLinesAreUsed() throws Exception {
		Path log = this.dir.resolve("mixed.log");
		Files.writeString(log, String.join("\n",
				"{\"@timestamp\":\"2026-10-29T09:00:00Z\",\"log\":{\"level\":\"INFO\"},\"message\":\"started\"}",
				"{\"@timestamp\":\"2026-10-29T09:00:01Z\",\"message\":\"event=audit.checkpoint\",\"event\":\"audit.checkpoint\","
						+ "\"chainSeq\":2,\"recordHash\":\"" + hash(2) + "\"}",
				"backend-1  | {\"version\":\"1.1\",\"short_message\":\"checkpoint\",\"_event\":\"audit.checkpoint\","
						+ "\"_chainSeq\":3,\"_recordHash\":\"" + hash(3) + "\"}",
				"INFO event=audit.checkpoint chainSeq=4 recordHash=" + hash(4) + " at=2026-10-29T09:00:03.000000Z", ""));
		assertThat(verify(log)).isZero();
		assertThat(output()).contains("3 checkpoint(s) consistent");

		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			try (Statement statement = su.createStatement()) {
				statement.executeUpdate("UPDATE audit.events SET event_type = 'manipulated' WHERE chain_seq = 2");
			}
			ChainTampering.recomputeFrom(su, 2);
		}
		assertThat(verify(log)).isEqualTo(1);
		assertThat(output()).contains("chain diverges from checkpoint at chain_seq 2");
	}

	private String hash(long chainSeq) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				java.sql.ResultSet rs = statement
					.executeQuery("SELECT encode(record_hash, 'hex') FROM audit.events WHERE chain_seq = " + chainSeq)) {
			rs.next();
			return rs.getString(1);
		}
	}

	private Path checkpointLog(long chainSeq) throws SQLException, IOException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			return ChainTampering.checkpointLog(su, chainSeq, this.dir.resolve("checkpoints.log"));
		}
	}

	private int verify(Path checkpoints) {
		this.output.reset();
		String url = "--oea.audit-verify.url=" + this.postgres.getJdbcUrl();
		String password = "--oea.audit-verify.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_auditor");
		PrintStream out = new PrintStream(this.output, true, StandardCharsets.UTF_8);
		return (checkpoints != null) ? AuditVerifyCommand.run(out, url, password, "--checkpoints=" + checkpoints)
				: AuditVerifyCommand.run(out, url, password);
	}

	private String output() {
		return this.output.toString(StandardCharsets.UTF_8);
	}

}
