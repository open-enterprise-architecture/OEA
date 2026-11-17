package io.oea.core.solution;

import io.oea.core.entity.TypedValue;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The rows the go-live of a solution reads and writes (design D1, D3, D4 of
 * us-152-solution-go-live): the solution, its deltas with their field values, the entity a delta
 * {@code new} created, the status, and the resolution of field conflicts; the open
 * issues live in {@link OpenIssueStore}.
 */
@Repository
public class GoLiveStore {

	private final JdbcTemplate jdbc;

	public GoLiveStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A solution with its name and status. */
	public record SolutionRow(UUID id, String name, String status) {
	}

	/**
	 * A field of a delta: a system field ({@code name}, {@code description}) or a property; the
	 * before value recorded by a delta {@code modified} and the after value, {@code null} without
	 * a value.
	 */
	public record FieldRow(String systemField, UUID propertyDefId, TypedValue before, TypedValue after) {

		/** The key of the field: the system field or the property ID. */
		public String key() {
			return (this.systemField != null) ? this.systemField : this.propertyDefId.toString();
		}

	}

	/** A delta with its fields; {@code entityId} is {@code null} for a delta {@code new} before the go-live. */
	public record DeltaRow(UUID id, String type, Integer entityId, UUID metatypeId, String name, String description,
			List<FieldRow> fields) {
	}

	/** An open issue with the name of its entity. */
	/** The solution, without a lock. */
	public Optional<SolutionRow> find(UUID id) {
		return this.jdbc.query("SELECT id, name, status FROM solutions WHERE id = ?", GoLiveStore::solution, id)
			.stream()
			.findFirst();
	}

	/** The solution, locked for the rest of the transaction. */
	public Optional<SolutionRow> lock(UUID id) {
		return this.jdbc.query("SELECT id, name, status FROM solutions WHERE id = ? FOR UPDATE", GoLiveStore::solution,
				id)
			.stream()
			.findFirst();
	}

	/** The deltas of the solution in capture order, with their fields. */
	public List<DeltaRow> deltas(UUID solutionId) {
		Map<UUID, DeltaRow> deltas = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, delta_type, entity_id, metatype_id, name, description FROM entity_deltas
				WHERE solution_id = ? ORDER BY created_at, id""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			deltas.put(id, new DeltaRow(id, rs.getString(2), rs.getObject(3, Integer.class), rs.getObject(4, UUID.class),
					rs.getString(5), rs.getString(6), new ArrayList<>()));
		}, solutionId);
		if (deltas.isEmpty()) {
			return List.of();
		}
		this.jdbc.query("""
				SELECT v.delta_id, v.system_field, v.property_def_id,
				       v.before_value_text, v.before_value_int, v.before_value_bool, v.before_value_date,
				       v.before_value_ref, v.before_value_enum,
				       v.after_value_text, v.after_value_int, v.after_value_bool, v.after_value_date,
				       v.after_value_ref, v.after_value_enum
				FROM entity_delta_property_values v JOIN entity_deltas d ON d.id = v.delta_id
				WHERE d.solution_id = ? ORDER BY v.system_field NULLS LAST, v.property_def_id""", (rs) -> {
			DeltaRow delta = deltas.get(rs.getObject(1, UUID.class));
			delta.fields()
				.add(new FieldRow(rs.getString(2), rs.getObject(3, UUID.class), typed(rs, 4), typed(rs, 10)));
		}, solutionId);
		return List.copyOf(deltas.values());
	}

	/** Records the entity the go-live created for a delta {@code new}. */
	/** The target plateau of the solution; empty in project mode. */
	public Optional<UUID> plateauOf(UUID solutionId) {
		return this.jdbc.queryForList("SELECT to_plateau_id FROM solutions WHERE id = ?", UUID.class, solutionId)
			.stream()
			.filter(Objects::nonNull)
			.findFirst();
	}

	public void setDeltaEntity(UUID deltaId, int entityId) {
		this.jdbc.update("UPDATE entity_deltas SET entity_id = ? WHERE id = ?", entityId, deltaId);
	}

	/** Sets the status of the solution. */
	public void setStatus(UUID solutionId, String status) {
		this.jdbc.update("UPDATE solutions SET status = ? WHERE id = ?", status, solutionId);
	}

	/**
	 * Replaces a field of a delta {@code modified} by a resolution: the current value as before
	 * value and the chosen after value.
	 */
	public void replaceField(UUID deltaId, FieldRow field) {
		removeField(deltaId, field.systemField(), field.propertyDefId());
		TypedValue before = field.before();
		TypedValue after = field.after();
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, system_field, property_def_id,
				    before_value_text, before_value_int, before_value_bool, before_value_date, before_value_ref,
				    before_value_enum, after_value_text, after_value_int, after_value_bool, after_value_date,
				    after_value_ref, after_value_enum)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), deltaId,
				field.systemField(), field.propertyDefId(), text(before), integer(before), bool(before), date(before),
				reference(before), enumValue(before), text(after), integer(after), bool(after), date(after),
				reference(after), enumValue(after));
	}

	/** Removes a field from a delta. */
	public void removeField(UUID deltaId, String systemField, UUID propertyDefId) {
		if (systemField != null) {
			this.jdbc.update("DELETE FROM entity_delta_property_values WHERE delta_id = ? AND system_field = ?", deltaId,
					systemField);
		}
		else {
			this.jdbc.update("DELETE FROM entity_delta_property_values WHERE delta_id = ? AND property_def_id = ?",
					deltaId, propertyDefId);
		}
	}

	/** Records who changed the delta last. */
	public void touch(UUID deltaId, long personId, Instant now) {
		this.jdbc.update("UPDATE entity_deltas SET updated_at = ?, updated_by = ? WHERE id = ?", Timestamp.from(now),
				personId, deltaId);
	}

	private static SolutionRow solution(ResultSet rs, int row) throws SQLException {
		return new SolutionRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3));
	}

	private static TypedValue typed(ResultSet rs, int first) throws SQLException {
		Date date = rs.getDate(first + 3);
		TypedValue value = new TypedValue(rs.getString(first), rs.getObject(first + 1, Long.class),
				rs.getObject(first + 2, Boolean.class), (date != null) ? date.toLocalDate() : null,
				rs.getObject(first + 4, Integer.class), rs.getString(first + 5));
		return (value.asText() != null) ? value : null;
	}

	private static String text(TypedValue value) {
		return (value != null) ? value.text() : null;
	}

	private static Long integer(TypedValue value) {
		return (value != null) ? value.integer() : null;
	}

	private static Boolean bool(TypedValue value) {
		return (value != null) ? value.bool() : null;
	}

	private static Date date(TypedValue value) {
		return (value != null && value.date() != null) ? Date.valueOf(value.date()) : null;
	}

	private static Integer reference(TypedValue value) {
		return (value != null) ? value.reference() : null;
	}

	private static String enumValue(TypedValue value) {
		return (value != null) ? value.enumValue() : null;
	}

}
