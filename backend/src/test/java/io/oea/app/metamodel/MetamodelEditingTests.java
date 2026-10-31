package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.metamodel.TestPackages;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs metamodel/metatype-editing, metamodel/property-definitions, metamodel/property-mappings,
 * metamodel/package-import (re-import setting) and repository/entity-write (marks, writable
 * properties) of us-032-metatype-editing, on an own database with the starter package.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelEditingTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestSession admin;

	private TestSession architect;

	private UUID component;

	private UUID status;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.architect = principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes WHERE name = 'Application Component'") == 0) {
			assertThat(call(HttpMethod.POST, "/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", null,
					this.admin)).hasStatus(HttpStatus.OK);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
	}

	// metamodel/metatype-editing

	@Test
	void securityZoneCreated() {
		String name = unique("SecurityZone");
		JsonNode created = created(createMetatype(name, null));
		assertThat(created.path("scope").asString()).isEqualTo("organization");
		assertThat(created.path("reimportPolicy").asString()).isEqualTo("inherit");
		assertThat(created.path("effectiveReimportPolicy").asString()).isEqualTo("keep");
		UUID id = UUID.fromString(created.path("id").asString());
		JsonNode written = batch(entity(id, "Zone 1"));
		assertThat(written.path("summary").path("created").asInt()).isOne();
		assertThat(audit("metamodel.metatype-created", id)).isOne();
		assertThat(this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'metamodel.metatype-created' AND target_id = ?""",
				Long.class, id.toString())).isNotNull();
	}

	@Test
	void nameOfAnImportedMetatype() {
		MvcTestResult result = createMetatype("application component", null);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
		assertThat(count("SELECT count(*) FROM metatypes WHERE name = 'application component'")).isZero();
	}

	@Test
	void withoutPermission() {
		ObjectNode body = metatype(unique("Zone"), null);
		assertThat(call(HttpMethod.POST, "/metamodel/metatypes", body, this.architect)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void connectionMetatypesAreNotSupportedYet() {
		ObjectNode body = metatype(unique("Link"), null).put("isConnection", true);
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/metatypes", body, this.admin);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("isConnection");
	}

	@Test
	void subtypeInheritsMappings() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		UUID trust = enumProperty(unique("Trust Level"), "low", "medium", "high");
		assertThat(map(zone, trust, "optional")).hasStatus(HttpStatus.CREATED);
		UUID cloud = id(created(createMetatype(unique("CloudZone"), zone)));
		JsonNode mappings = body(call(HttpMethod.GET, "/metamodel/metatypes/" + cloud + "/property-mappings", null,
				this.architect));
		assertThat(mappings).hasSize(1);
		assertThat(mappings.get(0).path("inheritedFrom").path("id").asString()).isEqualTo(zone.toString());
		JsonNode result = batch(entity(cloud, "Cloud 1", trust, "unknown"));
		assertThat(result.path("items").get(0).path("errors").get(0).path("code").asString()).isEqualTo("INVALID_VALUE");
		assertThat(batch(entity(cloud, "Cloud 2", trust, "high")).path("summary").path("created").asInt()).isOne();
	}

	@Test
	void cycle() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		UUID cloud = id(created(createMetatype(unique("CloudZone"), zone)));
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/metatypes/" + zone,
				metatype(name(zone), cloud), this.admin);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(this.jdbc.queryForObject("SELECT base_metatype_id FROM metatypes WHERE id = ?", UUID.class, zone))
			.isNull();
	}

	@Test
	void changedBaseTypeRemovesAndMarks() {
		UUID oldBase = id(created(createMetatype(unique("Old Base"), null)));
		UUID newBase = id(created(createMetatype(unique("New Base"), null)));
		UUID lost = stringProperty(unique("Lost"));
		UUID required = stringProperty(unique("Required"));
		map(oldBase, lost, "optional");
		map(newBase, required, "mandatory");
		UUID sub = id(created(createMetatype(unique("Sub"), oldBase)));
		int entity = batch(entity(sub, "Sub 1", lost, "value")).path("items").get(0).path("id").asInt();
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + sub, metatype(name(sub), newBase), this.admin))
			.hasStatus(HttpStatus.OK);
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE entity_id = " + entity)).isZero();
		assertThat(count("SELECT version FROM entities WHERE id = " + entity)).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE entity_id = " + entity)).isOne();
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'entitiesMarked' FROM audit.events
				WHERE event_type = 'metamodel.metatype-updated' AND target_id = ?""", String.class, sub.toString()))
			.isEqualTo("1");
	}

	@Test
	void removingAMandatoryMappingClearsTheMarks() {
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		batch(entity(zone, "Z1"));
		UUID required = stringProperty(unique("Required"));
		map(zone, required, "mandatory");
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE property_def_id = '" + required + "'"))
			.isOne();
		UUID mapping = this.jdbc.queryForObject("SELECT id FROM metatype_property_mappings WHERE property_def_id = ?",
				UUID.class, required);
		call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping, null, this.admin);
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE property_def_id = '" + required + "'"))
			.isZero();
	}

	@Test
	void propertyMappedTwiceInTheChain() {
		UUID base = id(created(createMetatype(unique("Base"), null)));
		UUID other = id(created(createMetatype(unique("Other"), null)));
		UUID shared = stringProperty(unique("Shared"));
		map(base, shared, "optional");
		map(other, shared, "optional");
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/metatypes/" + other, metatype(name(other), base),
				this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:conflict");
		UUID sub = id(created(createMetatype(unique("Sub"), base)));
		assertThat(type(map(sub, shared, "optional"))).isEqualTo("urn:oea:problem:conflict");
	}

	@Test
	void metatypeWithEntities() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		ArrayNode entities = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < 5; i++) {
			entities.add(entity(zone, "Zone " + i));
		}
		batch(entities);
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE metatype_id = ? AND name = 'Zone 0'", zone);
		MvcTestResult result = call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone, null, this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:in-use");
		assertThat(body(result).path("count").asInt()).isEqualTo(5);
		assertThat(count("SELECT count(*) FROM metatypes WHERE id = '" + zone + "'")).isOne();
	}

	@Test
	void metatypeUsedAsBaseTypeOrByACatalog() {
		UUID base = id(created(createMetatype(unique("Base"), null)));
		created(createMetatype(unique("Sub"), base));
		assertThat(type(call(HttpMethod.DELETE, "/metamodel/metatypes/" + base, null, this.admin)))
			.isEqualTo("urn:oea:problem:in-use");
		UUID listed = id(created(createMetatype(unique("Listed"), null)));
		Long person = this.jdbc.queryForObject("SELECT id FROM persons LIMIT 1", Long.class);
		this.jdbc.update("""
				INSERT INTO catalogs (name, primary_metatype_id, scope, created_by, last_modified_by)
				VALUES (?, ?, 'instance', ?, ?)""", unique("Catalog"), listed, person, person);
		assertThat(type(call(HttpMethod.DELETE, "/metamodel/metatypes/" + listed, null, this.admin)))
			.isEqualTo("urn:oea:problem:in-use");
		UUID unused = id(created(createMetatype(unique("Unused"), null)));
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + unused, null, this.admin))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(audit("metamodel.metatype-deleted", unused)).isOne();
	}

	@Test
	void importedMetatype() {
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/metatypes/" + this.component,
				metatype("Renamed Component", null), this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:read-only-metatype");
		assertThat(name(this.component)).isEqualTo("Application Component");
	}

	// metamodel/property-definitions

	@Test
	void enumPropertyWithValues() {
		String name = unique("Trust Level");
		UUID trust = enumProperty(name, "low", "medium", "high");
		JsonNode listed = null;
		for (JsonNode property : body(call(HttpMethod.GET, "/metamodel/property-definitions", null, this.architect))) {
			if (property.path("id").asString().equals(trust.toString())) {
				listed = property;
			}
		}
		assertThat(listed).isNotNull();
		assertThat(listed.path("scope").asString()).isEqualTo("organization");
		assertThat(listed.path("sqlColumnName").asString()).startsWith("test_trust_level");
		assertThat(listed.path("enumValues")).extracting((value) -> value.path("value").asString())
			.containsExactly("low", "medium", "high");
		assertThat(audit("metamodel.property-created", trust)).isOne();
		assertThat(audit("metamodel.enum-value-created", trust)).isEqualTo(3);
	}

	@Test
	void propertyNameTaken() {
		MvcTestResult result = createProperty("status", "string");
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
	}

	@Test
	void referencePropertiesAreNotSupportedYet() {
		MvcTestResult result = createProperty(unique("Ref"), "reference");
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void enumValueInUse() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		UUID trust = enumProperty(unique("Trust Level"), "low", "high");
		map(zone, trust, "optional");
		ArrayNode entities = JsonMapper.shared().createArrayNode();
		entities.add(entity(zone, "Z1", trust, "high")).add(entity(zone, "Z2", trust, "high"))
			.add(entity(zone, "Z3", trust, "low"));
		batch(entities);
		UUID high = this.jdbc.queryForObject(
				"SELECT id FROM property_enum_values WHERE property_def_id = ? AND value = 'high'", UUID.class, trust);
		assertThat(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + trust + "/enum-values/" + high, null,
				this.admin)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE property_def_id = '" + trust + "'")).isOne();
		assertThat(count("""
				SELECT count(*) FROM entities WHERE metatype_id = '%s' AND version = 2""".formatted(zone))).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'entitiesWithRemovedValues' FROM audit.events
				WHERE event_type = 'metamodel.enum-value-deleted' AND target_id = ?""", String.class, trust.toString()))
			.isEqualTo("2");
	}

	@Test
	void importedProperty() {
		ObjectNode value = JsonMapper.shared().createObjectNode().put("value", "retired");
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/property-definitions/" + this.status + "/enum-values",
				value, this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:read-only-metatype");
		assertThat(count("SELECT count(*) FROM property_enum_values WHERE property_def_id = '" + this.status + "'"))
			.isEqualTo(4);
	}

	@Test
	void dataTypeChangeWithValues() {
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		UUID owner = stringProperty(unique("Owner"));
		map(zone, owner, "optional");
		batch(entity(zone, "Z1", owner, "Alice"));
		ObjectNode change = property(name(owner, "property_definitions"), "integer");
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/property-definitions/" + owner, change, this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:in-use");
		// also with values only in the history
		UUID mapping = this.jdbc.queryForObject("SELECT id FROM metatype_property_mappings WHERE property_def_id = ?",
				UUID.class, owner);
		call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping, null, this.admin);
		assertThat(type(call(HttpMethod.PUT, "/metamodel/property-definitions/" + owner, change, this.admin)))
			.isEqualTo("urn:oea:problem:in-use");
	}

	@Test
	void deletingDefinitionsAndCategories() {
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		UUID mapped = stringProperty(unique("Mapped"));
		map(zone, mapped, "optional");
		assertThat(type(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + mapped, null, this.admin)))
			.isEqualTo("urn:oea:problem:in-use");
		batch(entity(zone, "Z1", mapped, "v"));
		UUID mapping = this.jdbc.queryForObject("SELECT id FROM metatype_property_mappings WHERE property_def_id = ?",
				UUID.class, mapped);
		call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping, null, this.admin);
		assertThat(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + mapped, null, this.admin))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.jdbc.queryForObject("SELECT retired_at IS NOT NULL FROM property_definitions WHERE id = ?",
				Boolean.class, mapped)).isTrue();
		assertThat(call(HttpMethod.GET, "/metamodel/property-definitions/" + mapped + "/enum-values", null, this.admin))
			.hasStatus(HttpStatus.NOT_FOUND);
		UUID unused = stringProperty(unique("Unused"));
		call(HttpMethod.DELETE, "/metamodel/property-definitions/" + unused, null, this.admin);
		assertThat(count("SELECT count(*) FROM property_definitions WHERE id = '" + unused + "'")).isZero();

		JsonNode category = created(call(HttpMethod.POST, "/metamodel/property-categories",
				JsonMapper.shared().createObjectNode().put("name", unique("Security")), this.admin));
		ObjectNode inCategory = property(unique("Classified"), "boolean").put("categoryId", category.path("id").asString());
		created(call(HttpMethod.POST, "/metamodel/property-definitions", inCategory, this.admin));
		assertThat(type(call(HttpMethod.DELETE, "/metamodel/property-categories/" + category.path("id").asString(), null,
				this.admin))).isEqualTo("urn:oea:problem:in-use");
	}

	// metamodel/property-mappings and repository/entity-write

	@Test
	void mandatoryPropertyForNewEntitiesAndExistingEntitiesMarked() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		UUID sub = id(created(createMetatype(unique("SubZone"), zone)));
		ArrayNode entities = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < 5; i++) {
			entities.add(entity(zone, "Zone " + i));
		}
		entities.add(entity(sub, "Sub"));
		batch(entities);
		UUID trust = enumProperty(unique("Trust Level"), "low", "high");
		MvcTestResult mapped = map(zone, trust, "mandatory");
		assertThat(mapped).hasStatus(HttpStatus.CREATED);
		assertThat(body(mapped).path("markedEntityCount").asInt()).isEqualTo(6);
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE property_def_id = '" + trust + "'"))
			.isEqualTo(6);
		JsonNode created = batch(entity(zone, "New zone"));
		assertThat(created.path("items").get(0).path("errors").get(0).path("code").asString())
			.isEqualTo("MANDATORY_MISSING");
	}

	@Test
	void markedEntityWrittenAgainAndMarkCleared() {
		UUID zone = id(created(createMetatype(unique("SecurityZone"), null)));
		String externalId = unique("zone");
		batch(entity(zone, "Zone").put("externalId", externalId));
		UUID trust = enumProperty(unique("Trust Level"), "low", "high");
		map(zone, trust, "mandatory");
		JsonNode rejected = batch(entity(zone, "Zone").put("externalId", externalId).put("description", "changed"));
		assertThat(rejected.path("items").get(0).path("errors").get(0).path("code").asString())
			.isEqualTo("MANDATORY_MISSING");
		JsonNode fixed = batch(entity(zone, "Zone", trust, "low").put("externalId", externalId));
		assertThat(fixed.path("summary").path("updated").asInt()).isOne();
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE property_def_id = '" + trust + "'")).isZero();
	}

	@Test
	void mandatoryPropertyTheWriterCannotChange() {
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		UUID secret = stringProperty(unique("Secret"));
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?", secret);
		map(zone, secret, "mandatory");
		assertThat(batch(entity(zone, "Zone")).path("summary").path("created").asInt()).isOne();
	}

	@Test
	void ownPropertyOnAnImportedMetatype() {
		UUID cost = stringProperty(unique("Cost Center"));
		assertThat(map(this.component, cost, "optional")).hasStatus(HttpStatus.CREATED);
		JsonNode metatype = body(call(HttpMethod.GET, "/metamodel/metatypes/" + this.component, null, this.architect));
		assertThat(metatype.path("propertyMappings")).anySatisfy((mapping) -> {
			assertThat(mapping.path("propertyDefinition").path("name").asString()).isEqualTo("Status");
			assertThat(mapping.path("scope").asString()).isEqualTo("imported");
		}).anySatisfy((mapping) -> {
			assertThat(mapping.path("propertyDefinition").path("id").asString()).isEqualTo(cost.toString());
			assertThat(mapping.path("scope").asString()).isEqualTo("organization");
		});
		UUID mapping = this.jdbc.queryForObject("SELECT id FROM metatype_property_mappings WHERE property_def_id = ?",
				UUID.class, cost);
		call(HttpMethod.DELETE, "/metamodel/metatypes/" + this.component + "/property-mappings/" + mapping, null,
				this.admin);
	}

	@Test
	void packageMappingsAndDefinitionsAreReadOnly() {
		UUID mapping = this.jdbc.queryForObject(
				"SELECT id FROM metatype_property_mappings WHERE metatype_id = ? AND property_def_id = ?", UUID.class,
				this.component, this.status);
		assertThat(type(call(HttpMethod.DELETE,
				"/metamodel/metatypes/" + this.component + "/property-mappings/" + mapping, null, this.admin)))
			.isEqualTo("urn:oea:problem:read-only-metatype");
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		assertThat(type(map(zone, this.status, "optional"))).isEqualTo("urn:oea:problem:read-only-metatype");
	}

	@Test
	void mappingWithValuesRemoved() {
		UUID zone = id(created(createMetatype(unique("Zone"), null)));
		UUID sub = id(created(createMetatype(unique("Sub"), zone)));
		UUID cost = stringProperty(unique("Cost Center"));
		map(zone, cost, "optional");
		ArrayNode entities = JsonMapper.shared().createArrayNode();
		entities.add(entity(zone, "Z1", cost, "A")).add(entity(zone, "Z2", cost, "B")).add(entity(sub, "S1", cost, "C"));
		batch(entities);
		UUID mapping = this.jdbc.queryForObject("SELECT id FROM metatype_property_mappings WHERE property_def_id = ?",
				UUID.class, cost);
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping, null,
				this.admin)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE property_def_id = '" + cost + "'")).isZero();
		assertThat(count("""
				SELECT count(*) FROM entity_property_value_history WHERE property_def_id = '%s' AND entity_version = 2"""
			.formatted(cost))).isEqualTo(3);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'entitiesWithRemovedValues' FROM audit.events
				WHERE event_type = 'metamodel.mapping-deleted' AND details ->> 'mappingId' = ?""", String.class,
				mapping.toString())).isEqualTo("3");
	}

	// metamodel/package-import: re-import setting

	@Test
	void defaultKeepsOrganizationMappings() {
		String packageId = "reimport-keep-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID type = metatypeOfPackage(packageId, 1);
		UUID cost = stringProperty(unique("Cost Center"));
		map(type, cost, "optional");
		ObjectNode update = TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0");
		importPackage(update);
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + cost + "'"))
			.isOne();
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE metatype_id = '" + type + "'"))
			.isEqualTo(2);
	}

	@Test
	void removalInheritedFromTheBaseType() {
		String packageId = "reimport-remove-" + UUID.randomUUID().toString().substring(0, 8);
		ObjectNode pkg = TestPackages.pkg(packageId, 3);
		ArrayNode types = pkg.withArray("metatypes");
		((ObjectNode) types.get(1)).put("baseMetatypeId", types.get(0).path("id").asString());
		((ObjectNode) types.get(2)).put("baseMetatypeId", types.get(0).path("id").asString());
		// a property may be mapped once per chain: the subtypes inherit it from the base type
		((ObjectNode) types.get(1)).putArray("propertyMappings");
		((ObjectNode) types.get(2)).putArray("propertyMappings");
		importPackage(pkg);
		UUID base = metatypeOfPackage(packageId, 1);
		UUID inheriting = metatypeOfPackage(packageId, 2);
		UUID keeping = metatypeOfPackage(packageId, 3);
		assertThat(this.jdbc.queryForObject("SELECT base_metatype_id FROM metatypes WHERE id = ?", UUID.class, inheriting))
			.isEqualTo(base);
		UUID onBase = stringProperty(unique("On Base"));
		UUID onInheriting = stringProperty(unique("On Inheriting"));
		UUID onKeeping = stringProperty(unique("On Keeping"));
		map(base, onBase, "optional");
		map(inheriting, onInheriting, "optional");
		map(keeping, onKeeping, "optional");
		batch(entity(inheriting, "Inheriting 1", onInheriting, "v"));
		policy(base, "remove");
		policy(keeping, "keep");
		JsonNode metatype = body(call(HttpMethod.GET, "/metamodel/metatypes/" + inheriting, null, this.admin));
		assertThat(metatype.path("effectiveReimportPolicy").asString()).isEqualTo("remove");
		importPackage(pkg.put("packageVersion", "1.1.0"));
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + onBase + "'"))
			.isZero();
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + onInheriting
				+ "'")).isZero();
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + onKeeping + "'"))
			.isOne();
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE property_def_id = '" + onInheriting + "'"))
			.isZero();
		assertThat(count("""
				SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.mapping-deleted'
				AND details ->> 'reason' = 'reimport' AND target_id IN ('%s', '%s')""".formatted(base, inheriting)))
			.isEqualTo(2);
	}

	@Test
	void globalRemove() {
		String packageId = "reimport-global-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID type = metatypeOfPackage(packageId, 1);
		UUID cost = stringProperty(unique("Cost Center"));
		map(type, cost, "optional");
		assertThat(call(HttpMethod.PUT, "/metamodel/settings/reimport-policy",
				JsonMapper.shared().createObjectNode().put("organizationMappings", "remove"), this.admin))
			.hasStatus(HttpStatus.OK);
		try {
			importPackage(TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0"));
			assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + cost + "'"))
				.isZero();
			assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.reimport-policy-changed'"))
				.isPositive();
		}
		finally {
			call(HttpMethod.PUT, "/metamodel/settings/reimport-policy",
					JsonMapper.shared().createObjectNode().put("organizationMappings", "keep"), this.admin);
		}
		assertThat(body(call(HttpMethod.GET, "/metamodel/settings/reimport-policy", null, this.admin))
			.path("organizationMappings").asString()).isEqualTo("keep");
	}

	@Test
	void baseTypesInAPackage() {
		String packageId = "bases-" + UUID.randomUUID().toString().substring(0, 8);
		ObjectNode unknown = TestPackages.pkg(packageId, 1);
		((ObjectNode) unknown.withArray("metatypes").get(0)).put("baseMetatypeId", this.component.toString());
		MvcTestResult imported = call(HttpMethod.POST, "/admin/metamodel/import", unknown, this.admin);
		assertThat(imported).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(imported).path("pointer").asString()).isEqualTo("/metatypes/0/baseMetatypeId");
		UUID builtIn = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, scope) VALUES (?::uuid, ?, 'built-in') RETURNING id""",
				UUID.class, CONFIG, unique("Built-in"));
		ObjectNode onBuiltIn = TestPackages.pkg(packageId, 1);
		((ObjectNode) onBuiltIn.withArray("metatypes").get(0)).put("baseMetatypeId", builtIn.toString());
		importPackage(onBuiltIn);
		assertThat(this.jdbc.queryForObject("SELECT base_metatype_id FROM metatypes WHERE id = ?", UUID.class,
				metatypeOfPackage(packageId, 1))).isEqualTo(builtIn);
	}

	@Test
	void newMandatoryPackageMappingMarksEntities() {
		String packageId = "mandatory-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID type = metatypeOfPackage(packageId, 1);
		batch(entity(type, "Without status"));
		ObjectNode update = TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0");
		((ObjectNode) update.withArray("metatypes").get(0).withArray("propertyMappings").get(0)).put("necessity",
				"mandatory");
		importPackage(update);
		assertThat(count("""
				SELECT count(*) FROM entity_mandatory_marks m JOIN entities e ON e.id = m.entity_id
				WHERE e.metatype_id = '%s'""".formatted(type))).isOne();
	}

	// helpers

	private MvcTestResult createMetatype(String name, UUID base) {
		return call(HttpMethod.POST, "/metamodel/metatypes", metatype(name, base), this.admin);
	}

	private static ObjectNode metatype(String name, UUID base) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("configId", CONFIG);
		if (base != null) {
			body.put("baseMetatypeId", base.toString());
		}
		return body;
	}

	private MvcTestResult createProperty(String name, String dataType) {
		return call(HttpMethod.POST, "/metamodel/property-definitions", property(name, dataType), this.admin);
	}

	private static ObjectNode property(String name, String dataType) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("dataType", dataType);
	}

	private UUID stringProperty(String name) {
		return id(created(createProperty(name, "string")));
	}

	private UUID enumProperty(String name, String... values) {
		UUID id = id(created(createProperty(name, "enum")));
		for (String value : values) {
			created(call(HttpMethod.POST, "/metamodel/property-definitions/" + id + "/enum-values",
					JsonMapper.shared().createObjectNode().put("value", value), this.admin));
		}
		return id;
	}

	private MvcTestResult map(UUID metatype, UUID property, String necessity) {
		return call(HttpMethod.POST, "/metamodel/metatypes/" + metatype + "/property-mappings",
				JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("necessity",
						necessity), this.admin);
	}

	private void policy(UUID metatype, String policy) {
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + metatype + "/reimport-policy",
				JsonMapper.shared().createObjectNode().put("organizationMappings", policy), this.admin))
			.hasStatus(HttpStatus.OK);
	}

	private void importPackage(ObjectNode pkg) {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", pkg, this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private UUID metatypeOfPackage(String packageId, int number) {
		return this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = ?", UUID.class,
				packageId + " Type " + number);
	}

	private static ObjectNode entity(UUID metatype, String name) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("metatypeId", metatype.toString());
	}

	private static ObjectNode entity(UUID metatype, String name, UUID property, String value) {
		ObjectNode entity = entity(metatype, name);
		entity.putArray("properties").addObject().put("propertyDefId", property.toString()).put("value", value);
		return entity;
	}

	private JsonNode batch(ObjectNode entity) {
		return batch(JsonMapper.shared().createArrayNode().add(entity));
	}

	private JsonNode batch(ArrayNode entities) {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.set("entities", entities);
		MvcTestResult result = call(HttpMethod.POST, "/entities/batch", request, this.architect);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri("/api/v1" + path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private String name(UUID metatype) {
		return name(metatype, "metatypes");
	}

	private String name(UUID id, String table) {
		return this.jdbc.queryForObject("SELECT name FROM " + table + " WHERE id = ?", String.class, id);
	}

	private int audit(String eventType, UUID target) {
		return count("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
				+ target + "'");
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.path("id").asString());
	}

	private static String type(MvcTestResult result) {
		return body(result).path("type").asString();
	}

	private static String unique(String name) {
		return "test " + name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-metamodel-editing").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
