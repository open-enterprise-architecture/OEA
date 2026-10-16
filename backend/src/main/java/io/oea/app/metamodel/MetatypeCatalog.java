package io.oea.app.metamodel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The metatypes of the instance configuration with their properties (design D8, UC-04 step 2). */
@Service
public class MetatypeCatalog {

	private final JdbcTemplate jdbc;

	public MetatypeCatalog(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	public record Category(UUID id, String name, String description, int sortOrder) {
	}

	public record Property(UUID id, String name, String dataType, Category category, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern, String scope,
			String sourcePackageId, String sourcePackageVersion, List<EnumValue> enumValues) {
	}

	public record Mapping(UUID id, Property property, String necessity, LocalDate validFrom, int sortOrder) {
	}

	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, List<Mapping> mappings) {
	}

	@Transactional(readOnly = true)
	public List<Metatype> list() {
		return query(null);
	}

	@Transactional(readOnly = true)
	public Metatype get(UUID id) {
		return query(id).stream().findFirst().orElseThrow(() -> new NoSuchElementException("no metatype " + id));
	}

	private List<Metatype> query(UUID id) {
		Map<UUID, Property> properties = properties();
		Map<UUID, List<Mapping>> mappings = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from, sort_order FROM metatype_property_mappings
				ORDER BY sort_order, id""", (rs) -> {
			mappings.computeIfAbsent(rs.getObject(2, UUID.class), (k) -> new ArrayList<>())
				.add(new Mapping(rs.getObject(1, UUID.class), properties.get(rs.getObject(3, UUID.class)),
						rs.getString(4), rs.getObject(5, LocalDate.class), rs.getInt(6)));
		});
		String sql = """
				SELECT id, name, abbreviation, is_connection, sort_order, scope, source_package_id, source_package_version
				FROM metatypes WHERE config_id = ?""" + ((id != null) ? " AND id = ?" : "") + " ORDER BY sort_order, name";
		Object[] arguments = (id != null) ? new Object[] { PackageImporter.INSTANCE_CONFIGURATION, id }
				: new Object[] { PackageImporter.INSTANCE_CONFIGURATION };
		return this.jdbc.query(sql, (rs, n) -> new Metatype(rs.getObject(1, UUID.class), rs.getString(2),
				rs.getString(3), rs.getBoolean(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8),
				List.copyOf(mappings.getOrDefault(rs.getObject(1, UUID.class), List.of()))), arguments);
	}

	private Map<UUID, Property> properties() {
		Map<UUID, Category> categories = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, name, description, sort_order FROM property_categories", (rs) -> {
			categories.put(rs.getObject(1, UUID.class),
					new Category(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)));
		});
		Map<UUID, List<EnumValue>> values = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, property_def_id, value, sort_order FROM property_enum_values ORDER BY sort_order",
				(rs) -> {
					values.computeIfAbsent(rs.getObject(2, UUID.class), (k) -> new ArrayList<>())
						.add(new EnumValue(rs.getObject(1, UUID.class), rs.getString(3), rs.getInt(4)));
				});
		Map<UUID, Property> properties = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT id, name, data_type, category_id, max_length, min_value, max_value, pattern, scope,
				       source_package_id, source_package_version FROM property_definitions""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new Property(id, rs.getString(2), rs.getString(3),
					categories.get(rs.getObject(4, UUID.class)), (Integer) rs.getObject(5), rs.getBigDecimal(6),
					rs.getBigDecimal(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
					List.copyOf(values.getOrDefault(id, List.of()))));
		});
		return properties;
	}

}
