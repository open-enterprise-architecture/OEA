package io.oea.app.hierarchy;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The component hierarchy (us-135-component-hierarchy): repository/entity-hierarchy on an own
 * database. Kurt is an enterprise architect, Franz a viewer, Ida holds {@code integration-writer}
 * ({@code entity:write} without {@code entity:delete}). Entities are found by their unique ids;
 * the composition metatype is replaced before each test, because at most one may be marked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityHierarchyTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final UUID STRUCTURING_AID = UUID.fromString("c3f523b8-f07f-5475-9ab0-6f2ea39b537e");

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

	private TestSession kurt;

	private TestSession franz;

	private TestSession ida;

	private long kurtId;

	private UUID application;

	private UUID technology;

	private UUID composition;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.franz = principals.personWithIdpRole("viewer");
		this.ida = principals.personWithIdpRole("integration-writer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.application = metatype("Application " + suffix(), "APP", false);
		this.technology = metatype("Technology " + suffix(), "TEC", false);
		this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE is_composition");
		this.composition = metatype("Composition " + suffix(), "CO", true);
		this.jdbc.update("UPDATE metatypes SET is_composition = true WHERE id = ?", this.composition);
	}

	// Parent of an element entity

	@Test
	void placeAModuleUnderItsSystem() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		JsonNode result = ok(setParent(this.kurt, module, "\"1\"", system));

		assertThat(result.path("version").asInt()).isEqualTo(2);
		assertThat(result.path("parentEntityId").asInt()).isEqualTo(system);
		assertThat(result.path("composition").path("created").asBoolean()).isTrue();
		assertThat(result.path("composition").path("reason").asString()).isEqualTo("created");
		assertThat(this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class,
				module))
			.isEqualTo(system);
		assertThat(version(system)).as("the parent keeps its version").isEqualTo(1);
		assertThat(this.jdbc.queryForList("SELECT coalesce(old_value, '') || '>' || new_value "
				+ "FROM entity_system_field_history WHERE entity_id = ? AND field_name = 'parent_entity_id'",
				String.class, module))
			.containsExactly(">" + system);
	}

	@Test
	void parentShownByNameInHistoryVersionsAndDiff() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		ok(setParent(this.kurt, module, "\"1\"", system));

		JsonNode history = ok(get("/api/v1/entities/" + module + "/history", this.kurt));
		JsonNode change = history.path("versions").get(0).path("changes").get(0);
		assertThat(change.path("field").asString()).isEqualTo("parentEntityId");
		assertThat(change.path("label").asString()).isEqualTo("Parent");
		assertThat(change.path("isSystemField").asBoolean()).isTrue();

		JsonNode state = ok(get("/api/v1/entities/" + module + "/versions/2", this.kurt));
		assertThat(field(state.path("fields"), "parentEntityId").path("value").asString()).isEqualTo("SAP S/4HANA");
		JsonNode before = ok(get("/api/v1/entities/" + module + "/versions/1", this.kurt));
		assertThat(field(before.path("fields"), "parentEntityId").path("value").isNull()).isTrue();

		JsonNode diff = ok(get("/api/v1/entities/" + module + "/diff?from=1&to=2", this.kurt));
		JsonNode parent = field(diff.path("changes"), "parentEntityId");
		assertThat(parent.path("oldValue").isNull()).isTrue();
		assertThat(parent.path("newValue").asString()).isEqualTo("SAP S/4HANA");
		assertThat(parent.path("label").asString()).isEqualTo("Parent");
	}

	@Test
	void restoreLeavesTheParentUnchanged() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		ok(setParent(this.kurt, module, "\"1\"", system));
		// version 3 renames the module
		this.jdbc.update("UPDATE entities SET name = 'Finance', version = 3 WHERE id = ?", module);
		header(module, 3);
		systemHistory(module, 3, "name", "Finance module", "Finance");

		JsonNode result = ok(restore(this.kurt, module, "\"3\"", 1));

		assertThat(result.path("version").asInt()).isEqualTo(4);
		assertThat(this.jdbc.queryForMap("SELECT name, parent_entity_id FROM entities WHERE id = ?", module))
			.containsEntry("name", "Finance module")
			.containsEntry("parent_entity_id", system);
	}

	@Test
	void restoreWhoseOnlyDifferenceIsTheParentHasNothingToRestore() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		ok(setParent(this.kurt, module, "\"1\"", system));

		MvcTestResult result = restore(this.kurt, module, "\"2\"", 1);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:nothing-to-restore");
		assertThat(parent(module)).isEqualTo(system);
	}

	@Test
	void detachToTheRootLevel() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		ok(setParent(this.kurt, module, "\"1\"", system));

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", null));

		assertThat(result.path("version").asInt()).isEqualTo(3);
		assertThat(result.path("parentEntityId").isNull()).isTrue();
		assertThat(result.path("composition").path("reason").asString()).isEqualTo("cleared");
		assertThat(parent(module)).isNull();
		JsonNode diff = ok(get("/api/v1/entities/" + module + "/diff?from=2&to=3", this.kurt));
		JsonNode parent = field(diff.path("changes"), "parentEntityId");
		assertThat(parent.path("oldValue").asString()).isEqualTo("SAP S/4HANA");
		assertThat(parent.path("newValue").isNull()).isTrue();
	}

	@Test
	void sameParentAgainWritesNothing() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		ok(setParent(this.kurt, module, "\"1\"", system));
		int events = count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'entity.%'");

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", system));

		assertThat(result.path("version").asInt()).isEqualTo(2);
		assertThat(result.path("composition").path("reason").asString()).isEqualTo("unchanged");
		assertThat(result.path("composition").path("created").asBoolean()).isFalse();
		assertThat(version(module)).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM entity_version_headers WHERE entity_id = " + module)).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'entity.%'")).isEqualTo(events);
	}

	// Rejected parents

	@ParameterizedTest
	@ValueSource(strings = { "itself", "child", "grandchild", "connection", "deleted", "unknown" })
	void rejectedParents(String kind) {
		int entity = entity("Entity");
		int child = entity("Child");
		int grandchild = entity("Grandchild");
		setParentDirect(child, entity);
		setParentDirect(grandchild, child);
		int other = entity("Other");
		int connection = connection("Flow", other, entity);
		int deleted = entity("Deleted");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, deleted);
		int parent = switch (kind) {
			case "itself" -> entity;
			case "child" -> child;
			case "grandchild" -> grandchild;
			case "connection" -> connection;
			case "deleted" -> deleted;
			default -> 987_654_321;
		};
		int events = count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.parent-changed'");

		MvcTestResult result = setParent(this.kurt, entity, "\"1\"", parent);

		assertInvalid(result, "parentEntityId");
		assertThat(version(entity)).isOne();
		assertThat(parent(entity)).isNull();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.parent-changed'"))
			.as("a rejection writes no event")
			.isEqualTo(events);
	}

	@Test
	void connectionAsChildGives422NamingTheEntity() {
		int connection = connection("Flow", entity("A"), entity("B"));
		int parent = entity("Parent");

		assertInvalid(setParent(this.kurt, connection, "\"1\"", parent), "entityId");
	}

	@Test
	void deletedChildIsNotFound() {
		int child = entity("Child");
		int parent = entity("Parent");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, child);

		assertThat(setParent(this.kurt, child, "\"1\"", parent)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(setParent(this.kurt, 987_654_321, "\"1\"", parent)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@ParameterizedTest
	@CsvSource({ "9, 1, true", "10, 1, false", "7, 3, true", "8, 3, false" })
	void depthOfTenLevels(int parentLevel, int subtreeLevels, boolean accepted) {
		int parent = chain(parentLevel);
		int child = entity("Subtree root");
		int last = child;
		for (int level = 2; level <= subtreeLevels; level++) {
			int next = entity("Subtree " + level);
			setParentDirect(next, last);
			last = next;
		}

		MvcTestResult result = setParent(this.kurt, child, "\"1\"", parent);

		if (accepted) {
			assertThat(ok(result).path("parentEntityId").asInt()).isEqualTo(parent);
			assertThat(parent(child)).isEqualTo(parent);
		}
		else {
			assertInvalid(result, "parentEntityId");
			assertThat(parent(child)).isNull();
		}
	}

	@Test
	void parentEntityIdOfOneIsAValueAndZeroIsNot() {
		int child = entity("Child");

		MvcTestResult one = setParent(this.kurt, child, "\"1\"", 1);
		assertThat(one.getResponse().getStatus()).isIn(200, 422);
		MvcTestResult zero = setParent(this.kurt, child, "\"1\"", 0);
		assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(version(child)).isLessThanOrEqualTo(2);
	}

	// If-Match, authorization and blocking

	@ParameterizedTest
	@CsvSource(value = { "NONE, 428", "'\"2\"', 412", "'\"0\"', 412", "abc, 412" }, nullValues = "NONE")
	void ifMatchIsRequiredAndChecked(String ifMatch, int status) {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		MvcTestResult result = setParent(this.kurt, module, ifMatch, system);

		assertThat(result).hasStatus(status);
		assertThat(version(module)).isOne();
		assertThat(parent(module)).isNull();
		if (status == 412) {
			assertThat(body(result).path("currentVersion").asInt()).isOne();
		}
	}

	@Test
	void withoutEntityWriteTheThreeWritesAreForbidden() {
		int aid = aid("Landscape", null);
		int module = entity("Finance module");

		assertThat(setParent(this.franz, module, "\"1\"", aid)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(createAid(this.franz, "Landscape", null, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleteAid(this.franz, aid, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(parent(module)).isNull();
		assertThat(deletedAt(aid)).isNull();
	}

	@Test
	void deletingAnAidNeedsEntityDeleteToo() {
		int aid = aid("Landscape", null);

		assertThat(deleteAid(this.ida, aid, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deletedAt(aid)).isNull();
		// entity:write alone creates and moves
		assertThat(createAid(this.ida, "By Ida", null, null)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void blockedChildGives409() {
		int child = entity("Blocked");
		int parent = entity("Parent");
		block(child);

		MvcTestResult result = setParent(this.kurt, child, "\"1\"", parent);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(parent(child)).isNull();
		assertThat(version(child)).isOne();
	}

	// Automatic composition connection

	@Test
	void compositionIsCreatedAutoMarkedAndAudited() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		JsonNode result = ok(setParent(this.kurt, module, "\"1\"", system));

		int connection = result.path("composition").path("connectionId").asInt();
		assertThat(this.jdbc.queryForMap("""
				SELECT e.auto_created, e.metatype_id, c.source_entity_id, c.target_entity_id, e.name
				FROM entities e JOIN entity_connections c ON c.entity_id = e.id WHERE e.id = ?""", connection))
			.containsEntry("auto_created", true)
			.containsEntry("metatype_id", this.composition)
			.containsEntry("source_entity_id", system)
			.containsEntry("target_entity_id", module)
			.containsEntry("name", "SAP S/4HANA → Finance module");
		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT entity_id, entity_name, details::text AS details FROM audit.events
				WHERE event_type = 'entity.parent-changed' AND entity_id = ?""", module);
		assertThat(event).containsEntry("entity_name", "Finance module");
		JsonNode details = JsonMapper.shared().readTree((String) event.get("details"));
		assertThat(details.path("version").asInt()).isEqualTo(2);
		assertThat(details.path("toParentId").asInt()).isEqualTo(system);
		assertThat(details.path("fromParentId").isMissingNode()).isTrue();
		assertThat(details.path("createdConnectionId").asInt()).isEqualTo(connection);
		assertThat(details.toString()).as("IDs only, no names").doesNotContain("SAP S/4HANA");
		assertThat(this.jdbc.queryForList("""
				SELECT c.property_name || ':' || coalesce(c.old_value, '') || '>' || coalesce(c.new_value, '')
				FROM audit.event_changes c JOIN audit.events e ON e.id = c.event_id
				WHERE e.event_type = 'entity.parent-changed' AND e.entity_id = ?""", String.class, module))
			.containsExactly("parent_entity_id:>" + system);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' AND entity_id = "
				+ connection)).isOne();
	}

	@Test
	void noCompositionUnderAStructuringAidOrWithOneAsChild() {
		int aid = aid("Finance landscape", null);
		int module = entity("Finance module");
		int system = entity("SAP S/4HANA");

		JsonNode under = ok(setParent(this.kurt, module, "\"1\"", aid));
		assertThat(under.path("composition").path("created").asBoolean()).isFalse();
		assertThat(under.path("composition").path("reason").asString()).isEqualTo("structuring-aid");
		JsonNode asChild = ok(setParent(this.kurt, aid, "\"1\"", system));
		assertThat(asChild.path("composition").path("reason").asString()).isEqualTo("structuring-aid");
		assertThat(count("SELECT count(*) FROM entity_connections c JOIN entities e ON e.id = c.entity_id "
				+ "WHERE e.metatype_id = '" + this.composition + "'")).isZero();
		assertThat(parent(module)).isEqualTo(aid);
	}

	@Test
	void noCompositionWithoutAMarkedMetatype() {
		this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE is_composition");
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		JsonNode result = ok(setParent(this.kurt, module, "\"1\"", system));

		assertThat(result.path("composition").path("created").asBoolean()).isFalse();
		assertThat(result.path("composition").path("reason").asString()).isEqualTo("no-composition-metatype");
		assertThat(parent(module)).isEqualTo(system);
	}

	@Test
	void noCompositionForAPairTheMetatypeDoesNotAllow() {
		allow(this.composition, "source", this.technology);
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		JsonNode result = ok(setParent(this.kurt, module, "\"1\"", system));

		assertThat(result.path("composition").path("reason").asString()).isEqualTo("not-allowed");
		assertThat(result.path("composition").path("connectionId").isNull()).isTrue();
		assertThat(parent(module)).isEqualTo(system);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void compositionOfASubtypeOfAnAllowedMetatype(boolean sourceIsSubtype) {
		UUID subtype = metatype("Sub " + suffix(), "SUB", false);
		this.jdbc.update("UPDATE metatypes SET base_metatype_id = ? WHERE id = ?", this.application, subtype);
		allow(this.composition, "source", this.application);
		allow(this.composition, "target", this.application);
		int parent = entity("Parent", sourceIsSubtype ? subtype : this.application);
		int child = entity("Child", sourceIsSubtype ? this.application : subtype);

		JsonNode result = ok(setParent(this.kurt, child, "\"1\"", parent));

		assertThat(result.path("composition").path("created").asBoolean()).isTrue();
	}

	@Test
	void compositionWithAMandatoryPropertyIsCreatedAndMarked() {
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, "Strength " + suffix(), "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'mandatory', DATE '2026-01-01', 'organization')""", this.composition, property);
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");

		JsonNode result = ok(setParent(this.kurt, module, "\"1\"", system));

		int connection = result.path("composition").path("connectionId").asInt();
		assertThat(result.path("composition").path("created").asBoolean()).isTrue();
		assertThat(this.jdbc.queryForList("SELECT property_def_id FROM entity_mandatory_marks WHERE entity_id = ?",
				UUID.class, connection))
			.containsExactly(property);
	}

	@Test
	void detachingRemovesTheAutoCreatedConnection() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		int connection = ok(setParent(this.kurt, module, "\"1\"", system)).path("composition")
			.path("connectionId")
			.asInt();

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", null));

		assertThat(result.path("removedConnectionId").asInt()).isEqualTo(connection);
		assertThat(deletedAt(connection)).isNotNull();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'cause' FROM audit.events WHERE event_type = 'entity.deleted' AND entity_id = ?""",
				String.class, connection))
			.isEqualTo("parent-changed");
	}

	@Test
	void movingRemovesTheOldConnectionAndCreatesANewOne() {
		int system = entity("SAP S/4HANA");
		int suite = entity("SAP Finance Suite");
		int module = entity("Finance module");
		int old = ok(setParent(this.kurt, module, "\"1\"", system)).path("composition").path("connectionId").asInt();

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", suite));

		assertThat(result.path("removedConnectionId").asInt()).isEqualTo(old);
		int created = result.path("composition").path("connectionId").asInt();
		assertThat(created).isNotEqualTo(old);
		assertThat(deletedAt(old)).isNotNull();
		assertThat(deletedAt(created)).isNull();
		assertThat(this.jdbc.queryForObject("SELECT source_entity_id FROM entity_connections WHERE entity_id = ?",
				Integer.class, created))
			.isEqualTo(suite);
	}

	@Test
	void aConnectionWrittenByOtherMeansStays() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		int manual = connection(this.composition, "Manual", system, module);
		ok(setParent(this.kurt, module, "\"1\"", system));

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", null));

		assertThat(result.path("removedConnectionId").asInt()).isNotEqualTo(manual);
		assertThat(deletedAt(manual)).isNull();
	}

	@Test
	void anAlreadyDeletedAutoCreatedConnectionCausesNoError() {
		int system = entity("SAP S/4HANA");
		int module = entity("Finance module");
		int connection = ok(setParent(this.kurt, module, "\"1\"", system)).path("composition")
			.path("connectionId")
			.asInt();
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId,
				connection);

		JsonNode result = ok(setParent(this.kurt, module, "\"2\"", null));

		assertThat(result.path("removedConnectionId").isNull()).isTrue();
		assertThat(parent(module)).isNull();
	}

	// Concurrency

	@Test
	void twoOppositeMovesLeaveNoCycle() throws Exception {
		int first = entity("First");
		int second = entity("Second");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> a = pool.submit(() -> {
				start.await();
				return setParent(this.kurt, first, "\"1\"", second).getResponse().getStatus();
			});
			Future<Integer> b = pool.submit(() -> {
				start.await();
				return setParent(this.kurt, second, "\"1\"", first).getResponse().getStatus();
			});
			start.countDown();
			List<Integer> statuses = List.of(a.get(), b.get());
			assertThat(statuses).containsExactlyInAnyOrder(200, 422);
		}
		finally {
			pool.shutdownNow();
		}
		Integer firstParent = parent(first);
		Integer secondParent = parent(second);
		assertThat(firstParent == null ^ secondParent == null).as("exactly one is placed").isTrue();
	}

	// Structuring aids

	@Test
	void createAStructuringAidAtTheRootLevel() {
		MvcTestResult result = createAid(this.kurt, "Finance landscape", "Navigation only", null);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode created = body(result);
		int id = created.path("id").asInt();
		assertThat(result.getResponse().getHeader("Location")).endsWith("/structuring-aids/" + id);
		assertThat(created.path("parentEntityId").isNull()).isTrue();
		assertThat(created.path("version").asInt()).isOne();
		assertThat(this.jdbc.queryForMap("""
				SELECT metatype_id, is_structural_grouper, parent_entity_id, name, description, version FROM entities
				WHERE id = ?""", id))
			.containsEntry("metatype_id", STRUCTURING_AID)
			.containsEntry("is_structural_grouper", true)
			.containsEntry("parent_entity_id", null)
			.containsEntry("name", "Finance landscape")
			.containsEntry("description", "Navigation only")
			.containsEntry("version", 1);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' AND entity_id = "
				+ id)).isOne();
		assertThat(count("SELECT count(*) FROM entity_system_field_history WHERE entity_id = " + id)).isZero();
	}

	@Test
	void createAStructuringAidUnderAParent() {
		int system = entity("SAP S/4HANA");

		JsonNode created = created(createAid(this.kurt, "Modules", null, system));

		assertThat(created.path("parentEntityId").asInt()).isEqualTo(system);
		assertThat(parent(created.path("id").asInt())).isEqualTo(system);
	}

	@ParameterizedTest
	@CsvSource({ "255, true", "256, false", "1, true" })
	void aidNameLength(int length, boolean accepted) {
		MvcTestResult result = createAid(this.kurt, "n".repeat(length), null, null);

		if (accepted) {
			assertThat(result).hasStatus(HttpStatus.CREATED);
		}
		else {
			assertInvalid(result, "name");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "     " })
	void aBlankAidNameIsRejected(String name) {
		assertInvalid(createAid(this.kurt, name, null, null), "name");
	}

	@Test
	void aidNameIsTrimmed() {
		JsonNode created = created(createAid(this.kurt, "  Landscape  ", null, null));

		assertThat(created.path("name").asString()).isEqualTo("Landscape");
	}

	@ParameterizedTest
	@CsvSource({ "2000, true", "2001, false", "0, true" })
	void aidDescriptionLength(int length, boolean accepted) {
		MvcTestResult result = createAid(this.kurt, "Landscape " + suffix(), "d".repeat(length), null);

		if (accepted) {
			assertThat(result).hasStatus(HttpStatus.CREATED);
		}
		else {
			assertInvalid(result, "description");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown", "deleted", "connection" })
	void aidParentMustBeAnActiveElement(String kind) {
		int deleted = entity("Deleted");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, deleted);
		int connection = connection("Flow", entity("A"), entity("B"));
		int parent = switch (kind) {
			case "deleted" -> deleted;
			case "connection" -> connection;
			default -> 987_654_321;
		};
		int aids = count("SELECT count(*) FROM entities WHERE metatype_id = '" + STRUCTURING_AID + "'");

		assertInvalid(createAid(this.kurt, "Landscape", null, parent), "parentEntityId");
		assertThat(count("SELECT count(*) FROM entities WHERE metatype_id = '" + STRUCTURING_AID + "'"))
			.isEqualTo(aids);
	}

	@ParameterizedTest
	@CsvSource({ "9, true", "10, false" })
	void aidDepthOfTenLevels(int parentLevel, boolean accepted) {
		int parent = chain(parentLevel);

		MvcTestResult result = createAid(this.kurt, "Deep", null, parent);

		if (accepted) {
			assertThat(result).hasStatus(HttpStatus.CREATED);
		}
		else {
			assertInvalid(result, "parentEntityId");
		}
	}

	@Test
	void parentEntityIdOfAnAidBelowOneIsInvalid() {
		assertInvalid(createAid(this.kurt, "Landscape", null, 0), "parentEntityId");
	}

	@Test
	void deleteAnAidWithChildren() {
		int aid = aid("Finance landscape", null);
		List<Integer> children = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			int child = entity("Child " + i);
			ok(setParent(this.kurt, child, "\"1\"", aid));
			children.add(child);
		}

		MvcTestResult result = deleteAid(this.kurt, aid, "\"1\"");

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(deletedAt(aid)).isNotNull();
		for (int child : children) {
			assertThat(parent(child)).isNull();
			assertThat(version(child)).isEqualTo(3);
			assertThat(deletedAt(child)).isNull();
			assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.parent-changed' "
					+ "AND entity_id = " + child)).isEqualTo(2);
		}
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'cause' FROM audit.events WHERE event_type = 'entity.deleted' AND entity_id = ?""",
				String.class, aid))
			.isEqualTo("structuring-aid-deleted");
	}

	@Test
	void deletingAnEntityThatIsNoAidGives422() {
		int system = entity("SAP S/4HANA");

		assertInvalid(deleteAid(this.kurt, system, "\"1\""), "entityId");
		assertThat(deletedAt(system)).isNull();
	}

	@Test
	void deletingADeletedAidIsNotFound() {
		int aid = aid("Landscape", null);
		assertThat(deleteAid(this.kurt, aid, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(deleteAid(this.kurt, aid, "\"1\"")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@ParameterizedTest
	@CsvSource(value = { "NONE, 428", "'\"2\"', 412" }, nullValues = "NONE")
	void deletingAnAidNeedsTheCurrentVersion(String ifMatch, int status) {
		int aid = aid("Landscape", null);

		assertThat(deleteAid(this.kurt, aid, ifMatch)).hasStatus(status);
		assertThat(deletedAt(aid)).isNull();
	}

	@Test
	void deletingAnAidWithABlockedChildChangesNothing() {
		int aid = aid("Landscape", null);
		int fine = entity("Fine");
		int blocked = entity("Blocked");
		ok(setParent(this.kurt, fine, "\"1\"", aid));
		ok(setParent(this.kurt, blocked, "\"1\"", aid));
		block(blocked);

		MvcTestResult result = deleteAid(this.kurt, aid, "\"1\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(deletedAt(aid)).isNull();
		assertThat(parent(fine)).isEqualTo(aid);
		assertThat(parent(blocked)).isEqualTo(aid);
		assertThat(version(fine)).isEqualTo(2);
	}

	// Keeping the metatype to its purpose

	@Test
	void theBatchRejectsTheStructuringAid() {
		String body = """
				{"atomic": true, "entities": [{"name": "Aid", "metatypeId": "%s"}]}""".formatted(STRUCTURING_AID);

		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("errors").get(0).path("field").asString()).isEqualTo("entities[0].metatypeId");
	}

	@Test
	void theBatchReportsTheStructuringAidPerItemWithoutAtomic() {
		String body = """
				{"entities": [{"name": "Aid", "metatypeId": "%s"}]}""".formatted(STRUCTURING_AID);

		JsonNode result = ok(this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange());

		assertThat(result.path("items").get(0).path("status").asString()).isEqualTo("error");
		assertThat(result.path("items").get(0).path("errors").get(0).path("field").asString()).isEqualTo("metatypeId");
	}

	@ParameterizedTest
	@ValueSource(strings = { "source", "target" })
	void noConnectionStartsOrEndsAtAStructuringAid(String end) {
		int aid = aid("Landscape", null);
		int system = entity("SAP S/4HANA");
		int source = "source".equals(end) ? aid : system;
		int target = "source".equals(end) ? system : aid;
		String body = """
				{"connectionMetatypeId": "%s", "sourceId": %d, "targetId": %d}""".formatted(this.composition, source,
				target);

		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/connections")
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("errors").get(0).path("field").asString()).isEqualTo(end + "Id");
	}

	@Test
	void theAllowedConnectionsOfAStructuringAidAreEmpty() {
		JsonNode result = ok(get("/api/v1/metamodel/allowed-connections?sourceMetatypeId=" + STRUCTURING_AID
				+ "&targetMetatypeId=" + this.application, this.kurt));

		assertThat(result.path("allowed").asBoolean()).isFalse();
		JsonNode other = ok(get("/api/v1/metamodel/allowed-connections?sourceMetatypeId=" + this.application
				+ "&targetMetatypeId=" + this.technology, this.kurt));
		assertThat(other.path("allowed").asBoolean()).isTrue();
	}

	@Test
	void aDeltaNewOfTheStructuringAidIsRejected() {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, 'draft')
				RETURNING id""", UUID.class, "Solution " + suffix(), this.kurtId, this.kurtId);
		String body = """
				{"deltaType": "new", "metatypeId": "%s", "name": "Aid", "properties": []}""".formatted(STRUCTURING_AID);

		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/solutions/" + solution + "/deltas")
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).contains("metatypeId");
	}

	@Test
	void aStructuringAidCannotBeRetiredByASolution() {
		int aid = aid("Landscape", null);
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, 'draft')
				RETURNING id""", UUID.class, "Solution " + suffix(), this.kurtId, this.kurtId);
		String body = """
				{"deltaType": "retiring", "entityId": %d, "properties": []}""".formatted(aid);

		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/solutions/" + solution + "/deltas")
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).contains("entityId");
	}

	// Helpers

	private MvcTestResult setParent(TestSession session, int id, String ifMatch, Integer parent) {
		var request = this.mvc.put()
			.uri("/api/v1/entities/" + id + "/parent")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"parentEntityId\": " + parent + "}");
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult createAid(TestSession session, String name, String description, Integer parent) {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("name", name);
		if (description != null) {
			body.put("description", description);
		}
		if (parent != null) {
			body.put("parentEntityId", parent);
		}
		return this.mvc.post()
			.uri("/api/v1/structuring-aids")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared().writeValueAsString(body))
			.exchange();
	}

	private MvcTestResult deleteAid(TestSession session, int id, String ifMatch) {
		var request = this.mvc.delete()
			.uri("/api/v1/structuring-aids/" + id)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult restore(TestSession session, int id, String ifMatch, int target) {
		return this.mvc.post()
			.uri("/api/v1/entities/" + id + "/restore")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", ifMatch)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetVersion\": " + target + "}")
			.exchange();
	}

	private MvcTestResult get(String path, TestSession session) {
		return this.mvc.get()
			.uri(path)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	/** A structuring aid created through the API; returns its ID. */
	private int aid(String name, Integer parent) {
		return created(createAid(this.kurt, name, null, parent)).path("id").asInt();
	}

	private JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	/** A chain of {@code levels} entities, each the parent of the next; returns the deepest. */
	private int chain(int levels) {
		int parent = entity("Level 1");
		for (int level = 2; level <= levels; level++) {
			int child = entity("Level " + level);
			setParentDirect(child, parent);
			parent = child;
		}
		return parent;
	}

	private void setParentDirect(int child, int parent) {
		this.jdbc.update("UPDATE entities SET parent_entity_id = ? WHERE id = ?", parent, child);
	}

	private UUID metatype(String name, String abbreviation, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, abbreviation)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?, ?) RETURNING id""", UUID.class, name, connection,
				abbreviation);
	}

	private void allow(UUID connection, String direction, UUID endpoint) {
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, ?, ?)""", connection, direction, endpoint);
	}

	private int entity(String name) {
		return entity(name, this.application);
	}

	private int entity(String name, UUID metatype) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				metatype, name, this.kurtId);
		header(id, 1);
		return id;
	}

	private int connection(String name, int source, int target) {
		return connection(this.composition, name, source, target);
	}

	private int connection(UUID metatype, String name, int source, int target) {
		int id = entity(name, metatype);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private void header(int id, int version) {
		this.jdbc.update(
				"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)", id,
				version, this.kurtId, Timestamp.from(Instant.now()));
	}

	private void systemHistory(int id, int version, String field, String before, String after) {
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, ?, ?, ?, ?, ?)""", id, version, field, before, after, this.kurtId);
	}

	/** An unresolved open issue of a solution blocks the entity (US-153). */
	private void block(int entity) {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, 'draft')
				RETURNING id""", UUID.class, "Blocking " + suffix(), this.kurtId, this.kurtId);
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, "Gone " + suffix(), "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		this.jdbc.update("""
				INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, 'not-in-metamodel', 'kept')""", solution, entity, property.toString(),
				property);
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private Integer parent(int id) {
		return this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, id);
	}

	private Object deletedAt(int id) {
		return this.jdbc.queryForObject("SELECT deleted_at FROM entities WHERE id = ?", Object.class, id);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private static JsonNode field(JsonNode fields, String key) {
		for (JsonNode node : fields) {
			if (key.equals(node.path("field").asString())) {
				return node;
			}
		}
		throw new AssertionError("no field " + key + " in " + fields);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
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

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-hierarchy").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
