package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code persons}: the person lock, status, language, WebAuthn user handle and the account
 * link to an element of the EA model (S17 change 3, ADR-051). Lock statements stay the first
 * statement of the calling service method.
 */
@Repository
public class PersonStore {

	/** The active elements of the person metatype and its subtypes. */
	private static final String PERSON_ELEMENTS = """
			WITH RECURSIVE person_types (id) AS (
			    SELECT person_metatype_id FROM visibility_settings WHERE id = 1 AND person_metatype_id IS NOT NULL
			    UNION SELECT m.id FROM metatypes m JOIN person_types p ON m.base_metatype_id = p.id)
			SELECT e.id FROM entities e WHERE e.metatype_id IN (SELECT id FROM person_types) AND e.deleted_at IS NULL""";

	/** A locked person row. */
	public record LockedPerson(String personType, String status, Integer entityId) {
	}

	private final JdbcTemplate jdbc;

	public PersonStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Locks the person row ({@code FOR UPDATE}); empty for an unknown person. */
	public Optional<LockedPerson> lockPerson(long personId) {
		List<LockedPerson> rows = this.jdbc.query(
				"SELECT person_type, status, entity_id FROM persons WHERE id = ? FOR UPDATE",
				(rs, n) -> new LockedPerson(rs.getString(1), rs.getString(2), rs.getObject(3, Integer.class)),
				personId);
		return rows.stream().findFirst();
	}

	/** The type of the person; empty for an unknown person. */
	public Optional<String> personType(long personId) {
		return this.jdbc.queryForList("SELECT person_type FROM persons WHERE id = ?", String.class, personId)
			.stream()
			.findFirst();
	}

	/** The name of the person. */
	public String name(long personId) {
		return this.jdbc.queryForObject("SELECT name FROM persons WHERE id = ?", String.class, personId);
	}

	/** Whether the person is a human in status invited or active (REQ-004). */
	public boolean mayLogIn(long personId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT person_type = 'human' AND status IN ('invited', 'active') FROM persons WHERE id = ?""",
				Boolean.class, personId));
	}

	/** The chosen language of the person, or {@code null}. */
	public String language(long personId) {
		List<String> languages = this.jdbc.queryForList("SELECT ui_language FROM persons WHERE id = ?", String.class,
				personId);
		return languages.isEmpty() ? null : languages.get(0);
	}

	/** Sets the language of a human person; returns the number of changed rows. */
	public int updateLanguage(long personId, String language) {
		return this.jdbc.update("UPDATE persons SET ui_language = ? WHERE id = ? AND person_type = 'human'", language,
				personId);
	}

	/** Changes the status with the principal that did it. */
	public void updateStatus(long personId, String status, Instant at, Long byPersonId, UUID byAdminId) {
		this.jdbc.update("""
				UPDATE persons SET status = ?, status_changed_at = ?, status_changed_by_person_id = ?,
				       status_changed_by_admin_id = ?
				WHERE id = ?""", status, Timestamp.from(at), byPersonId, byAdminId, personId);
	}

	/** The WebAuthn user handle of the person, or {@code null} before the first registration. */
	public byte[] webauthnUserHandle(long personId) {
		return this.jdbc.queryForObject("SELECT webauthn_user_handle FROM persons WHERE id = ?", byte[].class,
				personId);
	}

	/** Sets the user handle unless one exists. */
	public void setWebauthnUserHandleIfAbsent(long personId, byte[] handle) {
		this.jdbc.update("UPDATE persons SET webauthn_user_handle = ? WHERE id = ? AND webauthn_user_handle IS NULL",
				handle, personId);
	}

	/** Whether the element is an active element of the person metatype or one of its subtypes. */
	public boolean isPersonElement(int entityId) {
		Integer covered = this.jdbc.queryForObject("SELECT count(*) FROM (" + PERSON_ELEMENTS + ") elements WHERE id = ?",
				Integer.class, entityId);
		return covered != null && covered != 0;
	}

	/** Whether another account than the person links the element. */
	public boolean isLinkedToOtherPerson(int entityId, long personId) {
		Integer others = this.jdbc.queryForObject("SELECT count(*) FROM persons WHERE entity_id = ? AND id <> ?",
				Integer.class, entityId, personId);
		return others != null && others > 0;
	}

	/** Sets or, with {@code null}, removes the link of the person to an element. */
	public void updateEntityLink(long personId, Integer entityId) {
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", entityId, personId);
	}

	/** The name of the linked element. */
	public String entityName(int entityId) {
		return this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, entityId);
	}

	/** The effective element of a person: linked, active and of the person metatype; {@code null} otherwise. */
	public Integer anchor(long personId) {
		List<Integer> anchors = this.jdbc.queryForList(
				"SELECT p.entity_id FROM persons p WHERE p.id = ? AND p.entity_id IN (" + PERSON_ELEMENTS + ")",
				Integer.class, personId);
		return anchors.isEmpty() ? null : anchors.getFirst();
	}

}
