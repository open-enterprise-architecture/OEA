package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The translated labels of metamodel content in {@code i18n_entries} (change us-140-dynamic-labels,
 * ADR-025): the labels of a package ({@code imported}) and those of the organization.
 */
@Repository
public class MetamodelLabelStore {

	private static final String ELEMENTS = """
			WITH elements (entity_type, entity_id, name) AS (
			    SELECT 'metatype', id, name FROM metatypes WHERE config_id = ?
			    UNION ALL SELECT 'property_category', id, name FROM property_categories
			    UNION ALL SELECT 'property_def', id, name FROM property_definitions
			    UNION ALL SELECT 'enum_value', id, value FROM property_enum_values
			    UNION ALL SELECT 'architecture_layer', id, name FROM architecture_layers
			)
			SELECT e.entity_type, e.entity_id, e.name, p.value AS package_text, o.value AS organization_text
			FROM elements e
			LEFT JOIN i18n_entries p ON p.entity_type = e.entity_type AND p.entity_id = e.entity_id
			     AND p.locale = ? AND p.scope = 'imported'
			LEFT JOIN i18n_entries o ON o.entity_type = e.entity_type AND o.entity_id = e.entity_id
			     AND o.locale = ? AND o.scope = 'organization'
			ORDER BY e.entity_type, lower(e.name), e.entity_id""";

	private final JdbcTemplate jdbc;

	public MetamodelLabelStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The name of an element with its package and organization text in a locale. */
	public record LabelRow(String entityType, UUID entityId, String name, String packageText,
			String organizationText) {
	}

	/** The elements of the configuration (and the categories, properties, values and layers) with their labels. */
	public List<LabelRow> elements(UUID configId, String locale) {
		return this.jdbc.query(ELEMENTS,
				(rs, row) -> new LabelRow(rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
						rs.getString("name"), rs.getString("package_text"), rs.getString("organization_text")),
				configId, locale, locale);
	}

	/** The label of each property definition: organization, then package, then the name. */
	public Map<UUID, String> propertyLabels(String language, Collection<UUID> ids) {
		Map<UUID, String> labels = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT d.id, coalesce(o.value, p.value, d.name)
				FROM property_definitions d
				LEFT JOIN i18n_entries p ON p.entity_type = 'property_def' AND p.entity_id = d.id
				     AND p.locale = ? AND p.scope = 'imported'
				LEFT JOIN i18n_entries o ON o.entity_type = 'property_def' AND o.entity_id = d.id
				     AND o.locale = ? AND o.scope = 'organization'
				WHERE d.id = ANY(?)""", (rs) -> {
			labels.put(rs.getObject(1, UUID.class), rs.getString(2));
		}, language, language, ids.toArray(UUID[]::new));
		return labels;
	}

	/** Removes the organization label of a field; returns how many were removed. */
	public int deleteOrganization(String entityType, UUID entityId, String locale, String field) {
		return this.jdbc.update("""
				DELETE FROM i18n_entries WHERE entity_type = ? AND entity_id = ? AND locale = ? AND field = ?
				    AND scope = 'organization'""", entityType, entityId, locale, field);
	}

	public void upsertOrganization(String entityType, UUID entityId, String locale, String field, String value,
			Instant now) {
		this.jdbc.update("""
				INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, updated_at)
				VALUES (?, ?, ?, ?, ?, 'organization', ?)
				ON CONFLICT (entity_type, entity_id, locale, field, scope)
				DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at""", entityType, entityId,
				locale, field, value, Timestamp.from(now));
	}

	/** Removes the package labels of a package. */
	public void deleteImported(String packageId) {
		this.jdbc.update("DELETE FROM i18n_entries WHERE scope = 'imported' AND package_id = ?", packageId);
	}

	public void insertImported(String entityType, UUID entityId, String locale, String field, String value,
			String packageId, Instant now) {
		this.jdbc.update("""
				INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope, package_id, updated_at)
				VALUES (?, ?, ?, ?, ?, 'imported', ?, ?)""", entityType, entityId, locale, field, value, packageId,
				Timestamp.from(now));
	}

	/** Removes the labels of elements that no longer exist. */
	public void deleteOrphans() {
		this.jdbc.update("""
				DELETE FROM i18n_entries i WHERE NOT EXISTS (
				    SELECT 1 FROM metatypes WHERE i.entity_type = 'metatype' AND id = i.entity_id
				    UNION ALL SELECT 1 FROM property_categories WHERE i.entity_type = 'property_category' AND id = i.entity_id
				    UNION ALL SELECT 1 FROM property_definitions WHERE i.entity_type = 'property_def' AND id = i.entity_id
				    UNION ALL SELECT 1 FROM property_enum_values WHERE i.entity_type = 'enum_value' AND id = i.entity_id
				    UNION ALL SELECT 1 FROM architecture_layers WHERE i.entity_type = 'architecture_layer' AND id = i.entity_id)""");
	}

}
