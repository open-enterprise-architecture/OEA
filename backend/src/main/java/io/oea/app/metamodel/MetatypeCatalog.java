package io.oea.app.metamodel;

import java.math.BigDecimal;
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

	public MetatypeCatalog(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	public record Category(UUID id, String name, String description, int sortOrder, String scope) {
	}

	public record Property(UUID id, String name, String dataType, Category category, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern, String scope,
			String sourcePackageId, String sourcePackageVersion, List<EnumValue> enumValues, String sqlColumnName,
			boolean retired) {
	}

	/**
	 * A mapping; its scope is the scope of its property definition.
	 *
	 * @param inheritedFrom the base type that owns the mapping, {@code null} for an own mapping
	 */
	public record Mapping(UUID id, Property property, String necessity, LocalDate validFrom, int sortOrder,
			MetatypeRef inheritedFrom) {

		public String scope() {
			return this.property.scope();
		}

	}

	public record MetatypeRef(UUID id, String name, String abbreviation) {
	}

	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			String effectiveReimportPolicy, String architectureLayerId, List<Mapping> mappings) {
	}

	private record Row(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			String architectureLayerId) {
	}

	@Transactional(readOnly = true)
	public List<Metatype> list() {
		return query(null);
	}

	@Transactional(readOnly = true)
	public Metatype get(UUID id) {
		return query(id).stream().findFirst().orElseThrow(() -> new NoSuchElementException("no metatype " + id));
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

	private List<Metatype> query(UUID id) {
		Map<UUID, Property> properties = propertyMap();
		Map<UUID, List<Mapping>> own = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from, sort_order FROM metatype_property_mappings
				ORDER BY sort_order, id""", (rs) -> {
			own.computeIfAbsent(rs.getObject(2, UUID.class), (k) -> new ArrayList<>())
				.add(new Mapping(rs.getObject(1, UUID.class), properties.get(rs.getObject(3, UUID.class)),
						rs.getString(4), rs.getObject(5, LocalDate.class), rs.getInt(6), null));
		});
		Map<UUID, Row> rows = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, name, abbreviation, is_connection, sort_order, scope, source_package_id, source_package_version,
				       base_metatype_id, reimport_organization_mappings, architecture_layer_id
				FROM metatypes WHERE config_id = ? ORDER BY sort_order, name""", (rs) -> {
			UUID metatype = rs.getObject(1, UUID.class);
			rows.put(metatype, new Row(metatype, rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5),
					rs.getString(6), rs.getString(7), rs.getString(8), rs.getObject(9, UUID.class), rs.getString(10),
					rs.getString(11)));
		}, PackageImporter.INSTANCE_CONFIGURATION);
		Map<UUID, UUID> bases = new HashMap<>();
		rows.values().stream().filter((row) -> row.baseMetatypeId() != null)
			.forEach((row) -> bases.put(row.id(), row.baseMetatypeId()));
		Map<UUID, String> policies = effectiveReimportPolicies();
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
				own.getOrDefault(type, List.of())
					.forEach((mapping) -> mappings.add(new Mapping(mapping.id(), mapping.property(), mapping.necessity(),
							mapping.validFrom(), mapping.sortOrder(), inheritedFrom)));
			}
			metatypes.add(new Metatype(row.id(), row.name(), row.abbreviation(), row.connection(), row.sortOrder(),
					row.scope(), row.sourcePackageId(), row.sourcePackageVersion(), row.baseMetatypeId(),
					row.reimportPolicy(), policies.get(row.id()), row.architectureLayerId(), List.copyOf(mappings)));
		}
		return metatypes;
	}

	private Map<UUID, Category> categoryMap() {
		Map<UUID, Category> categories = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, name, description, sort_order, scope FROM property_categories ORDER BY sort_order, name",
				(rs) -> {
					categories.put(rs.getObject(1, UUID.class), new Category(rs.getObject(1, UUID.class),
							rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)));
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
				       source_package_id, source_package_version, sql_column_name, retired_at IS NOT NULL
				FROM property_definitions""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new Property(id, rs.getString(2), rs.getString(3),
					categories.get(rs.getObject(4, UUID.class)), (Integer) rs.getObject(5), rs.getBigDecimal(6),
					rs.getBigDecimal(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
					List.copyOf(values.getOrDefault(id, List.of())), rs.getString(12), rs.getBoolean(13)));
		});
		return properties;
	}

}
