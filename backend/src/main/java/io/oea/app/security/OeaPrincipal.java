package io.oea.app.security;

import java.time.Instant;
import java.util.UUID;

/**
 * The authenticated principal of a request: who it is and which session it uses.
 * Permissions are never part of it; they are resolved per request (ADR-032 §7).
 *
 * @param type principal type
 * @param systemAdminAccountId account of a system admin principal
 * @param personId person of a person principal
 * @param displayName name shown to the principal itself
 * @param sessionId current session
 * @param secondFactorVerified the session was confirmed with a second factor
 * @param authenticatedAt last (re-)authentication of the session (REQ-172); for a service account the
 * issue time of its access token
 * @param requiredAction action the session must complete first, or null
 * @param clientId client ID of a service account principal (its audit agent ID), null otherwise
 */
public record OeaPrincipal(Type type, UUID systemAdminAccountId, Long personId, String displayName, UUID sessionId,
		boolean secondFactorVerified, Instant authenticatedAt, String requiredAction, UUID clientId) {

	/** Action the session must complete first; {@code change-password} after a temporary password (REQ-173). */
	public static final String CHANGE_PASSWORD = "change-password";

	/** Action the session must complete first while the enforcement of a second factor is on (US-031 AC2). */
	public static final String ENROLL_SECOND_FACTOR = "enroll-second-factor";

	public OeaPrincipal(Type type, UUID systemAdminAccountId, Long personId, String displayName, UUID sessionId,
			boolean secondFactorVerified, Instant authenticatedAt, String requiredAction) {
		this(type, systemAdminAccountId, personId, displayName, sessionId, secondFactorVerified, authenticatedAt,
				requiredAction, null);
	}

	public OeaPrincipal(Type type, UUID systemAdminAccountId, Long personId, String displayName, UUID sessionId,
			boolean secondFactorVerified, Instant authenticatedAt) {
		this(type, systemAdminAccountId, personId, displayName, sessionId, secondFactorVerified, authenticatedAt, null);
	}

	/**
	 * A service account authenticated by an access token (design D5 of s12-client-credentials-token):
	 * no session, no second factor; its roles come from its local assignments.
	 */
	public static OeaPrincipal serviceAccount(long personId, UUID clientId, String name, Instant issuedAt) {
		return new OeaPrincipal(Type.SERVICE_ACCOUNT, null, personId, name, null, false, issuedAt, null, clientId);
	}

	public enum Type {

		SYSTEM_ADMIN_ACCOUNT, PERSON, SERVICE_ACCOUNT

	}

}
