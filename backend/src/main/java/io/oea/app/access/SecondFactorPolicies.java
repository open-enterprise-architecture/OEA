package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.IdentitySettingsStore;
import io.oea.core.access.RoleStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforcement of a second factor at the password login of persons (US-031, US-028, REQ-020,
 * REQ-029; design D1, D2, D5 of us-031-second-factor-enforcement): the policy in
 * {@code system_settings}, the operator floor, and whether a person must enroll a second factor.
 */
@Service
public class SecondFactorPolicies {

	private final IdentitySettingsStore settings;

	private final CredentialStore credentials;

	private final RoleStore roles;

	private final SecondFactorProperties properties;

	private final AuditWriter audit;

	private final Clock clock;

	public SecondFactorPolicies(IdentitySettingsStore settings, CredentialStore credentials, RoleStore roles,
			SecondFactorProperties properties, AuditWriter audit, Clock clock) {
		this.settings = settings;
		this.credentials = credentials;
		this.roles = roles;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	/** The stored policy and the operator floor. */
	public record View(boolean enforced, List<String> exemptRoles, boolean enforcedByOperator) {
	}

	public View view() {
		Stored stored = stored();
		return new View(stored.enforced() || this.properties.enforced(), stored.exemptRoles(),
				this.properties.enforced());
	}

	/** Whether the enforcement is effective: forced by the operator or switched on (D1). */
	public boolean enforced() {
		return this.properties.enforced() || stored().enforced();
	}

	/**
	 * Replaces the policy (D1).
	 * @throws InvalidAccessRequestException naming {@code enforced} or {@code exemptRoles}
	 */
	@Transactional
	public View update(OeaPrincipal principal, boolean enforced, List<String> exemptRoles) {
		Stored old = stored();
		List<String> roles = List.copyOf(new LinkedHashSet<>((exemptRoles != null) ? exemptRoles : List.of()));
		List<String> invalid = new ArrayList<>();
		if (!enforced && this.properties.enforced()) {
			invalid.add("enforced");
		}
		if (!roles.isEmpty()) {
			if (this.roles.countRolesWithoutAdminPermission(roles) != roles.size()) {
				invalid.add("exemptRoles");
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidAccessRequestException(invalid, "The second-factor policy is invalid: an operator floor, "
					+ "an unknown role or a role with an administrative permission (" + String.join(", ", invalid) + ").");
		}
		int updated = this.settings.updateSecondFactorSettings(enforced, roles, this.clock.instant(),
				principal.personId(), principal.systemAdminAccountId());
		if (updated != 1) {
			throw new IllegalStateException("the instance has no settings yet");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("old", Map.of("enforced", old.enforced(), "exemptRoles", old.exemptRoles()));
		details.put("new", Map.of("enforced", enforced, "exemptRoles", roles));
		this.audit.append(new AuditEvent("settings.second-factor-policy-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"settings", "second-factor-policy", details));
		return view();
	}

	/** D2: a person must enroll a second factor before anything else. */
	public boolean enrollmentRequired(long personId) {
		return enrollmentRequired(personId, null);
	}

	/**
	 * D5: whether a person would have to enroll if the given second factor were gone.
	 * @param ignoredCredential a credential to disregard, or {@code null}
	 */
	public boolean enrollmentRequired(long personId, UUID ignoredCredential) {
		Stored stored = stored();
		if (!this.properties.enforced() && !stored.enforced()) {
			return false;
		}
		if (this.credentials.countActiveSecondFactorsExcept(personId, ignoredCredential) > 0) {
			return false;
		}
		return !exempt(personId, stored.exemptRoles(), this.clock.instant());
	}

	/** REQ-029: at least one role through a local role assignment, and every such role exempt. */
	private boolean exempt(long personId, List<String> exemptRoles, Instant now) {
		Set<String> held = new LinkedHashSet<>(this.roles.grantedRoleIds(personId, now));
		return !held.isEmpty() && exemptRoles.containsAll(held);
	}

	private record Stored(boolean enforced, List<String> exemptRoles) {
	}

	private Stored stored() {
		return this.settings.secondFactorSettings()
			.map((row) -> new Stored(row.enforced(), row.exemptRoles()))
			.orElse(new Stored(false, List.of()));
	}

}
