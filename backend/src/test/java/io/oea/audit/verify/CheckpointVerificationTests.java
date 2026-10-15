package io.oea.audit.verify;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.audit.chain.ChainHasher;
import io.oea.audit.chain.ChainTable;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec audit/hash-chain: Chain recomputed or backup restored after a checkpoint (REQ-174 AC5). */
class CheckpointVerificationTests {

	private PostgreSQLContainer postgres;

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	@TempDir
	Path dir;

	@BeforeEach
	void chainWithFiveEvents() throws SQLException {
		this.postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(this.postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (int i = 1; i <= 5; i++) {
				statement.executeUpdate("""
						INSERT INTO audit.events (event_type, event_category, severity, outcome, source, actor_type,
						    chain_seq, hash_version, prev_hash, record_hash)
						VALUES ('test.cp-%d', 'system', 'info', 'success', 'Job', 'system', 0, 0, ''::bytea, ''::bytea)
						""".formatted(i));
			}
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
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("DELETE FROM audit.events WHERE chain_seq >= 5");
			statement.executeUpdate("""
					UPDATE audit.chain_head SET last_seq = 4,
					    last_hash = (SELECT record_hash FROM audit.events WHERE chain_seq = 4) WHERE id = 1""");
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
			recomputeFrom(su, 3);
		}
		assertThat(verify(null)).as("recomputed chain looks intact").isZero();
		assertThat(verify(log)).isEqualTo(1);
		assertThat(output()).contains("chain diverges from checkpoint at chain_seq 5");
	}

	private Path checkpointLog(long chainSeq) throws SQLException, IOException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres);
				PreparedStatement select = su.prepareStatement("""
						SELECT record_hash FROM audit.events WHERE chain_seq = ?
						UNION ALL SELECT record_hash FROM audit.retention_settings WHERE chain_seq = ?""")) {
			select.setLong(1, chainSeq);
			select.setLong(2, chainSeq);
			try (ResultSet rs = select.executeQuery()) {
				rs.next();
				Path log = this.dir.resolve("checkpoints.log");
				Files.writeString(log, "2026-10-15 INFO other line\n... event=audit.checkpoint chainSeq=" + chainSeq
						+ " recordHash=" + HexFormat.of().formatHex(rs.getBytes(1)) + " at=2026-10-15T09:00:00.000000Z\n");
				return log;
			}
		}
	}

	/** What an attacker with superuser rights would do: recompute every hash from a position on. */
	private static void recomputeFrom(Connection su, long fromSeq) throws SQLException {
		byte[] prev;
		try (Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT record_hash FROM audit.events WHERE chain_seq = %1$d
						UNION ALL SELECT record_hash FROM audit.retention_settings WHERE chain_seq = %1$d"""
					.formatted(fromSeq - 1))) {
			rs.next();
			prev = rs.getBytes(1);
		}
		try (Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT * FROM audit.events WHERE chain_seq >= " + fromSeq
						+ " ORDER BY chain_seq");
				PreparedStatement update = su.prepareStatement(
						"UPDATE audit.events SET prev_hash = ?, record_hash = ? WHERE chain_seq = ?")) {
			while (rs.next()) {
				byte[] hash = ChainHasher.recordHash(rs.getInt("hash_version"), rs.getLong("chain_seq"), prev,
						ChainTable.EVENTS, ChainHasher.fieldValues(ChainTable.EVENTS, rs));
				update.setBytes(1, prev);
				update.setBytes(2, hash);
				update.setLong(3, rs.getLong("chain_seq"));
				update.executeUpdate();
				prev = hash;
			}
			try (PreparedStatement head = su.prepareStatement("UPDATE audit.chain_head SET last_hash = ? WHERE id = 1")) {
				head.setBytes(1, prev);
				head.executeUpdate();
			}
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
