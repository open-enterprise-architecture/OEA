package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec audit/hash-chain: Chain values assigned by the database (ADR-033 §5). */
class HashChainTriggerTests {

	private static PostgreSQLContainer postgres;

	@BeforeAll
	static void start() {
		postgres = OeaDatabases.freshMigrated();
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void runtimeRoleAppendsLinksWithDatabaseComputedValues() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app")) {
			byte[] forged = new byte[32];
			forged[0] = 7;
			long first = insertEvent(app, 999, forged);
			long second = insertEvent(app, 1000, forged);

			Link a = link(app, first);
			Link b = link(app, second);
			assertThat(b.chainSeq()).isEqualTo(a.chainSeq() + 1);
			assertThat(b.prevHash()).isEqualTo(a.recordHash());
			assertThat(a.recordHash()).hasSize(32).isNotEqualTo(forged);
			assertThat(a.hashVersion()).isEqualTo(1);
			Link previous = linkBySeq(app, a.chainSeq() - 1);
			assertThat(a.prevHash()).isEqualTo(previous.recordHash());
		}
	}

	@Test
	void runtimeRoleHasNoAccessToTheChainHead() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeQuery("SELECT * FROM audit.chain_head"))
				.withMessageContaining("permission denied");
		}
	}

	@Test
	void auditInsertsOutsideReadCommittedAreRejected() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app")) {
			app.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			app.setAutoCommit(false);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insertEvent(app, 1, new byte[32]))
				.withMessageContaining("READ COMMITTED");
			app.rollback();
		}
	}

	private static long insertEvent(Connection connection, long chainSeq, byte[] hash) throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement("""
				INSERT INTO audit.events (event_type, event_category, severity, outcome, source, actor_type,
				                          chain_seq, hash_version, prev_hash, record_hash)
				VALUES ('test.trigger', 'system', 'info', 'success', 'Job', 'system', ?, 9, ?, ?) RETURNING id""")) {
			insert.setLong(1, chainSeq);
			insert.setBytes(2, hash);
			insert.setBytes(3, hash);
			try (ResultSet rs = insert.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private static Link link(Connection connection, long id) throws SQLException {
		return query(connection, "SELECT chain_seq, hash_version, prev_hash, record_hash FROM audit.events WHERE id = " + id);
	}

	private static Link linkBySeq(Connection connection, long chainSeq) throws SQLException {
		return query(connection, """
				SELECT chain_seq, hash_version, prev_hash, record_hash FROM audit.events WHERE chain_seq = %d
				UNION ALL
				SELECT chain_seq, hash_version, prev_hash, record_hash FROM audit.retention_settings WHERE chain_seq = %d
				""".formatted(chainSeq, chainSeq));
	}

	private static Link query(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
			assertThat(rs.next()).isTrue();
			return new Link(rs.getLong(1), rs.getInt(2), rs.getBytes(3), rs.getBytes(4));
		}
	}

	record Link(long chainSeq, int hashVersion, byte[] prevHash, byte[] recordHash) {

		@Override
		public String toString() {
			return chainSeq + ":" + HexFormat.of().formatHex(recordHash);
		}

	}

}
