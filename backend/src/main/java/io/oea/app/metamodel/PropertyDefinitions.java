package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.PropertyValueStore;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organization's own property definitions with their enum values, and property categories
 * (design D2, D5 of us-032-metatype-editing; ADR-022). Built-in and imported content is read-only.
 */
@Service
public class PropertyDefinitions {

	static final int NAME_MAX_LENGTH = 100;

	/** Data types an organization property can have; {@code reference} follows with connection metatypes. */
	static final Set<String> DATA_TYPES = Set.of("string", "text", "integer", "boolean", "date", "url", "enum");

	private final JdbcTemplate jdbc;

	private final PropertyValueStore values;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	public PropertyDefinitions(JdbcTemplate jdbc, PropertyValueStore values, MetamodelEffects effects,
			MetamodelAudit audit) {
		this.jdbc = jdbc;
		this.values = values;
		this.effects = effects;
		this.audit = audit;
	}

	/** The writable fields of a property definition. */
	public record Input(String name, String dataType, UUID categoryId, UUID refMetatypeId, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern) {
	}

	/** The writable fields of a category. */
	public record CategoryInput(String name, String description, int sortOrder) {
	}

	private record Stored(String name, String dataType, UUID categoryId, Integer maxLength, BigDecimal minValue,
			BigDecimal maxValue, String pattern, String scope) {
	}

	@Transactional
	public UUID create(OeaPrincipal principal, Input input) {
		this.effects.lock();
		String name = validate(input, null);
		UUID id = UUID.randomUUID();
		String column = StorageNames.forNewProperty(name, (candidate) -> exists(
				"SELECT EXISTS (SELECT 1 FROM property_definitions WHERE sql_column_name = ?)", candidate));
		this.jdbc.update("""
				INSERT INTO property_definitions (id, name, sql_column_name, data_type, category_id, max_length, min_value,
				    max_value, pattern, scope, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'organization', ?)""", id, name,
				column, input.dataType(), input.categoryId(), input.maxLength(), input.minValue(), input.maxValue(),
				input.pattern(), principal.personId());
		this.audit.changed("metamodel.property-created", principal, "property-definition", id,
				Map.of("name", name, "dataType", input.dataType(), "sqlColumnName", column));
		return id;
	}

	@Transactional
	public void update(OeaPrincipal principal, UUID id, Input input) {
		this.effects.lock();
		Stored stored = stored(id);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		String name = validate(input, id);
		if (!stored.dataType().equals(input.dataType()) && this.values.hasValues(id, true)) {
			throw MetamodelChangeException.inUse("The data type cannot change while entity values exist.", null);
		}
		if (!"enum".equals(input.dataType())) {
			this.jdbc.update("DELETE FROM property_enum_values WHERE property_def_id = ?", id);
		}
		this.jdbc.update("""
				UPDATE property_definitions SET name = ?, data_type = ?, category_id = ?, max_length = ?, min_value = ?,
				       max_value = ?, pattern = ? WHERE id = ?""", name, input.dataType(), input.categoryId(),
				input.maxLength(), input.minValue(), input.maxValue(), input.pattern(), id);
		List<String> changed = new ArrayList<>();
		compare(changed, "name", stored.name(), name);
		compare(changed, "dataType", stored.dataType(), input.dataType());
		compare(changed, "categoryId", stored.categoryId(), input.categoryId());
		compare(changed, "maxLength", stored.maxLength(), input.maxLength());
		compare(changed, "minValue", stored.minValue(), input.minValue());
		compare(changed, "maxValue", stored.maxValue(), input.maxValue());
		compare(changed, "pattern", stored.pattern(), input.pattern());
		this.audit.changed("metamodel.property-updated", principal, "property-definition", id,
				Map.of("changedFields", changed));
	}

