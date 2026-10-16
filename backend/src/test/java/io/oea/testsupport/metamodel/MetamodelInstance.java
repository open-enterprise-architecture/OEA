package io.oea.testsupport.metamodel;

import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * An OIDC test instance with a metamodel administrator: `dev-metamodel-admin` holds
 * `metamodel-admin` through the mapping of its realm group and logs in with TOTP (SoD-8);
 * `dev-viewer` holds `viewer`; `dev-no-role` is bound without role. Each logs in once, because the IdP accepts a TOTP code
 * only once.
 */
public final class MetamodelInstance implements AutoCloseable {

	private final OidcTestInstance instance;

	private final Session metamodelAdmin;

	private final Session noRole;

	private final Session viewer;

	/** Session cookie and CSRF token. */
	public record Session(String token, String csrfToken) {
	}

	private MetamodelInstance(OidcTestInstance instance, Session metamodelAdmin, Session noRole, Session viewer) {
		this.instance = instance;
		this.metamodelAdmin = metamodelAdmin;
		this.noRole = noRole;
		this.viewer = viewer;
	}

	public static MetamodelInstance start(String... arguments) throws Exception {
		OidcTestInstance instance = OidcTestInstance.start(arguments);
		instance.person("dev-metamodel-admin", "active");
		instance.person("dev-no-role", "active");
		instance.person("dev-viewer", "active");
		instance.mapping("oea-metamodel-admins", "metamodel-admin");
		instance.mapping("oea-viewers", "viewer");
		return new MetamodelInstance(instance, session(instance, instance.login("dev-metamodel-admin")),
				session(instance, instance.login("dev-no-role")), session(instance, instance.login("dev-viewer")));
	}

	public OidcTestInstance oidc() {
		return this.instance;
	}

	public Session metamodelAdmin() {
		return this.metamodelAdmin;
	}

	public Session noRole() {
		return this.noRole;
	}

	public Session viewer() {
		return this.viewer;
	}

	public HttpResponse<String> post(String path, String body, Session session) throws Exception {
		return this.instance.send(HttpRequest.newBuilder(URI.create(this.instance.url() + path))
			.header("Cookie", "__Host-OEA_SESSION=" + session.token())
			.header("X-XSRF-TOKEN", session.csrfToken())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	public HttpResponse<String> get(String path, Session session) throws Exception {
		return this.instance.get(path, session.token());
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
