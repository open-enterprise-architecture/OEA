package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.IdpMappingStore;
import io.oea.core.access.SessionStore;
import io.oea.core.domain.auth.LoginSession;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles derived from IdP group mappings (US-147, ADR-032 §7, DM-10; design D7). The groups
 * claim of the login's issuer is matched against the active mappings of that issuer; the
 * result replaces the rows of {@code session_idp_roles}. A missing or truncated groups claim
 * derives nothing (fail closed) and is audited as a warning.
 */
@Service
public class IdpRoles {

	/** Maximum age of an evaluation (owner decision Q16, REQ-165 AC5). */
	public static final java.time.Duration MAXIMUM_AGE = java.time.Duration.ofMinutes(15);

	private final SessionStore sessionStore;

	private final IdpMappingStore mappingStore;

	private final AuditWriter audit;

	private final Clock clock;

	public IdpRoles(SessionStore sessionStore, IdpMappingStore mappingStore, AuditWriter audit, Clock clock) {
		this.sessionStore = sessionStore;
		this.mappingStore = mappingStore;
		this.audit = audit;
		this.clock = clock;
	}

	/** Classified groups claim of an ID token. */
	public record GroupsClaim(String status, List<String> identifiers) {

		/** {@code groups} as list; Entra ID overage indicators count as truncated. */
		public static GroupsClaim of(Map<String, Object> claims) {
			Object groups = claims.get("groups");
			if (groups instanceof Collection<?> values) {
				List<String> identifiers = new ArrayList<>();
				values.forEach((value) -> identifiers.add(String.valueOf(value)));
				return new GroupsClaim("present", List.copyOf(identifiers));
			}
			Object claimNames = claims.get("_claim_names");
			if (Boolean.TRUE.equals(claims.get("hasgroups"))
					|| (claimNames instanceof Map<?, ?> names && names.containsKey("groups"))) {
				return new GroupsClaim("truncated", List.of());
			}
			return new GroupsClaim("missing", List.of());
		}

	}

	/** One derived role. */
	public record DerivedRole(UUID mappingId, String roleId) {
	}

	/**
	 * Evaluates the groups claim for the session and replaces its derived roles.
	 *
	 * @param trigger {@code login} or {@code refresh}
	 */
	@Transactional
	public List<DerivedRole> evaluate(LoginSession session, String issuer, GroupsClaim claim,
			boolean secondFactorVerified, String trigger) {
		Instant now = this.clock.instant();
		List<DerivedRole> previous = derived(session.getId());
		List<DerivedRole> derived = "present".equals(claim.status()) && !claim.identifiers().isEmpty()
				? this.mappingStore.findActiveForGroups(issuer, claim.identifiers())
					.stream()
					.map((mapping) -> new DerivedRole(mapping.id(), mapping.roleId()))
					.toList()
				: List.of();
		replace(session.getId(), derived, now);
		recordMatches(derived, secondFactorVerified, now);
		session.recordIdpEvaluation(claim.status(), "present".equals(claim.status()) ? claim.identifiers() : null,
				secondFactorVerified, now);
		if (!"present".equals(claim.status())) {
			unusable(session, claim.status());
		}
		if ("login".equals(trigger) || !previous.equals(derived)) {
			this.audit.append(new AuditEvent("auth.idp-roles-derived", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(session),
					"session", session.getId().toString(),
					Map.of("trigger", trigger, "roles", derived.stream().map(DerivedRole::roleId).toList(),
							"mappings", derived.stream().map((role) -> role.mappingId().toString()).toList(),
							"groupsClaim", claim.status())));
		}
		return derived;
	}

	/** The IdP could not be asked (unreachable, no refresh token): no derived role applies (Q17). */
	@Transactional
	public void withdraw(LoginSession session, String reason) {
		List<DerivedRole> previous = derived(session.getId());
		replace(session.getId(), List.of(), this.clock.instant());
		unusable(session, reason);
		if (!previous.isEmpty()) {
			this.audit.append(new AuditEvent("auth.idp-roles-derived", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(session),
					"session", session.getId().toString(),
					Map.of("trigger", "refresh", "roles", List.of(), "mappings", List.of(), "groupsClaim", reason)));
		}
	}

	/** Derived roles of a session whose mappings still apply (deleted or deactivated ones do not). */
	@Transactional(readOnly = true)
	public List<DerivedRole> effective(UUID sessionId) {
		return this.sessionStore.effectiveDerivedRoles(sessionId)
			.stream()
			.map((role) -> new DerivedRole(role.mappingId(), role.roleId()))
			.toList();
	}

	private List<DerivedRole> derived(UUID sessionId) {
		return this.sessionStore.derivedRoles(sessionId)
			.stream()
			.map((role) -> new DerivedRole(role.mappingId(), role.roleId()))
			.toList();
	}

	private void replace(UUID sessionId, List<DerivedRole> roles, Instant now) {
		this.sessionStore.deleteDerivedRoles(sessionId);
		for (DerivedRole role : roles) {
			this.sessionStore.insertDerivedRole(sessionId, role.mappingId(), role.roleId(), now);
		}
	}

	/**
	 * Records the last match of every mapping whose role is effective (a privileged role only
	 * with a second factor, as in PermissionResolver), at most once per hour (design D2 of
	 * us-019-admin-safeguards).
	 */
	private void recordMatches(List<DerivedRole> derived, boolean secondFactorVerified, Instant now) {
		for (DerivedRole role : derived) {
			this.mappingStore.recordMatch(role.mappingId(), now, now.minus(java.time.Duration.ofHours(1)),
					secondFactorVerified);
		}
	}

	private void unusable(LoginSession session, String reason) {
		this.audit.append(new AuditEvent("auth.idp-groups-unusable", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API, actor(session),
				"session", session.getId().toString(), Map.of("reason", reason)));
	}

	private static AuditEvent.Actor actor(LoginSession session) {
		return new AuditEvent.Actor("person", session.getPersonId(), null);
	}

}
