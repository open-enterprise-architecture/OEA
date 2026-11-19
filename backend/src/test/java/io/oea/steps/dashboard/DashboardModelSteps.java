package io.oea.steps.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;

/**
 * Metamodel and repository content of features/dashboard/widgets.feature for the KPI and chart
 * widgets (us-056-kpi-and-chart-widgets). Everything is created with names that start with
 * {@code Dash } and removed after each scenario tagged {@code @dashboard-aggregation} (only what the scenario created), because the
 * instance is shared with the other features, which count entities and metatypes.
 */
public class DashboardModelSteps {

	private static final String CREATOR = "(SELECT min(id) FROM persons)";

	private final java.util.Set<String> metatypes = new java.util.LinkedHashSet<>();

	private final java.util.Set<String> properties = new java.util.LinkedHashSet<>();

	private final java.util.Set<String> layers = new java.util.LinkedHashSet<>();

	private final java.util.Set<String> entityPatterns = new java.util.LinkedHashSet<>();

	private String previousPersonMetatype;

	private String previousArchitectEntity;

	@Given("the element type {string}")
	public void elementType(String name) throws Exception {
		this.metatypes.add(name);
		oidc().update("INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', '%s')"
			.formatted(q(name)));
	}

	@Given("the element type {string} with the base type {string}")
	public void subtype(String name, String base) throws Exception {
		this.metatypes.add(name);
		oidc().update("""
				INSERT INTO metatypes (config_id, name, base_metatype_id)
				VALUES ('00000000-0000-0000-0000-000000000001', '%s', %s)""".formatted(q(name), metatype(base)));
	}

	@Given("the element type {string} with the base type {string} in the layer {string}")
	public void subtypeInLayer(String name, String base, String layer) throws Exception {
		this.metatypes.add(name);
		this.layers.add(layer);
		oidc().update(("INSERT INTO architecture_layers (name, sort_order) SELECT '%1$s', 0 WHERE NOT EXISTS "
				+ "(SELECT 1 FROM architecture_layers WHERE name = '%1$s')").formatted(q(layer)));
		oidc().update("""
				INSERT INTO metatypes (config_id, name, base_metatype_id, architecture_layer_id)
				VALUES ('00000000-0000-0000-0000-000000000001', '%s', %s,
				        (SELECT id FROM architecture_layers WHERE name = '%s'))""".formatted(q(name), metatype(base),
				q(layer)));
	}

