package io.oea.app.entity;

import io.oea.core.entity.TypedValue;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The metamodel rules for writing entities, loaded once per batch (design D2): metatypes with
 * their active property mappings (latest {@code valid_from} not after the reference date,
 * ADR-022), including those inherited from their base types, and the property definitions with
 * their constraints and enum values.
 */
public final class MetatypeRules {

	/** A property definition with the constraints of its data type. */
	public record Definition(UUID id, String name, String dataType, Integer maxLength, BigDecimal minValue,
			BigDecimal maxValue, String pattern, String visibilityMode, UUID refMetatypeId, Set<String> enumValues) {
	}

	/** The active mapping of a property definition to a metatype. */
	public record Mapping(Definition definition, String necessity, TypedValue defaultValue) {
	}

	/**
	 * A metatype with its active mappings in display order; a connection metatype with its allowed
	 * sources and targets, empty for every metatype (us-036-connection-metatypes D2).
	 */
	public record Metatype(UUID id, String name, boolean connection, Map<UUID, Mapping> mappings,
			Set<UUID> allowedSources, Set<UUID> allowedTargets) {

		public Metatype(UUID id, String name, boolean connection, Map<UUID, Mapping> mappings) {
			this(id, name, connection, mappings, Set.of(), Set.of());
		}

	}

	private final Map<UUID, Metatype> metatypes;

	private final Map<UUID, UUID> bases;

	private MetatypeRules(Map<UUID, Metatype> metatypes, Map<UUID, UUID> bases) {
		this.metatypes = metatypes;
		this.bases = bases;
	}

	/** Loads the rules that apply on the given date. */
	public static MetatypeRules load(JdbcTemplate jdbc, LocalDate date) {
		Map<UUID, Set<String>> enumValues = new HashMap<>();
		jdbc.query("SELECT property_def_id, value FROM property_enum_values", (rs) -> {
			enumValues.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new HashSet<>()).add(rs.getString(2));
		});
		Map<UUID, Definition> definitions = new HashMap<>();
		jdbc.query("""
				SELECT id, name, data_type, max_length, min_value, max_value, pattern, visibility_mode, ref_metatype_id
				FROM property_definitions""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			definitions.put(id, new Definition(id, rs.getString(2), rs.getString(3), rs.getObject(4, Integer.class),
					rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8),
					rs.getObject(9, UUID.class), Set.copyOf(enumValues.getOrDefault(id, Set.of()))));
		});
		Map<UUID, Metatype> own = new HashMap<>();
		Map<UUID, UUID> bases = new HashMap<>();
		jdbc.query("SELECT id, name, is_connection, base_metatype_id FROM metatypes", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			own.put(id, new Metatype(id, rs.getString(2), rs.getBoolean(3), new LinkedHashMap<>()));
			UUID base = rs.getObject(4, UUID.class);
			if (base != null) {
				bases.put(id, base);
			}
		});
		// the mapping with the latest valid_from per metatype and property (ADR-022)
		jdbc.query("""
				SELECT metatype_id, property_def_id, necessity, default_value_text, default_value_int, default_value_bool,
				       default_value_date, default_value_ref, default_value_enum
				FROM (SELECT DISTINCT ON (metatype_id, property_def_id) *
				      FROM metatype_property_mappings WHERE valid_from <= ?
				      ORDER BY metatype_id, property_def_id, valid_from DESC) active
				ORDER BY sort_order, property_def_id""", (rs) -> {
			Metatype metatype = own.get(rs.getObject(1, UUID.class));
			Definition definition = definitions.get(rs.getObject(2, UUID.class));
			Date defaultDate = rs.getDate(7);
			TypedValue defaultValue = new TypedValue(rs.getString(4), rs.getObject(5, Long.class),
					rs.getObject(6, Boolean.class), (defaultDate != null) ? defaultDate.toLocalDate() : null,
					rs.getObject(8, Integer.class), rs.getString(9));
			metatype.mappings()
				.put(definition.id(), new Mapping(definition, rs.getString(3),
						(defaultValue.asText() != null) ? defaultValue : null));
		}, Date.valueOf(date));
		Map<UUID, Set<UUID>> sources = new HashMap<>();
		Map<UUID, Set<UUID>> targets = new HashMap<>();
		jdbc.query("SELECT metatype_id, direction, allowed_metatype_id FROM metatype_connection_constraints", (rs) -> {
			("source".equals(rs.getString(2)) ? sources : targets)
				.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new HashSet<>())
				.add(rs.getObject(3, UUID.class));
		});
		// a metatype inherits the mappings of all its base types (us-032-metatype-editing D4)
		Map<UUID, Metatype> metatypes = new HashMap<>();
		own.forEach((id, metatype) -> {
			Map<UUID, Mapping> mappings = new LinkedHashMap<>();
			for (UUID type : chain(id, bases).reversed()) {
				mappings.putAll(own.get(type).mappings());
			}
			metatypes.put(id, new Metatype(id, metatype.name(), metatype.connection(), mappings,
					Set.copyOf(sources.getOrDefault(id, Set.of())), Set.copyOf(targets.getOrDefault(id, Set.of()))));
		});
		return new MetatypeRules(metatypes, bases);
	}

	/** The metatype and its base types, nearest first; stops at a cycle. */
	private static List<UUID> chain(UUID id, Map<UUID, UUID> bases) {
		List<UUID> chain = new ArrayList<>();
		for (UUID type = id; type != null && !chain.contains(type); type = bases.get(type)) {
			chain.add(type);
		}
		return chain;
	}

	/** Rules from the given metatypes (tests). */
	static MetatypeRules of(Metatype... metatypes) {
		Map<UUID, Metatype> byId = new HashMap<>();
		for (Metatype metatype : metatypes) {
			byId.put(metatype.id(), metatype);
		}
		return new MetatypeRules(byId, Map.of());
	}

	/** Rules from the given metatypes and base types (tests). */
	static MetatypeRules of(Map<UUID, UUID> bases, Metatype... metatypes) {
		Map<UUID, Metatype> byId = new HashMap<>();
		for (Metatype metatype : metatypes) {
			byId.put(metatype.id(), metatype);
		}
		return new MetatypeRules(byId, Map.copyOf(bases));
	}

	/**
	 * Whether the connection metatype allows an endpoint of this metatype as source or target: the
	 * metatype or one of its base types is listed, or nothing is listed (us-036-connection-metatypes D2).
	 */
	public boolean allows(Metatype connection, boolean source, UUID endpointMetatype) {
		Set<UUID> allowed = source ? connection.allowedSources() : connection.allowedTargets();
		return allowed.isEmpty() || chain(endpointMetatype, this.bases).stream().anyMatch(allowed::contains);
	}

	/** The metatype with this ID, or {@code null}. */
	public Metatype metatype(UUID id) {
		return this.metatypes.get(id);
	}

}
