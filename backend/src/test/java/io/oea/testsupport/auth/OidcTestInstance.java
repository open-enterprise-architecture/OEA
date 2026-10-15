package io.oea.testsupport.auth;

import io.oea.config.secrets.KekProperties;
import io.oea.config.secrets.KeyRing;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaKeycloak;
import io.oea.testsupport.containers.OeaTestKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The running application on a fresh database with the development realm stored as provider
 * (profile {@code dev}: loopback issuer), for OIDC login tests (ADR-036 §5). Persons,
 * bindings and mappings are written directly to the database; logins go through the real
 * flow of the application and the IdP.
 */
public final class OidcTestInstance implements AutoCloseable {

	/** Public URL of the portal as registered as redirect URI in the realm. */
	public static final String PUBLIC_URL = "http://localhost:5173";

	public static final String CLIENT = "oea-portal";

	private final PostgreSQLContainer postgres;

	private final ConfigurableApplicationContext application;

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private final UUID providerId;

	private OidcTestInstance(PostgreSQLContainer postgres, ConfigurableApplicationContext application,
			UUID providerId) {
		this.postgres = postgres;
		this.application = application;
		this.providerId = providerId;
	}

	/** Starts the instance with the development realm as enabled provider; extra arguments for the application. */
	public static OidcTestInstance start(String... arguments) throws Exception {
		PostgreSQLContainer postgres = OeaDatabases.freshMigrated();
		UUID providerId = insertProvider(postgres, "Keycloak (dev realm)", OeaKeycloak.issuer(), true);
		Path tokenFile = Files.createTempDirectory("oea-oidc").resolve("setup-token");
		String[] args = new String[arguments.length + 2];
		args[0] = "--spring.profiles.active=dev";
		args[1] = "--oea.web.public-url=" + PUBLIC_URL;
		System.arraycopy(arguments, 0, args, 2, arguments.length);
		ConfigurableApplicationContext application = OeaApplications.start(postgres, tokenFile,
				WebApplicationType.SERVLET, args);
		return new OidcTestInstance(postgres, application, providerId);
	}

	public UUID providerId() {
		return this.providerId;
	}

	public PostgreSQLContainer postgres() {
		return this.postgres;
	}

	public ConfigurableApplicationContext application() {
		return this.application;
	}

	public String url() {
		return "http://localhost:" + this.application.getEnvironment().getProperty("local.server.port");
	}

	/** Stores a further provider (client {@code oea-portal}), returns its ID. */
	public UUID provider(String name, String issuer, boolean active) throws Exception {
		return insertProvider(this.postgres, name, issuer, active);
	}

	/** Creates a person with the status and binds it to the realm user; returns the person ID. */
	public long person(String username, String status) throws SQLException {
		long personId = Long.parseLong(single("""
				INSERT INTO persons (name, email, status) VALUES ('%s', '%s@oea.localhost', '%s') RETURNING id::text"""
			.formatted(username, username, status)));
		update("""
				INSERT INTO person_identities (person_id, auth_provider_id, issuer, subject, link_method)
				VALUES (%d, '%s', '%s', '%s', 'admin-entered')""".formatted(personId, this.providerId,
				OeaKeycloak.issuer(), DevRealm.subject(username)));
		return personId;
	}

	/** Maps a realm group to a role for the development realm; returns the mapping ID. */
	public UUID mapping(String groupName, String roleId) throws SQLException {
		return mapping(this.providerId, OeaKeycloak.issuer(), DevRealm.groupId(groupName), roleId);
	}

	public UUID mapping(UUID providerId, String issuer, String groupIdentifier, String roleId) throws SQLException {
		return UUID.fromString(single("""
				INSERT INTO idp_group_mappings (auth_provider_id, issuer, group_identifier, role_id)
				VALUES ('%s', '%s', '%s', '%s') RETURNING id::text""".formatted(providerId, issuer, groupIdentifier,
				roleId)));
	}

	public OidcLogin.ApplicationLogin login(String username) {
		return login(username, UnaryOperator.identity());
	}

	public OidcLogin.ApplicationLogin login(String username, UnaryOperator<URI> authorizationRequest) {
		return OidcLogin.loginThroughApplication(url(), PUBLIC_URL, this.providerId, username, authorizationRequest);
	}

	public HttpResponse<String> get(String path, String sessionToken) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url() + path)).GET();
		if (sessionToken != null) {
			request.header("Cookie", "__Host-OEA_SESSION=" + sessionToken);
		}
		return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	public HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** Number of audit events of the type whose details contain all given fragments. */
	public int audit(String eventType, String... detailFragments) throws SQLException {
		StringBuilder sql = new StringBuilder("SELECT count(*)::text FROM audit.events WHERE event_type = '")
			.append(eventType)
			.append("'");
		for (String fragment : detailFragments) {
			sql.append(" AND strpos(details::text, '").append(fragment.replace("'", "''")).append("') > 0");
		}
		return Integer.parseInt(single(sql.toString()));
	}

	public String single(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	public List<String> column(String sql) throws SQLException {
		List<String> values = new java.util.ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				values.add(rs.getString(1));
			}
		}
		return values;
	}

	public void update(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	@Override
	public void close() {
		this.application.close();
		this.postgres.stop();
	}

	private static UUID insertProvider(PostgreSQLContainer postgres, String name, String issuer, boolean active)
			throws Exception {
		UUID id = UUID.randomUUID();
		SecretCipher cipher = new SecretCipher(
				KeyRing.load(new KekProperties(Path.of(OeaTestKeys.sharedKeyFile()), List.of())));
		SecretCipher.Encrypted secret = cipher.encrypt(DevRealm.clientSecret(CLIENT), AuthProvider.encryptionContext(id));
		try (Connection su = OeaDatabases.asSuperuser(postgres); PreparedStatement insert = su.prepareStatement("""
				INSERT INTO auth_providers (id, name, issuer, client_id, client_secret_encrypted, client_secret_key_id,
				    active) VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
			insert.setObject(1, id);
			insert.setString(2, name);
			insert.setString(3, issuer);
			insert.setString(4, CLIENT);
			insert.setString(5, secret.ciphertext());
			insert.setString(6, secret.keyId());
			insert.setBoolean(7, active);
			insert.executeUpdate();
		}
		return id;
	}

}
