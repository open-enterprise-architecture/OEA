package io.oea.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Java serialization reproduces the trigger hashes (docs/audit-hash-chain.md). */
class ChainHasherTests {

	private static OeaDatabase postgres;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO audit.events (event_type, event_category, severity, outcome, occurred_at, source,
					    source_ip, request_id, actor_type, actor_admin_id, target_type, target_id, entity_id,
					    entity_name, details, agent_id, agent_label, agent_run, chain_seq, hash_version, prev_hash,
					    record_hash)
					VALUES ('test.all-types', 'change', 'high', 'success', '2026-10-15 10:11:12.123456+02', 'API',
					    '192.0.2.10', 'req-1', 'system-admin-account', 'A1B2C3D4-0000-4000-8000-0000000000FF',
					    'entity', '42', 42, 'Grüße "quoted"', '{"b": 1, "a": [true, null, "ü"]}', 'client-1',
					    'label', 'run-1', 0, 0, ''::bytea, ''::bytea)""");
			statement.executeUpdate("""
					INSERT INTO audit.events (event_type, event_category, severity, outcome, source, source_ip,
					    actor_type, actor_person_id, chain_seq, hash_version, prev_hash, record_hash)
					VALUES ('test.nulls', 'security', 'info', 'denied', 'UI', '2001:db8::1', 'person', 7,
					    0, 0, ''::bytea, ''::bytea)""");
			statement.executeUpdate("""
					INSERT INTO audit.event_changes (event_id, property_def_id, property_name, system_field,
					    value_protection, old_value_enc, new_value_enc, key_id, chain_seq, hash_version, prev_hash,
					    record_hash)
					SELECT id, 'b6f1c2e0-1111-4222-8333-444455556666', 'salary', false, 'encrypted',
					    '\\x00ff10'::bytea, '\\xdeadbeef'::bytea, 'key-1', 0, 0, ''::bytea, ''::bytea
					FROM audit.events WHERE event_type = 'test.all-types'""");
			statement.executeUpdate("""
					INSERT INTO audit.event_changes (event_id, property_name, system_field, old_value, new_value,
					    chain_seq, hash_version, prev_hash, record_hash)
					SELECT id, 'name', true, NULL, '', 0, 0, ''::bytea, ''::bytea
					FROM audit.events WHERE event_type = 'test.all-types'""");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void reproducesTheWorkedExampleOfTheSpecification() {
		List<byte[]> fields = Arrays.asList(ChainHasher.utf8("1"), ChainHasher.utf8("2555"),
				ChainHasher.utf8("2026-10-15T08:00:00.000000Z"), ChainHasher.utf8("2026-10-15T08:00:00.000000Z"), null,
				null);
		byte[] hash = ChainHasher.recordHash(1, 1, new byte[32], ChainTable.RETENTION_SETTINGS, fields);
		assertThat(HexFormat.of().formatHex(hash))
			.isEqualTo("643769ba104bab2f3320bdc1195390d0432a16618a21a475c34d506641b64e00");
	}

	@Test
	void reproducesTheTriggerHashOfEveryStoredLink() throws SQLException {
		int checked = 0;
		try (Connection auditor = OeaDatabases.asRole(postgres, "oea_auditor")) {
			for (ChainTable table : ChainTable.values()) {
				try (Statement statement = auditor.createStatement();
						ResultSet row = statement.executeQuery("SELECT * FROM " + table.qualifiedName())) {
					while (row.next()) {
						byte[] expected = ChainHasher.recordHash(row.getInt("hash_version"), row.getLong("chain_seq"),
								row.getBytes("prev_hash"), table, ChainHasher.fieldValues(table, row));
						assertThat(HexFormat.of().formatHex(expected)).as(table + " chain_seq " + row.getLong("chain_seq"))
							.isEqualTo(HexFormat.of().formatHex(row.getBytes("record_hash")));
						checked++;
					}
				}
			}
		}
		// retention seed + 2 events + 2 changes
		assertThat(checked).isEqualTo(5);
	}

}
