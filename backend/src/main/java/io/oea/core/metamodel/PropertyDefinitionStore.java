package io.oea.core.metamodel;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The property definitions with their enumeration values and the property categories (design D2,
 * D5 of us-032-metatype-editing; ADR-022): reads for the metamodel catalog, writes of the
 * organization's content, and the content a package installs.
 */
@Repository
public class PropertyDefinitionStore {

	private final JdbcTemplate jdbc;

	public PropertyDefinitionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A property category. */
	public record CategoryRow(UUID id, String name, String description, int sortOrder, String scope, int version) {
	}

	/** An enumeration value. */
	public record EnumRow(UUID id, UUID propertyDefId, String value, int sortOrder) {
	}

	/** A property definition, retired ones included. */
	public record PropertyRow(UUID id, String name, String dataType, UUID categoryId, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern, String scope, String sourcePackageId,
			String sourcePackageVersion, String sqlColumnName, boolean retired, int version) {
	}

	/** The stored fields of a property definition that is not retired. */
	public record StoredRow(String name, String dataType, UUID categoryId, Integer maxLength, BigDecimal minValue,
			BigDecimal maxValue, String pattern, String scope) {
	}

	/** The fields of an organization's property definition. */
	public record Fields(String name, String dataType, UUID categoryId, Integer maxLength, BigDecimal minValue,
			BigDecimal maxValue, String pattern) {
	}