	@Given("the property {string} of type {string} is mapped to the element type {string}")
	public void property(String name, String dataType, String type) throws Exception {
		this.properties.add(name);
		String key = "p_" + Integer.toHexString(name.hashCode() & 0xfffffff);
		oidc().update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES ('%s', '%s', '%s')""".formatted(q(name), key, dataType));
		map(name, type);
	}

	@Given("the property {string} is mapped to the element type {string}")
	public void map(String property, String type) throws Exception {
		oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (%s, %s, 'optional', DATE '2026-01-01', 'organization')""".formatted(metatype(type),
				property(property)));
	}

	@Given("the property {string} is no longer mapped to the element type {string}")
	public void unmap(String property, String type) throws Exception {
		oidc().update("DELETE FROM metatype_property_mappings WHERE metatype_id = %s AND property_def_id = %s"
			.formatted(metatype(type), property(property)));
	}

	@Given("the property {string} has the enumeration values {string}, {string} and {string}")
	public void enumValues(String property, String first, String second, String third) throws Exception {
		for (String value : new String[] { first, second, third }) {
			oidc().update("INSERT INTO property_enum_values (property_def_id, value, sort_order) VALUES (%s, '%s', 0)"
				.formatted(property(property), q(value)));
		}
	}

	@Given("the aggregated property {string} is readable only by the role {string}")
	public void restricted(String property, String role) throws Exception {
		oidc().update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = "
				+ property(property));
		oidc().update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (%s, '%s', 'read')"
			.formatted(property(property), q(role)));
	}

	@Given("the property {string} is connection-scoped and in scope of the enterprise architect for the entity {string}")
	public void scoped(String property, String entity) throws Exception {
		this.metatypes.add("Dash Actor");
		this.metatypes.add("Dash Assignment");
		this.entityPatterns.add("Dash Linked Kurt");
		this.entityPatterns.add("Dash Assignment Entity");
		this.previousPersonMetatype = oidc().single("SELECT coalesce(person_metatype_id::text, '') FROM visibility_settings");
		this.previousArchitectEntity = oidc()
			.single("SELECT coalesce(entity_id::text, '') FROM persons WHERE email LIKE 'dev-architect@%'");
		String actor = oidc().single("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Dash Actor')
				RETURNING id::text""");
		String assignment = oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Dash Assignment', true) RETURNING id::text""");
		oidc().update(("UPDATE property_definitions SET visibility_mode = 'connection-scoped', "
				+ "scoping_connection_type_id = '%s' WHERE id = %s").formatted(assignment, property(property)));
		oidc().update("UPDATE visibility_settings SET person_metatype_id = '%s'".formatted(actor));
		String element = oidc().single("INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'Dash Linked Kurt', %s) RETURNING id::text"
			.formatted(actor, CREATOR));
		oidc().update("UPDATE persons SET entity_id = %s WHERE email LIKE 'dev-architect@%%'".formatted(element));
		String connection = oidc().single("INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'Dash Assignment Entity', %s) RETURNING id::text"
			.formatted(assignment, CREATOR));
		oidc().update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (%s, %s, %s)"
			.formatted(connection, element, entity(entity)));
	}

	@Given("the entity {string} of the element type {string}")
	public void entity(String name, String type) throws Exception {
		this.entityPatterns.add(name);
		oidc().update("INSERT INTO entities (metatype_id, name, created_by) VALUES (%s, '%s', %s)"
			.formatted(metatype(type), q(name), CREATOR));
	}

	@Given("the entity {string} of the element type {string} with the {string} {string}")
	public void entityWithValue(String name, String type, String property, String value) throws Exception {
		entity(name, type);
		value(name, property, value);
	}

	@Given("the entity {string} has the {string} {string}")
	public void value(String name, String property, String value) throws Exception {
		String dataType = oidc().single("SELECT data_type FROM property_definitions WHERE id = " + property(property));
		String column = switch (dataType) {
			case "integer" -> "value_int";
			case "enum" -> "value_enum";
			case "boolean" -> "value_bool";
			default -> "value_text";
		};
		String literal = "integer".equals(dataType) || "boolean".equals(dataType) ? value : "'" + q(value) + "'";
		oidc().update("INSERT INTO entity_property_values (entity_id, property_def_id, %s) VALUES (%s, %s, %s)"
			.formatted(column, entity(name), property(property), literal));
	}

	@Given("the entity {string} is soft-deleted")
	public void deleted(String name) throws Exception {
		oidc().update("UPDATE entities SET deleted_at = now(), deleted_by = created_by WHERE id = " + entity(name));
	}

	@Given("{int} entities of the element type {string} named {string}")
	public void bulk(int count, String type, String prefix) throws Exception {
		this.entityPatterns.add(prefix + "%");
		oidc().update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT %s, '%s' || lpad(n::text, 2, '0'), %s FROM generate_series(1, %d) AS n"""
			.formatted(metatype(type), q(prefix), CREATOR, count));
	}

	@Given("{int} entities of the element type {string} with distinct values of the property {string}")
	public void distinct(int count, String type, String property) throws Exception {
		this.entityPatterns.add("Dash Distinct %");
		oidc().update("""
				WITH created AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT %1$s, 'Dash Distinct ' || lpad(n::text, 2, '0'), %2$s FROM generate_series(1, %3$d) AS n
				    RETURNING id, name)
				INSERT INTO entity_property_values (entity_id, property_def_id, value_text)
				SELECT id, %4$s, 'value ' || substring(name from 15) FROM created"""
			.formatted(metatype(type), CREATOR, count, property(property)));
	}

	@After("@dashboard-aggregation")
	public void remove() throws Exception {
		if (CatalogSteps.shared() == null) {
			return;
		}
		RepositoryInstance instance = CatalogSteps.shared();
		if (this.previousPersonMetatype != null) {
			instance.oidc().update("UPDATE persons SET entity_id = %s WHERE email LIKE 'dev-architect@%%'"
				.formatted(this.previousArchitectEntity.isEmpty() ? "NULL" : this.previousArchitectEntity));
			instance.oidc().update("UPDATE visibility_settings SET person_metatype_id = %s"
				.formatted(this.previousPersonMetatype.isEmpty() ? "NULL" : "'" + this.previousPersonMetatype + "'"));
		}
		String entities = "SELECT id FROM entities WHERE " + like("name", this.entityPatterns);
		if (!this.entityPatterns.isEmpty()) {
			instance.oidc().update("DELETE FROM entity_connections WHERE entity_id IN (" + entities
					+ ") OR source_entity_id IN (" + entities + ") OR target_entity_id IN (" + entities + ")");
			instance.oidc().update("DELETE FROM entity_property_values WHERE entity_id IN (" + entities + ")");
			instance.oidc().update("DELETE FROM entities WHERE " + like("name", this.entityPatterns));
		}
		if (!this.metatypes.isEmpty()) {
			String types = "SELECT id FROM metatypes WHERE " + in("name", this.metatypes);
			instance.oidc().update("DELETE FROM metatype_property_mappings WHERE metatype_id IN (" + types + ")");
		}
		if (!this.properties.isEmpty()) {
			String props = "SELECT id FROM property_definitions WHERE " + in("name", this.properties);
			instance.oidc().update("DELETE FROM metatype_property_mappings WHERE property_def_id IN (" + props + ")");
			instance.oidc().update("DELETE FROM property_visibility_roles WHERE property_def_id IN (" + props + ")");
			instance.oidc().update("DELETE FROM property_enum_values WHERE property_def_id IN (" + props + ")");
			instance.oidc().update("DELETE FROM property_definitions WHERE " + in("name", this.properties));
		}
		if (!this.metatypes.isEmpty()) {
			instance.oidc().update("UPDATE metatypes SET base_metatype_id = NULL, architecture_layer_id = NULL WHERE "
					+ in("name", this.metatypes));
			instance.oidc().update("DELETE FROM metatypes WHERE " + in("name", this.metatypes));
		}
		if (!this.layers.isEmpty()) {
			instance.oidc().update("DELETE FROM architecture_layers WHERE " + in("name", this.layers));
		}
	}

	private static String in(String column, java.util.Collection<String> names) {
		return column + " IN (" + names.stream().map((name) -> "'" + q(name) + "'").collect(java.util.stream.Collectors.joining(", ")) + ")";
	}

	private static String like(String column, java.util.Collection<String> patterns) {
		return patterns.isEmpty() ? "false" : patterns.stream()
			.map((pattern) -> column + " LIKE '" + q(pattern) + "'")
			.collect(java.util.stream.Collectors.joining(" OR "));
	}

	// ---- helpers

	private static String metatype(String name) throws Exception {
		String id = oidc().single("SELECT id::text FROM metatypes WHERE name = '" + q(name) + "'");
		assertThat(id).as("metatype " + name).isNotEmpty();
		return "'" + id + "'";
	}

	private static String property(String name) throws Exception {
		String id = oidc().single("SELECT id::text FROM property_definitions WHERE name = '" + q(name) + "'");
		assertThat(id).as("property " + name).isNotEmpty();
		return "'" + id + "'";
	}

	private static String entity(String name) throws Exception {
		return oidc().single("SELECT id::text FROM entities WHERE name = '" + q(name) + "' ORDER BY id LIMIT 1");
	}

	private static String q(String value) {
		return value.replace("'", "''");
	}

	private static io.oea.testsupport.auth.OidcTestInstance oidc() {
		return CatalogSteps.shared().oidc();
	}

}
