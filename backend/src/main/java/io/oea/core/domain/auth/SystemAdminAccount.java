package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Local system admin account (business object system-admin-account, ADR-032 §1). */
@Entity
@Table(name = "system_admin_accounts")
public class SystemAdminAccount extends NewAwareEntity<UUID> {

	/** Allowed login names (data model CHECK). */
	public static final Pattern LOGIN_NAME = Pattern.compile("^[a-z][a-z0-9._-]{2,63}$");

	public enum Status {

		BOOTSTRAPPED, ACTIVE, DEACTIVATED

	}

	@Id
	private UUID id;

	@Column(name = "login_name", nullable = false, unique = true, updatable = false)
	private String loginName;

	@Column(nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "first_login_at")
	private Instant firstLoginAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected SystemAdminAccount() {
	}

	private SystemAdminAccount(UUID id, String loginName, Instant createdAt) {
		this.id = id;
		this.loginName = loginName;
		this.status = Status.BOOTSTRAPPED.name().toLowerCase(Locale.ROOT);
		this.createdAt = createdAt;
	}

	/** New account in the state {@code bootstrapped} (UC-02). */
	public static SystemAdminAccount bootstrapped(String loginName, Instant now) {
		Objects.requireNonNull(loginName, "loginName");
		if (!LOGIN_NAME.matcher(loginName).matches()) {
			throw new IllegalArgumentException("invalid login name");
		}
		return new SystemAdminAccount(UUID.randomUUID(), loginName, now);
	}

	/** Successful login: {@code bootstrapped → active} at the first one (business object lifecycle). */
	public void recordLogin(Instant now) {
		if (getStatus() == Status.BOOTSTRAPPED) {
			this.status = "active";
			this.firstLoginAt = now;
		}
		this.lastUsedAt = now;
	}

	public boolean canLogIn() {
		return getStatus() != Status.DEACTIVATED;
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public String getLoginName() {
		return this.loginName;
	}

	public Status getStatus() {
		return Status.valueOf(this.status.toUpperCase(Locale.ROOT));
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
