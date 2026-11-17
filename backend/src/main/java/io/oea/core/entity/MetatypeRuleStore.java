package io.oea.core.entity;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The rows the write rules of entities are built from (ADR-051: SQL only in core): property
 * definitions with their enumeration values, the metatypes of a configuration, the mappings
 * active on a date (the latest {@code valid_from} per metatype and property, ADR-022) and the
 * endpoint constraints of connection metatypes. {@code app.entity.MetatypeRules} assembles them.
 */
@Repository
public class MetatypeRuleStore {

	private final JdbcTemplate jdbc;

	public MetatypeRuleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A property definition with the constraints of its data type and its enumeration values. */
	public record DefinitionRow(UUID id, String name, String dataType, Integer maxLength, BigDecimal minValue,
			BigDecimal maxValue, String pattern, String visibilityMode, UUID refMetatypeId, Set<String> enumValues) {
	}

	/** A metatype of the configuration, with its direct base type or {@code null}. */
	public record MetatypeRow(UUID id, String name, boolean connection, UUID baseId) {
	}

	/** A mapping active on the date, in display order; {@code defaultValue} is {@code null} without one. */
	public record MappingRow(UUID metatypeId, UUID propertyDefId, String necessity, TypedValue defaultValue) {
	}

	/** An allowed endpoint metatype of a connection metatype. */
	public record ConstraintRow(UUID metatypeId, boolean source, UUID allowedMetatypeId) {
	}

	/** Every property definition by ID. */
	public Map<UUID, DefinitionRow> definitions() {
		Map<UUID, Set<String>> enumValues = new HashMap<>();
		this.jdbc.query("SELECT property_def_id, value FROM property_enum_values", (rs) -> {
			enumValues.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new HashSet<>()).add(rs.getString(2));
		});
		Map<UUID, DefinitionRow> definitions = new HashMap<>();
		this.jdbc.query("""
				SELECT id, name, data_type, max_length, min_value, max_value, pattern, visibility_mode, ref_metatype_id
				FROM property_definitions""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			definitions.put(id, new DefinitionRow(id, rs.getString(2), rs.getString(3), rs.getObject(4, Integer.class),
					rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8),
					rs.getObject(9, UUID.class), Set.copyOf(enumValues.getOrDefault(id, Set.of()))));
		});
		return definitions;
	}

	/** The metatypes of the configuration. */
	public List<MetatypeRow> metatypes(UUID configId) {
		return this.jdbc.query("SELECT id, name, is_connection, base_metatype_id FROM metatypes WHERE config_id = ?",
				(rs, n) -> new MetatypeRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3),
						rs.getObject(4, UUID.class)),
				configId);
	}

	/** The mappings active on the date, ordered by display order. */
	public List<MappingRow> mappings(LocalDate date) {
		List<MappingRow> rows = new ArrayList<>();
		this.jdbc.query("""
				SELECT metatype_id, property_def_id, necessity, default_value_text, default_value_int, default_value_bool,
				       default_value_date, default_value_ref, default_value_enum
				FROM (SELECT DISTINCT ON (metatype_id, property_def_id) *
				      FROM metatype_property_mappings WHERE valid_from <= ?
				      ORDER BY metatype_id, property_def_id, valid_from DESC) active
				ORDER BY sort_order, property_def_id""", (rs) -> {
			Date defaultDate = rs.getDate(7);
			TypedValue defaultValue = new TypedValue(rs.getString(4), rs.getObject(5, Long.class),
					rs.getObject(6, Boolean.class), (defaultDate != null) ? defaultDate.toLocalDate() : null,
					rs.getObject(8, Integer.class), rs.getString(9));
			rows.add(new MappingRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
					(defaultValue.asText() != null) ? defaultValue : null));
		}, Date.valueOf(date));
		return rows;
	}

	/** The endpoint constraints of all connection metatypes. */
	public List<ConstraintRow> constraints() {
		return this.jdbc.query("SELECT metatype_id, direction, allowed_metatype_id FROM metatype_connection_constraints",
				(rs, n) -> new ConstraintRow(rs.getObject(1, UUID.class), "source".equals(rs.getString(2)),
						rs.getObject(3, UUID.class)));
	}

}