	/** The categories by sort order and name. */
	public List<CategoryRow> categories() {
		return this.jdbc.query(
				"SELECT id, name, description, sort_order, scope, version FROM property_categories ORDER BY sort_order, name",
				(rs, n) -> new CategoryRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
						rs.getString(5), rs.getInt(6)));
	}

	/** The enumeration values by sort order and value. */
	public List<EnumRow> enumValues() {
		return this.jdbc.query("SELECT id, property_def_id, value, sort_order FROM property_enum_values ORDER BY sort_order, value",
				(rs, n) -> new EnumRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
						rs.getInt(4)));
	}

	/** All property definitions, retired ones included. */
	public List<PropertyRow> properties() {
		return this.jdbc.query("""
				SELECT id, name, data_type, category_id, max_length, min_value, max_value, pattern, scope,
				       source_package_id, source_package_version, sql_column_name, retired_at IS NOT NULL, version
				FROM property_definitions""",
				(rs, n) -> new PropertyRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getObject(4, UUID.class), (Integer) rs.getObject(5), rs.getBigDecimal(6),
						rs.getBigDecimal(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11),
						rs.getString(12), rs.getBoolean(13), rs.getInt(14)));
	}

	public Optional<StoredRow> stored(UUID id) {
		return this.jdbc.query("""
				SELECT name, data_type, category_id, max_length, min_value, max_value, pattern, scope
				FROM property_definitions WHERE id = ? AND retired_at IS NULL""",
				(rs, n) -> new StoredRow(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class),
						rs.getObject(4, Integer.class), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7),
						rs.getString(8)),
				id)
			.stream()
			.findFirst();
	}

	/** Whether another definition than {@code excluded} (may be {@code null}) has the name, case-insensitively. */
	public boolean nameTaken(String name, UUID excluded) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_definitions WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid)",
				Boolean.class, name, excluded));
	}

	/** Whether a definition has the SQL column name. */
	public boolean columnNameTaken(String column) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_definitions WHERE sql_column_name = ?)", Boolean.class, column));
	}

	public void insert(UUID id, Fields fields, String column, Long personId) {
		this.jdbc.update("""
				INSERT INTO property_definitions (id, name, sql_column_name, data_type, category_id, max_length, min_value,
				    max_value, pattern, scope, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'organization', ?)""", id,
				fields.name(), column, fields.dataType(), fields.categoryId(), fields.maxLength(), fields.minValue(),
				fields.maxValue(), fields.pattern(), personId);
	}

	public void update(UUID id, Fields fields) {
		this.jdbc.update("""
				UPDATE property_definitions SET name = ?, data_type = ?, category_id = ?, max_length = ?, min_value = ?,
				       max_value = ?, pattern = ? WHERE id = ?""", fields.name(), fields.dataType(),
				fields.categoryId(), fields.maxLength(), fields.minValue(), fields.maxValue(), fields.pattern(), id);
	}

	/** Marks the definition as retired, because the entity history references it. */
	public void retire(UUID id, Instant now) {
		this.jdbc.update("UPDATE property_definitions SET retired_at = ? WHERE id = ?", Timestamp.from(now), id);
	}

	/** Deletes the definition with its enumeration values and visibility roles. */
	public void delete(UUID id) {
		deleteEnumValues(id);
		this.jdbc.update("DELETE FROM property_visibility_roles WHERE property_def_id = ?", id);
		this.jdbc.update("DELETE FROM property_definitions WHERE id = ?", id);
	}

	public void deleteEnumValues(UUID propertyDefId) {
		this.jdbc.update("DELETE FROM property_enum_values WHERE property_def_id = ?", propertyDefId);
	}

	public boolean enumValueExists(UUID propertyDefId, String value) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_enum_values WHERE property_def_id = ? AND value = ?)",
				Boolean.class, propertyDefId, value));
	}

	/** The next sort order of the enumeration values of the property. */
	public int nextEnumOrder(UUID propertyDefId) {
		return this.jdbc.queryForObject(
				"SELECT coalesce(max(sort_order) + 1, 0) FROM property_enum_values WHERE property_def_id = ?",
				Integer.class, propertyDefId);
	}

	public void insertEnumValue(UUID id, UUID propertyDefId, String value, int sortOrder) {
		this.jdbc.update("INSERT INTO property_enum_values (id, property_def_id, value, sort_order) VALUES (?, ?, ?, ?)",
				id, propertyDefId, value, sortOrder);
	}

	/** The text of the enumeration value of the property. */
	public Optional<String> enumValue(UUID valueId, UUID propertyDefId) {
		return this.jdbc
			.queryForList("SELECT value FROM property_enum_values WHERE id = ? AND property_def_id = ?", String.class,
					valueId, propertyDefId)
			.stream()
			.findFirst();
	}

	public void deleteEnumValue(UUID valueId) {
		this.jdbc.update("DELETE FROM property_enum_values WHERE id = ?", valueId);
	}

	public boolean categoryExists(UUID id) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_categories WHERE id = ?)", Boolean.class, id));
	}

	public boolean categoryNameTaken(String name, UUID excluded) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_categories WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid)",
				Boolean.class, name, excluded));
	}

	public boolean categoryInUse(UUID id) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_definitions WHERE category_id = ?)", Boolean.class, id));
	}

	public Optional<String> categoryScope(UUID id) {
		return this.jdbc.queryForList("SELECT scope FROM property_categories WHERE id = ?", String.class, id)
			.stream()
			.findFirst();
	}

	public void insertCategory(UUID id, String name, int sortOrder, String description) {
		this.jdbc.update("""
				INSERT INTO property_categories (id, name, sort_order, description, scope)
				VALUES (?, ?, ?, ?, 'organization')""", id, name, sortOrder, description);
	}

	public void updateCategory(UUID id, String name, int sortOrder, String description) {
		this.jdbc.update("UPDATE property_categories SET name = ?, sort_order = ?, description = ? WHERE id = ?", name,
				sortOrder, description, id);
	}

	public void deleteCategory(UUID id) {
		this.jdbc.update("DELETE FROM property_categories WHERE id = ?", id);
	}

	/** Existing categories with the identifier or the name of an imported one. */
	public List<ContentOwner> categoryOwners(UUID id, String name) {
		return this.jdbc.query("""
				SELECT scope, source_package_id FROM property_categories
				WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)""",
				(rs, n) -> new ContentOwner(rs.getString(1), rs.getString(2)), id, name, id);
	}

	/** Existing definitions with the identifier, the name or the column name of an imported one. */
	public List<ContentOwner> propertyOwners(UUID id, String name, String column) {
		return this.jdbc.query("""
				SELECT scope, source_package_id FROM property_definitions
				WHERE id = ? OR ((lower(name) = lower(?) OR sql_column_name = ?) AND id <> ?)""",
				(rs, n) -> new ContentOwner(rs.getString(1), rs.getString(2)), id, name, column, id);
	}

	/** Definitions that hold the enumeration value of an imported one under another property. */
	public List<ContentOwner> enumValueOwners(UUID valueId, UUID propertyDefId) {
		return this.jdbc.query("""
				SELECT d.scope, d.source_package_id FROM property_enum_values v
				JOIN property_definitions d ON d.id = v.property_def_id
				WHERE v.id = ? AND v.property_def_id <> ?""",
				(rs, n) -> new ContentOwner(rs.getString(1), rs.getString(2)), valueId, propertyDefId);
	}

	public void upsertImportedCategory(UUID id, String name, int sortOrder, String description, String packageId,
			String packageVersion) {
		this.jdbc.update("""
				INSERT INTO property_categories (id, name, sort_order, description, scope, source_package_id,
				    source_package_version) VALUES (?, ?, ?, ?, 'imported', ?, ?)
				ON CONFLICT (id) DO UPDATE SET name = excluded.name, sort_order = excluded.sort_order,
				    description = excluded.description, source_package_version = excluded.source_package_version,
				    version = property_categories.version + 1""", id, name, sortOrder, description, packageId,
				packageVersion);
	}

	/** Installs the definition of a package; the enumeration values are replaced by {@code values}. */
	public void upsertImportedProperty(UUID id, String name, String column, String dataType, UUID categoryId,
			UUID refMetatypeId, Integer maxLength, BigDecimal minValue, BigDecimal maxValue, String pattern,
			String packageId, String packageVersion, List<EnumRow> values) {
		this.jdbc.update("""
				INSERT INTO property_definitions (id, name, sql_column_name, data_type, category_id, ref_metatype_id,
				    max_length, min_value, max_value, pattern, scope, source_package_id, source_package_version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'imported', ?, ?)
				ON CONFLICT (id) DO UPDATE SET name = excluded.name, data_type = excluded.data_type,
				    category_id = excluded.category_id, ref_metatype_id = excluded.ref_metatype_id,
				    max_length = excluded.max_length, min_value = excluded.min_value, max_value = excluded.max_value,
				    pattern = excluded.pattern, source_package_version = excluded.source_package_version,
				    version = property_definitions.version + 1""", id, name, column, dataType, categoryId,
				refMetatypeId, maxLength, minValue, maxValue, pattern, packageId, packageVersion);
		deleteEnumValues(id);
		for (EnumRow value : values) {
			insertEnumValue(value.id(), id, value.value(), value.sortOrder());
		}
	}

}
