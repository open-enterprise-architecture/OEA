package io.oea.config.secrets;

import io.oea.config.startup.ConfigurationGuards;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * {@code oea secrets reencrypt} (REQ-188 AC2, design D8): re-encrypts every stored secret whose
 * key ID is not the current key with the current key, in one transaction, with the runtime
 * credentials ({@code spring.datasource.*}). The old key must be configured in
 * {@code oea.security.kek.previous}. Exit codes: 0 done, 1 a value could not be decrypted,
 * 2 usage or connection error.
 */
public final class ReencryptCommand {

	/**
	 * Every encrypted column. The additional authenticated data of a value is
	 * {@code <table>.<column>:<row id>}.
	 */
	static final List<Target> TARGETS = List.of(
			new Target("totp_credentials", "credential_id", "secret_encrypted", "key_id"),
			new Target("auth_providers", "id", "client_secret_encrypted", "client_secret_key_id"),
			new Target("sessions", "id", "idp_refresh_token_encrypted", "idp_refresh_token_key_id"),
			new Target("oidc_login_states", "encode(state_hash, 'hex')", "code_verifier_encrypted",
					"code_verifier_key_id"));

	record Target(String table, String idColumn, String valueColumn, String keyColumn) {

		String context(String id) {
			return this.table + "." + this.valueColumn + ":" + id;
		}

	}

	private ReencryptCommand() {
	}

	public static int run(String... args) {
		return run(System.out, args);
	}

	public static int run(PrintStream out, String... args) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ReencryptConfiguration.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.logStartupInfo(false)
			.properties(ConfigurationGuards.MODE_PROPERTY + "=secrets-reencrypt")
			.run(args)) {
			Environment environment = context.getEnvironment();
			String url = environment.getProperty("spring.datasource.url");
			if (url == null || url.isBlank()) {
				out.println("ERROR spring.datasource.url is not set");
				return 2;
			}
			KeyRing keys = context.getBean(KeyRing.class);
			SecretCipher cipher = context.getBean(SecretCipher.class);
			try (Connection connection = DriverManager.getConnection(url,
					environment.getProperty("spring.datasource.username"),
					environment.getProperty("spring.datasource.password"))) {
				connection.setAutoCommit(false);
				int count = 0;
				for (Target target : TARGETS) {
					count += reencrypt(connection, cipher, target);
				}
				connection.commit();
				out.println("OK " + count + " secret(s) re-encrypted with key " + keys.currentKeyId());
				return 0;
			}
			catch (IllegalStateException ex) {
				out.println("ERROR " + ex.getMessage() + "; nothing was changed");
				return 1;
			}
		}
		catch (Exception ex) {
			out.println("ERROR " + ex.getMessage());
			return 2;
		}
	}

	private static int reencrypt(Connection connection, SecretCipher cipher, Target target) throws SQLException {
		List<String[]> rows = new ArrayList<>();
		String select = "SELECT %s::text, %s, %s FROM %s WHERE %s IS NOT NULL FOR UPDATE"
			.formatted(target.idColumn(), target.valueColumn(), target.keyColumn(), target.table(), target.valueColumn());
		try (PreparedStatement statement = connection.prepareStatement(select);
				ResultSet rs = statement.executeQuery()) {
			while (rs.next()) {
				rows.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3) });
			}
		}
		int count = 0;
		String update = "UPDATE %s SET %s = ?, %s = ? WHERE %s::text = ?".formatted(target.table(),
				target.valueColumn(), target.keyColumn(), target.idColumn());
		try (PreparedStatement statement = connection.prepareStatement(update)) {
			for (String[] row : rows) {
				SecretCipher.Encrypted stored = new SecretCipher.Encrypted(row[1], row[2]);
				if (!cipher.needsReencryption(stored)) {
					continue;
				}
				String context = target.context(row[0]);
				SecretCipher.Encrypted renewed = cipher.encrypt(cipher.decrypt(stored, context), context);
				statement.setString(1, renewed.ciphertext());
				statement.setString(2, renewed.keyId());
				statement.setString(3, row[0]);
				statement.addBatch();
				count++;
			}
			statement.executeBatch();
		}
		return count;
	}

	@Configuration(proxyBeanMethods = false)
	@Import(SecretsConfiguration.class)
	static class ReencryptConfiguration {

	}

}
