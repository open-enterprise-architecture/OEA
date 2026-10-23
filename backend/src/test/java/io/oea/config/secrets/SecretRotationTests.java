package io.oea.config.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Specs platform/secret-storage "Rotation" and "Database dump" on an own database. */
class SecretRotationTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String PASSWORD = "correct horse battery staple";

	private static final String CLIENT_SECRET = "client-secret-" + UUID.randomUUID();

	private final HttpClient http = HttpClient.newHttpClient();

	private ConfigurableApplicationContext application;

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void rotationKeepsSecretsUsableAndDumpsHoldNoPlaintext() throws Exception {
		Path oldKey = OeaTestKeys.newKeyFile();
		Path newKey = OeaTestKeys.newKeyFile();
		String oldKeyId = KeyRing.keyId(KeyRing.readKey(oldKey));
		String newKeyId = KeyRing.keyId(KeyRing.readKey(newKey));

		// a TOTP credential and a provider client secret, both encrypted with the old key
		start(oldKey);
		String totpSecret;
		long usedStep;
		try {
			Path tokenFile = Path.of(this.application.getEnvironment().getProperty("oea.bootstrap.setup-token-file"));
			assertThat(post("/api/v1/setup", JsonMapper.shared()
				.createObjectNode()
				.put("setupToken", Files.readString(tokenFile).strip())
				.put("loginName", "admin")
				.put("password", PASSWORD)
				.put("instanceSlug", "acme-corp")
				.toString(), null).statusCode()).isEqualTo(201);
			HttpResponse<String> login = login();
			String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
			String csrf = json(login).path("csrfToken").asString();
			JsonNode enrollment = json(post("/api/v1/me/second-factors/totp", "{}", new String[] { cookie, csrf }));
			totpSecret = enrollment.path("secret").asString();
			usedStep = Instant.now().getEpochSecond() / 30;
			assertThat(post("/api/v1/me/second-factors/totp/" + enrollment.path("credentialId").asString() + "/confirm",
					"{\"code\":\"" + TotpAlgorithm.code(totpSecret, usedStep) + "\"}", new String[] { cookie, csrf })
				.statusCode()).isEqualTo(200);
		}
		finally {
			this.application.close();
		}
		insertProvider(oldKey);
		superuser("UPDATE system_settings SET setup_phase_ended_at = now(), "
				+ "setup_phase_ended_by_action = 'idp-group-mapping'");
		assertThat(keyIds()).containsOnly(oldKeyId).hasSize(2);

		// rotation: the new key becomes current, the old one stays readable for the command
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int exit = ReencryptCommand.run(new PrintStream(output, true, StandardCharsets.UTF_8),
				"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=oea_app",
				"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
				"--oea.security.kek.current=" + newKey, "--oea.security.kek.previous=" + oldKey);
		assertThat(output.toString(StandardCharsets.UTF_8)).startsWith("OK 2 secret(s) re-encrypted with key " + newKeyId);
		assertThat(exit).isZero();
		assertThat(keyIds()).containsOnly(newKeyId).hasSize(2);

		// the application with only the new key: TOTP login works
		start(newKey);
		try {
			HttpResponse<String> first = login();
			assertThat(first.statusCode()).isEqualTo(401);
			long step = Math.max(Instant.now().getEpochSecond() / 30, usedStep + 1);
			HttpResponse<String> second = post("/api/v1/auth/local/login/totp",
					"{\"loginTransaction\":\"" + json(first).path("loginTransaction").asString() + "\",\"code\":\""
							+ TotpAlgorithm.code(totpSecret, step) + "\"}",
					null);
			assertThat(second.statusCode()).isEqualTo(201);
		}
		finally {
			this.application.close();
		}

		// a dump contains neither secret in plaintext, Base32 or Base64
		var dump = postgres.execInContainer("pg_dump", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName());
		assertThat(dump.getExitCode()).as(dump.getStderr()).isZero();
		Base64.Encoder base64 = Base64.getEncoder();
		List<String> forms = List.of(totpSecret, base64.encodeToString(decodeBase32(totpSecret)),
				base64.encodeToString(totpSecret.getBytes(StandardCharsets.US_ASCII)), CLIENT_SECRET,
				base64.encodeToString(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8)));
		assertThat(dump.getStdout()).contains("totp_credentials");
		for (String form : forms) {
			assertThat(dump.getStdout().contains(form)).as("dump contains a secret").isFalse();
		}
	}

	private void start(Path key) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-rotation").resolve("setup-token");
		this.application = OeaApplications.start(postgres, tokenFile, WebApplicationType.SERVLET,
				"--oea.security.kek.current=" + key, "--oea.web.public-url=https://oea.example.org");
	}

	private HttpResponse<String> login() throws Exception {
		return post("/api/v1/auth/local/login",
				"{\"loginName\":\"admin\",\"password\":\"" + PASSWORD + "\"}", null);
	}

	private HttpResponse<String> post(String path, String body, String[] session) throws Exception {
		HttpRequest.Builder request = HttpRequest
			.newBuilder(URI.create("http://localhost:"
					+ this.application.getEnvironment().getProperty("local.server.port") + path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) {
			request.header("Cookie", session[0]).header("X-XSRF-TOKEN", session[1]);
		}
		return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static void insertProvider(Path key) throws Exception {
		SecretCipher cipher = new SecretCipher(KeyRing.load(new KekProperties(key, List.of())));
		UUID id = UUID.randomUUID();
		SecretCipher.Encrypted encrypted = cipher.encrypt(CLIENT_SECRET,
				"auth_providers.client_secret_encrypted:" + id);
		try (Connection su = OeaDatabases.asSuperuser(postgres); PreparedStatement insert = su.prepareStatement("""
				INSERT INTO auth_providers (id, name, issuer, client_id, client_secret_encrypted, client_secret_key_id)
				VALUES (?, 'Keycloak', 'https://idp.example.org/realms/oea', 'oea', ?, ?)""")) {
			insert.setObject(1, id);
			insert.setString(2, encrypted.ciphertext());
			insert.setString(3, encrypted.keyId());
			insert.executeUpdate();
		}
	}

	private static List<String> keyIds() throws Exception {
		List<String> ids = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT key_id FROM totp_credentials UNION ALL "
						+ "SELECT client_secret_key_id FROM auth_providers")) {
			while (rs.next()) {
				ids.add(rs.getString(1));
			}
		}
		return ids;
	}

	private static void superuser(String sql) throws Exception {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	private static JsonNode json(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

	private static byte[] decodeBase32(String value) {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : value.replace("=", "").toCharArray()) {
			buffer = (buffer << 5) | alphabet.indexOf(c);
			bits += 5;
			if (bits >= 8) {
				bytes.write((buffer >> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return bytes.toByteArray();
	}

}
