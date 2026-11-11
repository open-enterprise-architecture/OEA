package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for the scenario "Own domain and other domain" of features/catalog/catalog-query.feature
 * (us-127-connection-scoped-visibility D5), on the instance of the catalog features. The account
 * of the enterprise architect is linked to the element "Scope Kurt" of the person metatype.
 */
public class CatalogScopeSteps {

	private String catalog;

	private String key;

	private HttpResponse<String> response;

	@Given("the connection-scoped property {string} with the scoping type {string}, assigned through it to the enterprise architect's element for {string} but not for {string}")
	public void scoped(String property, String scopingType, String own, String other) throws Exception {
		RepositoryInstance instance = CatalogSteps.shared();
		this.key = "p_" + Integer.toHexString(property.hashCode() & 0xfffffff);
		String application = instance.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		String actor = instance.oidc().single("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Scope Business Actor')
				RETURNING id::text""");
		String assignment = instance.oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', '%s', true)
				RETURNING id::text""".formatted(scopingType));
		instance.oidc().update("UPDATE visibility_settings SET person_metatype_id = '%s'".formatted(actor));
		String propertyId = instance.oidc().single("""
				WITH property AS (
				    INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				    VALUES ('%1$s', '%2$s', 'string', 'connection-scoped', '%3$s') RETURNING id),
				mapping AS (
				    INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				    SELECT '%4$s', id, 'optional', DATE '2026-01-01', 'organization' FROM property)
				SELECT id::text FROM property""".formatted(property, this.key, assignment, application));
		String creator = instance.oidc().single("SELECT min(id)::text FROM persons");
		String element = instance.oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'Scope Kurt', %s) RETURNING id::text"""
			.formatted(actor, creator));
		instance.oidc().update("UPDATE persons SET entity_id = %s WHERE email LIKE 'dev-architect@%%'".formatted(element));
		String ownId = application(instance, application, own, "internal", creator, propertyId);
		application(instance, application, other, "secret", creator, propertyId);
		String connection = instance.oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'Scope Assignment', %s) RETURNING id::text"""
			.formatted(assignment, creator));
		instance.oidc().update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (%s, %s, %s)"
			.formatted(connection, element, ownId));
		HttpResponse<String> created = instance.send("POST", "/api/v1/catalogs", JsonMapper.shared()
			.createObjectNode()
			.put("name", "Scoped Inventory")
			.put("primaryMetatypeId", application)
			.put("scope", "instance")
			.toString(), instance.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.catalog = JsonMapper.shared().readTree(created.body()).path("id").asString();
		ObjectNode columns = JsonMapper.shared().createObjectNode();
		columns.putArray("columns")
			.add(JsonMapper.shared().createObjectNode().put("field", "name"))
			.add(JsonMapper.shared().createObjectNode().put("propertyDefId", propertyId));
		HttpResponse<String> saved = instance.send("PUT", "/api/v1/catalogs/" + this.catalog + "/columns",
				columns.toString(), instance.architect());
		assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
	}

	@When("the enterprise architect queries the scoped catalog")
	public void query() throws Exception {
		this.response = CatalogSteps.shared()
			.oidc()
			.get("/api/v1/catalogs/" + this.catalog + "/query", CatalogSteps.shared().architect().token());
	}

	@Then("{string} shows {string} and {string} an empty cell")
	public void values(String own, String value, String other) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode result = JsonMapper.shared().readTree(this.response.body());
		assertThat(row(result, own).path("values").path(this.key).asString()).isEqualTo(value);
		assertThat(row(result, other).path("values").path(this.key).isNull()).isTrue();
	}

	private static String application(RepositoryInstance instance, String metatype, String name, String value,
			String creator, String property) throws Exception {
		String id = instance.oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', '%s', %s) RETURNING id::text"""
			.formatted(metatype, name, creator));
		instance.oidc().update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (%s, '%s', '%s')"
			.formatted(id, property, value));
		return id;
	}

	private static JsonNode row(JsonNode result, String name) {
		for (JsonNode row : result.path("rows")) {
			if (row.path("values").path("name").asString().equals(name)) {
				return row;
			}
		}
		throw new AssertionError("no row " + name);
	}

}
