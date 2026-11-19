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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Spec metamodel/package-import, the viewpoint part (US-092 AC3, REQ-059; design D4, D8 of
 * us-091-viewpoint-notations), on an own database with the default import size limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PackageViewpointImportTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String IMPORT = "/api/v1/metamodel/import";

	private static final String STARTER = "oea-starter-togaf-classic";

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

	private TestSession admin;

	@BeforeEach
	void principal() {
		this.admin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("metamodel-admin");
	}

	// Requirement: Viewpoints in a package

	@Test
	void theStarterPackageInstallsTwoSystemDefinedViewpoints() {
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/metamodel/starter-packages/" + STARTER + "/import")
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.exchange();
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		JsonNode outcome = body(result);
		assertThat(outcome.path("viewpointsCreated").asInt()).isEqualTo(2);
		assertThat(outcome.path("viewpointsUpdated").asInt()).isZero();
		assertThat(outcome.path("viewpointsDeleted").asInt()).isZero();
		assertThat(this.jdbc.queryForList("""
				SELECT name || ':' || notation || ':' || viewpoint_type || ':' || source_package_id || ':'
				       || source_package_version FROM viewpoints WHERE source_package_id = ? ORDER BY name""",
				String.class, STARTER)).containsExactly(
						"Application Landscape:archimate3:system-defined:" + STARTER + ":1.4.0",
						"Technology Landscape:archimate3:system-defined:" + STARTER + ":1.4.0");
		assertThat(this.jdbc.queryForList("""
				SELECT m.name || '=' || vm.notation_element FROM viewpoint_mappings vm
				JOIN viewpoints v ON v.id = vm.viewpoint_id JOIN metatypes m ON m.id = vm.metatype_id
				WHERE v.name = 'Application Landscape' ORDER BY m.name""", String.class))
			.containsExactly("Application Component=archimate:ApplicationComponent",
					"Application Service=archimate:ApplicationService", "Data Object=archimate:DataObject");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM viewpoint_metatypes l JOIN viewpoints v ON v.id = l.viewpoint_id
				WHERE v.name = 'Technology Landscape'""", Integer.class)).isEqualTo(4);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'viewpointsCreated' || '/' || (details ->> 'viewpointsUpdated') || '/'
				       || jsonb_array_length(details -> 'viewpointsDeleted')
				FROM audit.events WHERE event_type = 'metamodel.package-imported' AND target_id = ?""", String.class,
				STARTER)).isEqualTo("2/0/0");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM viewpoint_sizes s JOIN viewpoints v ON v.id = s.viewpoint_id
				WHERE v.source_package_id = ?""", Integer.class, STARTER)).isZero();
		// a second import replaces them and keeps the IDs
		List<String> ids = this.jdbc.queryForList("SELECT id::text FROM viewpoints WHERE source_package_id = ? ORDER BY id",
				String.class, STARTER);
		MvcTestResult again = this.mvc.post()
			.uri("/api/v1/metamodel/starter-packages/" + STARTER + "/import")
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.exchange();
		assertThat(again).as(() -> content(again)).hasStatus(HttpStatus.OK);
		assertThat(body(again).path("viewpointsCreated").asInt()).isZero();
		assertThat(body(again).path("viewpointsUpdated").asInt()).isEqualTo(2);
		assertThat(this.jdbc.queryForList("SELECT id::text FROM viewpoints WHERE source_package_id = ? ORDER BY id",
				String.class, STARTER)).isEqualTo(ids);
	}

	@Test
	void anUnknownMetatypeFailsTheImport() {
		ObjectNode pkg = pkg("unknown-type");
		viewpoint(pkg, "Landscape", List.of(UUID.randomUUID().toString()));
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/viewpoints/0/metatypeIds");
		assertNothingStored("unknown-type");
	}

	@Test
	void aMetatypeOfASolutionExtensionFailsTheImport() {
		long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		String solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), 'Ext', ?, ?)
				RETURNING id::text""", String.class, person, person);
		String config = this.jdbc.queryForObject("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name)
				VALUES (gen_random_uuid(), 'solution', ?::uuid, ?::uuid, 'Extension') RETURNING id::text""",
				String.class, INSTANCE, solution);
		String extension = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES (?::uuid, 'Cloud Service') RETURNING id::text""",
				String.class, config);
		ObjectNode pkg = pkg("extension-type");
		viewpoint(pkg, "Landscape", List.of(extension));
		MvcTestResult result = post(pkg);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/viewpoints/0/metatypeIds");
		assertNothingStored("extension-type");
	}

	@Test
	void aConnectionTypeAmongTheElementTypesFailsTheImport() {
		ObjectNode pkg = pkg("kind-mix");
		String connection = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, 'Kind Mix Flow', true)
				RETURNING id::text""", String.class, INSTANCE);
		viewpoint(pkg, "Landscape", List.of(connection));
		assertThat(post(pkg)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertNothingStored("kind-mix");
	}

	@Test
	void shapeAssignmentsStayUnsupported() {
		ObjectNode pkg = pkg("shapes");
		pkg.putArray("shapeAssignments")
			.addObject()
			.put("viewpointKey", "landscape")
			.put("metatypeId", UUID.randomUUID().toString())
			.put("shapeKey", "box");
		MvcTestResult result = post(pkg);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/shapeAssignments");
	}

	@Test
	void aMappingOfAForeignElementFailsTheImport() {
		ObjectNode pkg = pkg("foreign-element");
		ObjectNode viewpoint = viewpoint(pkg, "Landscape", List.of(typeId(pkg, 0)));
		viewpoint.put("notation", "archimate3");
		viewpoint.withArray("mappings").addObject().put("metatypeId", typeId(pkg, 0)).put("notationElement", "bpmn:Task");
		MvcTestResult result = post(pkg);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/viewpoints/0");
		assertNothingStored("foreign-element");
	}

	@Test
	void aVisualHintWithMarkupFailsTheImport() {
		ObjectNode pkg = pkg("hint");
		ObjectNode viewpoint = viewpoint(pkg, "Landscape", List.of(typeId(pkg, 0)));
		viewpoint.put("notation", "archimate3");
		viewpoint.withArray("mappings")
			.addObject()
			.put("metatypeId", typeId(pkg, 0))
			.put("notationElement", "archimate:Node")
			.put("visualHint", "red;background:url(x)");
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).startsWith("/viewpoints/0");
		assertNothingStored("hint");
	}

	// Requirement: Package viewpoints never take over foreign viewpoints

	@Test
	void anotherPackageOrTheOrganizationNeverLosesAViewpointByName() {
		ObjectNode owner = pkg("owner-package");
		viewpoint(owner, "Shared Landscape", List.of(typeId(owner, 0)));
		assertThat(post(owner)).hasStatus(HttpStatus.OK);
		ObjectNode other = pkg("other-package");
		viewpoint(other, "shared landscape", List.of(typeId(other, 0)));
		MvcTestResult conflict = post(other);
		assertThat(conflict).as(() -> content(conflict)).hasStatus(HttpStatus.CONFLICT);
		JsonNode entry = body(conflict).path("conflictList").get(0);
		assertThat(entry.path("element").asString()).isEqualTo("viewpoint shared landscape");
		assertThat(entry.path("reason").asString()).isEqualTo("other-package");
		assertNothingStored("other-package");

		String organization = organizationViewpoint("Organization View");
		ObjectNode byName = pkg("by-name");
		viewpoint(byName, "Organization View", List.of(typeId(byName, 0)));
		MvcTestResult byNameResult = post(byName);
		assertThat(byNameResult).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(byNameResult).path("conflictList").get(0).path("reason").asString()).isEqualTo("organization");
		assertThat(viewpointState(organization)).isEqualTo("Organization View:user-defined:null:1");
	}

	@Test
	void theIdOfAForeignViewpointIsAConflictAndNothingChanges() {
		String organization = organizationViewpoint("Integration View");
		ObjectNode takeover = pkg("takeover");
		viewpoint(takeover, "Integration Overview", List.of(typeId(takeover, 0))).put("id", organization);
		MvcTestResult conflict = post(takeover);
		assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
		assertThat(viewpointState(organization)).isEqualTo("Integration View:user-defined:null:1");

		ObjectNode owner = pkg("id-owner");
		ObjectNode owned = viewpoint(owner, "Owned", List.of(typeId(owner, 0)));
		assertThat(post(owner)).hasStatus(HttpStatus.OK);
		String ownedId = owned.path("id").asString();
		ObjectNode other = pkg("id-thief");
		viewpoint(other, "Thief Name", List.of(typeId(other, 0))).put("id", ownedId);
		assertThat(post(other)).hasStatus(HttpStatus.CONFLICT);
		assertThat(viewpointState(ownedId)).startsWith("Owned:system-defined:id-owner:");
	}

	@Test
	void aReimportUpdatesRenamesAndDropsOnlyItsOwnViewpoints() {
		String organization = organizationViewpoint("Untouched Organization View");
		ObjectNode first = pkg("reimport-vp");
		ObjectNode keep = viewpoint(first, "Reimport Apps", List.of(typeId(first, 0)));
		ObjectNode drop = viewpoint(first, "Reimport Tech", List.of(typeId(first, 1)));
		MvcTestResult created = post(first);
		assertThat(created).as(() -> content(created)).hasStatus(HttpStatus.OK);
		assertThat(body(created).path("viewpointsCreated").asInt()).isEqualTo(2);
		String keepId = keep.path("id").asString();
		String dropId = drop.path("id").asString();

		ObjectNode second = pkg("reimport-vp");
		second.put("packageVersion", "1.1.0");
		viewpoint(second, "Reimport Apps Overview", List.of(typeId(second, 0), typeId(second, 1))).put("id", keepId);
		MvcTestResult again = post(second);
		assertThat(again).as(() -> content(again)).hasStatus(HttpStatus.OK);
		assertThat(body(again).path("viewpointsCreated").asInt()).isZero();
		assertThat(body(again).path("viewpointsUpdated").asInt()).isOne();
		assertThat(body(again).path("viewpointsDeleted").asInt()).isOne();
		assertThat(viewpointState(keepId)).startsWith("Reimport Apps Overview:system-defined:reimport-vp:2");
		assertThat(this.jdbc.queryForObject("SELECT source_package_version FROM viewpoints WHERE id = ?::uuid",
				String.class, keepId)).isEqualTo("1.1.0");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE id = ?::uuid", Integer.class, dropId))
			.isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_metatypes WHERE viewpoint_id = ?::uuid",
				Integer.class, keepId)).isEqualTo(2);
		assertThat(viewpointState(organization)).isEqualTo("Untouched Organization View:user-defined:null:1");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'viewpointsCreated' || '/' || (details ->> 'viewpointsUpdated') || '/'
				       || (details -> 'viewpointsDeleted' ->> 0)
				FROM audit.events WHERE event_type = 'metamodel.package-imported' AND target_id = 'reimport-vp'
				ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo("0/1/" + dropId);
	}

	@Test
	void aReimportThatDropsAViewpointRemovesItsDiagramsForGoodAndCountsThem() {
		ObjectNode first = pkg("reimport-diagrams");
		ObjectNode keep = viewpoint(first, "Diagrams Kept", List.of(typeId(first, 0)));
		ObjectNode drop = viewpoint(first, "Diagrams Dropped", List.of(typeId(first, 1)));
		assertThat(post(first)).hasStatus(HttpStatus.OK);
		String keepId = keep.path("id").asString();
		String dropId = drop.path("id").asString();
		diagram(keepId, "Kept 1", false);
		diagram(keepId, "Kept 2", false);
		String first1 = diagram(dropId, "Dropped 1", false);
		String second1 = diagram(dropId, "Dropped 2", false);
		diagram(dropId, "Dropped 3", true);

		// a rejected re-import (unknown metatype) deletes nothing
		ObjectNode broken = pkg("reimport-diagrams");
		viewpoint(broken, "Diagrams Kept", List.of(UUID.randomUUID().toString())).put("id", keepId);
		assertThat(post(broken)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE viewpoint_id = ?::uuid",
				Integer.class, dropId)).isEqualTo(3);

		// a re-import that keeps the viewpoint keeps its diagrams
		ObjectNode kept = pkg("reimport-diagrams");
		kept.put("packageVersion", "1.0.1");
		viewpointWithId(kept, keepId, "Diagrams Kept", List.of(typeId(kept, 0)));
		viewpointWithId(kept, dropId, "Diagrams Dropped", List.of(typeId(kept, 1)));
		MvcTestResult unchanged = post(kept);
		assertThat(unchanged).as(() -> content(unchanged)).hasStatus(HttpStatus.OK);
		assertThat(body(unchanged).path("diagramsDeleted").asInt()).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagrams", Integer.class)).isEqualTo(5);

		// a re-import without the viewpoint removes its diagrams, the deleted one included
		ObjectNode second = pkg("reimport-diagrams");
		second.put("packageVersion", "1.1.0");
		viewpointWithId(second, keepId, "Diagrams Kept", List.of(typeId(second, 0)));
		MvcTestResult dropped = post(second);
		assertThat(dropped).as(() -> content(dropped)).hasStatus(HttpStatus.OK);
		assertThat(body(dropped).path("diagramsDeleted").asInt()).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE viewpoint_id = ?::uuid",
				Integer.class, dropId)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE viewpoint_id = ?::uuid",
				Integer.class, keepId)).isEqualTo(2);
		for (String id : List.of(first1, second1)) {
			assertThat(this.jdbc.queryForObject("""
					SELECT details ->> 'cause' FROM audit.events
					WHERE event_type = 'diagram.deleted' AND target_id = ?""", String.class, id))
				.isEqualTo("viewpoint-deleted");
		}
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'diagramsDeleted' FROM audit.events
				WHERE event_type = 'metamodel.package-imported' AND target_id = 'reimport-diagrams'
				ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo("2");
	}

	private String diagram(String viewpointId, String name, boolean deleted) {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		return this.jdbc.queryForObject("""
				INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by, deleted_at, deleted_by)
				VALUES (?, ?::uuid, ?, ?, CASE WHEN ? THEN now() END, CASE WHEN ? THEN ?::bigint END) RETURNING id::text""",
				String.class, name + " " + viewpointId.substring(0, 8), viewpointId, person, person, deleted, deleted,
				person);
	}

	@Test
	void twoViewpointsMaySwapTheirNamesAtAReimport() {
		ObjectNode first = pkg("swap");
		viewpoint(first, "Alpha", List.of(typeId(first, 0)));
		viewpoint(first, "Beta", List.of(typeId(first, 1)));
		assertThat(post(first)).hasStatus(HttpStatus.OK);
		ObjectNode second = pkg("swap");
		List<String> ids = new ArrayList<>();
		first.withArray("viewpoints").forEach((viewpoint) -> ids.add(viewpoint.path("id").asString()));
		viewpointWithId(second, ids.get(0), "Beta", List.of(typeId(second, 0)));
		viewpointWithId(second, ids.get(1), "Alpha", List.of(typeId(second, 1)));
		MvcTestResult swapped = post(second);
		assertThat(swapped).as(() -> content(swapped)).hasStatus(HttpStatus.OK);
		assertThat(viewpointState(ids.get(0))).startsWith("Beta:system-defined:swap:");
		assertThat(viewpointState(ids.get(1))).startsWith("Alpha:system-defined:swap:");
	}

	// Requirement: Limits and checks of package viewpoints

	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 422" })
	void viewpointNameLength(int length, int status) {
		ObjectNode pkg = pkg("name-" + length);
		viewpoint(pkg, "n".repeat(length), List.of(typeId(pkg, 0)));
		assertLimit(pkg, "name-" + length, status);
	}

	@ParameterizedTest
	@CsvSource({ "1000, 200", "1001, 422" })
	void viewpointDescriptionLength(int length, int status) {
		ObjectNode pkg = pkg("description-" + length);
		viewpoint(pkg, "Described", List.of(typeId(pkg, 0))).put("description", "d".repeat(length));
		assertLimit(pkg, "description-" + length, status);
	}

	@ParameterizedTest
	@CsvSource({ "500, 200", "501, 422" })
	void numberOfMetatypes(int count, int status) {
		ObjectNode pkg = pkg("types-" + count);
		viewpoint(pkg, "Many Types", java.util.Collections.nCopies(count, typeId(pkg, 0)));
		assertLimit(pkg, "types-" + count, status);
	}

	@ParameterizedTest
	@CsvSource({ "500, 200", "501, 422" })
	void numberOfConnectionTypes(int count, int status) {
		ObjectNode pkg = pkg("connections-" + count);
		ObjectNode viewpoint = viewpoint(pkg, "Many Connections", List.of(typeId(pkg, 0)));
		String connection = connectionType(pkg);
		java.util.Collections.nCopies(count, connection).forEach(viewpoint.withArray("connectionTypeIds")::add);
		assertLimit(pkg, "connections-" + count, status);
	}

	@ParameterizedTest
	@CsvSource({ "1000, 200", "1001, 422" })
	void numberOfMappings(int count, int status) {
		ObjectNode pkg = pkg("mappings-" + count);
		List<String> elements = new ArrayList<>();
		List<String> connections = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			elements.add(addType(pkg, "Element " + i, false));
			connections.add(addType(pkg, "Connection " + i, true));
		}
		ObjectNode viewpoint = viewpoint(pkg, "Many Mappings", elements);
		viewpoint.put("notation", "archimate3");
		connections.forEach(viewpoint.withArray("connectionTypeIds")::add);
		ArrayNode mappings = viewpoint.withArray("mappings");
		for (int i = 0; i < 1000; i++) {
			boolean connection = i >= 500;
			mappings.addObject()
				.put("metatypeId", connection ? connections.get(i - 500) : elements.get(i))
				.put("notationElement", connection ? "archimate:Flow" : "archimate:Node");
		}
		if (count > 1000) {
			mappings.addObject().put("metatypeId", elements.get(0)).put("notationElement", "archimate:Node");
		}
		assertLimit(pkg, "mappings-" + count, status);
	}

	@ParameterizedTest
	@CsvSource({ "10000, 200", "10001, 422" })
	void numberOfViewpoints(int count, int status) {
		ObjectNode pkg = pkg("count-" + count);
		String type = typeId(pkg, 0);
		ArrayNode viewpoints = pkg.putArray("viewpoints");
		for (int i = 0; i < count; i++) {
			ObjectNode viewpoint = viewpoints.addObject();
			viewpoint.put("id", UUID.nameUUIDFromBytes(("count-" + count + "/" + i).getBytes()).toString())
				.put("name", "Viewpoint " + i);
			viewpoint.putArray("metatypeIds").add(type);
		}
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.hasStatus((status == 200) ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE source_package_id = ?",
				Integer.class, "count-" + count)).isEqualTo((status == 200) ? count : 0);
	}

	@Test
	void namesThatDifferOnlyInCaseAreRejected() {
		ObjectNode pkg = pkg("case");
		viewpoint(pkg, "Application Landscape", List.of(typeId(pkg, 0)));
		viewpoint(pkg, "application landscape", List.of(typeId(pkg, 1)));
		MvcTestResult result = post(pkg);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/viewpoints/1/name");
		assertNothingStored("case");
	}

	// Requirement: Limits and checks of package viewpoints, default sizes (s17-viewpoint-default-sizes D4)

	@Test
	void sizesAreImportedAndAReimportReplacesThem() {
		ObjectNode first = pkg("sizes-vp");
		String connection = connectionType(first);
		ObjectNode viewpoint = viewpoint(first, "Sized", List.of(typeId(first, 0), typeId(first, 1)));
		viewpoint.putArray("connectionTypeIds").add(connection);
		viewpoint.putArray("sizes").addObject().put("metatypeId", typeId(first, 0)).put("width", 120).put("height", 40);
		viewpoint.withArray("sizes").addObject().put("metatypeId", connection).put("length", 150);
		MvcTestResult created = post(first);
		assertThat(created).as(() -> content(created)).hasStatus(HttpStatus.OK);
		String id = viewpoint.path("id").asString();
		assertThat(sizes(id)).containsExactlyInAnyOrder(typeId(first, 0) + "=120:40:null", connection + "=null:null:150");

		ObjectNode second = pkg("sizes-vp");
		second.put("packageVersion", "1.1.0");
		String again = connectionType(second);
		ObjectNode changed = viewpointWithId(second, id, "Sized", List.of(typeId(second, 0), typeId(second, 1)));
		changed.putArray("connectionTypeIds").add(again);
		changed.putArray("sizes").addObject().put("metatypeId", typeId(second, 1)).put("width", 200).put("height", 90);
		MvcTestResult reimported = post(second);
		assertThat(reimported).as(() -> content(reimported)).hasStatus(HttpStatus.OK);
		assertThat(sizes(id)).containsExactly(typeId(second, 1) + "=200:90:null");
	}

	@ParameterizedTest
	@CsvSource({ "1, 1, 200", "2000, 2000, 200", "0, 30, 422", "90, 0, 422", "2001, 30, 422", "90, 2001, 422" })
	void sizeBoundaries(int width, int height, int status) {
		ObjectNode pkg = pkg("size-bounds-" + width + "-" + height);
		ObjectNode viewpoint = viewpoint(pkg, "Bounds " + pkg.path("packageId").asString(), List.of(typeId(pkg, 0)));
		viewpoint.putArray("sizes").addObject().put("metatypeId", typeId(pkg, 0)).put("width", width).put("height", height);
		assertSizes(pkg, status);
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "2000, 200", "0, 422", "2001, 422" })
	void lengthBoundaries(int length, int status) {
		ObjectNode pkg = pkg("length-bounds-" + length);
		String connection = connectionType(pkg);
		ObjectNode viewpoint = viewpoint(pkg, "Bounds " + pkg.path("packageId").asString(), List.of(typeId(pkg, 0)));
		viewpoint.putArray("connectionTypeIds").add(connection);
		viewpoint.putArray("sizes").addObject().put("metatypeId", connection).put("length", length);
		assertSizes(pkg, status);
	}

	@ParameterizedTest
	@ValueSource(strings = { "length on element", "width on connection", "width only", "not allowed", "twice" })
	void aWrongSizeFailsTheImport(String kind) {
		ObjectNode pkg = pkg("wrong-" + kind.replace(' ', '-'));
		String connection = connectionType(pkg);
		ObjectNode viewpoint = viewpoint(pkg, "Wrong " + pkg.path("packageId").asString(), List.of(typeId(pkg, 0)));
		viewpoint.putArray("connectionTypeIds").add(connection);
		ArrayNode sizes = viewpoint.putArray("sizes");
		switch (kind) {
			case "length on element" -> sizes.addObject().put("metatypeId", typeId(pkg, 0)).put("length", 90);
			case "width on connection" -> sizes.addObject().put("metatypeId", connection).put("width", 90);
			case "width only" -> sizes.addObject().put("metatypeId", typeId(pkg, 0)).put("width", 90);
			case "not allowed" -> sizes.addObject().put("metatypeId", typeId(pkg, 1)).put("width", 90).put("height", 30);
			default -> {
				sizes.addObject().put("metatypeId", typeId(pkg, 0)).put("width", 90).put("height", 30);
				sizes.addObject().put("metatypeId", typeId(pkg, 0)).put("width", 91).put("height", 31);
			}
		}
		assertSizes(pkg, 422);
	}

	@ParameterizedTest
	@CsvSource({ "1000, 200", "1001, 422" })
	void numberOfSizes(int count, int status) {
		ObjectNode pkg = pkg("sizes-" + count);
		List<String> elements = new ArrayList<>();
		List<String> connections = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			elements.add(addType(pkg, "Sized Element " + i, false));
			connections.add(addType(pkg, "Sized Connection " + i, true));
		}
		ObjectNode viewpoint = viewpoint(pkg, "Many Sizes", elements);
		connections.forEach(viewpoint.withArray("connectionTypeIds")::add);
		ArrayNode sizes = viewpoint.putArray("sizes");
		for (int i = 0; i < 1000; i++) {
			boolean connection = i >= 500;
			ObjectNode size = sizes.addObject().put("metatypeId", connection ? connections.get(i - 500) : elements.get(i));
			if (connection) {
				size.put("length", 90);
			}
			else {
				size.put("width", 90).put("height", 30);
			}
		}
		if (count > 1000) {
			sizes.addObject().put("metatypeId", elements.get(0)).put("width", 90).put("height", 30);
		}
		assertSizes(pkg, status);
	}

	@Test
	void aPackageMappingWithADefaultSizeIsImportedWithoutASize() {
		ObjectNode pkg = pkg("old-mapping-size");
		ObjectNode viewpoint = viewpoint(pkg, "Old", List.of(typeId(pkg, 0)));
		viewpoint.put("notation", "archimate3");
		viewpoint.withArray("mappings")
			.addObject()
			.put("metatypeId", typeId(pkg, 0))
			.put("notationElement", "archimate:Node")
			.put("defaultWidth", 120)
			.put("defaultHeight", 55);
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(sizes(viewpoint.path("id").asString())).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_mappings WHERE viewpoint_id = ?::uuid",
				Integer.class, viewpoint.path("id").asString())).isOne();
	}

	// helpers

	private void assertSizes(ObjectNode pkg, int status) {
		String packageId = pkg.path("packageId").asString();
		MvcTestResult result = post(pkg);
		if (status == 200) {
			assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
				.hasStatus(HttpStatus.OK);
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE source_package_id = ?",
					Integer.class, packageId)).isOne();
		}
		else {
			assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
			assertThat(body(result).path("pointer").asString()).startsWith("/viewpoints/0/sizes");
			assertNothingStored(packageId);
		}
	}

	private List<String> sizes(String viewpointId) {
		return this.jdbc.queryForList("""
				SELECT metatype_id || '=' || coalesce(width_pt::text, 'null') || ':' || coalesce(height_pt::text, 'null')
				       || ':' || coalesce(length_pt::text, 'null') FROM viewpoint_sizes WHERE viewpoint_id = ?::uuid""",
				String.class, viewpointId);
	}

	private void assertLimit(ObjectNode pkg, String packageId, int status) {
		MvcTestResult result = post(pkg);
		if (status == 200) {
			assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
				.hasStatus(HttpStatus.OK);
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE source_package_id = ?",
					Integer.class, packageId)).isOne();
		}
		else {
			assertThat(result).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
			assertThat(body(result).path("pointer").asString()).startsWith("/viewpoints/0");
			assertNothingStored(packageId);
		}
	}

	private void assertNothingStored(String packageId) {
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE source_package_id = ?",
				Integer.class, packageId)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id = ?",
				Integer.class, packageId)).isZero();
	}

	/** A package with two element metatypes and no viewpoints. */
	private static ObjectNode pkg(String packageId) {
		ObjectNode pkg = TestPackages.pkg(packageId, 2);
		pkg.putArray("viewpoints");
		return pkg;
	}

	private static String typeId(ObjectNode pkg, int index) {
		return pkg.withArray("metatypes").get(index).path("id").asString();
	}

	private static ObjectNode viewpoint(ObjectNode pkg, String name, List<String> metatypeIds) {
		return viewpointWithId(pkg, UUID.nameUUIDFromBytes((pkg.path("packageId").asString() + "/" + name).getBytes())
			.toString(), name, metatypeIds);
	}

	private static ObjectNode viewpointWithId(ObjectNode pkg, String id, String name, List<String> metatypeIds) {
		ObjectNode viewpoint = pkg.withArray("viewpoints").addObject().put("id", id).put("name", name);
		ArrayNode types = viewpoint.putArray("metatypeIds");
		metatypeIds.forEach(types::add);
		return viewpoint;
	}

	/** Adds a metatype without property mappings to the package and returns its ID. */
	private static String addType(ObjectNode pkg, String name, boolean connection) {
		String id = UUID.nameUUIDFromBytes((pkg.path("packageId").asString() + "/added/" + name).getBytes()).toString();
		ObjectNode type = pkg.withArray("metatypes").addObject().put("id", id).put("name", name)
			.put("abbreviation", "A" + Math.abs(name.hashCode() % 100000)).put("sortOrder", 0);
		if (connection) {
			type.put("isConnection", true);
		}
		return id;
	}

	private static String connectionType(ObjectNode pkg) {
		return addType(pkg, "Flow " + pkg.path("packageId").asString(), true);
	}

	private String organizationViewpoint(String name) {
		long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		return this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, created_by) VALUES (gen_random_uuid(), ?, ?) RETURNING id::text""",
				String.class, name, person);
	}

	private String viewpointState(String id) {
		return this.jdbc.queryForObject("""
				SELECT name || ':' || viewpoint_type || ':' || coalesce(source_package_id, 'null') || ':' || version
				FROM viewpoints WHERE id = ?::uuid""", String.class, id);
	}

	private MvcTestResult post(ObjectNode pkg) {
		return this.mvc.post()
			.uri(IMPORT)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.contentType("application/json")
			.content(pkg.toString())
			.exchange();
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

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-import-viewpoints").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
