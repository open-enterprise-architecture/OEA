package io.oea.app.metamodel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The metatypes of the instance configuration with their properties (design D8, UC-04 step 2),
 * their base types, inherited mappings and re-import settings (us-032-metatype-editing D4, D8).
 */
@Service
public class MetatypeCatalog {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public MetatypeCatalog(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	public record Category(UUID id, String name, String description, int sortOrder, String scope, int version) {
	}

	public record Property(UUID id, String name, String dataType, Category category, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern, String scope,
			String sourcePackageId, String sourcePackageVersion, List<EnumValue> enumValues, String sqlColumnName,
			boolean retired, int version) {
	}

	/**
	 * A mapping entry (us-137-mapping-effective-date D1, D5).
	 *
	 * @param scope {@code organization} for an entry of an administrator, else the package scope
	 * @param state {@code past}, {@code active} or {@code future} relative to today
	 * @param inheritedFrom the base type that owns the mapping, {@code null} for an own mapping
	 */
	public record Mapping(UUID id, Property property, String necessity, LocalDate validFrom, int sortOrder,
			String scope, String state, MetatypeRef inheritedFrom) {

		Mapping inheritedFrom(MetatypeRef owner) {
			return new Mapping(this.id, this.property, this.necessity, this.validFrom, this.sortOrder, this.scope,
					this.state, owner);
		}

	}

	public record MetatypeRef(UUID id, String name, String abbreviation) {
	}

	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			String effectiveReimportPolicy, String architectureLayerId, List<Mapping> mappings, int version,
			List<Constraint> constraints, UUID configId) {
	}

	/** An allowed source or target of a connection metatype (us-036-connection-metatypes D2). */
	public record Constraint(String direction, MetatypeRef allowed) {
	}

	private record Row(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			String architectureLayerId, int version, UUID configId) {
	}

	/** The metatypes of the instance configuration. */
	@Transactional(readOnly = true)
	public List<Metatype> list() {
		return query(null, false, PackageImporter.INSTANCE_CONFIGURATION);
	}

	/**
	 * The effective metamodel of a configuration: for an extension the instance metatypes and its
	 * own (design D4 of us-037-solution-extensions-and-lock-mode).
	 *
	 * @throws NoSuchElementException if the configuration does not exist (404)
	 */
	@Transactional(readOnly = true)
	public List<Metatype> list(UUID configId) {
		Long exists = this.jdbc.queryForObject("SELECT count(*) FROM metamodel_configurations WHERE id = ?", Long.class,
				configId);
		if (exists == null || exists == 0) {
			throw new NoSuchElementException("no metamodel configuration " + configId);
		}
		return query(null, false, configId);
	}

	/** The metatype with the mappings in effect today. */
	@Transactional(readOnly = true)
	public Metatype get(UUID id) {
		return get(id, false);
	}

	/** The metatype; with history, every mapping entry ordered by property and date. */
	@Transactional(readOnly = true)
	public Metatype get(UUID id, boolean includeHistory) {
		UUID config = this.jdbc.queryForList("SELECT config_id FROM metatypes WHERE id = ?", UUID.class, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
		return query(id, includeHistory, config).stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
	}

	/** The property definitions that are not retired, by name. */
	@Transactional(readOnly = true)
	public List<Property> properties() {
		return propertyMap().values()
			.stream()
			.filter((property) -> !property.retired())
			.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
			.toList();
	}

	@Transactional(readOnly = true)
	public Property property(UUID id) {
		Property property = propertyMap().get(id);
		if (property == null || property.retired()) {
			throw new NoSuchElementException("no property definition " + id);
		}
		return property;
	}

	@Transactional(readOnly = true)
	public List<Category> categories() {
		return List.copyOf(categoryMap().values());
	}

	/** The global re-import setting of the instance configuration. */
	String globalReimportPolicy() {
		return this.jdbc.queryForObject("SELECT reimport_organization_mappings FROM metamodel_configurations WHERE id = ?",
				String.class, PackageImporter.INSTANCE_CONFIGURATION);
	}

	/** The re-import setting that applies to each metatype (design D8). */
	Map<UUID, String> effectiveReimportPolicies() {
		Map<UUID, String> own = new HashMap<>();
		Map<UUID, UUID> bases = new HashMap<>();
		this.jdbc.query("SELECT id, reimport_organization_mappings, base_metatype_id FROM metatypes", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			own.put(id, rs.getString(2));
			if (rs.getObject(3, UUID.class) != null) {
				bases.put(id, rs.getObject(3, UUID.class));
			}
		});
		String global = globalReimportPolicy();
		Map<UUID, String> effective = new HashMap<>();
		own.keySet().forEach((id) -> effective.put(id, MetamodelEffects.chain(id, bases)
			.stream()
			.map(own::get)
			.filter((policy) -> !"inherit".equals(policy))
			.findFirst()
			.orElse(global)));
		return effective;
	}

