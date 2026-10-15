package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.repository.LocalCredentialRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Setup phase of ADR-042 (design D9). */
@Service
public class SetupPhase {

	private final JdbcTemplate jdbc;

	private final LocalCredentialRepository credentials;

	private final AuditWriter audit;

	private final Clock clock;

	public SetupPhase(JdbcTemplate jdbc, LocalCredentialRepository credentials, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.credentials = credentials;
		this.audit = audit;
		this.clock = clock;
	}

	/** Thrown when the first foreign access needs the admin's confirmed second factor. */
	public static class SecondFactorRequiredForAccessException extends RuntimeException {

		public SecondFactorRequiredForAccessException() {
			super("second factor required before other identities get access (ADR-042)");
		}

	}

	@Transactional(readOnly = true)
	public boolean isActive() {
		Boolean ended = this.jdbc.query("SELECT setup_phase_ended_at IS NOT NULL FROM system_settings WHERE id = 1",
				(rs) -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE);
		return !Boolean.TRUE.equals(ended);
	}

	/**
	 * Called in the transaction of an action that gives another identity access. In the setup
	 * phase it requires an active TOTP credential of the acting system admin and a session
	 * confirmed with it, then ends the phase irreversibly.
	 */
	@Transactional
	public void requireSecondFactorFor(String action, OeaPrincipal principal) {
		Boolean ended = this.jdbc.query(
				"SELECT setup_phase_ended_at IS NOT NULL FROM system_settings WHERE id = 1 FOR UPDATE",
				(rs) -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE);
		if (Boolean.TRUE.equals(ended)) {
			return;
		}
		boolean hasTotp = principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT && !this.credentials
			.findBySystemAdminAccountIdAndTypeAndStatus(principal.systemAdminAccountId(), "totp", "active")
			.isEmpty();
		if (!hasTotp || !principal.secondFactorVerified()) {
			throw new SecondFactorRequiredForAccessException();
		}
		this.jdbc.update("UPDATE system_settings SET setup_phase_ended_at = ?, setup_phase_ended_by_action = ? WHERE id = 1",
				Timestamp.from(this.clock.instant()), action);
		this.audit.append(new AuditEvent("system.setup-phase-ended", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"system-settings", "1", Map.of("triggeringAction", action)));
	}

}
