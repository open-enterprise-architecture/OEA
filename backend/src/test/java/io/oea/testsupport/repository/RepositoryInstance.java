package io.oea.testsupport.repository;

import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * An OIDC test instance with the starter package imported: `dev-architect` holds
 * `enterprise-architect` (entity:write), `dev-viewer` holds `viewer`. Besides the starter
 * types, the metatype "Checked Type" maps the mandatory property "Version", the property
 * "Owner" with necessity warning and the role-restricted property "Secret".
 */
public final class RepositoryInstance implements AutoCloseable {

	private final OidcTestInstance instance;

	private final Session architect;

	private final Session viewer;

	/** Session cookie and CSRF token. */
	public record Session(String token, String csrfToken) {
	}

	private RepositoryInstance(OidcTestInstance instance, Session architect, Session viewer) {
		this.instance = instance;
		this.architect = architect;
		this.viewer = viewer;
	}

	public static RepositoryInstance start() throws Exception {
		return start(new String[0]);
	}

	/** As {@link #start()} with extra arguments for the application. */
	public static RepositoryInstance start(String... arguments) throws Exception {
		OidcTestInstance instance = OidcTestInstance.start(withTestRateLimits(arguments));
		instance.person("dev-metamodel-admin", "active");
		instance.person("dev-architect", "active");
		instance.person("dev-viewer", "active");
		instance.mapping("oea-metamodel-admins", "metamodel-admin");
		instance.mapping("oea-enterprise-architects", "enterprise-architect");
		instance.mapping("oea-viewers", "viewer");
		Session metamodelAdmin = session(instance, instance.login("dev-metamodel-admin"));
		RepositoryInstance repository = new RepositoryInstance(instance,
				session(instance, instance.login("dev-architect")), session(instance, instance.login("dev-viewer")));
		HttpResponse<String> imported = repository.post(
				"/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", "", metamodelAdmin, Map.of());
		if (imported.statusCode() != 200) {
			throw new IllegalStateException("starter import failed: " + imported.body());
		}
		instance.update("""
				WITH type AS (
				    INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Checked Type')
				    RETURNING id),
				version AS (
				    INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Version', 'version', 'string')
				    RETURNING id),
				owner AS (
				    INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Owner', 'owner', 'string')
				    RETURNING id),
				secret AS (
				    INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				    VALUES ('Secret', 'secret', 'string', 'role-restricted') RETURNING id)
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, sort_order)
				SELECT type.id, version.id, 'mandatory', DATE '2026-01-01', 0 FROM type, version
				UNION ALL SELECT type.id, owner.id, 'warning', DATE '2026-01-01', 1 FROM type, owner
				UNION ALL SELECT type.id, secret.id, 'optional', DATE '2026-01-01', 2 FROM type, secret""");
		return repository;
	}

	/**
	 * The features drive one shared principal through many requests and batches within a
	 * minute; unless a feature sets its own rate limits, they are raised far above the defaults
	 * of REQ-186 (the default limits are tested by the request-limits feature and RateLimitTests).
	 */
	private static String[] withTestRateLimits(String... arguments) {
		if (Arrays.stream(arguments).anyMatch((argument) -> argument.startsWith("--oea.web.rate-limit."))) {
			return arguments;
		}
		List<String> raised = new ArrayList<>(List.of(arguments));
		raised.add("--oea.web.rate-limit.requests-per-minute=100000");
		raised.add("--oea.web.rate-limit.entity-batch-per-minute=100000");
		return raised.toArray(String[]::new);
	}

	public OidcTestInstance oidc() {
		return this.instance;
	}

	public Session architect() {
		return this.architect;
	}

	public Session viewer() {
		return this.viewer;
	}

	/** Logs in a user of the development realm as an active person; returns the session. */
	public Session login(String username) throws Exception {
		this.instance.person(username, "active");
		return session(this.instance, this.instance.login(username));
	}

	public HttpResponse<String> post(String path, String body, Session session, Map<String, String> headers)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(this.instance.url() + path))
			.header("Cookie", "__Host-OEA_SESSION=" + session.token())
			.header("X-XSRF-TOKEN", session.csrfToken())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		headers.forEach(request::header);
		return this.instance.send(request);
	}

	@Override
	public void close() {
		this.instance.close();
	}

	private static Session session(OidcTestInstance instance, ApplicationLogin login) throws Exception {
		if (login.sessionToken() == null) {
			throw new IllegalStateException("login failed: " + login.location());
		}
		String csrf = JsonMapper.shared()
			.readTree(instance.get("/api/v1/me", login.sessionToken()).body())
			.path("csrfToken")
			.asString();
		return new Session(login.sessionToken(), csrf);
	}

}
