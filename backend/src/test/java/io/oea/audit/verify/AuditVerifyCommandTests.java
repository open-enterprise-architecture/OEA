package io.oea.audit.verify;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec audit/hash-chain: Verification command (REQ-174 AC3). */
class AuditVerifyCommandTests {

	private PostgreSQLContainer postgres;

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	@BeforeEach
	void freshChain() throws SQLException {
		this.postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(this.postgres, "oea_app"); Statement statement = app.createStatement()) {
			for (int i = 1; i <= 3; i++) {
				statement.executeUpdate("""
						INSERT INTO audit.events (event_type, event_category, severity, outcome, source, actor_type,
						    chain_seq, hash_version, prev_hash, record_hash)
						VALUES ('test.verify-%d', 'system', 'info', 'success', 'Job', 'system', 0, 0, ''::bytea, ''::bytea)
						""".formatted(i));
			}
		}
	}

	@AfterEach
	void stop() {
		this.postgres.stop();
	}

	@Test
	void intactChainIsReportedAsOk() {
		assertThat(verify()).isZero();
		assertThat(output()).startsWith("OK 4 links, head 4 ");
	}

	@Test
	void modifiedRecordIsTheFirstBrokenLink() throws SQLException {
		long seq = seqOf("test.verify-2");
		asSuperuser("UPDATE audit.events SET event_type = 'manipulated' WHERE event_type = 'test.verify-2'");
		assertThat(verify()).isEqualTo(1);
		assertThat(output()).contains("BROKEN first broken link: chain_seq " + seq + " in audit.events: expected record_hash");
	}

	@Test
	void deletedRecordIsReportedWithItsChainSeq() throws SQLException {
		long seq = seqOf("test.verify-2");
		asSuperuser("DELETE FROM audit.events WHERE event_type = 'test.verify-2'");
		assertThat(verify()).isEqualTo(1);
		assertThat(output()).contains("BROKEN first broken link: chain_seq " + seq + " is missing");
	}

	@Test
	void deletedNewestRecordIsDetectedThroughTheChainHead() throws SQLException {
		long seq = seqOf("test.verify-3");
		asSuperuser("DELETE FROM audit.events WHERE event_type = 'test.verify-3'");
		assertThat(verify()).isEqualTo(1);
		assertThat(output()).contains("chain_seq " + seq + " is missing");
	}

	@Test
	void missingPasswordIsAUsageError() {
		assertThat(AuditVerifyCommand.run(new PrintStream(this.output, true, StandardCharsets.UTF_8),
				"--oea.audit-verify.url=" + this.postgres.getJdbcUrl()))
			.isEqualTo(2);
		assertThat(output()).contains("oea.audit-verify.password is empty");
	}

	int verify(String... extra) {
		String[] base = { "--oea.audit-verify.url=" + this.postgres.getJdbcUrl(),
				"--oea.audit-verify.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_auditor") };
		String[] args = Stream.concat(Arrays.stream(base), Arrays.stream(extra))
			.toArray(String[]::new);
		return AuditVerifyCommand.run(new PrintStream(this.output, true, StandardCharsets.UTF_8), args);
	}

	String output() {
		return this.output.toString(StandardCharsets.UTF_8);
	}

	long seqOf(String eventType) throws SQLException {
		try (Connection connection = OeaDatabases.asSuperuser(this.postgres);
				Statement statement = connection.createStatement();
				ResultSet rs = statement
					.executeQuery("SELECT chain_seq FROM audit.events WHERE event_type = '" + eventType + "'")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	void asSuperuser(String sql) throws SQLException {
		try (Connection connection = OeaDatabases.asSuperuser(this.postgres);
				Statement statement = connection.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}
