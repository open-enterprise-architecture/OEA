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
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Spec metamodel/viewpoints, the notation part (US-091, US-092; design D1–D3, D8 of
 * us-091-viewpoint-notations): notation, mapping and its limits, fallback, element catalog, and
 * read-only system-defined viewpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ViewpointNotationTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

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

	private TestPrincipals principals;

	private TestSession kurt;

	private UUID application;

	private UUID dataObject;

	private UUID dataFlow;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.application = metatype("Application", false);
		this.dataObject = metatype("Data Object", false);
		this.dataFlow = metatype("Data Flow", true);
	}

	// Requirement: Notation of a viewpoint

	@ParameterizedTest
	@ValueSource(strings = { "generic", "archimate3", "uml", "bpmn2" })
	void aNotationIsStored(String notation) {
		JsonNode created = created(write(unique("Notation"), notation, List.of(this.application), List.of()));
		assertThat(created.path("notation").asString()).isEqualTo(notation);
		assertThat(created.path("viewpointType").asString()).isEqualTo("user-defined");
		assertThat(created.path("sourcePackage").isNull()).isTrue();
		assertThat(ok(call(HttpMethod.GET, VIEWPOINTS + "/" + created.path("id").asString(), null, this.kurt, null))
			.path("notation")
			.asString()).isEqualTo(notation);
		assertThat(events("viewpoint.created", created.path("id").asString(), "notation")).isEqualTo(notation);
	}

	@Test
	void aMissingNotationBecomesGenericAndAnUnknownOneIsRejected() {
		ObjectNode without = write(unique("Without"), null, List.of(this.application), List.of());
		assertThat(created(without).path("notation").asString()).isEqualTo("generic");
		MvcTestResult unknown = post(write(unique("Unknown"), "visio", List.of(this.application), List.of()));
		assertThat(unknown).as(() -> content(unknown)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void aViewpointFromBeforeTheNotationIsGeneric() {
		long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		String id = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), ?, ?) RETURNING id::text""",
				String.class, unique("Integration View"), person);
		JsonNode read = ok(call(HttpMethod.GET, VIEWPOINTS + "/" + id, null, this.kurt, null));
		assertThat(read.path("notation").asString()).isEqualTo("generic");
		assertThat(read.path("viewpointType").asString()).isEqualTo("user-defined");
	}

	// Requirement: Notation mapping

	@Test
	void anElementOfTheNotationIsMapped() {
		JsonNode created = created(write(unique("Cloud Security"), "archimate3", List.of(this.application),
				List.of(this.dataFlow))
			.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", "#1a2B3c"),
					mapping(this.dataFlow, "archimate:Flow", null))));
		JsonNode mapped = created.path("mappings");
		assertThat(mapped).hasSize(2);
		JsonNode component = byMetatype(mapped, this.application);
		assertThat(component.path("notationElement").asString()).isEqualTo("archimate:ApplicationComponent");
		assertThat(component.path("visualHint").asString()).isEqualTo("#1a2B3c");
		assertThat(byMetatype(mapped, this.dataFlow).path("visualHint").isNull()).isTrue();
	}

	@Test
	void aMappingMayBeChangedAndRemoved() {
		JsonNode created = created(write(unique("Changed"), "archimate3", List.of(this.application), List.of())
			.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", null))));
		String id = created.path("id").asString();
		ObjectNode update = write(created.path("name").asString(), "archimate3", List.of(this.application), List.of());
		update.set("mappings", mappings(mapping(this.application, "archimate:Node", null)));
		JsonNode changed = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\""));
		assertThat(changed.path("mappings").get(0).path("notationElement").asString()).isEqualTo("archimate:Node");
		assertThat(this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = "
				+ "'viewpoint.updated' AND target_id = ?", String.class, id))
			.contains("\"mappings\"")
			.contains("archimate3")
			.doesNotContain("archimate:Node");
		update.set("mappings", mappings());
		assertThat(ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"2\"")).path("mappings"))
			.isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "archimate3, bpmn:Task, element", "archimate3, archimate:NoSuchElement, element",
			"archimate3, archimate:Flow, element", "archimate3, archimate:ApplicationComponent, connection",
			"uml, archimate:Node, element", "generic, archimate:Node, element", "bpmn2, generic:Arrow, element",
			"bpmn2, generic:Box, connection" })
	void aWrongElementIsRejected(String notation, String element, String kind) {
		boolean connection = "connection".equals(kind);
		UUID type = connection ? this.dataFlow : this.application;
		ObjectNode body = write(unique("Wrong"), notation, connection ? List.of(this.application) : List.of(type),
				connection ? List.of(type) : List.of());
		body.set("mappings", mappings(mapping(type, element, null)));
		long before = events();
		assertInvalid(post(body), "mappings");
		assertThat(events()).isEqualTo(before);
	}

	@Test
	void genericElementsFitEveryNotation() {
		ObjectNode body = write(unique("Generic Box"), "bpmn2", List.of(this.application), List.of(this.dataFlow));
		body.set("mappings", mappings(mapping(this.application, "generic:Box", null),
				mapping(this.dataFlow, "generic:Arrow", null)));
		assertThat(created(body).path("mappings")).hasSize(2);
	}

	@Test
	void aTypeThatIsNotAllowedAndADuplicateAreRejected() {
		ObjectNode notAllowed = write(unique("Not Allowed"), "archimate3", List.of(this.application), List.of());
		notAllowed.set("mappings", mappings(mapping(this.dataObject, "archimate:DataObject", null)));
		assertInvalid(post(notAllowed), "mappings");
		ObjectNode twice = write(unique("Twice"), "archimate3", List.of(this.application), List.of());
		twice.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", null),
				mapping(this.application, "archimate:Node", null)));
		assertInvalid(post(twice), "mappings");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name = ? OR name = ?",
				Integer.class, notAllowed.path("name").asString(), twice.path("name").asString())).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "0, 2001", "1, 2000" })
	void aMappingThatStillCarriesASizeIsAcceptedAndStoresNone(int width, int height) {
		ObjectNode body = write(unique("Old Size"), "archimate3", List.of(this.application), List.of());
		ObjectNode mapping = mapping(this.application, "archimate:ApplicationComponent", null);
		mapping.put("defaultWidth", width).put("defaultHeight", height);
		body.set("mappings", mappings(mapping));
		JsonNode created = created(body);
		assertThat(created.path("mappings")).hasSize(1);
		assertThat(created.path("mappings").get(0).has("defaultWidth")).isFalse();
		assertThat(created.path("sizes")).isEmpty();
		assertThat(created.path("effectiveMappings").get(0).path("sizeStored").asBoolean()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({ "#1a2B3c, 201", "#000000, 201", "red, 400", "#1a2b3, 400", "#1a2b3c4, 400", "1a2b3c, 400",
			"'#1a2b3g', 400" })
	void visualHint(String hint, int status) {
		ObjectNode body = write(unique("Hint"), "archimate3", List.of(this.application), List.of());
		body.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", hint)));
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result)).hasStatus(status);
	}

	@Test
	void aNotationChangeWithOldElementsIsRejected() {
		JsonNode created = created(write(unique("Change Notation"), "archimate3", List.of(this.application), List.of())
			.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", null))));
		String id = created.path("id").asString();
		ObjectNode update = write(created.path("name").asString(), "bpmn2", List.of(this.application), List.of());
		update.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", null)));
		assertInvalid(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\""), "mappings");
		update.set("mappings", mappings());
		JsonNode changed = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\""));
		assertThat(changed.path("notation").asString()).isEqualTo("bpmn2");
		assertThat(this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = "
				+ "'viewpoint.updated' AND target_id = ?", String.class, id))
			.contains("\"notation\"")
			.contains("\"mappings\"");
	}

	// Requirement: Fallback rendering

	@Test
	void unmappedTypesAreRenderedAsGenericShapes() {
		ObjectNode body = write(unique("Fallback"), "archimate3", List.of(this.application, this.dataObject),
				List.of(this.dataFlow));
		body.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", null)));
		JsonNode effective = created(body).path("effectiveMappings");
		assertThat(effective).hasSize(3);
		JsonNode mapped = byMetatype(effective, this.application);
		assertThat(mapped.path("fallback").asBoolean()).isFalse();
		assertThat(mapped.path("notationElement").asString()).isEqualTo("archimate:ApplicationComponent");
		assertThat(mapped.path("width").asInt()).isEqualTo(90);
		assertThat(mapped.path("height").asInt()).isEqualTo(30);
		assertThat(mapped.path("sizeStored").asBoolean()).isFalse();
		JsonNode box = byMetatype(effective, this.dataObject);
		assertThat(box.path("fallback").asBoolean()).isTrue();
		assertThat(box.path("notationElement").asString()).isEqualTo("generic:Box");
		assertThat(box.path("width").asInt()).isEqualTo(90);
		JsonNode arrow = byMetatype(effective, this.dataFlow);
		assertThat(arrow.path("fallback").asBoolean()).isTrue();
		assertThat(arrow.path("notationElement").asString()).isEqualTo("generic:Arrow");
		assertThat(arrow.path("length").asInt()).isEqualTo(90);
		assertThat(arrow.path("width").isNull()).isTrue();
	}

	@Test
	void aMappedHintIsEffective() {
		ObjectNode body = write(unique("Own Hint"), "archimate3", List.of(this.application), List.of());
		body.set("mappings", mappings(mapping(this.application, "archimate:ApplicationComponent", "#ff0000")));
		JsonNode effective = created(body).path("effectiveMappings").get(0);
		assertThat(effective.path("visualHint").asString()).isEqualTo("#ff0000");
	}

	// Requirement: Limits of the notation mapping

	@ParameterizedTest
	@CsvSource({ "1000, 201", "1001, 400" })
	void numberOfMappings(int count, int status) {
		List<UUID> elements = new ArrayList<>();
		List<UUID> connections = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			elements.add(metatype("Element " + i, false));
			connections.add(metatype("Connection " + i, true));
		}
		ObjectNode body = write(unique("Many"), "archimate3", elements, connections);
		ArrayNode mappings = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < 1000; i++) {
			boolean connection = i >= 500;
			mappings.add(mapping(connection ? connections.get(i - 500) : elements.get(i),
					connection ? "archimate:Flow" : "archimate:Node", null));
		}
		if (count > 1000) {
			// a 1001st mapping of the same type, which the contract rejects by its count
			mappings.add(mapping(elements.get(0), "archimate:Node", null));
		}
		body.set("mappings", mappings);
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.hasStatus(status);
	}

	@Test
	void aKeyOf100CharactersReachesTheCatalogAnd101IsRejected() {
		ObjectNode body = write(unique("Key"), "archimate3", List.of(this.application), List.of());
		body.set("mappings", mappings(mapping(this.application, "archimate:" + "x".repeat(90), null)));
		assertInvalid(post(body), "mappings");
		body.set("mappings", mappings(mapping(this.application, "archimate:" + "x".repeat(91), null)));
		MvcTestResult tooLong = post(body);
		assertThat(tooLong).as(() -> content(tooLong)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Requirement: Origin of a viewpoint set only by the import

	@Test
	void aForgedOriginIsIgnored() {
		ObjectNode body = write(unique("Forged"), "generic", List.of(this.application), List.of());
		body.put("viewpointType", "system-defined");
		body.putObject("sourcePackage").put("packageId", "oea-starter-togaf-classic").put("version", "1.2.0");
		JsonNode created = created(body);
		assertThat(created.path("viewpointType").asString()).isEqualTo("user-defined");
		assertThat(created.path("sourcePackage").isNull()).isTrue();
		String id = created.path("id").asString();
		assertThat(call(HttpMethod.DELETE, VIEWPOINTS + "/" + id, null, this.kurt, "\"1\""))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void aSystemDefinedViewpointCannotBeChangedEvenWithTheCurrentVersion() {
		String id = systemDefined("Application Landscape");
		long before = events();
		ObjectNode update = write(unique("Renamed"), "generic", List.of(this.application), List.of());
		MvcTestResult updated = call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\"");
		assertThat(updated).as(() -> content(updated)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(updated).path("type").asString()).isEqualTo("urn:oea:problem:read-only-viewpoint");
		assertThat(body(updated).path("detail").asString()).isEqualTo("System-defined viewpoints are read-only");
		MvcTestResult deleted = call(HttpMethod.DELETE, VIEWPOINTS + "/" + id, null, this.kurt, "\"1\"");
		assertThat(deleted).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(deleted).path("type").asString()).isEqualTo("urn:oea:problem:read-only-viewpoint");
		assertThat(events()).isEqualTo(before);
		JsonNode read = ok(call(HttpMethod.GET, VIEWPOINTS + "/" + id, null, this.kurt, null));
		assertThat(read.path("viewpointType").asString()).isEqualTo("system-defined");
		assertThat(read.path("sourcePackage").path("packageId").asString()).isEqualTo("oea-starter-togaf-classic");
		assertThat(read.path("sourcePackage").path("version").asString()).isEqualTo("1.2.0");
		assertThat(read.path("version").asInt()).isOne();
	}

	// Requirement: Element catalog

	@Test
	void theCatalogIsReadFromItsLiteralPath() {
		JsonNode catalog = ok(call(HttpMethod.GET, VIEWPOINTS + "/notations", null, this.kurt, null));
		assertThat(catalog.isArray()).isTrue();
		JsonNode component = element(catalog, "archimate:ApplicationComponent");
		assertThat(component.path("notation").asString()).isEqualTo("archimate3");
		assertThat(component.path("kind").asString()).isEqualTo("element");
		assertThat(component.path("label").asString()).isEqualTo("Application Component");
		assertThat(element(catalog, "archimate:Flow").path("kind").asString()).isEqualTo("relationship");
		assertThat(element(catalog, "generic:Box").path("notation").asString()).isEqualTo("generic");
	}

	@Test
	void theCatalogNeedsEntityReadOrViewpointManage() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(call(HttpMethod.GET, VIEWPOINTS + "/notations", null, viewer, null)).hasStatus(HttpStatus.OK);
		String role = "manage-only-" + UUID.randomUUID().toString().substring(0, 8);
		this.jdbc.update("INSERT INTO authorization_roles (id, name, is_builtin) VALUES (?, ?, false)", role, role);
		this.jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, 'viewpoint:manage')", role);
		TestSession manager = this.principals.personWithIdpRole(role);
		assertThat(call(HttpMethod.GET, VIEWPOINTS + "/notations", null, manager, null)).hasStatus(HttpStatus.OK);
		TestSession reader = this.principals.personWithIdpRole("portal-reader");
		assertThat(call(HttpMethod.GET, VIEWPOINTS + "/notations", null, reader, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.GET, VIEWPOINTS + "/notations", null, this.principals.personWithoutRole(), null))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Default sizes per metatype (s17-viewpoint-default-sizes)

	@Test
	void sizesAreStoredWithoutAMappingAndReturnedEffective() {
		ObjectNode body = write(unique("Sized"), null, List.of(this.application, this.dataObject),
				List.of(this.dataFlow));
		body.set("sizes", sizes(size(this.application, 120, 40), length(this.dataFlow, 150)));
		JsonNode created = created(body);
		assertThat(created.path("mappings")).isEmpty();
		assertThat(created.path("sizes")).hasSize(2);
		JsonNode effective = created.path("effectiveMappings");
		JsonNode component = byMetatype(effective, this.application);
		assertThat(component.path("width").asInt()).isEqualTo(120);
		assertThat(component.path("height").asInt()).isEqualTo(40);
		assertThat(component.path("length").isNull()).isTrue();
		assertThat(component.path("sizeStored").asBoolean()).isTrue();
		JsonNode flow = byMetatype(effective, this.dataFlow);
		assertThat(flow.path("length").asInt()).isEqualTo(150);
		assertThat(flow.path("width").isNull()).isTrue();
		assertThat(flow.path("sizeStored").asBoolean()).isTrue();
		JsonNode box = byMetatype(effective, this.dataObject);
		assertThat(box.path("width").asInt()).isEqualTo(90);
		assertThat(box.path("height").asInt()).isEqualTo(30);
		assertThat(box.path("sizeStored").asBoolean()).isFalse();
	}

	@Test
	void theDefaultSizesApplyWhateverTheNotationElement() {
		ObjectNode body = write(unique("Defaults"), "bpmn2", List.of(this.application), List.of(this.dataFlow));
		body.set("mappings", mappings(mapping(this.application, "bpmn:Pool", null),
				mapping(this.dataFlow, "bpmn:SequenceFlow", null)));
		JsonNode effective = created(body).path("effectiveMappings");
		assertThat(byMetatype(effective, this.application).path("width").asInt()).isEqualTo(90);
		assertThat(byMetatype(effective, this.application).path("height").asInt()).isEqualTo(30);
		assertThat(byMetatype(effective, this.dataFlow).path("length").asInt()).isEqualTo(90);
	}

	@ParameterizedTest
	@CsvSource({ "1, 1, 201", "2000, 2000, 201", "0, 80, 400", "80, 0, 400", "2001, 80, 400", "80, 2001, 400" })
	void elementSizeBoundaries(int width, int height, int status) {
		ObjectNode body = write(unique("Size"), null, List.of(this.application), List.of());
		body.set("sizes", sizes(size(this.application, width, height)));
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result)).hasStatus(status);
		if (status != 201) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name = ?", Integer.class,
					body.path("name").asString())).isZero();
		}
	}

	@ParameterizedTest
	@CsvSource({ "1, 201", "2000, 201", "0, 400", "2001, 400" })
	void lengthBoundaries(int length, int status) {
		ObjectNode body = write(unique("Length"), null, List.of(this.application), List.of(this.dataFlow));
		body.set("sizes", sizes(length(this.dataFlow, length)));
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result)).hasStatus(status);
	}

	@ParameterizedTest
	@CsvSource({ "1000, 201", "1001, 400" })
	void numberOfSizes(int count, int status) {
		List<UUID> elements = new ArrayList<>();
		List<UUID> connections = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			elements.add(metatype("Element " + i, false));
			connections.add(metatype("Connection " + i, true));
		}
		ObjectNode body = write(unique("Many Sizes"), null, elements, connections);
		ArrayNode sizes = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < 1000; i++) {
			sizes.add((i >= 500) ? length(connections.get(i - 500), 90) : size(elements.get(i), 90, 30));
		}
		if (count > 1000) {
			sizes.add(size(elements.get(0), 90, 30));
		}
		body.set("sizes", sizes);
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.hasStatus(status);
	}

	@ParameterizedTest
	@ValueSource(strings = { "length on element", "width on connection", "width only", "height only", "not allowed",
			"twice", "empty" })
	void aWrongSizeIsRejectedAndChangesNothing(String kind) {
		ObjectNode body = write(unique("Wrong Size"), null, List.of(this.application), List.of(this.dataFlow));
		ArrayNode sizes = JsonMapper.shared().createArrayNode();
		switch (kind) {
			case "length on element" -> sizes.add(length(this.application, 100));
			case "width on connection" -> sizes.add(size(this.dataFlow, 100, null));
			case "width only" -> sizes.add(size(this.application, 100, null));
			case "height only" -> sizes.add(size(this.application, null, 50));
			case "not allowed" -> sizes.add(size(this.dataObject, 100, 50));
			case "twice" -> sizes.add(size(this.application, 100, 50)).add(size(this.application, 110, 60));
			default -> sizes.add(JsonMapper.shared().createObjectNode().put("metatypeId", this.application.toString()));
		}
		body.set("sizes", sizes);
		long before = events();
		assertInvalid(post(body), "sizes");
		assertThat(events()).isEqualTo(before);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name = ?", Integer.class,
				body.path("name").asString())).isZero();
	}

	@Test
	void anUpdateReplacesTheSizesAndWithoutSizesRemovesThem() {
		ObjectNode body = write(unique("Resize"), null, List.of(this.application), List.of());
		body.set("sizes", sizes(size(this.application, 120, 40)));
		JsonNode created = created(body);
		String id = created.path("id").asString();
		body.set("sizes", sizes(size(this.application, 130, 40)));
		JsonNode changed = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, body, this.kurt, "\"1\""));
		assertThat(changed.path("sizes").get(0).path("width").asInt()).isEqualTo(130);
		assertThat(this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = "
				+ "'viewpoint.updated' AND target_id = ?", String.class, id)).contains("\"sizes\"");
		// the same sizes again do not name sizes among the changed fields
		ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, body, this.kurt, "\"2\""));
		assertThat(this.jdbc.queryForList("SELECT jsonb_exists(details -> 'fields', 'sizes') FROM audit.events WHERE event_type = "
				+ "'viewpoint.updated' AND target_id = ? ORDER BY id", Boolean.class, id)).containsExactly(true, false);
		body.remove("sizes");
		JsonNode removed = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, body, this.kurt, "\"3\""));
		assertThat(removed.path("sizes")).isEmpty();
		assertThat(byMetatype(removed.path("effectiveMappings"), this.application).path("sizeStored").asBoolean())
			.isFalse();
	}

	@Test
	void removingAMetatypeDropsItsSizeAndDeletingAMetatypeRemovesItsSizes() {
		UUID doomed = metatype("Doomed", false);
		ObjectNode body = write(unique("Shrinking"), null, List.of(this.application, this.dataObject, doomed),
				List.of());
		body.set("sizes", sizes(size(this.application, 120, 40), size(this.dataObject, 100, 50), size(doomed, 80, 20)));
		JsonNode created = created(body);
		String id = created.path("id").asString();
		this.jdbc.update("DELETE FROM metatypes WHERE id = ?", doomed);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_sizes WHERE viewpoint_id = ?::uuid",
				Integer.class, id)).isEqualTo(2);
		ObjectNode update = write(created.path("name").asString(), null, List.of(this.dataObject), List.of());
		update.set("sizes", sizes(size(this.dataObject, 100, 50)));
		JsonNode changed = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\""));
		assertThat(changed.path("sizes")).hasSize(1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_sizes WHERE viewpoint_id = ?::uuid "
				+ "AND metatype_id = ?", Integer.class, id, this.application)).isZero();
		// a size for the removed metatype is rejected
		update.set("sizes", sizes(size(this.application, 120, 40)));
		assertInvalid(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"2\""), "sizes");
	}

	@Test
	void aSystemDefinedViewpointKeepsItsSizesReadOnly() {
		String id = systemDefined("Sized Landscape");
		this.jdbc.update("INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt) "
				+ "VALUES (?::uuid, ?, 100, 50)", id, this.application);
		ObjectNode update = write(unique("Renamed"), null, List.of(this.application), List.of());
		update.set("sizes", sizes(size(this.application, 200, 60)));
		long before = events();
		MvcTestResult result = call(HttpMethod.PUT, VIEWPOINTS + "/" + id, update, this.kurt, "\"1\"");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(this.jdbc.queryForObject("SELECT width_pt FROM viewpoint_sizes WHERE viewpoint_id = ?::uuid", Integer.class,
				id)).isEqualTo(100);
		assertThat(events()).isEqualTo(before);
	}

	@Test
	void aSizeChangeNeedsTheCurrentVersion() {
		ObjectNode body = write(unique("Stale"), null, List.of(this.application), List.of());
		JsonNode created = created(body);
		body.set("sizes", sizes(size(this.application, 120, 40)));
		String path = VIEWPOINTS + "/" + created.path("id").asString();
		assertThat(call(HttpMethod.PUT, path, body, this.kurt, "\"7\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(call(HttpMethod.PUT, path, body, this.kurt, null)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_sizes WHERE viewpoint_id = ?::uuid",
				Integer.class, created.path("id").asString())).isZero();
	}

	// helpers

	private MvcTestResult post(ObjectNode body) {
		return call(HttpMethod.POST, VIEWPOINTS, body, this.kurt, null);
	}

	private JsonNode created(ObjectNode body) {
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static ObjectNode write(String name, String notation, List<UUID> metatypes, List<UUID> connections) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		if (notation != null) {
			body.put("notation", notation);
		}
		ArrayNode types = body.putArray("metatypeIds");
		metatypes.forEach((id) -> types.add(id.toString()));
		ArrayNode relations = body.putArray("connectionTypeIds");
		connections.forEach((id) -> relations.add(id.toString()));
		return body;
	}

	private static ObjectNode mapping(UUID metatype, String element, String hint) {
		ObjectNode mapping = JsonMapper.shared()
			.createObjectNode()
			.put("metatypeId", metatype.toString())
			.put("notationElement", element);
		if (hint != null) {
			mapping.put("visualHint", hint);
		}
		return mapping;
	}

	private static ObjectNode size(UUID metatype, Integer width, Integer height) {
		ObjectNode size = JsonMapper.shared().createObjectNode().put("metatypeId", metatype.toString());
		if (width != null) {
			size.put("width", width);
		}
		if (height != null) {
			size.put("height", height);
		}
		return size;
	}

	private static ObjectNode length(UUID metatype, int length) {
		return JsonMapper.shared().createObjectNode().put("metatypeId", metatype.toString()).put("length", length);
	}

	private static ArrayNode sizes(ObjectNode... sizes) {
		ArrayNode array = JsonMapper.shared().createArrayNode();
		for (ObjectNode size : sizes) {
			array.add(size);
		}
		return array;
	}

	private static ArrayNode mappings(ObjectNode... mappings) {
		ArrayNode array = JsonMapper.shared().createArrayNode();
		for (ObjectNode mapping : mappings) {
			array.add(mapping);
		}
		return array;
	}

	private static JsonNode byMetatype(JsonNode mappings, UUID metatype) {
		for (JsonNode mapping : mappings) {
			if (mapping.path("metatypeId").asString().equals(metatype.toString())) {
				return mapping;
			}
		}
		throw new AssertionError("no mapping of " + metatype);
	}

	private static JsonNode element(JsonNode catalog, String key) {
		for (JsonNode element : catalog) {
			if (element.path("key").asString().equals(key)) {
				return element;
			}
		}
		throw new AssertionError("no catalog element " + key);
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private long events() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type LIKE 'viewpoint.%'",
				Long.class);
	}

	private String events(String type, String id, String detail) {
		return this.jdbc.queryForObject("SELECT details ->> ? FROM audit.events WHERE event_type = ? AND target_id = ?",
				String.class, detail, type, id);
	}

	/** A system-defined viewpoint as the package import stores it, with version 1. */
	private String systemDefined(String name) {
		long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		return this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, viewpoint_type, source_package_id, source_package_version, created_by)
				VALUES (gen_random_uuid(), ?, 'system-defined', 'oea-starter-togaf-classic', '1.2.0', ?)
				RETURNING id::text""", String.class, unique(name), person);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, ?) RETURNING id""", UUID.class,
				INSTANCE, unique(name), connection);
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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
		return JsonMapper.shared().readTree(content(result));
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-viewpoint-notations").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
