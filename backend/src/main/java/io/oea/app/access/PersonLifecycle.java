package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.PersonStore;
import io.oea.core.access.SessionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status transitions of human persons (US-148, UC-23 A2; design of us-148-person-lifecycle):
 * leaving {@code active} revokes the sessions, offboarding also revokes the local role
 * assignments, all in one transaction (D3). Login and request authentication already refuse
 * persons that are neither {@code invited} nor {@code active} (REQ-004, D4).
 */
@Service
public class PersonLifecycle {

	/** Allowed transitions (design D2); everything else is {@code invalid-status-transition}. */
	private static final Map<String, Set<String>> TRANSITIONS = Map.of("invited", Set.of("offboarded"), "active",
			Set.of("inactive", "offboarded"), "inactive", Set.of("active", "offboarded"), "offboarded", Set.of());

	private final PersonStore persons;

	private final SessionStore sessionStore;

	private final AdministrationPaths paths;

	private final RoleAssignments assignments;

	private final PermissionDenials denials;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	public PersonLifecycle(PersonStore persons, SessionStore sessionStore, AdministrationPaths paths, RoleAssignments assignments,
			PermissionDenials denials, PermissionResolver permissions, AuditWriter audit, Clock clock) {
		this.persons = persons;
		this.sessionStore = sessionStore;
		this.paths = paths;
		this.assignments = assignments;
		this.denials = denials;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Changes the status of a person.
	 * @throws NoSuchElementException for an unknown person
	 * @throws InvalidAccessRequestException for a service account
	 * @throws SelfAdministrationException for the own status (SoD-1)
	 * @throws InvalidStatusTransitionException for a transition outside the table of D2
	 * @throws LastAdminPathException if the person holds the last usable path to {@code system-admin}
	 */
	@Transactional
	public void change(OeaPrincipal principal, long personId, String status, String reason) {
		PersonStore.LockedPerson locked = this.persons.lockPerson(personId)
			.orElseThrow(() -> new NoSuchElementException("no person " + personId));
		String from = locked.status();
		if ("service-account".equals(locked.personType())) {
			// SoD-4 area: service accounts change status with service-account:manage (US-142)
			throw new InvalidAccessRequestException("personId",
					"The status of a service account is changed with the service account management.");
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "changePersonStatus", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(status)) {
			throw new InvalidStatusTransitionException(from, status);
		}
		if (!"active".equals(status) && this.paths.isPersonPath(personId)) {
			this.paths.requireRemainingPath(AdministrationPaths.Removal.person(personId));
		}
		Instant now = this.clock.instant();
		this.persons.updateStatus(personId, status, now, principal.personId(), principal.systemAdminAccountId());
		int sessions = "active".equals(status) ? 0 : revokeSessions(principal, personId, now);
		int assignmentsRevoked = "offboarded".equals(status)
				? this.assignments.revokeAllForOffboarding(principal, personId) : 0;
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("from", from);
		details.put("to", status);
		if (reason != null) {
			details.put("reason", reason);
		}
		details.put("sessionsRevoked", sessions);
		details.put("assignmentsRevoked", assignmentsRevoked);
		this.audit.append(new AuditEvent("person.status-changed", AuditEvent.Category.SECURITY,
				"active".equals(status) ? AuditEvent.Severity.INFO : AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "person", Long.toString(personId), details));
		this.permissions.evictAll();
	}

	/** REQ-164 AC2: every session of the person ends at once, each audited as {@code session.revoked}. */
	private int revokeSessions(OeaPrincipal principal, long personId, Instant now) {
		List<UUID> revoked = this.sessionStore.revokeAllOfPersonReturningIds(personId, now, "person-status-change",
				principal.personId(), principal.systemAdminAccountId());
		for (UUID session : revoked) {
			this.audit.append(new AuditEvent("session.revoked", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "session",
					session.toString(), Map.of("reason", "person-status-change")));
		}
		return revoked.size();
	}

}
