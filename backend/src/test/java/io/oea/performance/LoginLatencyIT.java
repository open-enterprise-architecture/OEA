package io.oea.performance;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.oea.core.service.PasswordHasher;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import io.oea.testsupport.containers.OeaKeycloak;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec platform/login-latency, US-008 AC1 and REQ-008 (design D2 of us-008-login-latency): the
 * 95th percentile of the server-side processing time of OIDC logins and password logins of persons
 * stays below 300 ms with 10,000 persons, 1,000 roles and 1,000 IdP group mappings. Every login is
 * measured by the difference of the timer {@code oea.login.processing} before and after it, so the
 * warm-up does not count.
 */
class LoginLatencyIT {

	private static final Logger LOG = LoggerFactory.getLogger(LoginLatencyIT.class);

	/** The target of REQ-008 in milliseconds. */
	private static final double TARGET_MILLIS = 300;

	private static final int WARM_UP = 5;

	private static final int MEASURED = 30;

	private static final String PASSWORD = "latency-password-123";

	private static OidcTestInstance instance;

	@BeforeAll
	static void seed() throws Exception {
		instance = OidcTestInstance.start("--oea.auth.person-password.enabled=true");
		instance.person("dev-architect", "active");
		instance.mapping("oea-enterprise-architects", "enterprise-architect");
		String provider = instance.providerId().toString();
		String issuer = OeaKeycloak.issuer();
		instance.update("INSERT INTO system_admin_accounts (login_name, status) VALUES ('latency-admin', 'active')");
		// 10,000 persons; the first 200 log in with a password
		instance.update("""
				INSERT INTO persons (name, email, status)
				SELECT 'Person ' || g, 'person-' || g || '@example.org', 'active' FROM generate_series(1, 10000) AS g""");
		// 1,000 roles with 3 permissions each
		instance.update("""
				INSERT INTO authorization_roles (id, name)
				SELECT 'latency-role-' || g, 'Latency role ' || g FROM generate_series(1, 1000) AS g""");
		instance.update("""
				INSERT INTO role_permissions (role_id, permission_id)
				SELECT 'latency-role-' || g, p.id FROM generate_series(1, 1000) AS g
				CROSS JOIN LATERAL (SELECT id FROM permissions WHERE NOT admin_class
				                    ORDER BY md5(id || g) LIMIT 3) AS p""");
		// 1,000 IdP group mappings of the realm issuer, for groups the test user does not have
		instance.update("""
				INSERT INTO idp_group_mappings (auth_provider_id, issuer, group_identifier, role_id)
				SELECT '%s', '%s', 'latency-group-' || g, 'latency-role-' || g FROM generate_series(1, 1000) AS g"""
			.formatted(provider, issuer));
		// 10,000 local role assignments
		instance.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_admin_id, valid_from)
				SELECT p.id, 'latency-role-' || (1 + (p.id % 1000)), a.id, now() - interval '1 day'
				FROM persons p CROSS JOIN (SELECT id FROM system_admin_accounts WHERE login_name = 'latency-admin') a
				WHERE p.email LIKE 'person-%@example.org'""");
		String hash = new PasswordHasher().hash(PASSWORD);
		instance.update("""
				WITH credentials AS (
				    INSERT INTO local_credentials (person_id, type)
				    SELECT id, 'password' FROM persons WHERE email LIKE 'person-%%@example.org' ORDER BY id LIMIT 200
				    RETURNING id)
				INSERT INTO password_credentials (credential_id, password_hash) SELECT id, '%s' FROM credentials"""
			.formatted(hash));
		instance.update("ANALYZE");
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void oidcLogins() {
		List<Double> times = measure("oidc", () -> {
			ApplicationLogin login = instance.login("dev-architect");
			assertThat(login.sessionToken()).as("OIDC login").isNotBlank();
		});
		assertBelowTarget("oidc", times);
	}

	@Test
	void passwordLoginsOfPersons() {
		int[] next = { 1 };
		List<Double> times = measure("password", () -> {
			String email = "person-" + (next[0]++) + "@example.org";
			HttpResponse<String> login = post("/api/v1/auth/local/login",
					JsonMapper.shared().createObjectNode().put("loginName", email).put("password", PASSWORD).toString());
			assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		});
		assertBelowTarget("password", times);
	}

	/** Runs the warm-up and the measured logins; returns the processing time of each measured login. */
	private static List<Double> measure(String method, Runnable login) {
		for (int i = 0; i < WARM_UP; i++) {
			login.run();
		}
		List<Double> times = new ArrayList<>();
		for (int i = 0; i < MEASURED; i++) {
			double before = total(method);
			login.run();
			times.add(total(method) - before);
		}
		return times;
	}

	private static void assertBelowTarget(String method, List<Double> times) {
		List<Double> sorted = new ArrayList<>(times);
		Collections.sort(sorted);
		double median = sorted.get(sorted.size() / 2);
		double p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
		LOG.info("Login latency {}: median {} ms, p95 {} ms, max {} ms over {} logins", method, round(median),
				round(p95), round(sorted.getLast()), sorted.size());
		assertThat(p95).as("p95 of %s logins in ms", method).isLessThan(TARGET_MILLIS);
	}

	private static double total(String method) {
		Supplier<Timer> timer = () -> instance.application()
			.getBean(MeterRegistry.class)
			.find("oea.login.processing")
			.tag("method", method)
			.tag("outcome", "success")
			.timer();
		return (timer.get() != null) ? timer.get().totalTime(TimeUnit.MILLISECONDS) : 0;
	}

	private static HttpResponse<String> post(String path, String body) {
		try {
			return instance.send(HttpRequest.newBuilder(URI.create(instance.url() + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static long round(double value) {
		return Math.round(value);
	}

}
