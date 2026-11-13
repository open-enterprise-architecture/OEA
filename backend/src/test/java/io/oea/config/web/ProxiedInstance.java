package io.oea.config.web;

import io.oea.app.bootstrapping.BootstrapCommand;
import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP requests against a running instance, so that they pass Tomcat's
 * {@code RemoteIpValve} (design D3 of s11-review-hardening). The test client connects from
 * {@code 127.0.0.1} and plays the reverse proxy by sending {@code X-Forwarded-*} headers.
 */
final class ProxiedInstance {

	static final String ADMIN = "admin";

	static final String PASSWORD = "correct horse battery staple";

	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private final int port;

	private ProxiedInstance(int port) {
		this.port = port;
	}

	static ProxiedInstance on(int port) {
		return new ProxiedInstance(port);
	}

	static void register(DynamicPropertyRegistry registry, OeaDatabase postgres, Path tokenFile) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	/** Bootstrapped instance with the local admin, and no throttle state left from other tests. */
	static void prepare(Bootstrapping bootstrapping, JdbcTemplate jdbc, Path tokenFile) {
		jdbc.update("DELETE FROM auth_throttle");
		if (bootstrapping.setupRequired()) {
			try {
				bootstrapping.bootstrap(new BootstrapCommand(Files.readString(tokenFile).strip(), ADMIN, PASSWORD,
						"acme-corp"));
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	/** Local login whose client address the proxy forwards; {@code null} sends no header. */
	HttpResponse<String> login(String loginName, String password, String forwardedFor) {
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("loginName", loginName)
			.put("password", password)
			.toString();
		HttpRequest.Builder request = request("/api/v1/auth/local/login").header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (forwardedFor != null) {
			request.header("X-Forwarded-For", forwardedFor);
		}
		return send(request);
	}

	HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path));
	}

	static HttpResponse<String> send(HttpRequest.Builder request) {
		try {
			return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	static Path tokenFile() {
		try {
			return Files.createTempDirectory("oea-proxy").resolve("setup-token-" + UUID.randomUUID());
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
