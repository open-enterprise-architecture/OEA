package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.repository.SystemAdminAccountRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The local system admin account of the bootstrapping (US-019; design D3, D7 of
 * us-019-admin-safeguards): its state and its deactivation, which keeps at least one usable
 * path to {@code system-admin} (ADR-032 SoD-7).
 */
@Service
public class LocalAdminAccount {

	private final SystemAdminAccountRepository accounts;

	private final AdministrationPaths paths;

	private final AuditWriter audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	/** State as shown to administrators; {@code status} in its lower-case API form. */
	public record State(String loginName, String status, Instant createdAt, Instant firstLoginAt,
			Instant lastUsedAt, Instant deactivatedAt) {
	}

	public LocalAdminAccount(SystemAdminAccountRepository accounts, AdministrationPaths paths, AuditWriter audit,
			JdbcTemplate jdbc, Clock clock) {
		this.accounts = accounts;
		this.paths = paths;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** @throws NoSuchElementException on an instance that is not bootstrapped */
	@Transactional(readOnly = true)
	public State state() {
		return view(this.accounts.findById(accountId()).orElseThrow());
	}

	/**
	 * Deactivates the account and revokes its sessions; an already deactivated account stays
	 * unchanged without a new event.
	 * @throws LastAdminPathException if no other usable path to {@code system-admin} remains
	 */
	@Transactional(noRollbackFor = LastAdminPathException.class)
	public State deactivate(OeaPrincipal principal) {
		UUID id = accountId();
		// the row lock serializes concurrent deactivations and the path check
		this.jdbc.queryForObject("SELECT id FROM system_admin_accounts WHERE id = ? FOR UPDATE", UUID.class, id);
		SystemAdminAccount account = this.accounts.findById(id).orElseThrow();
		if (account.getStatus() == SystemAdminAccount.Status.DEACTIVATED) {
			return view(account);
		}
		try {
			this.paths.requireRemainingPath(AdministrationPaths.Removal.localAccount());
		}
		catch (LastAdminPathException ex) {
			this.audit.append(event(principal, account, AuditEvent.Outcome.DENIED, Map.of("reason", "last-admin-path")));
			throw ex;
		}
		Instant now = this.clock.instant();
		account.deactivate(principal.systemAdminAccountId(), principal.personId(), now);
		int revoked = this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'admin-action', revoked_by_admin_id = ?,
				       revoked_by_person_id = ?
				WHERE system_admin_account_id = ? AND revoked_at IS NULL""", Timestamp.from(now),
				principal.systemAdminAccountId(), principal.personId(), id);
		this.accounts.flush();
		this.audit.append(event(principal, account, AuditEvent.Outcome.SUCCESS, Map.of("sessionsRevoked", revoked)));
		return view(account);
	}

	private UUID accountId() {
		UUID id = this.jdbc.query("SELECT id FROM system_admin_accounts ORDER BY created_at, id LIMIT 1",
				(rs) -> rs.next() ? rs.getObject(1, UUID.class) : null);
		if (id == null) {
			throw new NoSuchElementException("The instance is not bootstrapped.");
		}
		return id;
	}

	private static AuditEvent event(OeaPrincipal principal, SystemAdminAccount account, AuditEvent.Outcome outcome,
			Map<String, Object> details) {
		return new AuditEvent("system-admin-account.deactivated", AuditEvent.Category.SECURITY,
				(outcome == AuditEvent.Outcome.SUCCESS) ? AuditEvent.Severity.HIGH : AuditEvent.Severity.WARNING,
				outcome, AuditEvent.Source.API, Sessions.actor(principal), "system-admin-account",
				account.getId().toString(), details);
	}

	private static State view(SystemAdminAccount account) {
		return new State(account.getLoginName(), account.getStatus().name().toLowerCase(Locale.ROOT),
				account.getCreatedAt(), account.getFirstLoginAt(),
				account.getLastUsedAt(), account.getDeactivatedAt());
	}

}
