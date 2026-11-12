package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for the scenarios "Catalog column empty without read role" and "Override role" of
 * features/catalog/catalog-query.feature (us-126-role-restricted-enforcement D2,
 * us-128-override-role-list D3), on the instance of the catalog features.
 */
public class CatalogVisibilitySteps {

	private String catalog;

	private String key;

	private HttpResponse<String> response;

	private boolean override;

	@Given("the catalog {string} with a column on {string}, readable only by {string}, and an application {string} with the value {string}")
	public void restrictedCatalog(String catalog, String property, String role, String application, String value)
			throws Exception {
		RepositoryInstance instance = CatalogSteps.shared();
		this.key = "p_" + Integer.toHexString(property.hashCode() & 0xfffffff);
		String propertyId = instance.oidc().single("""
				WITH property AS (
				    INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				    VALUES ('%1$s', '%2$s', 'string', 'role-restricted') RETURNING id),
				roles AS (
				    INSERT INTO property_visibility_roles (property_def_id, role_id, access) SELECT id, '%3$s', 'read' FROM property),
				mapping AS (
				    INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				    SELECT (SELECT id FROM metatypes WHERE name = 'Application Component'), id, 'optional', DATE '2026-01-01',
				           'organization' FROM property)
				SELECT id::text FROM property""".formatted(property, this.key, role));
		instance.oidc().update("""
				WITH entity AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    VALUES ((SELECT id FROM metatypes WHERE name = 'Application Component'), '%s', (SELECT min(id) FROM persons))
				    RETURNING id)
				INSERT INTO entity_property_values (entity_id, property_def_id, value_text) SELECT id, '%s', '%s' FROM entity"""
			.formatted(application, propertyId, value));
		HttpResponse<String> created = instance.send("POST", "/api/v1/catalogs", JsonMapper.shared()
			.createObjectNode()
			.put("name", catalog)
			.put("primaryMetatypeId", instance.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"))
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

	@When("the viewer queries that catalog")
	public void viewerQueries() throws Exception {
		this.response = CatalogSteps.shared()
			.oidc()
			.get("/api/v1/catalogs/" + this.catalog + "/query", CatalogSteps.shared().viewer().token());
	}

	@Then("the column is not readable and has no value for {string}")
	public void notReadable(String application) throws Exception {
		JsonNode result = ok(this.response);
		assertThat(column(result).path("readable").asBoolean()).isFalse();
		assertThat(row(result, application).path("values").path(this.key).isNull()).isTrue();
	}

	@Then("the enterprise architect sees {string} for {string}")
	public void architectSees(String value, String application) throws Exception {
		JsonNode result = ok(CatalogSteps.shared()
			.oidc()
			.get("/api/v1/catalogs/" + this.catalog + "/query", CatalogSteps.shared().architect().token()));
		assertThat(column(result).path("readable").asBoolean(true)).isTrue();
		assertThat(row(result, application).path("values").path(this.key).asString()).isEqualTo(value);
	}

	@Given("the override role list contains the role of the viewer")
	public void overrideViewer() throws Exception {
		this.override = true;
		CatalogSteps.shared().oidc().update("UPDATE authorization_roles SET overrides_property_visibility = (id = 'viewer')");
	}

	@Then("the viewer sees {string} for {string} and the read is audited")
	public void viewerSeesThroughOverride(String value, String application) throws Exception {
		JsonNode result = ok(this.response);
		assertThat(column(result).path("readable").asBoolean()).isTrue();
		assertThat(row(result, application).path("values").path(this.key).asString()).isEqualTo(value);
		assertThat(CatalogSteps.shared()
			.oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = 'property-override-access' AND target_id = '"
					+ this.catalog + "'")).isEqualTo("1");
	}

	/** The override list is shared by all catalog scenarios; it is emptied after the one that set it. */
	@After
	public void resetOverride() throws Exception {
		if (this.override) {
			CatalogSteps.shared().oidc().update("UPDATE authorization_roles SET overrides_property_visibility = false");
		}
	}

	private JsonNode column(JsonNode result) {
		for (JsonNode column : result.path("columns")) {
			if (column.path("key").asString().equals(this.key)) {
				return column;
			}
		}
		throw new AssertionError("no column " + this.key);
	}

	private static JsonNode row(JsonNode result, String name) {
		for (JsonNode row : result.path("rows")) {
			if (row.path("values").path("name").asString().equals(name)) {
				return row;
			}
		}
		throw new AssertionError("no row " + name);
	}

	private static JsonNode ok(HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body());
	}

}
