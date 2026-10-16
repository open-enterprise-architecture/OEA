package io.oea.testsupport.auth;

import io.oea.app.security.Sessions;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.repository.SystemAdminAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sessions for integration tests, created through the real session service or stored
 * exactly as the service stores them. No mock authentication (ADR-036 §5).
 */
public final class TestPrincipals {

	private final Sessions sessions;

	private final SystemAdminAccountRepository accounts;

	private final JdbcTemplate jdbc;

	public TestPrincipals(Sessions sessions, SystemAdminAccountRepository accounts, JdbcTemplate jdbc) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.jdbc = jdbc;
	}

	/** Cookie value and CSRF token of a session. */
	public record TestSession(String token, String csrfToken, UUID sessionId) {
	}

	/** A session of a new local system admin account (as after a password login). */
	public TestSession systemAdmin() {
		SystemAdminAccount account = this.accounts
			.save(SystemAdminAccount.bootstrapped("admin-" + UUID.randomUUID().toString().substring(0, 8), Instant.now()));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(account, false, "127.0.0.1", "test");
		return new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
	}

	/** A session of an active person without any role (REQ-003). */
	public TestSession personWithoutRole() {
		Long personId = this.jdbc.queryForObject("""
				INSERT INTO persons (name, email, status) VALUES (?, ?, 'active') RETURNING id""", Long.class,
				"No Role", "no-role-" + UUID.randomUUID() + "@example.org");
		String token = randomToken();
		UUID sessionId = this.jdbc.queryForObject("""
				INSERT INTO sessions (token_hash, person_id, client_type, auth_method, auth_provider_id,
				                      authenticated_at, last_activity_at, absolute_expires_at)
				VALUES (?, ?, 'web-portal', 'password', NULL, now(), now(), now() + interval '12 hours')
				RETURNING id""", UUID.class, sha256(token), personId);
		return new TestSession(token, Sessions.csrfToken(token), sessionId);
	}

	/**
	 * A session of an active person who holds the role through an IdP group mapping and logged
	 * in with a second factor (stored as the OIDC login stores it).
	 */
	public TestSession personWithIdpRole(String roleId) {
		String suffix = UUID.randomUUID().toString();
		String issuer = "https://idp-" + suffix.substring(0, 8) + ".example.org/realms/test";
		UUID providerId = this.jdbc.queryForObject("""
				INSERT INTO auth_providers (name, issuer, client_id) VALUES (?, ?, 'oea') RETURNING id""", UUID.class,
				"Test IdP " + suffix.substring(0, 8), issuer);
		String group = UUID.randomUUID().toString();
		UUID mappingId = this.jdbc.queryForObject("""
				INSERT INTO idp_group_mappings (auth_provider_id, issuer, group_identifier, role_id)
				VALUES (?, ?, ?, ?) RETURNING id""", UUID.class, providerId, issuer, group, roleId);
		Long personId = this.jdbc.queryForObject("""
				INSERT INTO persons (name, email, status) VALUES (?, ?, 'active') RETURNING id""", Long.class,
				"Holder of " + roleId, roleId + "-" + suffix + "@example.org");
		String token = randomToken();
		// evaluation time in the future: no IdP re-evaluation during the test
		UUID sessionId = this.jdbc.queryForObject("""
				INSERT INTO sessions (token_hash, person_id, client_type, auth_method, auth_provider_id,
				                      second_factor_verified, authenticated_at, last_activity_at, absolute_expires_at,
				                      idp_claims_evaluated_at, idp_groups_claim_status, idp_group_identifiers)
				VALUES (?, ?, 'web-portal', 'oidc', ?, true, now(), now(), now() + interval '12 hours',
				        now() + interval '12 hours', 'present', ARRAY[?])
				RETURNING id""", UUID.class, sha256(token), personId, providerId, group);
		this.jdbc.update("INSERT INTO session_idp_roles (session_id, idp_group_mapping_id, role_id) VALUES (?, ?, ?)",
				sessionId, mappingId, roleId);
		return new TestSession(token, Sessions.csrfToken(token), sessionId);
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] sha256(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
