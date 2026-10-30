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
 * @param authenticatedAt last (re-)authentication of the session (REQ-172)
 */
public record OeaPrincipal(Type type, UUID systemAdminAccountId, Long personId, String displayName, UUID sessionId,
		boolean secondFactorVerified, Instant authenticatedAt, String requiredAction) {

	/** Action the session must complete first; {@code change-password} after a temporary password (REQ-173). */
	public static final String CHANGE_PASSWORD = "change-password";

	/** Action the session must complete first while the enforcement of a second factor is on (US-031 AC2). */
	public static final String ENROLL_SECOND_FACTOR = "enroll-second-factor";

	public OeaPrincipal(Type type, UUID systemAdminAccountId, Long personId, String displayName, UUID sessionId,
			boolean secondFactorVerified, Instant authenticatedAt) {
		this(type, systemAdminAccountId, personId, displayName, sessionId, secondFactorVerified, authenticatedAt, null);
	}

	public enum Type {

		SYSTEM_ADMIN_ACCOUNT, PERSON

	}

}
