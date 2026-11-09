package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.audit.verify.AuditVerifyCommand;
import io.oea.config.secrets.KekProperties;
import io.oea.config.secrets.KeyRing;
import io.oea.config.secrets.ReencryptCommand;
import io.oea.config.secrets.SecretCipher;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Spec platform/audit-value-encryption (REQ-160 AC4, ADR-033 §3; design D4 of
 * us-126-role-restricted-enforcement) and migration db/migration-audit/V5__audit_value_keys.sql,
 * on an own database with the runtime role.
 */
class AuditValueEncryptionTests {

	private static OeaDatabase postgres;

	private static Path key;

	private static JdbcTemplate jdbc;

	private static UUID cost;

	private static UUID vendor;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		key = OeaTestKeys.newKeyFile();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "oea_app",
				OeaPostgres.ROLE_PASSWORDS.get("oea_app")));
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
					VALUES ('Investment Cost', 'investment_cost', 'integer', 'role-restricted') RETURNING id""")) {
				rs.next();
				cost = rs.getObject(1, UUID.class);
			}
			try (ResultSet rs = statement.executeQuery("""
					INSERT INTO property_definitions (name, sql_column_name, data_type)
					VALUES ('Vendor', 'vendor', 'string') RETURNING id""")) {
				rs.next();
				vendor = rs.getObject(1, UUID.class);
			}
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	// Migration V5

	@Test
	void valueKeysTableOwnerAndGrants() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT tableowner FROM pg_tables WHERE schemaname = 'audit' AND tablename = 'value_keys'")) {
				assertThat(rs.next()).isTrue();
				assertThat(rs.getString(1)).isEqualTo("oea_owner");
			}
			List<String> table = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT privilege_type FROM information_schema.role_table_grants
					WHERE table_schema = 'audit' AND table_name = 'value_keys' AND grantee = 'oea_app' ORDER BY 1""")) {
				while (rs.next()) {
					table.add(rs.getString(1));
				}
			}
			assertThat(table).containsExactly("INSERT", "SELECT");
			List<String> columns = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT column_name FROM information_schema.column_privileges
					WHERE table_schema = 'audit' AND table_name = 'value_keys' AND grantee = 'oea_app'
					  AND privilege_type = 'UPDATE' ORDER BY 1""")) {
				while (rs.next()) {
					columns.add(rs.getString(1));
				}
			}
			assertThat(columns).containsExactly("kek_id", "wrapped_key");
			// not chained: no trigger on the table
			try (ResultSet rs = statement.executeQuery(
					"SELECT count(*) FROM pg_trigger WHERE tgrelid = 'audit.value_keys'::regclass AND NOT tgisinternal")) {
				rs.next();
				assertThat(rs.getInt(1)).isZero();
			}
		}
		assertThatExceptionOfType(Exception.class)
			.isThrownBy(() -> jdbc.update("DELETE FROM audit.value_keys"))
			.havingRootCause()
			.withMessageContaining("permission denied");
	}

	// Requirement: Restricted values encrypted in the audit trail

	@Test
	void restrictedValueEncryptedPublicValuePlain() {
		AuditValueCipher cipher = cipher(key);
		AuditWriter writer = new AuditWriter(jdbc, cipher);
		AppendedLink link = writer.append(event(), new AuditEvent.EntityTarget(42, "CRM System"),
				new AuditEvent.Agent(null, null), List.of(new AuditEvent.Change(cost, "Investment Cost", false, "100", "120"),
						new AuditEvent.Change(vendor, "Vendor", false, "SAP", "Oracle"),
						new AuditEvent.Change(null, "name", true, "CRM", "CRM System")));
		Map<String, Object> restricted = change(link.id(), "Investment Cost");
		assertThat(restricted).containsEntry("value_protection", "encrypted")
			.containsEntry("old_value", null)
			.containsEntry("new_value", null);
		String keyId = (String) restricted.get("key_id");
		assertThat(keyId).startsWith("avk-");
		assertThat(cipher.decrypt(keyId, (byte[]) restricted.get("old_value_enc"),
				AuditValueCipher.context(link.id(), cost, true))).isEqualTo("100");
		assertThat(cipher.decrypt(keyId, (byte[]) restricted.get("new_value_enc"),
				AuditValueCipher.context(link.id(), cost, false))).isEqualTo("120");
		// another place of the ciphertext does not decrypt
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> cipher.decrypt(keyId,
				(byte[]) restricted.get("new_value_enc"), AuditValueCipher.context(link.id() + 1, cost, false)));
		assertThat(change(link.id(), "Vendor")).containsEntry("value_protection", "plain")
			.containsEntry("old_value", "SAP")
			.containsEntry("new_value", "Oracle")
			.containsEntry("key_id", null);
		assertThat(change(link.id(), "name")).containsEntry("value_protection", "plain");
	}

	@Test
	void emptyOldValueStaysEmptyAndTheKeyIsReused() {
		AuditValueCipher cipher = cipher(key);
		AuditWriter writer = new AuditWriter(jdbc, cipher);
		long first = writer.append(event(), new AuditEvent.EntityTarget(43, "ERP"), new AuditEvent.Agent(null, null),
				List.of(new AuditEvent.Change(cost, "Investment Cost", false, null, "250"))).id();
		long second = writer.append(event(), new AuditEvent.EntityTarget(43, "ERP"), new AuditEvent.Agent(null, null),
				List.of(new AuditEvent.Change(cost, "Investment Cost", false, "250", null))).id();
		assertThat(change(first, "Investment Cost").get("old_value_enc")).isNull();
		assertThat(change(second, "Investment Cost").get("new_value_enc")).isNull();
		assertThat(change(first, "Investment Cost").get("key_id")).isEqualTo(change(second, "Investment Cost").get("key_id"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.value_keys", Integer.class)).isEqualTo(1);
		// the stored key is wrapped, never the plain data key
		String wrapped = jdbc.queryForObject("SELECT wrapped_key FROM audit.value_keys", String.class);
		assertThat(wrapped).isNotBlank();
		assertThat(jdbc.queryForObject("SELECT kek_id FROM audit.value_keys", String.class))
			.isEqualTo(KeyRing.keyId(KeyRing.readKey(key)));
	}

	@Test
	void chainStaysVerifiable() {
		new AuditWriter(jdbc, cipher(key)).append(event(), new AuditEvent.EntityTarget(44, "Intranet"),
				new AuditEvent.Agent(null, null), List.of(new AuditEvent.Change(cost, "Investment Cost", false, "1", "2")));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int exit = AuditVerifyCommand.run(new PrintStream(output, true, StandardCharsets.UTF_8),
				"--oea.audit-verify.url=" + postgres.getJdbcUrl(),
				"--oea.audit-verify.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_auditor"));
		assertThat(output.toString(StandardCharsets.UTF_8)).startsWith("OK ");
		assertThat(exit).isZero();
	}

	// Requirement: Audit value keys

	@Test
	void rotationOfTheKeyEncryptionKey() {
		Path oldKey = OeaTestKeys.newKeyFile();
		Path newKey = OeaTestKeys.newKeyFile();
		OeaDatabase own = OeaDatabases.freshMigrated();
		try {
			JdbcTemplate ownJdbc = new JdbcTemplate(new DriverManagerDataSource(own.getJdbcUrl(), "oea_app",
					OeaPostgres.ROLE_PASSWORDS.get("oea_app")));
			UUID property = ownJdbc.queryForObject("""
					INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
					VALUES ('Budget', 'budget', 'integer', 'role-restricted') RETURNING id""", UUID.class);
			AuditValueCipher before = cipher(oldKey, ownJdbc);
			long event = new AuditWriter(ownJdbc, before).append(event(), new AuditEvent.EntityTarget(1, "CRM"),
					new AuditEvent.Agent(null, null), List.of(new AuditEvent.Change(property, "Budget", false, "7", "8"))).id();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			int exit = ReencryptCommand.run(new PrintStream(output, true, StandardCharsets.UTF_8),
					"--spring.datasource.url=" + own.getJdbcUrl(), "--spring.datasource.username=oea_app",
					"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
					"--oea.security.kek.current=" + newKey, "--oea.security.kek.previous=" + oldKey);
			assertThat(output.toString(StandardCharsets.UTF_8)).startsWith("OK 1 secret(s) re-encrypted");
			assertThat(exit).isZero();
			assertThat(ownJdbc.queryForObject("SELECT kek_id FROM audit.value_keys", String.class))
				.isEqualTo(KeyRing.keyId(KeyRing.readKey(newKey)));
			// only the new key-encryption key: the values still decrypt
			AuditValueCipher after = cipher(newKey, ownJdbc);
			Map<String, Object> row = ownJdbc.queryForMap(
					"SELECT key_id, new_value_enc FROM audit.event_changes WHERE event_id = ?", event);
			assertThat(after.decrypt((String) row.get("key_id"), (byte[]) row.get("new_value_enc"),
					AuditValueCipher.context(event, property, false))).isEqualTo("8");
		}
		finally {
			own.stop();
		}
	}

	private static AuditValueCipher cipher(Path kek) {
		return cipher(kek, jdbc);
	}

	private static AuditValueCipher cipher(Path kek, JdbcTemplate template) {
		return new AuditValueCipher(template, new SecretCipher(KeyRing.load(new KekProperties(kek, List.of()))));
	}

	private static AuditEvent event() {
		return new AuditEvent("entity.updated", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, AuditEvent.Actor.system(), "entity", "42", Map.of());
	}

	private static Map<String, Object> change(long eventId, String property) {
		return jdbc.queryForMap("""
				SELECT value_protection, old_value, new_value, old_value_enc, new_value_enc, key_id
				FROM audit.event_changes WHERE event_id = ? AND property_name = ?""", eventId, property);
	}

}