	/** Deletes an unmapped definition, or retires it when the entity history references it. */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id) {
		this.effects.lock();
		Stored stored = stored(id);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		if (exists("SELECT EXISTS (SELECT 1 FROM metatype_property_mappings WHERE property_def_id = ?)", id)) {
			throw MetamodelChangeException.inUse("The property definition is mapped to metatypes.", null);
		}
		boolean retired = this.values.hasValues(id, true);
		if (retired) {
			this.jdbc.update("UPDATE property_definitions SET retired_at = ? WHERE id = ?",
					Timestamp.from(this.effects.now()), id);
		}
		else {
			this.jdbc.update("DELETE FROM property_enum_values WHERE property_def_id = ?", id);
			this.jdbc.update("DELETE FROM property_definitions WHERE id = ?", id);
		}
		this.audit.changed("metamodel.property-deleted", principal, "property-definition", id,
				Map.of("name", stored.name(), "retired", retired));
	}

	@Transactional
	public UUID createEnumValue(OeaPrincipal principal, UUID propertyDefId, String value, Integer sortOrder) {
		this.effects.lock();
		Stored stored = stored(propertyDefId);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		if (!"enum".equals(stored.dataType())) {
			throw MetamodelChangeException.invalid("value", "Only enum properties have values.");
		}
		String stripped = (value != null) ? value.strip() : "";
		if (stripped.isEmpty() || stripped.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("value", "The value must have 1 to 100 characters.");
		}
		if (exists("SELECT EXISTS (SELECT 1 FROM property_enum_values WHERE property_def_id = ? AND value = ?)",
				propertyDefId, stripped)) {
			throw MetamodelChangeException.nameTaken("value");
		}
		// a missing or zero sort order appends the value
		int order = (sortOrder != null && sortOrder != 0) ? sortOrder
				: this.jdbc.queryForObject(
						"SELECT coalesce(max(sort_order) + 1, 0) FROM property_enum_values WHERE property_def_id = ?",
						Integer.class, propertyDefId);
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO property_enum_values (id, property_def_id, value, sort_order) VALUES (?, ?, ?, ?)",
				id, propertyDefId, stripped, order);
		this.audit.changed("metamodel.enum-value-created", principal, "property-definition", propertyDefId,
				Map.of("valueId", id.toString(), "value", stripped));
		return id;
	}

	/** Deletes the value; entities holding it lose it with a new version (design D5, D6). */
	@Transactional
	public void deleteEnumValue(OeaPrincipal principal, UUID propertyDefId, UUID valueId) {
		this.effects.lock();
		Stored stored = stored(propertyDefId);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		String value = this.jdbc
			.queryForList("SELECT value FROM property_enum_values WHERE id = ? AND property_def_id = ?", String.class,
					valueId, propertyDefId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no enum value " + valueId));
		List<UUID> metatypes = this.jdbc.queryForList("SELECT id FROM metatypes", UUID.class);
		int removed = this.values.removeValues(propertyDefId, metatypes, value, MetamodelEffects.author(principal),
				this.effects.now());
		this.jdbc.update("DELETE FROM property_enum_values WHERE id = ?", valueId);
		int marked = (removed > 0) ? this.effects.markWhereMandatory(propertyDefId) : 0;
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("valueId", valueId.toString());
		details.put("value", value);
		details.put("entitiesWithRemovedValues", removed);
		details.put("entitiesMarked", marked);
		this.audit.changed("metamodel.enum-value-deleted", principal, "property-definition", propertyDefId, details);
	}

	@Transactional
	public UUID createCategory(OeaPrincipal principal, CategoryInput input) {
		this.effects.lock();
		String name = categoryName(input, null);
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO property_categories (id, name, sort_order, description, scope)
				VALUES (?, ?, ?, ?, 'organization')""", id, name, input.sortOrder(), input.description());
		this.audit.changed("metamodel.category-created", principal, "property-category", id, Map.of("name", name));
		return id;
	}

	@Transactional
	public void updateCategory(OeaPrincipal principal, UUID id, CategoryInput input) {
		this.effects.lock();
		readOnlyUnlessOrganization(categoryScope(id), "property category");
		String name = categoryName(input, id);
		this.jdbc.update("UPDATE property_categories SET name = ?, sort_order = ?, description = ? WHERE id = ?", name,
				input.sortOrder(), input.description(), id);
		this.audit.changed("metamodel.category-updated", principal, "property-category", id, Map.of("name", name));
	}

	@Transactional
	public void deleteCategory(OeaPrincipal principal, UUID id) {
		this.effects.lock();
		readOnlyUnlessOrganization(categoryScope(id), "property category");
		if (exists("SELECT EXISTS (SELECT 1 FROM property_definitions WHERE category_id = ?)", id)) {
			throw MetamodelChangeException.inUse("Property definitions belong to the category.", null);
		}
		this.jdbc.update("DELETE FROM property_categories WHERE id = ?", id);
		this.audit.changed("metamodel.category-deleted", principal, "property-category", id, Map.of());
	}

	/** Checks the input; returns the stripped name. */
	private String validate(Input input, UUID id) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		if (!DATA_TYPES.contains(input.dataType())) {
			throw MetamodelChangeException.invalid("dataType", "The data type is not supported.");
		}
		if (input.refMetatypeId() != null) {
			throw MetamodelChangeException.invalid("refMetatypeId", "Reference properties are not supported yet.");
		}
		if (input.maxLength() != null
				&& (!Set.of("string", "text", "url").contains(input.dataType()) || input.maxLength() < 1)) {
			throw MetamodelChangeException.invalid("maxLength", "A positive maximum length applies to string, text and url.");
		}
		if ((input.minValue() != null || input.maxValue() != null) && !"integer".equals(input.dataType())) {
			throw MetamodelChangeException.invalid("minValue", "A range applies to integer properties.");
		}
		if (input.minValue() != null && input.maxValue() != null && input.minValue().compareTo(input.maxValue()) > 0) {
			throw MetamodelChangeException.invalid("minValue", "The minimum is greater than the maximum.");
		}
		if (input.pattern() != null) {
			if (!"string".equals(input.dataType())) {
				throw MetamodelChangeException.invalid("pattern", "A pattern applies to string properties.");
			}
			try {
				Pattern.compile(input.pattern());
			}
			catch (PatternSyntaxException ex) {
				throw MetamodelChangeException.invalid("pattern", "The pattern is not a valid regular expression.");
			}
		}
		if (input.categoryId() != null
				&& !exists("SELECT EXISTS (SELECT 1 FROM property_categories WHERE id = ?)", input.categoryId())) {
			throw MetamodelChangeException.invalid("categoryId", "The category does not exist.");
		}
		if (exists("SELECT EXISTS (SELECT 1 FROM property_definitions WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid)",
				name, id)) {
			throw MetamodelChangeException.nameTaken("name");
		}
		return name;
	}

	private String categoryName(CategoryInput input, UUID id) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		if (exists("SELECT EXISTS (SELECT 1 FROM property_categories WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid)",
				name, id)) {
			throw MetamodelChangeException.nameTaken("name");
		}
		return name;
	}

	private Stored stored(UUID id) {
		return this.jdbc.query("""
				SELECT name, data_type, category_id, max_length, min_value, max_value, pattern, scope
				FROM property_definitions WHERE id = ? AND retired_at IS NULL""",
				(rs, n) -> new Stored(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class),
						rs.getObject(4, Integer.class), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7),
						rs.getString(8)), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no property definition " + id));
	}

	private String categoryScope(UUID id) {
		return this.jdbc.queryForList("SELECT scope FROM property_categories WHERE id = ?", String.class, id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no property category " + id));
	}

	private boolean exists(String sql, Object... arguments) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(sql, Boolean.class, arguments));
	}

	static void readOnlyUnlessOrganization(String scope, String element) {
		if (!"organization".equals(scope)) {
			throw MetamodelChangeException.readOnly(element);
		}
	}

	private static void compare(List<String> changed, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changed.add(field);
		}
	}

}
