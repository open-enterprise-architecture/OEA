package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnTransformer;

/** Server-side session (business object session, REQ-164, REQ-165). */
@Entity
@Table(name = "sessions")
public class LoginSession extends NewAwareEntity<UUID> {

	@Id
	private UUID id;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "person_id", updatable = false)
	private Long personId;

	@Column(name = "system_admin_account_id", updatable = false)
	private UUID systemAdminAccountId;

	@Column(name = "client_type", nullable = false, updatable = false)
	private String clientType;

	@Column(name = "auth_method", nullable = false)
	private String authMethod;

	@Column(name = "second_factor_verified", nullable = false)
	private boolean secondFactorVerified;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "authenticated_at", nullable = false)
	private Instant authenticatedAt;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "absolute_expires_at", nullable = false, updatable = false)
	private Instant absoluteExpiresAt;

	@Column(name = "client_ip", updatable = false)
	@ColumnTransformer(write = "?::inet", read = "host(client_ip)")
	private String clientIp;

	@Column(name = "user_agent", updatable = false)
	private String userAgent;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason")
	private String revokedReason;

	@Column(name = "revoked_by_admin_id")
	private UUID revokedByAdminId;

	@Column(name = "revoked_by_person_id")
	private Long revokedByPersonId;

	@Column(name = "auth_provider_id", updatable = false)
	private UUID authProviderId;

	@Column(name = "idp_refresh_token_encrypted")
	private String idpRefreshTokenEncrypted;

	@Column(name = "idp_refresh_token_key_id")
	private String idpRefreshTokenKeyId;

	@Column(name = "idp_token_expires_at")
	private Instant idpTokenExpiresAt;

	@Column(name = "idp_claims_evaluated_at")
	private Instant idpClaimsEvaluatedAt;

	@Column(name = "idp_groups_claim_status")
	private String idpGroupsClaimStatus;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "idp_group_identifiers")
	private String[] idpGroupIdentifiers;

	protected LoginSession() {
	}

	/** New web session of the local system admin account. */
	public static LoginSession forSystemAdmin(UUID accountId, byte[] tokenHash, String authMethod,
			boolean secondFactorVerified, Instant now, Duration absoluteLifetime, String clientIp, String userAgent) {
		LoginSession session = new LoginSession();
		session.id = UUID.randomUUID();
		session.tokenHash = tokenHash.clone();
		session.systemAdminAccountId = accountId;
		session.clientType = "web-portal";
		session.authMethod = authMethod;
		session.secondFactorVerified = secondFactorVerified;
		session.createdAt = now;
		session.authenticatedAt = now;
		session.lastActivityAt = now;
		session.absoluteExpiresAt = now.plus(absoluteLifetime);
		session.clientIp = clientIp;
		session.userAgent = (userAgent != null && userAgent.length() > 500) ? userAgent.substring(0, 500) : userAgent;
		return session;
	}

	/** New web session of a person established through an OIDC provider (US-001, design D6). */
	public static LoginSession forPerson(long personId, UUID authProviderId, byte[] tokenHash,
			boolean secondFactorVerified, Instant now, Duration absoluteLifetime, String clientIp, String userAgent) {
		LoginSession session = new LoginSession();
		session.id = UUID.randomUUID();
		session.tokenHash = tokenHash.clone();
		session.personId = personId;
		session.authProviderId = authProviderId;
		session.clientType = "web-portal";
		session.authMethod = "oidc";
		session.secondFactorVerified = secondFactorVerified;
		session.createdAt = now;
		session.authenticatedAt = now;
		session.lastActivityAt = now;
		session.absoluteExpiresAt = now.plus(absoluteLifetime);
		session.clientIp = clientIp;
		session.userAgent = (userAgent != null && userAgent.length() > 500) ? userAgent.substring(0, 500) : userAgent;
		return session;
	}

	/** Additional authenticated data that binds the encrypted IdP refresh token to this row. */
	public static String refreshTokenEncryptionContext(UUID sessionId) {
		return "sessions.idp_refresh_token_encrypted:" + sessionId;
	}

	/** Stores the (rotated) IdP refresh token and its expiry; {@code null} removes it. */
	public void storeIdpRefreshToken(String encrypted, String keyId, Instant expiresAt) {
		this.idpRefreshTokenEncrypted = encrypted;
		this.idpRefreshTokenKeyId = keyId;
		this.idpTokenExpiresAt = expiresAt;
	}

	/** Result of an evaluation of the groups claim (design D7). */
	public void recordIdpEvaluation(String claimStatus, List<String> groupIdentifiers, boolean secondFactorVerified,
			Instant now) {
		this.idpGroupsClaimStatus = claimStatus;
		this.idpGroupIdentifiers = (groupIdentifiers != null) ? groupIdentifiers.toArray(String[]::new) : null;
		this.secondFactorVerified = secondFactorVerified;
		this.idpClaimsEvaluatedAt = now;
	}

	/** True if the last evaluation of the groups claim is older than the maximum age (Q16). */
	public boolean isIdpEvaluationDue(Instant now, Duration maximumAge) {
		return "oidc".equals(this.authMethod)
				&& (this.idpClaimsEvaluatedAt == null || !now.isBefore(this.idpClaimsEvaluatedAt.plus(maximumAge)));
	}

	/** The IdP could not be asked; the next attempt is due after the retry delay. */
	public void scheduleIdpRetry(Instant now, Duration maximumAge, Duration retryDelay) {
		this.idpClaimsEvaluatedAt = now.minus(maximumAge).plus(retryDelay);
	}

	/** True if the IdP refresh token has expired (owner decision 2026-10-15). */
	public boolean isIdpTokenExpired(Instant now) {
		return this.idpTokenExpiresAt != null && !now.isBefore(this.idpTokenExpiresAt);
	}

	/** True if neither revoked nor beyond idle timeout or absolute lifetime. */
	public boolean isActive(Instant now, Duration idleTimeout) {
		return this.revokedAt == null && now.isBefore(this.absoluteExpiresAt)
				&& now.isBefore(this.lastActivityAt.plus(idleTimeout));
	}

	public boolean touch(Instant now) {
		if (this.lastActivityAt.plusSeconds(60).isAfter(now)) {
			return false;
		}
		this.lastActivityAt = now;
		return true;
	}

	/** Second factor confirmed in this session (enrollment, ADR-042); counts as fresh authentication. */
	public void confirmSecondFactor(Instant now) {
		this.secondFactorVerified = true;
		this.authMethod = "password-totp";
		this.authenticatedAt = now;
	}

	public void revoke(String reason, UUID byAdminId, Instant now) {
		revoke(reason, byAdminId, null, now);
	}

	public void revoke(String reason, UUID byAdminId, Long byPersonId, Instant now) {
		if (this.revokedAt == null) {
			this.revokedAt = now;
			this.revokedReason = reason;
			this.revokedByAdminId = byAdminId;
			this.revokedByPersonId = byPersonId;
			// secrets of an ended session are removed at once (BR-07)
			this.idpRefreshTokenEncrypted = null;
			this.idpRefreshTokenKeyId = null;
		}
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public Long getPersonId() {
		return this.personId;
	}

	public UUID getSystemAdminAccountId() {
		return this.systemAdminAccountId;
	}

	public boolean isSecondFactorVerified() {
		return this.secondFactorVerified;
	}

	public Instant getAuthenticatedAt() {
		return this.authenticatedAt;
	}

	public String getAuthMethod() {
		return this.authMethod;
	}

	public Instant getRevokedAt() {
		return this.revokedAt;
	}

	public UUID getAuthProviderId() {
		return this.authProviderId;
	}

	public String getIdpRefreshTokenEncrypted() {
		return this.idpRefreshTokenEncrypted;
	}

	public String getIdpRefreshTokenKeyId() {
		return this.idpRefreshTokenKeyId;
	}

	public Instant getIdpClaimsEvaluatedAt() {
		return this.idpClaimsEvaluatedAt;
	}

	public String getIdpGroupsClaimStatus() {
		return this.idpGroupsClaimStatus;
	}

	public List<String> getIdpGroupIdentifiers() {
		return (this.idpGroupIdentifiers != null) ? List.of(this.idpGroupIdentifiers) : List.of();
	}

}
