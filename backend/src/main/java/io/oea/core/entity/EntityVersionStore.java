package io.oea.core.entity;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reconstructs the state of an entity at a version (V6, ADR-022; design D1 of
 * us-099-version-diff). A history row holds the value <em>before</em> the change of its version,
 * so the value of a field at version N is the value of its first history row after N, or the
 * current value when no later row exists. Nothing is stored per version.
 */
@Repository
public class EntityVersionStore {

	/** The system fields of {@code entities} with their history keys ({@code field_name}). */
	public static final String NAME = "name";

	public static final String DESCRIPTION = "description";

	public static final String LOGICAL = "is_logical";

	public static final String METATYPE = "metatype_id";

	public static final String SOURCE = "source_entity_id";

	public static final String TARGET = "target_entity_id";

	private final JdbcTemplate jdbc;

	public EntityVersionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A version header with the person who made it. */
	public record Header(int version, Instant changedAt, long changedBy, String changedByName) {
	}

	/**
	 * The state of an entity at a version: the system fields as text by their history key (the
	 * endpoints only for a connection), the property values that were set, and the properties
	 * mapped to the metatype of that version on its date with their mapping order.
	 */
	public record State(Header header, Map<String, String> systemFields, Map<UUID, TypedValue> values,
			Map<UUID, Integer> mapped) {

		/** The metatype of the entity at this version. */
		public UUID metatypeId() {
			return UUID.fromString(this.systemFields.get(METATYPE));
		}

	}

	/** The header of the version, if the entity has it. */
	public Optional<Header> header(int entityId, int version) {
		return this.jdbc.query("""
				SELECT h.version, h.changed_at, h.changed_by, p.name
				FROM entity_version_headers h JOIN persons p ON p.id = h.changed_by
				WHERE h.entity_id = ? AND h.version = ?""",
				(rs, n) -> new Header(rs.getInt(1), rs.getTimestamp(2).toInstant(), rs.getLong(3), rs.getString(4)),
				entityId, version)
			.stream()
			.findFirst();
	}

	/** The state at the version, if the entity has it. */
	public Optional<State> state(int entityId, int version) {
		Optional<Header> header = header(entityId, version);
		if (header.isEmpty()) {
			return Optional.empty();
		}
		Map<String, String> system = currentSystemFields(entityId);
		this.jdbc.query("""
				SELECT DISTINCT ON (field_name) field_name, old_value FROM entity_system_field_history
				WHERE entity_id = ? AND entity_version > ? ORDER BY field_name, entity_version, id""", (rs) -> {
			if (system.containsKey(rs.getString(1))) {
				system.put(rs.getString(1), rs.getString(2));
			}
		}, entityId, version);

		Map<UUID, TypedValue> values = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT property_def_id, value_text, value_int, value_bool, value_date, value_ref, value_enum
				FROM entity_property_values WHERE entity_id = ?""",
				(rs) -> {
					values.put(rs.getObject(1, UUID.class), value(rs));
				}, entityId);
		this.jdbc.query("""
				SELECT DISTINCT ON (property_def_id)
				       property_def_id, value_text, value_int, value_bool, value_date, value_ref, value_enum
				FROM entity_property_value_history
				WHERE entity_id = ? AND entity_version > ? ORDER BY property_def_id, entity_version, id""",
				(rs) -> {
					values.put(rs.getObject(1, UUID.class), value(rs));
				}, entityId, version);
		values.values().removeIf((value) -> value == null);

		Map<UUID, Integer> mapped = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT property_def_id, min(sort_order) FROM metatype_property_mappings
				WHERE metatype_id = ? AND valid_from <= (? AT TIME ZONE 'UTC')::date
				GROUP BY property_def_id ORDER BY min(sort_order), property_def_id""", (rs) -> {
			mapped.put(rs.getObject(1, UUID.class), rs.getInt(2));
		}, UUID.fromString(system.get(METATYPE)), header.get().changedAt().atOffset(ZoneOffset.UTC));
		return Optional.of(new State(header.get(), Collections.unmodifiableMap(system), Map.copyOf(values),
				Collections.unmodifiableMap(mapped)));
	}

	/** A property definition: its name and data type ({@code property_definitions.data_type}). */
	public record Definition(String name, String dataType) {
	}

	/** The property definitions with these IDs, retired or not. */
	public Map<UUID, Definition> definitions(Collection<UUID> ids) {
		Map<UUID, Definition> definitions = new HashMap<>();
		if (!ids.isEmpty()) {
			this.jdbc.query("SELECT id, name, data_type FROM property_definitions WHERE id = ANY(?)", (rs) -> {
				definitions.put(rs.getObject(1, UUID.class), new Definition(rs.getString(2), rs.getString(3)));
			}, (Object) ids.toArray(UUID[]::new));
		}
		return definitions;
	}

	/** The current names of entities, deleted or not. */
	public Map<Integer, String> entityNames(Collection<Integer> ids) {
		Map<Integer, String> names = new HashMap<>();
		if (!ids.isEmpty()) {
			this.jdbc.query("SELECT id, name FROM entities WHERE id = ANY(?)", (rs) -> {
				names.put(rs.getInt(1), rs.getString(2));
			}, (Object) ids.toArray(Integer[]::new));
		}
		return names;
	}

	/** The current names of metatypes. */
	public Map<UUID, String> metatypeNames(Collection<UUID> ids) {
		Map<UUID, String> names = new HashMap<>();
		if (!ids.isEmpty()) {
			this.jdbc.query("SELECT id, name FROM metatypes WHERE id = ANY(?)", (rs) -> {
				names.put(rs.getObject(1, UUID.class), rs.getString(2));
			}, (Object) ids.toArray(UUID[]::new));
		}
		return names;
	}

	private Map<String, String> currentSystemFields(int entityId) {
		Map<String, String> fields = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT e.name, e.description, e.is_logical, e.metatype_id, c.source_entity_id, c.target_entity_id
				FROM entities e LEFT JOIN entity_connections c ON c.entity_id = e.id
				WHERE e.id = ?""", (rs) -> {
			fields.put(NAME, rs.getString(1));
			fields.put(DESCRIPTION, rs.getString(2));
			fields.put(LOGICAL, Boolean.toString(rs.getBoolean(3)));
			fields.put(METATYPE, rs.getObject(4, UUID.class).toString());
			if (rs.getObject(5) != null) {
				fields.put(SOURCE, Integer.toString(rs.getInt(5)));
				fields.put(TARGET, Integer.toString(rs.getInt(6)));
			}
		}, entityId);
		return fields;
	}

	/** The typed value of a row, {@code null} when every value column is null. */
	private static TypedValue value(ResultSet rs) throws SQLException {
		String text = rs.getString(2);
		Long integer = (rs.getObject(3) != null) ? rs.getLong(3) : null;
		Boolean bool = (rs.getObject(4) != null) ? rs.getBoolean(4) : null;
		Date date = rs.getDate(5);
		Integer reference = (rs.getObject(6) != null) ? rs.getInt(6) : null;
		String enumValue = rs.getString(7);
		if (text == null && integer == null && bool == null && date == null && reference == null
				&& enumValue == null) {
			return null;
		}
		return new TypedValue(text, integer, bool, (date != null) ? date.toLocalDate() : null, reference, enumValue);
	}

}