	/** Metatypes of the instance and of {@code config}; base types cross the boundary (design D4). */
	private List<Metatype> query(UUID id, boolean includeHistory, UUID config) {
		Map<UUID, Map<UUID, List<Mapping>>> own = entries(includeHistory);
		Map<UUID, Row> rows = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, name, abbreviation, is_connection, sort_order, scope, source_package_id, source_package_version,
				       base_metatype_id, reimport_organization_mappings, architecture_layer_id, version, config_id
				FROM metatypes WHERE config_id IN (?, ?) ORDER BY sort_order, name""", (rs) -> {
			UUID metatype = rs.getObject(1, UUID.class);
			rows.put(metatype, new Row(metatype, rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5),
					rs.getString(6), rs.getString(7), rs.getString(8), rs.getObject(9, UUID.class), rs.getString(10),
					rs.getString(11), rs.getInt(12), rs.getObject(13, UUID.class)));
		}, PackageImporter.INSTANCE_CONFIGURATION, config);
		Map<UUID, UUID> bases = new HashMap<>();
		rows.values().stream().filter((row) -> row.baseMetatypeId() != null)
			.forEach((row) -> bases.put(row.id(), row.baseMetatypeId()));
		Map<UUID, String> policies = effectiveReimportPolicies();
		Map<UUID, List<Constraint>> constraints = new HashMap<>();
		this.jdbc.query("""
				SELECT metatype_id, direction, allowed_metatype_id FROM metatype_connection_constraints
				ORDER BY metatype_id, direction, allowed_metatype_id""", (rs) -> {
			Row allowed = rows.get(rs.getObject(3, UUID.class));
			if (allowed != null) {
				constraints.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
					.add(new Constraint(rs.getString(2),
							new MetatypeRef(allowed.id(), allowed.name(), allowed.abbreviation())));
			}
		});
		List<Metatype> metatypes = new ArrayList<>();
		for (Row row : rows.values()) {
			if (id != null && !id.equals(row.id())) {
				continue;
			}
			List<Mapping> mappings = new ArrayList<>();
			for (UUID type : MetamodelEffects.chain(row.id(), bases).reversed()) {
				Row owner = rows.get(type);
				MetatypeRef inheritedFrom = type.equals(row.id()) ? null
						: new MetatypeRef(owner.id(), owner.name(), owner.abbreviation());
				own.getOrDefault(type, Map.of())
					.values()
					.forEach((entries) -> entries.forEach((mapping) -> mappings.add(mapping.inheritedFrom(inheritedFrom))));
			}
			metatypes.add(new Metatype(row.id(), row.name(), row.abbreviation(), row.connection(), row.sortOrder(),
					row.scope(), row.sourcePackageId(), row.sourcePackageVersion(), row.baseMetatypeId(),
					row.reimportPolicy(), policies.get(row.id()), row.architectureLayerId(), List.copyOf(mappings),
					row.version(), List.copyOf(constraints.getOrDefault(row.id(), List.of())), row.configId()));
		}
		return metatypes;
	}

	/**
	 * The mapping entries per metatype and property, in display order; without history only the
	 * entry in effect today (latest {@code valid_from} not after today, ADR-022).
	 */
	private Map<UUID, Map<UUID, List<Mapping>>> entries(boolean includeHistory) {
		Map<UUID, Property> properties = propertyMap();
		LocalDate today = LocalDate.now(this.clock);
		record Entry(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, LocalDate validFrom,
				int sortOrder, String scope) {
		}
		List<Entry> rows = this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from, sort_order, scope
				FROM metatype_property_mappings ORDER BY sort_order, property_def_id, valid_from""",
				(rs, n) -> new Entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, LocalDate.class), rs.getInt(6),
						rs.getString(7)));
		Map<List<UUID>, LocalDate> active = new HashMap<>();
		for (Entry row : rows) {
			if (!row.validFrom().isAfter(today)) {
				active.merge(List.of(row.metatypeId(), row.propertyDefId()), row.validFrom(),
						(a, b) -> a.isAfter(b) ? a : b);
			}
		}
		Map<UUID, Map<UUID, List<Mapping>>> entries = new LinkedHashMap<>();
		for (Entry row : rows) {
			LocalDate activeFrom = active.get(List.of(row.metatypeId(), row.propertyDefId()));
			String state = row.validFrom().isAfter(today) ? "future"
					: row.validFrom().equals(activeFrom) ? "active" : "past";
			if (includeHistory || "active".equals(state)) {
				entries.computeIfAbsent(row.metatypeId(), (k) -> new LinkedHashMap<>())
					.computeIfAbsent(row.propertyDefId(), (k) -> new ArrayList<>())
					.add(new Mapping(row.id(), properties.get(row.propertyDefId()), row.necessity(), row.validFrom(),
							row.sortOrder(), row.scope(), state, null));
			}
		}
		return entries;
	}

	private Map<UUID, Category> categoryMap() {
		Map<UUID, Category> categories = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, name, description, sort_order, scope, version FROM property_categories ORDER BY sort_order, name",
				(rs) -> {
					categories.put(rs.getObject(1, UUID.class), new Category(rs.getObject(1, UUID.class),
							rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getInt(6)));
				});
		return categories;
	}

	private Map<UUID, Property> propertyMap() {
		Map<UUID, Category> categories = categoryMap();
		Map<UUID, List<EnumValue>> values = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, property_def_id, value, sort_order FROM property_enum_values ORDER BY sort_order, value",
				(rs) -> {
					values.computeIfAbsent(rs.getObject(2, UUID.class), (k) -> new ArrayList<>())
						.add(new EnumValue(rs.getObject(1, UUID.class), rs.getString(3), rs.getInt(4)));
				});
		Map<UUID, Property> properties = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, name, data_type, category_id, max_length, min_value, max_value, pattern, scope,
				       source_package_id, source_package_version, sql_column_name, retired_at IS NOT NULL, version
				FROM property_definitions""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new Property(id, rs.getString(2), rs.getString(3),
					categories.get(rs.getObject(4, UUID.class)), (Integer) rs.getObject(5), rs.getBigDecimal(6),
					rs.getBigDecimal(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
					List.copyOf(values.getOrDefault(id, List.of())), rs.getString(12), rs.getBoolean(13), rs.getInt(14)));
		});
		return properties;
	}

}
