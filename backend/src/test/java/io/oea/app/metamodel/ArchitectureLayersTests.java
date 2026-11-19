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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec metamodel/architecture-layers (change s17-architecture-layers; requirements "Architecture
 * layers", "Labels of a layer" and "Layer of a metatype") on an own database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureLayersTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String INSTANCE = PackageImporter.INSTANCE_CONFIGURATION.toString();

	private static final String LAYERS = "/metamodel/architecture-layers";

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

	private TestSession viewer;

	private TestSession nobody;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.architect = principals.personWithIdpRole("enterprise-architect");
		this.viewer = principals.personWithIdpRole("viewer");
		this.nobody = principals.personWithoutRole();
		clean();
	}

	@AfterEach
	void clean() {
		this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'gui-and-import' WHERE id = ?::uuid", INSTANCE);
		this.jdbc.update("UPDATE metatypes SET architecture_layer_id = NULL");
		this.jdbc.update("DELETE FROM i18n_entries WHERE entity_type = 'architecture_layer'");
		this.jdbc.update("DELETE FROM architecture_layers");
	}

	// Requirement: Architecture layers

	@Test
	void createALayerAndSeeItInTheChanges() {
		UUID first = layer("Business");
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write("Data", null), this.admin);
		UUID second = id(body(result));
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"1\"");
		JsonNode created = body(result);
		assertThat(created.path("name").asString()).isEqualTo("Data");
		assertThat(created.path("sortOrder").asInt()).isOne();
		assertThat(created.path("scope").asString()).isEqualTo("organization");
		assertThat(created.path("metatypeCount").asInt()).isZero();
		assertThat(created.path("version").asInt()).isOne();
		// listed after the existing layers, for every reader with entity:read
		assertThat(names(this.viewer)).containsExactly("Business", "Data");
		assertThat(names(this.admin)).containsExactly("Business", "Data");

		List<JsonNode> changes = changes(first, second);
		assertThat(changes).hasSize(2);
		assertThat(changes.get(0).path("eventType").asString()).isEqualTo("metamodel.layer-created");
		assertThat(changes.get(0).path("kind").asString()).isEqualTo("layer");
		assertThat(changes.get(0).path("elementName").asString()).isEqualTo("Data");
		assertThat(body(call(HttpMethod.GET, "/metamodel/changes?kind=metatype", null, this.admin)).path("items")
			.valueStream()
			.map((item) -> item.path("eventType").asString())).noneMatch((type) -> type.contains("layer"));
	}

	@ParameterizedTest
	@CsvSource({ "100, 201", "101, 422", "1, 201" })
	void nameLengthAtItsLimits(int length, int status) {
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write("n".repeat(length), null), this.admin);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(status);
		if (status == 201) {
			assertThat(body(result).path("name").asString()).hasSize(length);
		}
		else {
			assertThat(body(result).path("invalidFields")).extracting(JsonNode::asString).contains("name");
			assertThat(names(this.admin)).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "     " })
	void blankNameRejected(String name) {
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write(name, null), this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields")).extracting(JsonNode::asString).contains("name");
		assertThat(names(this.admin)).isEmpty();
	}

	@Test
	void theNameIsStripped() {
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write("  Strategy  ", null), this.admin);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(body(result).path("name").asString()).isEqualTo("Strategy");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Business", "business", "BUSINESS", "  bUsInEsS " })
	void nameTakenRegardlessOfCase(String name) {
		layer("Business");
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write(name, null), this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
		assertThat(names(this.admin)).containsExactly("Business");
	}

	@Test
	void numberOfLayersAtTheLimit() {
		for (int i = 1; i <= 19; i++) {
			layer("Layer " + i);
		}
		// the 20th is created, the 21st is rejected
		assertThat(call(HttpMethod.POST, LAYERS, write("Layer 20", null), this.admin)).hasStatus(HttpStatus.CREATED);
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write("Layer 21", null), this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields")).extracting(JsonNode::asString).contains("architectureLayers");
		assertThat(names(this.admin)).hasSize(20).doesNotContain("Layer 21");
		// deleting one makes room again
		UUID last = id(find(list(this.admin), "Layer 20"));
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + last, null, this.admin, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(call(HttpMethod.POST, LAYERS, write("Layer 21", null), this.admin)).hasStatus(HttpStatus.CREATED);
	}

	@ParameterizedTest
	@CsvSource({ "0, 0", "1, 1", "19, 19", "50, 19", "1000, 19" })
	void reorderToAPosition(int position, int expectedIndex) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ids.add(layer("Layer " + i));
		}
		UUID moved = ids.get(10);
		MvcTestResult result = call(HttpMethod.PUT, LAYERS + "/" + moved, write("Layer 10", position), this.admin, "\"1\"");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("sortOrder").asInt()).isEqualTo(expectedIndex);
		JsonNode list = list(this.viewer);
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			if (i != 10) {
				expected.add("Layer " + i);
			}
		}
		expected.add(expectedIndex, "Layer 10");
		assertThat(list.valueStream().map((layer) -> layer.path("name").asString())).containsExactlyElementsOf(expected);
		// dense afterwards for every reader
		for (int i = 0; i < 20; i++) {
			assertThat(list.get(i).path("sortOrder").asInt()).isEqualTo(i);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 1001 })
	void positionOutsideTheContractIsRejected(int position) {
		UUID id = layer("Business");
		assertThat(call(HttpMethod.POST, LAYERS, write("Data", position), this.admin))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + id, write("Business", position), this.admin, "\"1\""))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(names(this.admin)).containsExactly("Business");
	}

	@Test
	void createAtAPositionAndMoveDataBeforeApplication() {
		layer("Business");
		UUID data = layer("Data");
		UUID application = layer("Application");
		MvcTestResult first = call(HttpMethod.POST, LAYERS, write("Technology", 0), this.admin);
		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(names(this.viewer)).containsExactly("Technology", "Business", "Data", "Application");
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + application, write("Application", 2), this.admin, "\"1\""))
			.hasStatus(HttpStatus.OK);
		assertThat(names(this.viewer)).containsExactly("Technology", "Business", "Application", "Data");
		assertThat(data).isNotNull();
	}

	@Test
	void renameKeepsThePositionAndRaisesTheVersion() {
		layer("Business");
		UUID data = layer("Data");
		MvcTestResult result = call(HttpMethod.PUT, LAYERS + "/" + data, write("Information", null), this.admin, "\"1\"");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"2\"");
		assertThat(body(result).path("version").asInt()).isEqualTo(2);
		assertThat(names(this.viewer)).containsExactly("Business", "Information");
		// the same name in another case is the layer itself, not a second one
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + data, write("INFORMATION", null), this.admin, "\"2\""))
			.hasStatus(HttpStatus.OK);
		MvcTestResult taken = call(HttpMethod.PUT, LAYERS + "/" + data, write("business", null), this.admin, "\"3\"");
		assertThat(taken).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(taken)).isEqualTo("urn:oea:problem:name-taken");
		assertThat(names(this.viewer)).containsExactly("Business", "INFORMATION");
	}

	@Test
	void ifMatchIsRequiredAndChecked() {
		UUID id = layer("Business");
		MvcTestResult missing = call(HttpMethod.PUT, LAYERS + "/" + id, write("Strategy", null), this.admin);
		assertThat(missing).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + id, null, this.admin)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		MvcTestResult stale = call(HttpMethod.PUT, LAYERS + "/" + id, write("Strategy", null), this.admin, "\"7\"");
		assertThat(stale).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(type(stale)).isEqualTo("urn:oea:problem:stale-version");
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + id, null, this.admin, "\"7\""))
			.hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(names(this.admin)).containsExactly("Business");
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + UUID.randomUUID(), write("Strategy", null), this.admin, "\"1\""))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + UUID.randomUUID(), null, this.admin, "\"1\""))
			.hasStatus(HttpStatus.NOT_FOUND);
		// after a change the old version is stale
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + id, write("Strategy", null), this.admin, "\"1\""))
			.hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + id, write("Strategy 2", null), this.admin, "\"1\""))
			.hasStatus(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void deleteAnUnusedLayerWithItsLabels() {
		layer("Business");
		UUID data = layer("Data");
		UUID application = layer("Application");
		String key = "architecture-layer." + data + ".name";
		assertThat(setLabels("de", key, "Daten")).hasStatus(HttpStatus.OK);
		assertThat(count("SELECT count(*) FROM i18n_entries WHERE entity_id = '" + data + "'")).isOne();
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + data, null, this.admin, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM i18n_entries WHERE entity_id = '" + data + "'")).isZero();
		assertThat(names(this.viewer)).containsExactly("Business", "Application");
		// the order stays dense
		JsonNode list = list(this.viewer);
		assertThat(list.get(1).path("sortOrder").asInt()).isOne();
		assertThat(list.get(1).path("id").asString()).isEqualTo(application.toString());
		List<JsonNode> changes = changes(data);
		assertThat(changes.get(0).path("eventType").asString()).isEqualTo("metamodel.layer-deleted");
		assertThat(changes.get(0).path("elementName").asString()).isEqualTo("Data");
	}

	@Test
	void deleteALayerInUseIsRejectedWithTheCount() {
		UUID application = layer("Application");
		UUID first = metatype("First", application, INSTANCE, this.admin);
		UUID extension = extension();
		metatype("Second", application, extension.toString(), this.architect);
		JsonNode before = find(list(this.admin), "Application");
		assertThat(before.path("metatypeCount").asInt()).isEqualTo(2);

		MvcTestResult result = call(HttpMethod.DELETE, LAYERS + "/" + application, null, this.admin, "\"1\"");
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:in-use");
		assertThat(body(result).path("count").asInt()).isEqualTo(2);
		assertThat(names(this.admin)).containsExactly("Application");
		// unassigning comes first
		clearLayer(first);
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + application, null, this.admin, "\"1\""))
			.hasStatus(HttpStatus.CONFLICT);
		this.jdbc.update("UPDATE metatypes SET architecture_layer_id = NULL");
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + application, null, this.admin, "\"1\""))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void importedLayersAreReadOnly() {
		importLayerPackage("layers-readonly", "Technology", "Business");
		UUID imported = UUID.fromString(this.jdbc.queryForObject(
				"SELECT id::text FROM architecture_layers WHERE name = 'Technology'", String.class));
		JsonNode before = find(list(this.viewer), "Technology");
		assertThat(before.path("scope").asString()).isEqualTo("imported");
		assertThat(before.path("sourcePackage").path("packageId").asString()).isEqualTo("layers-readonly");
		MvcTestResult rename = call(HttpMethod.PUT, LAYERS + "/" + imported, write("Tech", null), this.admin, "\"1\"");
		assertThat(rename).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(rename)).isEqualTo("urn:oea:problem:read-only-metatype");
		MvcTestResult move = call(HttpMethod.PUT, LAYERS + "/" + imported, write("Technology", 0), this.admin, "\"1\"");
		assertThat(move).hasStatus(HttpStatus.CONFLICT);
		MvcTestResult delete = call(HttpMethod.DELETE, LAYERS + "/" + imported, null, this.admin, "\"1\"");
		assertThat(delete).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(delete)).isEqualTo("urn:oea:problem:read-only-metatype");
		// whatever the version
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + imported, null, this.admin, "\"9\"")).hasStatus(HttpStatus.CONFLICT);
		assertThat(find(list(this.viewer), "Technology")).isEqualTo(before);
		assertThat(names(this.viewer)).containsExactly("Technology", "Business");
	}

	@Test
	void anImportedLayerBlocksAnOrganizationName() {
		importLayerPackage("layers-name", "Technology");
		MvcTestResult result = call(HttpMethod.POST, LAYERS, write("technology", null), this.admin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
	}

	@Test
	void readersAndWriters() {
		layer("Business");
		assertThat(call(HttpMethod.GET, LAYERS, null, this.viewer)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.GET, LAYERS, null, this.architect)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.GET, LAYERS, null, this.admin)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.GET, LAYERS, null, this.nobody)).hasStatus(HttpStatus.FORBIDDEN);
		UUID id = id(find(list(this.admin), "Business"));
		for (TestSession reader : List.of(this.viewer, this.architect, this.nobody)) {
			assertThat(call(HttpMethod.POST, LAYERS, write("Data", null), reader)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(call(HttpMethod.PUT, LAYERS + "/" + id, write("Data", null), reader, "\"1\""))
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(call(HttpMethod.DELETE, LAYERS + "/" + id, null, reader, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(this.mvc.get().uri("/api/v1" + LAYERS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(names(this.admin)).containsExactly("Business");
	}

	@Test
	void everyWriteIsRejectedInAnImportOnlyInstance() {
		UUID business = layer("Business");
		UUID data = layer("Data");
		this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'import-only' WHERE id = ?::uuid", INSTANCE);
		int events = count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.layer-%'");

		MvcTestResult create = call(HttpMethod.POST, LAYERS, write("Technology", null), this.admin);
		assertThat(create).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(type(create)).isEqualTo("urn:oea:problem:metamodel-locked");
		MvcTestResult rename = call(HttpMethod.PUT, LAYERS + "/" + business, write("Strategy", null), this.admin, "\"1\"");
		assertThat(rename).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(type(rename)).isEqualTo("urn:oea:problem:metamodel-locked");
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + data, write("Data", 0), this.admin, "\"1\""))
			.hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult delete = call(HttpMethod.DELETE, LAYERS + "/" + business, null, this.admin, "\"1\"");
		assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(type(delete)).isEqualTo("urn:oea:problem:metamodel-locked");
		// the layers and the audit trail are unchanged, reading still works
		assertThat(names(this.viewer)).containsExactly("Business", "Data");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.layer-%'")).isEqualTo(events);
		// unlocked, the same write succeeds
		this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'gui-and-import' WHERE id = ?::uuid", INSTANCE);
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + business, write("Strategy", null), this.admin, "\"1\""))
			.hasStatus(HttpStatus.OK);
	}

	@Test
	void auditEventsOfTheWritesAndNoneOnARejection() {
		UUID id = layer("Business");
		assertThat(call(HttpMethod.PUT, LAYERS + "/" + id, write("Strategy", 0), this.admin, "\"1\""))
			.hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.DELETE, LAYERS + "/" + id, null, this.admin, "\"2\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.jdbc.queryForList("""
				SELECT event_type FROM audit.events WHERE target_type = 'architecture-layer' AND target_id = ?
				ORDER BY id""", String.class, id.toString()))
			.containsExactly("metamodel.layer-created", "metamodel.layer-updated", "metamodel.layer-deleted");
		assertThat(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'metamodel.layer-updated' AND target_id = ?""",
				String.class, id.toString())).contains("Strategy").contains("name");
		List<JsonNode> items = changes(id);
		assertThat(items.stream().map((item) -> item.path("eventType").asString())).containsExactly(
				"metamodel.layer-deleted", "metamodel.layer-updated", "metamodel.layer-created");
		assertThat(items.stream().map((item) -> item.path("kind").asString())).containsOnly("layer");

		int events = count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.layer-%'");
		call(HttpMethod.POST, LAYERS, write(" ", null), this.admin);
		call(HttpMethod.POST, LAYERS, write("Data", null), this.viewer);
		layer("Data");
		call(HttpMethod.POST, LAYERS, write("data", null), this.admin);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.layer-%'"))
			.isEqualTo(events + 1);
	}

	// Requirement: Labels of a layer

	@Test
	void labelsOfALayer() {
		UUID strategy = layer("Strategy");
		String key = "architecture-layer." + strategy + ".name";
		MvcTestResult result = setLabels("de", key, "  Strategie ");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		JsonNode listed = null;
		for (JsonNode label : body(call(HttpMethod.GET, "/metamodel/labels/de", null, this.admin))) {
			if (key.equals(label.path("key").asString())) {
				listed = label;
			}
		}
		assertThat(listed).isNotNull();
		assertThat(listed.path("elementType").asString()).isEqualTo("architecture-layer");
		assertThat(listed.path("name").asString()).isEqualTo("Strategy");
		assertThat(listed.path("organizationText").asString()).isEqualTo("Strategie");
		// the bundle carries it, the English one carries the name
		assertThat(body(call(HttpMethod.GET, "/i18n/de", null, this.viewer)).path(key).asString()).isEqualTo("Strategie");
		assertThat(body(call(HttpMethod.GET, "/i18n/en", null, this.viewer)).path(key).asString()).isEqualTo("Strategy");
		// a layer that does not exist is an unknown key
		MvcTestResult unknown = setLabels("de", "architecture-layer." + UUID.randomUUID() + ".name", "Nichts");
		assertThat(unknown).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		// removing the label restores the name
		assertThat(setLabels("de", key, "")).hasStatus(HttpStatus.OK);
		assertThat(body(call(HttpMethod.GET, "/i18n/de", null, this.viewer)).path(key).asString()).isEqualTo("Strategy");
	}

	@ParameterizedTest
	@CsvSource({ "200, 200", "201, 422" })
	void layerLabelLength(int length, int status) {
		UUID id = layer("Strategy");
		MvcTestResult result = setLabels("de", "architecture-layer." + id + ".name", "x".repeat(length));
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
	}

	// Requirement: Layer of a metatype

	@Test
	void assignAndClearTheLayerOfAMetatype() {
		UUID application = layer("Application");
		UUID data = layer("Data");
		UUID type = metatype("Business Application", application, INSTANCE, this.admin);
		JsonNode read = body(call(HttpMethod.GET, "/metamodel/metatypes/" + type, null, this.viewer));
		assertThat(read.path("architectureLayerId").asString()).isEqualTo(application.toString());
		assertThat(read.path("architectureLayerName").asString()).isEqualTo("Application");
		assertThat(read.path("version").asInt()).isOne();
		assertThat(find(list(this.admin), "Application").path("metatypeCount").asInt()).isOne();

		// change the layer: audited with the field, the version rises
		MvcTestResult changed = putMetatype(type, "Business Application", data, "\"1\"");
		assertThat(changed).as(() -> content(changed)).hasStatus(HttpStatus.OK);
		assertThat(body(changed).path("architectureLayerName").asString()).isEqualTo("Data");
		assertThat(body(changed).path("version").asInt()).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'metamodel.metatype-updated' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, type.toString())).contains("architectureLayerId");

		// clear it
		MvcTestResult cleared = putMetatype(type, "Business Application", null, "\"2\"");
		assertThat(cleared).hasStatus(HttpStatus.OK);
		assertThat(body(cleared).path("architectureLayerId").isNull()).isTrue();
		assertThat(body(cleared).path("architectureLayerName").isNull()).isTrue();
		assertThat(body(cleared).path("version").asInt()).isEqualTo(3);
		assertThat(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'metamodel.metatype-updated' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, type.toString())).contains("architectureLayerId");
		// without a change of the layer the field is not named
		MvcTestResult same = putMetatype(type, "Business Application 2", null, "\"3\"");
		assertThat(same).hasStatus(HttpStatus.OK);
		assertThat(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'metamodel.metatype-updated' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, type.toString())).doesNotContain("architectureLayerId");
	}

	@Test
	void anUnknownLayerIsRejectedAndChangesNothing() {
		UUID application = layer("Application");
		UUID type = metatype("Typed", application, INSTANCE, this.admin);
		int events = count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.metatype-updated'");
		UUID unknown = UUID.randomUUID();
		MvcTestResult update = putMetatype(type, "Typed", unknown, "\"1\"");
		assertThat(update).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(update).path("invalidFields")).extracting(JsonNode::asString).containsExactly("architectureLayerId");
		MvcTestResult create = call(HttpMethod.POST, "/metamodel/metatypes", metatypeBody("Untyped " + unknown, unknown, INSTANCE),
				this.admin);
		assertThat(create).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(create).path("invalidFields")).extracting(JsonNode::asString).containsExactly("architectureLayerId");
		assertThat(body(call(HttpMethod.GET, "/metamodel/metatypes/" + type, null, this.admin)).path("version").asInt())
			.isOne();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.metatype-updated'"))
			.isEqualTo(events);
		assertThat(count("SELECT count(*) FROM metatypes WHERE name = 'Untyped " + unknown + "'")).isZero();
	}

	@Test
	void aMetatypeOfAnExtensionTakesAnInstanceLayer() {
		UUID application = layer("Application");
		UUID extension = extension();
		UUID type = metatype("Cloud Service", application, extension.toString(), this.architect);
		JsonNode read = body(call(HttpMethod.GET, "/metamodel/metatypes/" + type, null, this.architect));
		assertThat(read.path("architectureLayerId").asString()).isEqualTo(application.toString());
		assertThat(read.path("architectureLayerName").asString()).isEqualTo("Application");
		MvcTestResult unknown = call(HttpMethod.PUT, "/metamodel/metatypes/" + type,
				metatypeBody("Cloud Service", UUID.randomUUID(), extension.toString()), this.architect, "\"1\"");
		assertThat(unknown).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		// the effective metamodel of the extension lists the layer of its metatype
		JsonNode effective = body(call(HttpMethod.GET, "/metamodel/metatypes?configId=" + extension, null, this.architect));
		assertThat(effective.valueStream().filter((node) -> type.toString().equals(node.path("id").asString())))
			.singleElement()
			.satisfies((node) -> assertThat(node.path("architectureLayerName").asString()).isEqualTo("Application"));
	}

	@Test
	void anImportedMetatypeKeepsItsLayerAndCannotBeChanged() {
		importLayerPackage("layers-typed", "Technology");
		UUID type = UUID.fromString(this.jdbc.queryForObject(
				"SELECT id::text FROM metatypes WHERE source_package_id = 'layers-typed' ORDER BY name LIMIT 1", String.class));
		UUID other = layer("Business");
		JsonNode read = body(call(HttpMethod.GET, "/metamodel/metatypes/" + type, null, this.viewer));
		assertThat(read.path("architectureLayerName").asString()).isEqualTo("Technology");
		MvcTestResult result = putMetatype(type, read.path("name").asString(), other, "\"" + read.path("version").asInt() + "\"");
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:read-only-metatype");
	}

	// helpers

	/** Imports a package whose metatype "Type 1" has the first layer. */
	private void importLayerPackage(String packageId, String... layerNames) {
		ObjectNode pkg = TestPackages.pkg(packageId, 1);
		var layers = pkg.putArray("architectureLayers");
		for (int i = 0; i < layerNames.length; i++) {
			layers.addObject().put("id", TestPackages.uuid(packageId, "layer" + i)).put("name", layerNames[i])
				.put("sortOrder", i);
		}
		((ObjectNode) pkg.path("metatypes").get(0)).put("architectureLayerId", TestPackages.uuid(packageId, "layer0"));
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/import", pkg, this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("layersCreated").asInt()).isEqualTo(layerNames.length);
	}

	private UUID extension() {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by)
				SELECT gen_random_uuid(), ?, id, id FROM persons ORDER BY id LIMIT 1 RETURNING id""", UUID.class,
				unique("Solution"));
		return id(created(call(HttpMethod.POST, "/metamodel/configurations", JsonMapper.shared().createObjectNode()
			.put("solutionId", solution.toString()).put("name", unique("Extension")), this.architect)));
	}

	private UUID metatype(String name, UUID layer, String config, TestSession session) {
		return id(created(call(HttpMethod.POST, "/metamodel/metatypes", metatypeBody(unique(name), layer, config), session)));
	}

	private MvcTestResult putMetatype(UUID id, String name, UUID layer, String ifMatch) {
		return call(HttpMethod.PUT, "/metamodel/metatypes/" + id, metatypeBody(name, layer, INSTANCE), this.admin, ifMatch);
	}

	private void clearLayer(UUID metatype) {
		JsonNode read = body(call(HttpMethod.GET, "/metamodel/metatypes/" + metatype, null, this.admin));
		MvcTestResult result = putMetatype(metatype, read.path("name").asString(), null,
				"\"" + read.path("version").asInt() + "\"");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private static ObjectNode metatypeBody(String name, UUID layer, String config) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("configId", config);
		if (layer != null) {
			body.put("architectureLayerId", layer.toString());
		}
		else {
			body.putNull("architectureLayerId");
		}
		return body;
	}

	private UUID layer(String name) {
		return id(created(call(HttpMethod.POST, LAYERS, write(name, null), this.admin)));
	}

	private static ObjectNode write(String name, Integer sortOrder) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		if (sortOrder != null) {
			body.put("sortOrder", sortOrder);
		}
		return body;
	}

	/** The changes of kind layer that name one of the layers, newest first. */
	private List<JsonNode> changes(UUID... layers) {
		List<String> targets = java.util.Arrays.stream(layers).map(UUID::toString).toList();
		return body(call(HttpMethod.GET, "/metamodel/changes?kind=layer", null, this.admin)).path("items")
			.valueStream()
			.filter((item) -> targets.contains(item.path("targetId").asString()))
			.toList();
	}

	private JsonNode list(TestSession session) {
		MvcTestResult result = call(HttpMethod.GET, LAYERS, null, session);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private List<String> names(TestSession session) {
		return list(session).valueStream().map((layer) -> layer.path("name").asString()).toList();
	}

	private static JsonNode find(JsonNode list, String name) {
		for (JsonNode node : list) {
			if (name.equals(node.path("name").asString())) {
				return node;
			}
		}
		throw new AssertionError("no element " + name);
	}

	private MvcTestResult setLabels(String locale, String key, String text) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putObject("labels").put(key, text);
		return call(HttpMethod.PUT, "/metamodel/labels/" + locale, body, this.admin);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		return call(method, path, body, session, null);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri("/api/v1" + path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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
			return JsonMapper.shared().readTree(content(result));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-layers").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
