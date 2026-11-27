package io.oea.core.access;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL of the account mapping and the derived account link (V53, design D5 of
 * us-075-lane-assignments): the mapping row, the inputs of the recomputation and the column
 * {@code persons.mapped_entity_id}.
 */
@Repository
public class PersonLinkMappingStore {

	/** Key of the transaction-scoped advisory lock of the mapping. */
	private static final long LOCK_KEY = 0x6F65615F6C696E6BL;

	/** The active elements of the person metatype of visibility and its subtypes. */
	private static final String PERSON_ELEMENTS = """
			WITH RECURSIVE person_types (id) AS (
			    SELECT person_metatype_id FROM visibility_settings WHERE id = 1 AND person_metatype_id IS NOT NULL
			    UNION SELECT m.id FROM metatypes m JOIN person_types p ON m.base_metatype_id = p.id)
			SELECT e.id FROM entities e WHERE e.metatype_id IN (SELECT id FROM person_types) AND e.deleted_at IS NULL""";

	private final JdbcTemplate jdbc;

	public PersonLinkMappingStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The mapping with the name of its property; all {@code null} while unset. */
	public record Mapping(UUID propertyDefId, String propertyName, String accountAttribute) {
	}

	/** An account that takes part: a human in status invited or active. */
	public record Account(long id, String email, Integer manualEntityId, Integer mappedEntityId) {
	}

	/** One value of an account attribute. */
	public record AccountValue(long personId, String value) {
	}

	/** One element of the person metatype with its mapping value. */
	public record ElementValue(int entityId, String value, boolean linkedManually) {
	}

	/** The mapping. */
	public Mapping mapping() {
		return this.jdbc.queryForObject("""
				SELECT m.property_def_id, p.name, m.account_attribute
				FROM person_link_mapping m LEFT JOIN property_definitions p ON p.id = m.property_def_id
				WHERE m.id = 1""", (rs, n) -> new Mapping(rs.getObject(1, UUID.class), rs.getString(2),
				(rs.getObject(1) != null) ? rs.getString(3) : null));
	}

	/** Sets or, with {@code null}, clears the mapping. */
	public void setMapping(UUID propertyDefId, String accountAttribute) {
		this.jdbc.update("UPDATE person_link_mapping SET property_def_id = ?, account_attribute = ? WHERE id = 1",
				propertyDefId, (propertyDefId != null) ? accountAttribute : null);
	}

	/** Takes the transaction-scoped lock that serializes the recomputation. */
	public void lock() {
		this.jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)::text", String.class, LOCK_KEY);
	}

	/** Whether the property is of data type string and not retired; empty text when it does not exist. */
	public String propertyIssue(UUID propertyDefId) {
		List<String> rows = this.jdbc.queryForList(
				"SELECT CASE WHEN retired_at IS NOT NULL THEN 'retired' WHEN data_type <> 'string' THEN 'type' "
						+ "WHEN visibility_mode <> 'role-restricted' THEN 'mode' ELSE '' END "
						+ "FROM property_definitions WHERE id = ?",
				String.class, propertyDefId);
		return rows.isEmpty() ? "unknown" : rows.getFirst();
	}

	/**
	 * Whether the property is mapped to the person metatype of visibility, to one of its subtypes or
	 * to one of its base types (mappings that have taken effect).
	 */
	public boolean mappedToPersonMetatype(UUID propertyDefId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				WITH RECURSIVE sub (id) AS (
				    SELECT person_metatype_id FROM visibility_settings WHERE id = 1 AND person_metatype_id IS NOT NULL
				    UNION SELECT m.id FROM metatypes m JOIN sub s ON m.base_metatype_id = s.id),
				sup (id) AS (
				    SELECT person_metatype_id FROM visibility_settings WHERE id = 1 AND person_metatype_id IS NOT NULL
				    UNION SELECT m.base_metatype_id FROM metatypes m JOIN sup s ON m.id = s.id
				    WHERE m.base_metatype_id IS NOT NULL)
				SELECT EXISTS (SELECT 1 FROM metatype_property_mappings mp
				               WHERE mp.property_def_id = ? AND mp.valid_from <= current_date
				                 AND (mp.metatype_id IN (SELECT id FROM sub) OR mp.metatype_id IN (SELECT id FROM sup)))""",
				Boolean.class, propertyDefId));
	}

	/** The accounts that take part: humans in status invited or active, by ID. */
	public List<Account> accounts() {
		return this.jdbc.query("""
				SELECT id, email, entity_id, mapped_entity_id FROM persons
				WHERE person_type = 'human' AND status IN ('invited', 'active') ORDER BY id""",
				(rs, n) -> new Account(rs.getLong(1), rs.getString(2), rs.getObject(3, Integer.class),
						rs.getObject(4, Integer.class)));
	}

	/** The subjects of the identity bindings of the accounts that take part. */
	public List<AccountValue> subjects() {
		return this.jdbc.query("""
				SELECT i.person_id, i.subject FROM person_identities i JOIN persons p ON p.id = i.person_id
				WHERE p.person_type = 'human' AND p.status IN ('invited', 'active') ORDER BY i.person_id, i.subject""",
				(rs, n) -> new AccountValue(rs.getLong(1), rs.getString(2)));
	}

	/**
	 * The active elements of the person metatype that have a value of the property, with whether
	 * another account links them manually.
	 */
	public List<ElementValue> elementValues(UUID propertyDefId) {
		return this.jdbc.query("SELECT e.id, v.value_text, EXISTS (SELECT 1 FROM persons p WHERE p.entity_id = e.id) "
				+ "FROM entities e JOIN entity_property_values v ON v.entity_id = e.id AND v.property_def_id = ? "
				+ "WHERE e.id IN (" + PERSON_ELEMENTS.replace("SELECT e.id FROM", "SELECT e.id FROM")
				+ ") AND v.value_text IS NOT NULL ORDER BY e.id",
				(rs, n) -> new ElementValue(rs.getInt(1), rs.getString(2), rs.getBoolean(3)), propertyDefId);
	}

	/** Sets or, with {@code null}, removes the derived link of the account. */
	public void setMappedEntity(long personId, Integer entityId) {
		this.jdbc.update("UPDATE persons SET mapped_entity_id = ? WHERE id = ?", entityId, personId);
	}

	/** The derived links of all accounts that have one. */
	public Map<Long, Integer> mappedLinks() {
		Map<Long, Integer> links = new HashMap<>();
		this.jdbc.query("SELECT id, mapped_entity_id FROM persons WHERE mapped_entity_id IS NOT NULL",
				(rs) -> {
					links.put(rs.getLong(1), rs.getInt(2));
				});
		return links;
	}

}
