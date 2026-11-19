package io.oea.app.diagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec diagram/diagrams (US-074, UC-10; design D1 to D3, D5 and D8 of us-074-diagrams):
 * creating, listing, reading, changing, deleting and restoring diagrams, the viewpoint deletion
 * with its diagrams, the browser category and the navigation tree entries, on an own database.
 * Kurt holds {@code entity:write}, {@code entity:restore} and {@code viewpoint:manage}, Anna only
 * {@code entity:write}, Franz only {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

	private static final String BROWSER = "/api/v1/views/browser";

	private static final String TREE = "/api/v1/tree-nodes";

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

	@Autowired
	private Diagrams diagrams;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession anna;

	private TestSession franz;

	private UUID viewpoint;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.viewpoint = viewpoint("Process Model");
	}

	// Requirement: Create a diagram

	@Test
	void diagramCreated() {
		String name = unique("Order to Cash");
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS, create(name, "From order to payment"), this.anna, null);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(201);
		JsonNode created = body(result);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(DIAGRAMS + "/" + created.path("id").asString());
		assertThat(created.path("version").asInt()).isOne();
		assertThat(created.path("name").asString()).isEqualTo(name);
		assertThat(created.path("viewpoint").path("id").asString()).isEqualTo(this.viewpoint.toString());
		assertThat(created.path("viewpoint").path("notation").asString()).isEqualTo("bpmn2");
		assertThat(created.path("createdBy").path("principalType").asString()).isEqualTo("person");
		assertThat(created.path("createdBy").path("id").asInt()).isPositive();
		assertThat(event("diagram.created", created.path("id").asString())).isOne();
		JsonNode details = details("diagram.created", created.path("id").asString());
		assertThat(details.path("name").asString()).isEqualTo(name);
		assertThat(details.path("viewpointId").asString()).isEqualTo(this.viewpoint.toString());
	}

	@Test
	void createWithoutEntityWriteOrAsSystemAdminIsForbidden() {
		String name = unique("Denied");
		assertThat(call(HttpMethod.POST, DIAGRAMS, create(name, null), this.franz, null).getResponse().getStatus())
			.isEqualTo(403);
		assertThat(call(HttpMethod.POST, DIAGRAMS, create(name, null), this.principals.systemAdmin(), null)
			.getResponse()
			.getStatus()).isEqualTo(403);
		assertThat(count(name)).isZero();
	}

	@Test
	void principalWithoutPersonGetsPermissionDenied() {
		OeaPrincipal admin = new OeaPrincipal(OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT, UUID.randomUUID(), null, "admin",
				UUID.randomUUID(), true, Instant.now(), null, null);
		assertThatExceptionOfType(PermissionDeniedException.class)
			.isThrownBy(() -> this.diagrams.create(admin, unique("No person"), null, this.viewpoint));
	}

	@Test
	void serviceAccountCreatesRenamesAndDeletesAndIsTheActor() {
		long account = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'Process Importer', 'active')
				RETURNING id""", Long.class);
		OeaPrincipal principal = OeaPrincipal.serviceAccount(account, UUID.randomUUID(), "Process Importer",
				Instant.now());
		String name = unique("Imported Processes");
		Diagrams.Diagram created = this.diagrams.create(principal, name, null, this.viewpoint);
		assertThat(created.createdBy().id()).isEqualTo(account);
		assertThat(created.createdBy().serviceAccount()).isTrue();
		Diagrams.Diagram renamed = this.diagrams.update(principal, created.id(), name + " 2", null, 1L);
		assertThat(renamed.version()).isEqualTo(2);
		this.diagrams.delete(principal, created.id(), 2L);
		assertThat(this.jdbc.queryForObject("SELECT deleted_by FROM diagrams WHERE id = ?", Long.class, created.id()))
			.isEqualTo(account);
	}

	@Test
	void nameTakenIgnoringCaseAndBlanks() {
		String name = unique("Order to Cash");
		created(name);
		MvcTestResult taken = call(HttpMethod.POST, DIAGRAMS, create("  " + name.toUpperCase() + " ", null), this.anna,
				null);
		assertThat(taken.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(taken).path("type").asString()).isEqualTo("urn:oea:problem:diagram-name-taken");
		assertThat(body(taken).path("invalidFields").toString()).isEqualTo("[\"name\"]");
		assertThat(count(name)).isOne();
	}

	@Test
	void unknownOrMissingViewpoint() {
		ObjectNode unknown = create(unique("Unknown"), null).put("viewpointId", UUID.randomUUID().toString());
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS, unknown, this.anna, null);
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"viewpointId\"]");
		ObjectNode missing = create(unique("Missing"), null);
		missing.remove("viewpointId");
		MvcTestResult none = call(HttpMethod.POST, DIAGRAMS, missing, this.anna, null);
		assertThat(none.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(none).path("invalidFields").toString()).contains("viewpointId");
	}

	/** name length (0: blank), description length, status, rejected field */
	@ParameterizedTest
	@CsvSource({ "1, 0, 201,", "200, 0, 201,", "201, 0, 422, name", "0, 0, 422, name", "10, 2000, 201,",
			"10, 2001, 422, description" })
	void limits(int nameLength, int descriptionLength, int status, String field) {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		String name = (nameLength == 0) ? "   "
				: (nameLength == 1) ? prefix.substring(0, 1) : prefix + "n".repeat(nameLength - prefix.length());
		if (nameLength == 1) {
			this.jdbc.update("DELETE FROM diagrams WHERE lower(name) = ?", name.toLowerCase());
		}
		ObjectNode body = create(name, (descriptionLength > 0) ? "d".repeat(descriptionLength) : null);
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS, body, this.anna, null);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(status);
		if (field != null) {
			assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
		}
		assertThat(count(name.strip())).isEqualTo((status == 201) ? 1 : 0);
	}

	// Requirement: Read and list diagrams

	@Test
	void listIsOrderedByNameIgnoringCaseAndFiltered() {
		String suffix = " " + UUID.randomUUID().toString().substring(0, 8);
		created("Order to Cash" + suffix);
		created("hire to retire" + suffix);
		created("Procure to Pay" + suffix);
		JsonNode all = list("?q=" + suffix.strip() + "&pageSize=200", this.franz);
		assertThat(names(all)).containsExactly("hire to retire" + suffix, "Order to Cash" + suffix,
				"Procure to Pay" + suffix);
		assertThat(all.path("items").get(0).path("viewpoint").path("notation").asString()).isEqualTo("bpmn2");
		assertThat(names(list("?q=to p" + suffix.strip().substring(0, 0) + "&pageSize=200", this.franz)))
			.contains("Procure to Pay" + suffix)
			.doesNotContain("Order to Cash" + suffix);
	}

	@Test
	void wildcardsAreMatchedLiterally() {
		String suffix = " " + UUID.randomUUID().toString().substring(0, 8);
		created("100% Digital" + suffix);
		created("1000 Days" + suffix);
		created("a_b" + suffix);
		created("axb" + suffix);
		assertThat(names(list("?q=0%25&pageSize=200", this.franz))).contains("100% Digital" + suffix)
			.doesNotContain("1000 Days" + suffix);
		assertThat(names(list("?q=a_b&pageSize=200", this.franz))).contains("a_b" + suffix)
			.doesNotContain("axb" + suffix);
	}

	@Test
	void secondPageWithTheCursor() {
		this.jdbc.update("DELETE FROM diagrams");
		for (int i = 0; i < 51; i++) {
			created(String.format("Paged %02d", i));
		}
		JsonNode first = list("", this.franz);
		assertThat(first.path("items")).hasSize(50);
		assertThat(first.path("page").path("totalElements").asInt()).isEqualTo(51);
		String cursor = first.path("nextCursor").asString();
		assertThat(cursor).isNotEmpty();
		JsonNode second = list("?page=1&cursor=" + cursor, this.franz);
		assertThat(names(second)).containsExactly("Paged 50");
		assertThat(second.path("nextCursor").isNull()).isTrue();
		// without the cursor the offset gives the same page
		assertThat(names(list("?page=1", this.franz))).containsExactly("Paged 50");
		// a cursor of another filter, a changed cursor and a cursor for another page answer 400 naming cursor
		for (String other : List.of("?page=1&q=Paged&cursor=" + cursor, "?page=1&cursor=" + cursor + "x",
				"?page=2&cursor=" + cursor, "?page=1&cursor=!!")) {
			MvcTestResult result = call(HttpMethod.GET, DIAGRAMS + other, null, this.franz, null);
			assertThat(result.getResponse().getStatus()).as(other).isEqualTo(400);
			assertThat(body(result).path("invalidFields").toString()).as(other).contains("cursor");
		}
	}

	/** page size or filter length, status */
	@ParameterizedTest
	@CsvSource({ "pageSize=1, 200", "pageSize=200, 200", "pageSize=0, 400", "pageSize=201, 400", "page=-1, 400" })
	void pageLimits(String query, int status) {
		assertThat(call(HttpMethod.GET, DIAGRAMS + "?" + query, null, this.franz, null).getResponse().getStatus())
			.isEqualTo(status);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/deleted?" + query, null, this.kurt, null).getResponse().getStatus())
			.isEqualTo(status);
	}

	@ParameterizedTest
	@CsvSource({ "200, 200", "201, 400" })
	void filterLimit(int length, int status) {
		assertThat(call(HttpMethod.GET, DIAGRAMS + "?q=" + "f".repeat(length), null, this.franz, null).getResponse()
			.getStatus()).isEqualTo(status);
	}

	@Test
	void unknownDiagramAndMissingPermission() {
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/" + UUID.randomUUID(), null, this.franz, null).getResponse()
			.getStatus()).isEqualTo(404);
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(call(HttpMethod.GET, DIAGRAMS, null, nobody, null).getResponse().getStatus()).isEqualTo(403);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/" + UUID.randomUUID(), null, nobody, null).getResponse()
			.getStatus()).isEqualTo(403);
	}

	// Requirement: Change and delete a diagram

	@Test
	void renamedWithItsVersionAndAudited() {
		JsonNode diagram = created(unique("Order to Cash"));
		String id = diagram.path("id").asString();
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("Order to Cash (2026) " + id, "New"),
				this.anna, "\"1\"");
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(200);
		assertThat(body(result).path("version").asInt()).isEqualTo(2);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"2\"");
		assertThat(details("diagram.updated", id).path("fields").toString()).isEqualTo("[\"name\",\"description\"]");
		assertThat(event("diagram.updated", id)).isOne();
	}

	@Test
	void staleOrMissingVersionChangesAndAuditsNothing() {
		JsonNode diagram = created(unique("Versions"));
		String id = diagram.path("id").asString();
		call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("Versions " + id, null), this.anna, "\"1\"");
		ObjectNode again = update("Other " + id, null);
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, again, this.anna, "\"1\"").getResponse().getStatus())
			.isEqualTo(412);
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, again, this.anna, null).getResponse().getStatus())
			.isEqualTo(428);
		assertThat(call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.anna, "\"1\"").getResponse().getStatus())
			.isEqualTo(412);
		assertThat(call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.anna, null).getResponse().getStatus())
			.isEqualTo(428);
		assertThat(event("diagram.updated", id)).isOne();
		assertThat(event("diagram.deleted", id)).isZero();
		assertThat(version(id)).isEqualTo(2);
	}

	@Test
	void renameToATakenNameIgnoringCase() {
		String taken = unique("Order to Cash");
		created(taken);
		JsonNode other = created(unique("Procure to Pay"));
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + other.path("id").asString(),
				update(taken.toUpperCase(), null), this.anna, "\"1\"");
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:diagram-name-taken");
		assertThat(version(other.path("id").asString())).isOne();
		assertThat(event("diagram.updated", other.path("id").asString())).isZero();
	}

	@Test
	void viewpointInTheUpdateIsIgnored() {
		JsonNode diagram = created(unique("Fixed viewpoint"));
		String id = diagram.path("id").asString();
		ObjectNode body = update("Fixed viewpoint " + id, null).put("viewpointId", viewpoint("Other").toString());
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + id, body, this.anna, "\"1\"");
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(200);
		assertThat(body(result).path("viewpoint").path("id").asString()).isEqualTo(this.viewpoint.toString());
	}

	@Test
	void updateLimits() {
		JsonNode diagram = created(unique("Update limits"));
		String id = diagram.path("id").asString();
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("u".repeat(201), null), this.anna, "\"1\"")
			.getResponse()
			.getStatus()).isEqualTo(422);
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("  ", null), this.anna, "\"1\"").getResponse()
			.getStatus()).isEqualTo(422);
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("Update limits", "d".repeat(2001)), this.anna,
				"\"1\"")
			.getResponse()
			.getStatus()).isEqualTo(422);
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, update(id.substring(0, 8) + "u".repeat(192), "d".repeat(2000)),
				this.anna, "\"1\"")
			.getResponse()
			.getStatus()).isEqualTo(200);
	}

	@Test
	void readerCannotChangeOrDelete() {
		JsonNode diagram = created(unique("Read only"));
		String id = diagram.path("id").asString();
		assertThat(call(HttpMethod.PUT, DIAGRAMS + "/" + id, update("Renamed", null), this.franz, "\"1\"")
			.getResponse()
			.getStatus()).isEqualTo(403);
		assertThat(call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.franz, "\"1\"").getResponse().getStatus())
			.isEqualTo(403);
		assertThat(version(id)).isOne();
		assertThat(event("diagram.updated", id) + event("diagram.deleted", id)).isZero();
	}

	// Restorable deletion (design D8)

	@Test
	void deletedIsHiddenItsNameFreeAndItCanBeRestored() {
		String name = unique("Order to Cash");
		JsonNode diagram = created(name);
		String id = diagram.path("id").asString();
		MvcTestResult deleted = call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.anna, "\"1\"");
		assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
		assertThat(event("diagram.deleted", id)).isOne();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class)).isNotNull();
		for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE)) {
			assertThat(call(method, DIAGRAMS + "/" + id, update(name, null), this.kurt, "\"2\"").getResponse()
				.getStatus()).as(method.name()).isEqualTo(404);
		}
		assertThat(names(list("?q=" + name + "&pageSize=200", this.franz))).isEmpty();
		JsonNode listed = list("/deleted?q=" + name, this.kurt);
		assertThat(names(listed)).containsExactly(name);
		assertThat(listed.path("items").get(0).path("deletedBy").path("displayName").asString()).isNotEmpty();
		assertThat(listed.path("items").get(0).path("deletedAt").asString()).isNotEmpty();
		// the name is free again
		JsonNode replacement = created(name);
		// restoring while an active diagram has the name
		MvcTestResult taken = call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, "\"2\"");
		assertThat(taken.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(taken).path("type").asString()).isEqualTo("urn:oea:problem:diagram-name-taken");
		assertThat(event("diagram.restored", id)).isZero();
		call(HttpMethod.DELETE, DIAGRAMS + "/" + replacement.path("id").asString(), null, this.anna, "\"1\"");
		MvcTestResult restored = call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, "\"2\"");
		assertThat(restored.getResponse().getStatus()).as(() -> content(restored)).isEqualTo(200);
		assertThat(body(restored).path("version").asInt()).isEqualTo(3);
		assertThat(restored.getResponse().getHeader("ETag")).isEqualTo("\"3\"");
		assertThat(event("diagram.restored", id)).isOne();
		assertThat(names(list("?q=" + name, this.franz))).containsExactly(name);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/" + id, null, this.franz, null).getResponse().getStatus())
			.isEqualTo(200);
	}

	@Test
	void restoreRejections() {
		JsonNode diagram = created(unique("Restore rejections"));
		String id = diagram.path("id").asString();
		// not deleted: 409
		MvcTestResult notDeleted = call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, "\"1\"");
		assertThat(notDeleted.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(notDeleted).path("type").asString()).isEqualTo("urn:oea:problem:diagram-not-deleted");
		call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.anna, "\"1\"");
		assertThat(call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, "\"1\"").getResponse()
			.getStatus()).isEqualTo(412);
		assertThat(call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, null).getResponse()
			.getStatus()).isEqualTo(428);
		assertThat(call(HttpMethod.POST, DIAGRAMS + "/" + UUID.randomUUID() + "/restore", null, this.kurt, "\"1\"")
			.getResponse()
			.getStatus()).isEqualTo(404);
		// without entity:restore both operations are forbidden
		assertThat(call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.anna, "\"2\"").getResponse()
			.getStatus()).isEqualTo(403);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/deleted", null, this.anna, null).getResponse().getStatus())
			.isEqualTo(403);
		assertThat(event("diagram.restored", id)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM diagrams WHERE id = ?::uuid",
				Boolean.class, id))
			.isTrue();
	}

	// Requirement: Diagram count of a viewpoint, deleting a viewpoint

	@Test
	void diagramCountOfAViewpointIgnoresDeletedDiagrams() {
		UUID counted = viewpoint("Counted");
		assertThat(viewpointJson(counted).path("diagramCount").asInt()).isZero();
		for (int i = 0; i < 5; i++) {
			createIn(counted, unique("Counted " + i));
		}
		JsonNode gone = createIn(counted, unique("Gone"));
		call(HttpMethod.DELETE, DIAGRAMS + "/" + gone.path("id").asString(), null, this.anna, "\"1\"");
		assertThat(viewpointJson(counted).path("diagramCount").asInt()).isEqualTo(5);
		MvcTestResult list = call(HttpMethod.GET, VIEWPOINTS, null, this.kurt, null);
		for (JsonNode listed : body(list)) {
			if (listed.path("id").asString().equals(counted.toString())) {
				assertThat(listed.path("diagramCount").asInt()).isEqualTo(5);
			}
		}
	}

	@Test
	void deletingAViewpointRemovesItsDiagramsForGood() {
		UUID doomed = viewpoint("Doomed");
		JsonNode first = createIn(doomed, unique("Doomed 1"));
		JsonNode second = createIn(doomed, unique("Doomed 2"));
		JsonNode softDeleted = createIn(doomed, unique("Doomed 3"));
		call(HttpMethod.DELETE, DIAGRAMS + "/" + softDeleted.path("id").asString(), null, this.anna, "\"1\"");
		int entities = this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class);
		MvcTestResult result = call(HttpMethod.DELETE, VIEWPOINTS + "/" + doomed, null, this.kurt, "\"1\"");
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(204);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE viewpoint_id = ?", Integer.class,
				doomed))
			.isZero();
		for (JsonNode diagram : List.of(first, second)) {
			assertThat(details("diagram.deleted", diagram.path("id").asString()).path("cause").asString())
				.isEqualTo("viewpoint-deleted");
		}
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class)).isEqualTo(entities);
	}

	@Test
	void staleVersionDeletesNeitherTheViewpointNorItsDiagrams() {
		UUID kept = viewpoint("Kept");
		JsonNode diagram = createIn(kept, unique("Kept 1"));
		assertThat(call(HttpMethod.DELETE, VIEWPOINTS + "/" + kept, null, this.kurt, "\"7\"").getResponse().getStatus())
			.isEqualTo(412);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/" + diagram.path("id").asString(), null, this.franz, null)
			.getResponse()
			.getStatus()).isEqualTo(200);
		assertThat(event("diagram.deleted", diagram.path("id").asString())).isZero();
	}

	@Test
	void systemDefinedViewpointKeepsItsDiagrams() {
		UUID system = viewpoint("System", "system-defined");
		JsonNode diagram = createIn(system, unique("System 1"));
		MvcTestResult result = call(HttpMethod.DELETE, VIEWPOINTS + "/" + system, null, this.kurt, "\"1\"");
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(call(HttpMethod.GET, DIAGRAMS + "/" + diagram.path("id").asString(), null, this.franz, null)
			.getResponse()
			.getStatus()).isEqualTo(200);
	}

	// Requirement: Diagrams in the browser, place a diagram

	@Test
	void browserCategoryCountsAndListsActiveDiagrams() {
		this.jdbc.update("DELETE FROM diagrams");
		MvcTestResult empty = call(HttpMethod.GET, BROWSER, null, this.franz, null);
		assertThat(body(empty).path("currentState").path("diagrams").asInt()).isZero();
		UUID archimate = viewpoint("Landscape", "user-defined", "archimate3");
		createIn(this.viewpoint, "Procure to Pay");
		createIn(archimate, "order to Cash");
		JsonNode gone = createIn(archimate, "Gone");
		call(HttpMethod.DELETE, DIAGRAMS + "/" + gone.path("id").asString(), null, this.anna, "\"1\"");
		assertThat(body(call(HttpMethod.GET, BROWSER, null, this.franz, null)).path("currentState")
			.path("diagrams")
			.asInt()).isEqualTo(2);
		MvcTestResult items = call(HttpMethod.GET, BROWSER + "/items?scope=current-state&category=diagrams", null,
				this.franz, null);
		assertThat(items.getResponse().getStatus()).as(() -> content(items)).isEqualTo(200);
		JsonNode page = body(items);
		assertThat(page.path("items").get(0).path("name").asString()).isEqualTo("order to Cash");
		assertThat(page.path("items").get(0).path("kind").asString()).isEqualTo("diagram");
		assertThat(page.path("items").get(0).path("notation").asString()).isEqualTo("archimate3");
		assertThat(page.path("items").get(1).path("notation").asString()).isEqualTo("bpmn2");
		assertThat(page.path("items").get(1).path("childCount").asInt()).isZero();
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(2);
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void browserPageSizes(int pageSize, int status) {
		assertThat(call(HttpMethod.GET,
				BROWSER + "/items?scope=current-state&category=diagrams&pageSize=" + pageSize, null, this.franz, null)
			.getResponse()
			.getStatus()).isEqualTo(status);
	}

	@Test
	void browserSecondPage() {
		this.jdbc.update("DELETE FROM diagrams");
		for (int i = 0; i < 51; i++) {
			createIn(this.viewpoint, String.format("Browsed %02d", i));
		}
		JsonNode first = body(call(HttpMethod.GET, BROWSER + "/items?scope=current-state&category=diagrams", null,
				this.franz, null));
		assertThat(first.path("items")).hasSize(50);
		JsonNode second = body(call(HttpMethod.GET, BROWSER + "/items?scope=current-state&category=diagrams&page=1",
				null, this.franz, null));
		assertThat(second.path("items")).hasSize(1);
		assertThat(second.path("items").get(0).path("name").asString()).isEqualTo("Browsed 50");
	}

	@Test
	void diagramsOfASolutionAreRejected() {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by)
				VALUES (gen_random_uuid(), ?, (SELECT min(id) FROM persons), (SELECT min(id) FROM persons))
				RETURNING id""", UUID.class, unique("CRM Migration"));
		MvcTestResult result = call(HttpMethod.GET,
				BROWSER + "/items?scope=solution&solutionId=" + solution + "&category=diagrams", null, this.franz,
				null);
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"category\"]");
	}

	@Test
	void diagramPlacedInTheTreeHiddenWhileDeletedAndShownAgainAfterTheRestore() {
		String name = unique("Order to Cash");
		JsonNode diagram = created(name);
		String id = diagram.path("id").asString();
		String folder = folder(unique("Processes"));
		ObjectNode item = JsonMapper.shared().createObjectNode().put("itemType", "diagram").put("referenceId", id);
		MvcTestResult placed = call(HttpMethod.POST, TREE + "/" + folder + "/items", item, this.kurt, null);
		assertThat(placed.getResponse().getStatus()).as(() -> content(placed)).isEqualTo(201);
		assertThat(entries(folder)).containsExactly(name);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'itemType' || '/' || (details ->> 'referenceId') FROM audit.events
				WHERE event_type = 'tree-node.item-added' AND target_id = ?""", String.class, folder))
			.isEqualTo("diagram/" + id);
		// a second entry in the folder is rejected
		MvcTestResult second = call(HttpMethod.POST, TREE + "/" + folder + "/items", item, this.kurt, null);
		assertThat(second.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(second).path("invalidFields").toString()).isEqualTo("[\"referenceId\"]");
		call(HttpMethod.DELETE, DIAGRAMS + "/" + id, null, this.anna, "\"1\"");
		assertThat(entries(folder)).isEmpty();
		call(HttpMethod.POST, DIAGRAMS + "/" + id + "/restore", null, this.kurt, "\"2\"");
		assertThat(entries(folder)).containsExactly(name);
	}

	@Test
	void unknownOrDeletedDiagramCannotBePlacedAndWritersNeedViewpointManage() {
		String folder = folder(unique("Rejected"));
		ObjectNode unknown = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "diagram")
			.put("referenceId", UUID.randomUUID().toString());
		MvcTestResult result = call(HttpMethod.POST, TREE + "/" + folder + "/items", unknown, this.kurt, null);
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"referenceId\"]");
		JsonNode gone = created(unique("Gone"));
		call(HttpMethod.DELETE, DIAGRAMS + "/" + gone.path("id").asString(), null, this.anna, "\"1\"");
		ObjectNode deleted = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "diagram")
			.put("referenceId", gone.path("id").asString());
		assertThat(call(HttpMethod.POST, TREE + "/" + folder + "/items", deleted, this.kurt, null).getResponse()
			.getStatus()).isEqualTo(422);
		JsonNode live = created(unique("Live"));
		ObjectNode placing = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "diagram")
			.put("referenceId", live.path("id").asString());
		assertThat(call(HttpMethod.POST, TREE + "/" + folder + "/items", placing, this.anna, null).getResponse()
			.getStatus()).isEqualTo(403);
		assertThat(entries(folder)).isEmpty();
	}

	// Helpers

	private String folder(String name) {
		JsonNode root = body(call(HttpMethod.GET, "/api/v1/views/explorer", null, this.kurt, null));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("parentId",
				root.path("id").asString());
		MvcTestResult result = call(HttpMethod.POST, TREE, body, this.kurt, null);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(201);
		return find(body(result), name).path("id").asString();
	}

	private static JsonNode find(JsonNode node, String name) {
		if (name.equals(node.path("name").asString())) {
			return node;
		}
		for (JsonNode child : node.path("children")) {
			JsonNode found = find(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** The shown names of the entries of the folder, as the architect reads the tree. */
	private List<String> entries(String folder) {
		JsonNode tree = body(call(HttpMethod.GET, "/api/v1/views/explorer", null, this.kurt, null));
		List<String> names = new ArrayList<>();
		JsonNode found = findById(tree, folder);
		found.path("items").forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

	private static JsonNode findById(JsonNode node, String id) {
		if (id.equals(node.path("id").asString())) {
			return node;
		}
		for (JsonNode child : node.path("children")) {
			JsonNode found = findById(child, id);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private UUID viewpoint(String name) {
		return viewpoint(name, "user-defined", "bpmn2");
	}

	private UUID viewpoint(String name, String type) {
		return viewpoint(name, type, "bpmn2");
	}

	private UUID viewpoint(String name, String type, String notation) {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		boolean system = "system-defined".equals(type);
		return this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, viewpoint_type, source_package_id, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, ?) RETURNING id""", UUID.class, unique(name), notation, type,
				system ? "test-package" : null, person);
	}

	private JsonNode viewpointJson(UUID id) {
		return body(call(HttpMethod.GET, VIEWPOINTS + "/" + id, null, this.kurt, null));
	}

	private ObjectNode create(String name, String description) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("viewpointId",
				this.viewpoint.toString());
		if (description != null) {
			body.put("description", description);
		}
		return body;
	}

	private static ObjectNode update(String name, String description) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		if (description != null) {
			body.put("description", description);
		}
		return body;
	}

	private JsonNode created(String name) {
		return createIn(this.viewpoint, name);
	}

	private JsonNode createIn(UUID viewpointId, String name) {
		ObjectNode body = create(name, null).put("viewpointId", viewpointId.toString());
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS, body, this.anna, null);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(201);
		return body(result);
	}

	private JsonNode list(String query, TestSession session) {
		String path = query.startsWith("/") ? DIAGRAMS + query : DIAGRAMS + query;
		MvcTestResult result = call(HttpMethod.GET, path, null, session, null);
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private static List<String> names(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.path("items").forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

	private int count(String name) {
		return this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE lower(name) = lower(?)", Integer.class,
				name);
	}

	private int version(String id) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, id);
	}

	private int event(String type, String id) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?",
				Integer.class, type, id);
	}

	private JsonNode details(String type, String id) {
		String json = this.jdbc.queryForObject(
				"SELECT details::text FROM audit.events WHERE event_type = ? AND target_id = ? ORDER BY id DESC LIMIT 1",
				String.class, type, id);
		return JsonMapper.shared().readTree(json);
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path.replace(" ", "%20")))
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
			return Files.createTempDirectory("oea-diagrams").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
