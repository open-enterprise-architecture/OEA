package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.IdentitySettingsStore;
import io.oea.core.repository.LocalCredentialRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Setup phase of ADR-042 (design D9). */
@Service
public class SetupPhase {

	private final IdentitySettingsStore settings;

	private final LocalCredentialRepository credentials;

	private final AuditWriter audit;

	private final Clock clock;

	public SetupPhase(IdentitySettingsStore settings, LocalCredentialRepository credentials, AuditWriter audit, Clock clock) {
		this.settings = settings;
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
		return !this.settings.setupPhaseEnded();
	}

	/**
	 * Called in the transaction of an action that gives another identity access. In the setup
	 * phase it requires an active TOTP credential of the acting system admin and a session
	 * confirmed with it, then ends the phase irreversibly.
	 */
	@Transactional
	public void requireSecondFactorFor(String action, OeaPrincipal principal) {
		if (this.settings.lockSetupPhaseEnded()) {
			return;
		}
		boolean hasTotp = principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT && !this.credentials
			.findBySystemAdminAccountIdAndTypeAndStatus(principal.systemAdminAccountId(), "totp", "active")
			.isEmpty();
		if (!hasTotp || !principal.secondFactorVerified()) {
			throw new SecondFactorRequiredForAccessException();
		}
		this.settings.endSetupPhase(this.clock.instant(), action);
		this.audit.append(new AuditEvent("system.setup-phase-ended", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"system-settings", "1", Map.of("triggeringAction", action)));
	}

}
