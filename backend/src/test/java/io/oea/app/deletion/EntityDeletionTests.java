package io.oea.app.deletion;

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
 * Specs repository/entity-delete and the version restore of a deleted entity
 * (s17-entity-delete): the preview, the deletion with its cascade, the cascade restore and the
 * rows it must not touch. Kurt is an enterprise architect ({@code entity:delete},
 * {@code entity:restore}), Franz a viewer, Ida holds {@code integration-writer}
 * ({@code entity:write} only). The fixture is the one of the spec: "ERP" with the children
 * "ERP Finance" and "ERP Logistics", "Warehouse" below "ERP Logistics", and four connections.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityDeletionTests {

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

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession franz;

	private TestSession ida;

	private long kurtId;

	private UUID application;

	private UUID flow;

	private UUID composition;

	private int erp;

	private int finance;

	private int logistics;

	private int warehouse;

	private int crm;

	private int erpToCrm;

	private int erpToFinance;

	private int logisticsToCrm;

	private int warehouseToLogistics;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.ida = this.principals.personWithIdpRole("integration-writer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.application = metatype("Application " + suffix(), false);
		this.flow = metatype("Flow " + suffix(), true);
		this.composition = metatype("Composition " + suffix(), true);
		this.erp = entity("ERP " + suffix());
		this.finance = child("ERP Finance " + suffix(), this.erp);
		this.logistics = child("ERP Logistics " + suffix(), this.erp);
		this.warehouse = child("Warehouse " + suffix(), this.logistics);
		this.crm = entity("CRM " + suffix());
		this.erpToCrm = connection(this.flow, "ERP to CRM", this.erp, this.crm);
		this.erpToFinance = connection(this.flow, "ERP to Finance", this.erp, this.finance);
		this.logisticsToCrm = connection(this.flow, "Logistics to CRM", this.logistics, this.crm);
		this.warehouseToLogistics = connection(this.flow, "Warehouse to Logistics", this.warehouse, this.logistics);
	}

	// Requirement: Preview of a deletion

	@Test
	void previewNamesChildrenAndConnections() {
		JsonNode preview = ok(preview(this.kurt, this.erp));

		assertThat(preview.path("entity").path("id").asInt()).isEqualTo(this.erp);
		assertThat(preview.path("entity").path("version").asInt()).isEqualTo(1);
		assertThat(preview.path("childCount").asInt()).isEqualTo(3);
		assertThat(preview.path("connectionCount").asInt()).isEqualTo(4);
		assertThat(preview.path("solutions")).isEmpty();
		assertThat(deleted(this.erp)).isFalse();
	}

	@Test
	void previewCountsOnlyActiveChildrenAndConnections() {
		deleteAlone(this.warehouse);
		deleteAlone(this.warehouseToLogistics);
		deleteAlone(this.erpToFinance);

		JsonNode preview = ok(preview(this.kurt, this.erp));

		// the deleted child and the deleted connection are not part of the deletion
		assertThat(preview.path("childCount").asInt()).isEqualTo(2);
		assertThat(preview.path("connectionCount").asInt()).isEqualTo(2);
	}

	@Test
	void previewNamesActiveSolutionsNotCompletedOnes() {
		String active = solution("Finance Upgrade", "draft");
		String implemented = solution("Old Upgrade", "implemented");
		String archived = solution("Older Upgrade", "archived");
		delta(active, "modified", this.finance);
		delta(active, "retiring", this.warehouse);
		delta(implemented, "modified", this.finance);
		delta(archived, "retiring", this.finance);
		String other = solution("Unrelated", "proposed");
		delta(other, "modified", this.crm);

		JsonNode solutions = ok(preview(this.kurt, this.erp)).path("solutions");

		assertThat(solutions).hasSize(1);
		assertThat(solutions.get(0).path("id").asString()).isEqualTo(active);
		assertThat(solutions.get(0).path("name").asString()).startsWith("Finance Upgrade");
		assertThat(solutions.get(0).path("deltaCount").asInt()).isEqualTo(2);
	}

	@Test
	void previewOfAnUnknownOrDeletedEntityIsNotFound() {
		assertThat(preview(this.kurt, 2_000_000_099)).hasStatus(HttpStatus.NOT_FOUND);
		deleteAlone(this.crm);
		assertThat(preview(this.kurt, this.crm)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void previewOfAStructuringAidAndOfAnAutomaticConnectionIsRejected() {
		int aid = entity("Finance landscape", STRUCTURING_AID);
		int automatic = connection(this.composition, "ERP to Finance auto", this.erp, this.finance);
		this.jdbc.update("UPDATE entities SET auto_created = true WHERE id = ?", automatic);

		assertInvalid(preview(this.kurt, aid), "entityId");
		assertInvalid(preview(this.kurt, automatic), "entityId");
	}

	@Test
	void previewNeedsEntityDelete() {
		assertThat(preview(this.ida, this.erp)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(preview(this.franz, this.erp)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Delete an entity

	@Test
	void deletedWithChildrenAndConnections() {
		JsonNode result = ok(delete(this.kurt, this.erp, "\"1\""));

		List<Integer> ids = ids(result.path("deleted"));
		assertThat(ids).hasSize(8).first().isEqualTo(this.erp);
		assertThat(ids).containsExactlyInAnyOrder(this.erp, this.finance, this.logistics, this.warehouse,
				this.erpToCrm, this.erpToFinance, this.logisticsToCrm, this.warehouseToLogistics);
		assertThat(deleted(this.crm)).isFalse();
		// one time, one actor, the entity as origin of the others, none for the entity itself
		assertThat(this.jdbc.queryForObject("SELECT count(DISTINCT deleted_at) FROM entities WHERE id = ANY(?)",
				Integer.class, (Object) ids.toArray(Integer[]::new)))
			.isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT count(DISTINCT deleted_by) FROM entities WHERE id = ANY(?)",
				Integer.class, (Object) ids.toArray(Integer[]::new)))
			.isEqualTo(1);
		Map<String, Object> entity = row(this.erp);
		assertThat(entity.get("deleted_by")).isEqualTo(this.kurtId);
		assertThat(entity.get("deleted_with_entity_id")).isNull();
		for (int id : ids.subList(1, ids.size())) {
			assertThat(row(id).get("deleted_with_entity_id")).isEqualTo(this.erp);
			assertThat(row(id).get("deletion_reason")).isEqualTo("cascade: entity " + this.erp + " deleted");
			assertThat(version(id)).as("versions do not change").isEqualTo(1);
		}
		assertThat(version(this.erp)).isEqualTo(1);
		// audit: entity.deleted for the entity, entity.cascade-deleted naming it for the others
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.deleted' AND entity_id = "
				+ this.erp)).isOne();
		JsonNode details = auditDetails("entity.deleted", this.erp).get(0);
		assertThat(details.path("childCount").asInt()).isEqualTo(3);
		assertThat(details.path("connectionCount").asInt()).isEqualTo(4);
		for (int id : ids.subList(1, ids.size())) {
			List<JsonNode> cascade = auditDetails("entity.cascade-deleted", id);
			assertThat(cascade).hasSize(1);
			assertThat(cascade.get(0).path("triggeredByEntityId").asInt()).isEqualTo(this.erp);
		}
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.deleted' AND entity_id = "
				+ this.finance)).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10 })
	void childrenOnTheFirstAndOnTheDeepestLevel(int levels) {
		int root = entity("Chain 1 " + suffix());
		int deepest = root;
		List<Integer> chain = new ArrayList<>(List.of(root));
		for (int level = 2; level <= levels; level++) {
			deepest = child("Chain " + level + " " + suffix(), deepest);
			chain.add(deepest);
		}

		List<Integer> ids = ids(ok(delete(this.kurt, root, "\"1\"")).path("deleted"));

		assertThat(ids).containsExactlyElementsOf(chain);
		assertThat(deleted(deepest)).isTrue();
		assertThat(row(deepest).get("deleted_with_entity_id")).isEqualTo((levels == 1) ? null : root);
	}

	@Test
	void whatWasDeletedBeforeStaysAsItWas() {
		ok(delete(this.kurt, this.warehouse, "\"1\""));
		Object before = row(this.warehouse).get("deleted_at");

		List<Integer> ids = ids(ok(delete(this.kurt, this.erp, "\"1\"")).path("deleted"));

		assertThat(ids).doesNotContain(this.warehouse, this.warehouseToLogistics);
		assertThat(row(this.warehouse).get("deleted_at")).isEqualTo(before);
		assertThat(row(this.warehouse).get("deleted_with_entity_id")).isNull();
		assertThat(row(this.warehouseToLogistics).get("deleted_with_entity_id")).isEqualTo(this.warehouse);
	}

	@Test
	void connectionDeletedAlone() {
		List<Integer> ids = ids(ok(delete(this.kurt, this.erpToCrm, "\"1\"")).path("deleted"));

		assertThat(ids).containsExactly(this.erpToCrm);
		assertThat(deleted(this.erpToCrm)).isTrue();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(this.crm)).isFalse();
		assertThat(row(this.erpToCrm).get("deleted_with_entity_id")).isNull();
	}

	@Test
	void staleVersionAndMissingHeader() {
		this.jdbc.update("UPDATE entities SET version = 2 WHERE id = ?", this.erp);

		MvcTestResult stale = delete(this.kurt, this.erp, "\"1\"");
		MvcTestResult missing = delete(this.kurt, this.erp, null);

		assertThat(stale).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(body(stale).path("currentVersion").asInt()).isEqualTo(2);
		assertThat(missing).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(deleted(this.erp)).isFalse();
		// the current version is accepted
		ok(delete(this.kurt, this.erp, "\"2\""));
		assertThat(deleted(this.erp)).isTrue();
	}

	@Test
	void withoutEntityDelete() {
		assertThat(delete(this.ida, this.erp, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(this.franz, this.erp, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(this.finance)).isFalse();
	}

	@Test
	void scopingConnectionNeedsVisibilityScopeAssign() {
		UUID scoping = metatype("Domain Assignment " + suffix(), true);
		this.jdbc.update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?)""", "Classification " + suffix(),
				"p_" + suffix().replace("-", ""), scoping);
		int assignment = connection(scoping, "Assignment", this.finance, this.crm);

		assertThat(delete(this.kurt, this.erp, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(this.kurt, assignment, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(assignment)).isFalse();

		TestSession both = this.principals.personWithIdpRoles("enterprise-architect", "security-admin");
		List<Integer> ids = ids(ok(delete(both, this.erp, "\"1\"")).path("deleted"));
		assertThat(ids).contains(assignment);
		assertThat(deleted(assignment)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "entity", "child", "connection" })
	void blockedByOpenIssues(String which) {
		int blocked = switch (which) {
			case "entity" -> this.erp;
			case "child" -> this.finance;
			default -> this.erpToCrm;
		};
		block(blocked);

		MvcTestResult result = delete(this.kurt, this.erp, "\"1\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(body(result).path("entities").get(0).path("entityId").asInt()).isEqualTo(blocked);
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(this.finance)).isFalse();
		assertThat(deleted(this.erpToCrm)).isFalse();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'entity.%deleted' AND entity_id = "
				+ this.erp)).isZero();
	}

	@Test
	void structuringAidAndAutomaticConnectionAreNotDeletedHere() {
		int aid = entity("Finance landscape", STRUCTURING_AID);
		int automatic = connection(this.composition, "ERP to Finance auto", this.erp, this.finance);
		this.jdbc.update("UPDATE entities SET auto_created = true WHERE id = ?", automatic);

		assertInvalid(delete(this.kurt, aid, "\"1\""), "entityId");
		assertInvalid(delete(this.kurt, automatic, "\"1\""), "entityId");

		assertThat(deleted(aid)).isFalse();
		assertThat(deleted(automatic)).isFalse();
	}

	@Test
	void alreadyDeletedOrUnknown() {
		ok(delete(this.kurt, this.erp, "\"1\""));

		assertThat(delete(this.kurt, this.erp, "\"1\"")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(this.kurt, 2_000_000_099, "\"1\"")).hasStatus(HttpStatus.NOT_FOUND);
	}

	// Requirement: Restore a deleted entity

	@Test
	void restoredWithItsCascade() {
		List<Integer> deleted = ids(ok(delete(this.kurt, this.erp, "\"1\"")).path("deleted"));

		JsonNode result = ok(undelete(this.kurt, this.erp, "\"1\""));

		assertThat(ids(result.path("restored"))).first().isEqualTo(this.erp);
		assertThat(ids(result.path("restored"))).containsExactlyInAnyOrderElementsOf(deleted);
		assertThat(result.path("movedToTopLevel")).isEmpty();
		assertThat(result.path("notRestored")).isEmpty();
		for (int id : deleted) {
			Map<String, Object> row = row(id);
			assertThat(row.get("deleted_at")).isNull();
			assertThat(row.get("deleted_by")).isNull();
			assertThat(row.get("deletion_reason")).isNull();
			assertThat(row.get("deleted_with_entity_id")).isNull();
			assertThat(version(id)).as("versions are unchanged").isEqualTo(1);
			assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.undeleted' AND entity_id = "
					+ id)).isOne();
		}
		assertThat(auditDetails("entity.undeleted", this.finance).get(0).path("triggeredByEntityId").asInt())
			.isEqualTo(this.erp);
		assertThat(auditDetails("entity.undeleted", this.erp).get(0).has("triggeredByEntityId")).isFalse();
		assertThat(parent(this.finance)).isEqualTo(this.erp);
	}

	@Test
	void connectionToAStillDeletedEndpointStaysDeleted() {
		ok(delete(this.kurt, this.erp, "\"1\""));
		ok(delete(this.kurt, this.crm, "\"1\""));

		JsonNode result = ok(undelete(this.kurt, this.erp, "\"1\""));

		assertThat(deleted(this.crm)).isTrue();
		assertThat(deleted(this.erpToCrm)).isTrue();
		assertThat(deleted(this.logisticsToCrm)).isTrue();
		assertThat(result.path("notRestored")).hasSize(2);
		for (JsonNode entry : result.path("notRestored")) {
			assertThat(entry.path("reason").asString()).isEqualTo("endpoint-deleted");
		}
		assertThat(ids(result.path("notRestored").findValues("entityId")))
			.containsExactlyInAnyOrder(this.erpToCrm, this.logisticsToCrm);
		assertThat(ids(result.path("restored"))).doesNotContain(this.erpToCrm, this.crm);
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(this.erpToFinance)).isFalse();
	}

	@Test
	void whatWasDeletedEarlierOrByAnotherDeletionIsNeverRestored() {
		// the connection was deleted before, the child alone before as well
		ok(delete(this.kurt, this.erpToCrm, "\"1\""));
		ok(delete(this.kurt, this.warehouse, "\"1\""));
		ok(delete(this.kurt, this.erp, "\"1\""));

		JsonNode result = ok(undelete(this.kurt, this.erp, "\"1\""));

		assertThat(deleted(this.erpToCrm)).isTrue();
		assertThat(deleted(this.warehouse)).isTrue();
		assertThat(deleted(this.warehouseToLogistics)).as("deleted with the warehouse").isTrue();
		assertThat(ids(result.path("restored"))).doesNotContain(this.erpToCrm, this.warehouse);
		assertThat(result.path("notRestored")).isEmpty();
		assertThat(deleted(this.logistics)).isFalse();
	}

	@Test
	void separateDeletionsInTheSameInstantAreNotMixed() {
		ok(delete(this.kurt, this.erp, "\"1\""));
		int other = entity("Other " + suffix());
		int otherChild = child("Other child " + suffix(), other);
		ok(delete(this.kurt, other, "\"1\""));
		// the same instant by the same actor
		this.jdbc.update("""
				UPDATE entities SET deleted_at = (SELECT deleted_at FROM entities WHERE id = ?)
				WHERE id IN (?, ?)""", this.erp, other, otherChild);

		JsonNode result = ok(undelete(this.kurt, this.erp, "\"1\""));

		assertThat(ids(result.path("restored"))).doesNotContain(other, otherChild);
		assertThat(deleted(other)).isTrue();
		assertThat(deleted(otherChild)).isTrue();
		assertThat(deleted(this.finance)).isFalse();
	}

	@Test
	void childRestoredAloneGoesToTheTopLevel() {
		int logisticsToFinance = connection(this.flow, "Logistics to Finance", this.logistics, this.finance);
		ok(delete(this.kurt, this.erp, "\"1\""));

		JsonNode result = ok(undelete(this.kurt, this.logistics, "\"1\""));

		assertThat(ids(result.path("restored"))).containsExactlyInAnyOrder(this.logistics, this.warehouse,
				this.logisticsToCrm, this.warehouseToLogistics);
		assertThat(ids(result.path("movedToTopLevel"))).containsExactly(this.logistics);
		assertThat(result.path("notRestored")).hasSize(1);
		assertThat(result.path("notRestored").get(0).path("entityId").asInt()).isEqualTo(logisticsToFinance);
		assertThat(result.path("notRestored").get(0).path("reason").asString()).isEqualTo("endpoint-deleted");
		assertThat(deleted(this.erp)).isTrue();
		assertThat(deleted(this.finance)).isTrue();
		assertThat(deleted(logisticsToFinance)).isTrue();
		assertThat(parent(this.logistics)).isNull();
		assertThat(version(this.logistics)).as("a new version").isEqualTo(2);
		assertThat(parent(this.warehouse)).isEqualTo(this.logistics);
		assertThat(version(this.warehouse)).isEqualTo(1);
		assertThat(auditDetails("entity.parent-changed", this.logistics).get(0).path("reason").asString())
			.isEqualTo("Parent deleted");
		assertThat(this.jdbc.queryForList("""
				SELECT old_value || '>' || coalesce(new_value, 'none') FROM entity_system_field_history
				WHERE entity_id = ? AND field_name = 'parent_entity_id' AND entity_version = 2""", String.class,
				this.logistics))
			.containsExactly(this.erp + ">none");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.parent-changed' AND entity_id = "
				+ this.logistics)).isOne();
	}

	/**
	 * The subtree of the restored entity must end at level 10 at the latest: a parent at level
	 * {@code parentLevel} and a subtree of {@code subtreeLevels} levels (the entity included) are
	 * 10 levels deep when their sum is 10.
	 */
	@ParameterizedTest
	@CsvSource({ "5, 5, false", "5, 6, true", "9, 1, false", "9, 2, true", "1, 9, false", "1, 10, true" })
	void depthBoundaryOfARestoredSubtree(int parentLevel, int subtreeLevels, boolean movedToTopLevel) {
		int parent = entity("Level 1 " + suffix());
		for (int level = 2; level <= parentLevel; level++) {
			parent = child("Level " + level + " " + suffix(), parent);
		}
		int root = child("Subtree 1 " + suffix(), parent);
		int deepest = root;
		for (int level = 2; level <= subtreeLevels; level++) {
			deepest = child("Subtree " + level + " " + suffix(), deepest);
		}
		int automatic = connection(this.composition, "Parent to subtree", parent, root);
		this.jdbc.update("UPDATE entities SET auto_created = true WHERE id = ?", automatic);
		ok(delete(this.kurt, root, "\"1\""));

		JsonNode result = ok(undelete(this.kurt, root, "\"1\""));

		if (movedToTopLevel) {
			assertThat(ids(result.path("movedToTopLevel"))).containsExactly(root);
			assertThat(parent(root)).isNull();
			assertThat(version(root)).isEqualTo(2);
			assertThat(auditDetails("entity.parent-changed", root).get(0).path("reason").asString())
				.isEqualTo("Hierarchy too deep");
			assertThat(result.path("notRestored")).hasSize(1);
			assertThat(result.path("notRestored").get(0).path("reason").asString()).isEqualTo("parent-changed");
			assertThat(deleted(automatic)).isTrue();
		}
		else {
			assertThat(result.path("movedToTopLevel")).isEmpty();
			assertThat(parent(root)).isEqualTo(parent);
			assertThat(version(root)).isEqualTo(1);
			assertThat(result.path("notRestored")).isEmpty();
			assertThat(deleted(automatic)).isFalse();
		}
		assertThat(deleted(deepest)).isFalse();
	}

	@Test
	void entityDeletedBeforeTheOriginExistedIsRestoredAlone() {
		// deleted the way earlier versions did: no origin, children and connections untouched in a row of their own
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, this.erp);
		this.jdbc.update("""
				UPDATE entities SET deleted_at = now(), deleted_by = ?, deletion_reason = 'Retired' WHERE id = ?""",
				this.kurtId, this.erpToCrm);

		JsonNode result = ok(undelete(this.kurt, this.erp, "\"1\""));

		assertThat(ids(result.path("restored"))).containsExactly(this.erp);
		assertThat(deleted(this.erpToCrm)).isTrue();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(result.path("notRestored")).isEmpty();
	}

	@Test
	void connectionRestoredDirectly() {
		ok(delete(this.kurt, this.erpToCrm, "\"1\""));

		JsonNode result = ok(undelete(this.kurt, this.erpToCrm, "\"1\""));

		assertThat(ids(result.path("restored"))).containsExactly(this.erpToCrm);
		assertThat(deleted(this.erpToCrm)).isFalse();
	}

	@Test
	void connectionWithADeletedEndpointCannotBeRestoredDirectly() {
		ok(delete(this.kurt, this.erpToCrm, "\"1\""));
		ok(delete(this.kurt, this.crm, "\"1\""));

		MvcTestResult result = undelete(this.kurt, this.erpToCrm, "\"1\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:connection-not-restorable");
		assertThat(body(result).path("reason").asString()).isEqualTo("endpoint-deleted");
		assertThat(deleted(this.erpToCrm)).isTrue();
	}

	@Test
	void anActiveEntityIsNotRestored() {
		MvcTestResult result = undelete(this.kurt, this.crm, "\"1\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-not-deleted");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.undeleted' AND entity_id = "
				+ this.crm)).isZero();
	}

	@Test
	void restoreNeedsTheRightPermissionAndTheDeletedVersion() {
		ok(delete(this.kurt, this.erp, "\"1\""));
		this.jdbc.update("UPDATE entities SET version = 2 WHERE id = ?", this.erp);

		assertThat(undelete(this.ida, this.erp, "\"2\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(undelete(this.kurt, this.erp, "\"1\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(undelete(this.kurt, this.erp, null)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(undelete(this.kurt, 2_000_000_099, "\"1\"")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(deleted(this.erp)).isTrue();
		ok(undelete(this.kurt, this.erp, "\"2\""));
		assertThat(deleted(this.erp)).isFalse();
	}

	@Test
	void scopingConnectionNeedsVisibilityScopeAssignToRestore() {
		UUID scoping = metatype("Domain Assignment " + suffix(), true);
		this.jdbc.update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?)""", "Classification " + suffix(),
				"p_" + suffix().replace("-", ""), scoping);
		int assignment = connection(scoping, "Assignment", this.finance, this.crm);
		TestSession both = this.principals.personWithIdpRoles("enterprise-architect", "security-admin");
		ok(delete(both, this.erp, "\"1\""));

		assertThat(undelete(this.kurt, this.erp, "\"1\"")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleted(this.erp)).isTrue();
		assertThat(deleted(assignment)).isTrue();
		assertThat(ids(ok(undelete(both, this.erp, "\"1\"")).path("restored"))).contains(assignment);
		assertThat(deleted(assignment)).isFalse();
	}

	// Requirement: Version restore needs an active entity

	@Test
	void versionRestoreOfADeletedEntityIsRejected() {
		this.jdbc.update("""
				UPDATE entities SET name = 'ERP renamed', version = 2 WHERE id = ?""", this.erp);
		this.jdbc.update("""
				INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 2, ?)""", this.erp,
				this.kurtId);
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, 2, 'name', 'ERP', 'ERP renamed', ?)""", this.erp, this.kurtId);
		ok(delete(this.kurt, this.erp, "\"2\""));

		MvcTestResult preview = this.mvc.get()
			.uri("/api/v1/entities/" + this.erp + "/restore?targetVersion=1")
			.cookie(cookie(this.kurt))
			.exchange();
		MvcTestResult restore = restoreVersion(this.kurt, this.erp, "\"2\"", 1);

		assertThat(preview).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(preview).path("type").asString()).isEqualTo("urn:oea:problem:entity-deleted");
		assertThat(restore).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(restore).path("type").asString()).isEqualTo("urn:oea:problem:entity-deleted");
		assertThat(version(this.erp)).as("no version is written").isEqualTo(2);
		assertThat(deleted(this.erp)).isTrue();
		// after the undelete, the version restore works as before
		ok(undelete(this.kurt, this.erp, "\"2\""));
		assertThat(restoreVersion(this.kurt, this.erp, "\"2\"", 1)).hasStatus(HttpStatus.OK);
		assertThat(version(this.erp)).isEqualTo(3);
	}

	// Helpers

	private MvcTestResult preview(TestSession session, int id) {
		return this.mvc.get().uri("/api/v1/entities/" + id + "/deletion").cookie(cookie(session)).exchange();
	}

	private MvcTestResult delete(TestSession session, int id, String ifMatch) {
		var request = this.mvc.delete()
			.uri("/api/v1/entities/" + id)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult undelete(TestSession session, int id, String ifMatch) {
		var request = this.mvc.post()
			.uri("/api/v1/entities/" + id + "/undelete")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult restoreVersion(TestSession session, int id, String ifMatch, int target) {
		return this.mvc.post()
			.uri("/api/v1/entities/" + id + "/restore")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", ifMatch)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetVersion\": " + target + "}")
			.exchange();
	}

	/** Deletes an entity or a connection on its own, as an earlier deletion would have. */
	private void deleteAlone(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, id);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class, name, connection);
	}

	private int entity(String name) {
		return entity(name, this.application);
	}

	private int entity(String name, UUID metatype) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				metatype, name, this.kurtId);
		this.jdbc.update(
				"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, 1, ?, ?)", id,
				this.kurtId, Timestamp.from(Instant.now()));
		return id;
	}

	private int child(String name, int parent) {
		int id = entity(name);
		this.jdbc.update("UPDATE entities SET parent_entity_id = ? WHERE id = ?", parent, id);
		return id;
	}

	private int connection(UUID metatype, String name, int source, int target) {
		int id = entity(name, metatype);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private String solution(String name, String status) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.kurtId, this.kurtId, status);
	}

	private void delta(String solution, String type, int entity) {
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?)""", solution, type, entity, this.kurtId);
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

	private boolean deleted(int id) {
		return Boolean.TRUE.equals(
				this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class, id));
	}

	private Map<String, Object> row(int id) {
		return this.jdbc.queryForMap("SELECT * FROM entities WHERE id = ?", id);
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private Integer parent(int id) {
		return this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, id);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private List<JsonNode> auditDetails(String type, int entityId) {
		return this.jdbc
			.queryForList("SELECT details::text FROM audit.events WHERE event_type = ? AND entity_id = ?", String.class,
					type, entityId)
			.stream()
			.map((text) -> JsonMapper.shared().readTree(text))
			.toList();
	}

	private static List<Integer> ids(Iterable<JsonNode> nodes) {
		List<Integer> ids = new ArrayList<>();
		nodes.forEach((node) -> ids.add(node.asInt()));
		return ids;
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo(field);
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
			return Files.createTempDirectory("oea-deletion").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
