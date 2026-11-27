package io.oea.app.access;

import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.PersonStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The account link of a person to the element of the EA model that represents it (REQ-127,
 * ADR-032 DM-7, SoD-1; design D3 of us-127-connection-scoped-visibility): set by
 * {@code person:manage}, never for the own account, only to an active element of the person
 * metatype or one of its subtypes, one account per element.
 */
@Service
public class PersonEntityLinks implements PropertyAccess.PersonEntityLinksView {

	private final PersonStore persons;

	private final AuditWriter audit;

	private final PermissionDenials denials;

	private final Clock clock;

	public PersonEntityLinks(PersonStore persons, AuditWriter audit, PermissionDenials denials, Clock clock) {
		this.persons = persons;
		this.audit = audit;
		this.denials = denials;
		this.clock = clock;
	}

	/** The link of a person after a change, with the name of the element. */
	public record Link(long personId, Integer entityId, String entityName, Instant linkedAt) {
	}

	/**
	 * Sets or, with {@code null}, removes the link.
	 *
	 * @throws NoSuchElementException for an unknown person (404)
	 * @throws SelfAdministrationException for the own account (403, SoD-1)
	 * @throws InvalidAccessRequestException for a service account or an element that is not an
	 *         active element of the person metatype (422)
	 * @throws AccessConflictException if another account links the element (409)
	 */
	@Transactional
	public Link set(OeaPrincipal principal, long personId, Integer entityId) {
		PersonStore.LockedPerson locked = this.persons.lockPerson(personId)
			.orElseThrow(() -> new NoSuchElementException("no person " + personId));
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "setPersonEntityLink", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		if ("service-account".equals(locked.personType())) {
			throw new InvalidAccessRequestException("personId", "A service account cannot be linked to an element.");
		}
		if (entityId != null) {
			if (!this.persons.isPersonElement(entityId)) {
				throw new InvalidAccessRequestException("entityId",
						"The element must be an active element of the person metatype; a person metatype must be set.");
			}
			if (this.persons.isLinkedToOtherPerson(entityId, personId)) {
				throw new AccessConflictException("The element is linked to another account.");
			}
		}
		Integer old = locked.entityId();
		this.persons.updateEntityLink(personId, entityId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("oldEntityId", (old != null) ? old : "");
		details.put("newEntityId", (entityId != null) ? entityId : "");
		details.put("source", "manual");
		this.audit.append(new AuditEvent("person.account-linked", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"person", Long.toString(personId), details));
		String name = (entityId != null) ? this.persons.entityName(entityId) : null;
		return new Link(personId, entityId, name, this.clock.instant());
	}

	/**
	 * The effective element of a person: linked, active and of the person metatype; {@code null}
	 * otherwise (design D2).
	 */
	@Override
	public Integer anchor(long personId) {
		return this.persons.anchor(personId);
	}

}
