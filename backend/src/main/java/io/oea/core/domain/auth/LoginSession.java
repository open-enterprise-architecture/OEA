package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
		if (this.revokedAt == null) {
			this.revokedAt = now;
			this.revokedReason = reason;
			this.revokedByAdminId = byAdminId;
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

}
