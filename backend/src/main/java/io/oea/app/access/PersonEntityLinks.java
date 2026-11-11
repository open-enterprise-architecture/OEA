package io.oea.app.access;

import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.jdbc.core.JdbcTemplate;
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

	/** The active elements of the person metatype and its subtypes. */
	static final String PERSON_ELEMENTS = """
			WITH RECURSIVE person_types (id) AS (
			    SELECT person_metatype_id FROM visibility_settings WHERE id = 1 AND person_metatype_id IS NOT NULL
			    UNION SELECT m.id FROM metatypes m JOIN person_types p ON m.base_metatype_id = p.id)
			SELECT e.id FROM entities e WHERE e.metatype_id IN (SELECT id FROM person_types) AND e.deleted_at IS NULL""";

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	private final PermissionDenials denials;

	private final Clock clock;

	public PersonEntityLinks(JdbcTemplate jdbc, AuditWriter audit, PermissionDenials denials, Clock clock) {
		this.jdbc = jdbc;
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
		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT person_type, entity_id FROM persons WHERE id = ? FOR UPDATE", personId);
		if (rows.isEmpty()) {
			throw new NoSuchElementException("no person " + personId);
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "setPersonEntityLink", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		if ("service-account".equals(rows.getFirst().get("person_type"))) {
			throw new InvalidAccessRequestException("personId", "A service account cannot be linked to an element.");
		}
		if (entityId != null) {
			Integer covered = this.jdbc.queryForObject(
					"SELECT count(*) FROM (" + PERSON_ELEMENTS + ") elements WHERE id = ?", Integer.class, entityId);
			if (covered == null || covered == 0) {
				throw new InvalidAccessRequestException("entityId",
						"The element must be an active element of the person metatype; a person metatype must be set.");
			}
			Integer others = this.jdbc.queryForObject(
					"SELECT count(*) FROM persons WHERE entity_id = ? AND id <> ?", Integer.class, entityId, personId);
			if (others != null && others > 0) {
				throw new AccessConflictException("The element is linked to another account.");
			}
		}
		Integer old = (Integer) rows.getFirst().get("entity_id");
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", entityId, personId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("oldEntityId", (old != null) ? old : "");
		details.put("newEntityId", (entityId != null) ? entityId : "");
		this.audit.append(new AuditEvent("person.account-linked", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"person", Long.toString(personId), details));
		String name = (entityId != null)
				? this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, entityId) : null;
		return new Link(personId, entityId, name, this.clock.instant());
	}

	/**
	 * The effective element of a person: linked, active and of the person metatype; {@code null}
	 * otherwise (design D2).
	 */
	@Override
	public Integer anchor(long personId) {
		List<Integer> anchors = this.jdbc.queryForList("SELECT p.entity_id FROM persons p WHERE p.id = ? AND p.entity_id IN ("
				+ PERSON_ELEMENTS + ")", Integer.class, personId);
		return anchors.isEmpty() ? null : anchors.getFirst();
	}

}
