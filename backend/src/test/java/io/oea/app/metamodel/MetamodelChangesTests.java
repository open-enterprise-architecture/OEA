package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec metamodel/change-history of us-034-metamodel-changes, on an own database: the metamodel
 * changes from the audit trail with names, kinds, filters, import batches, and the atomic write.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelChangesTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String INSTANCE = PackageImporter.INSTANCE_CONFIGURATION.toString();

	private static final String CHANGES = "/metamodel/changes";

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
	private AuditWriter audit;

	@Autowired
	private TransactionTemplate transactions;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession lukas;

	@BeforeEach
	void administrators() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = named(this.principals.personWithIdpRole("metamodel-admin"), "Kurt " + suffix());
		this.lukas = named(this.principals.personWithIdpRole("metamodel-admin"), "Lukas " + suffix());
	}

	// Reading metamodel changes

	@Test
	void twoAdministratorsChangedTheMetamodel() {
		String zone = "SecurityZone " + suffix();
		UUID created = id(created(createMetatype(zone, this.kurt)));
		String other = "Other " + suffix();
		UUID deleted = id(created(createMetatype(other, this.lukas)));
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + deleted, null, this.lukas))
			.hasStatus(HttpStatus.NO_CONTENT);

		JsonNode page = ok(call(HttpMethod.GET, CHANGES + "?kind=metatype", null, this.kurt));
		JsonNode creation = event(page, "metamodel.metatype-created", created);
		assertThat(creation.path("actor").path("actorName").asString()).isEqualTo(name(this.kurt));
		assertThat(creation.path("elementName").asString()).isEqualTo(zone);
		assertThat(creation.path("kind").asString()).isEqualTo("metatype");
		assertThat(creation.path("occurredAt").asString()).isNotEmpty();
		JsonNode deletion = event(page, "metamodel.metatype-deleted", deleted);
		assertThat(deletion.path("actor").path("actorName").asString()).isEqualTo(name(this.lukas));
		assertThat(deletion.path("elementName").asString()).isEqualTo(other);
		// newest first
		assertThat(page.path("items").get(0).path("eventType").asString()).isEqualTo("metamodel.metatype-deleted");
	}

	@Test
	void readersWithoutMetamodelAdministration() {
		for (String role : List.of("viewer", "enterprise-architect", "auditor")) {
			assertThat(call(HttpMethod.GET, CHANGES, null, this.principals.personWithIdpRole(role))).as(role)
				.hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	@Test
	void onlyMetamodelEvents() {
		createMetatype("Only " + suffix(), this.kurt);
		JsonNode page = ok(call(HttpMethod.GET, CHANGES + "?pageSize=200", null, this.kurt));
		assertThat(page.path("items")).isNotEmpty()
			.allMatch((item) -> item.path("eventType").asString().startsWith("metamodel."));
		assertThat(page.path("page").path("totalElements").asInt())
			.isEqualTo(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.%'",
					Integer.class));
	}

	// Filtering and import batches

	@Test
	void anImportAsOneBatch() {
		String packageId = "test-batch-" + suffix();
		MvcTestResult imported = call(HttpMethod.POST, "/admin/metamodel/import", TestPackages.pkg(packageId, 5),
				this.kurt);
		assertThat(imported).as(() -> content(imported)).hasStatus(HttpStatus.OK);
		String batch = body(imported).path("batchId").asString();
		JsonNode page = ok(call(HttpMethod.GET, CHANGES + "?batchId=" + batch, null, this.kurt));
		List<JsonNode> metatypes = new ArrayList<>();
		page.path("items").forEach((item) -> {
			assertThat(item.path("batchId").asString()).isEqualTo(batch);
			assertThat(item.path("kind").asString()).isEqualTo("package");
			if ("metamodel.metatype-imported".equals(item.path("eventType").asString())) {
				metatypes.add(item);
			}
		});
		assertThat(metatypes).hasSize(5);
		assertThat(metatypes).allMatch((item) -> item.path("elementName").asString().startsWith(packageId + " Type "));
		JsonNode packageEvent = event(page, "metamodel.package-imported", null);
		assertThat(packageEvent.path("elementName").asString()).isEqualTo(packageId);
	}

	@Test
	void changesOfOnePerson() {
		createMetatype("By Kurt " + suffix(), this.kurt);
		createMetatype("By Lukas " + suffix(), this.lukas);
		String lukasRef = personId(this.lukas).toString();
		JsonNode page = ok(call(HttpMethod.GET, CHANGES + "?actorRef=" + lukasRef, null, this.kurt));
		assertThat(page.path("items")).isNotEmpty()
			.allMatch((item) -> item.path("actor").path("actorRef").asString().equals(lukasRef));
		List<String> actors = new ArrayList<>();
		page.path("actors").forEach((actor) -> actors.add(actor.path("actorName").asString()));
		assertThat(actors).contains(name(this.kurt), name(this.lukas));
	}

	@Test
	void eachKind() {
		UUID metatype = id(created(createMetatype("Kinds " + suffix(), this.kurt)));
		UUID property = id(created(call(HttpMethod.POST, "/metamodel/property-definitions",
				object().put("name", "Kind " + suffix()).put("dataType", "enum"), this.kurt)));
		created(call(HttpMethod.POST, "/metamodel/property-definitions/" + property + "/enum-values",
				object().put("value", "high"), this.kurt));
		created(call(HttpMethod.POST, "/metamodel/property-categories", object().put("name", "Kind " + suffix()),
				this.kurt));
		created(call(HttpMethod.POST, "/metamodel/metatypes/" + metatype + "/property-mappings",
				object().put("propertyDefId", property.toString()).put("necessity", "optional"), this.kurt));
		ObjectNode labels = object();
		labels.putObject("labels").put("metatype." + metatype + ".name", "Arten");
		assertThat(call(HttpMethod.PUT, "/metamodel/labels/de", labels, this.kurt)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.PUT, "/metamodel/settings/reimport-policy",
				object().put("organizationMappings", "keep"), this.kurt)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.PUT, "/metamodel/configurations/" + INSTANCE,
				object().put("name", "Instance metamodel").put("editMode", "gui-and-import"), this.kurt))
			.hasStatus(HttpStatus.OK);
		Map<String, String> expected = Map.of("metatype", "metamodel.metatype-created", "property",
				"metamodel.property-created", "enum-value", "metamodel.enum-value-created", "category",
				"metamodel.category-created", "mapping", "metamodel.mapping-created", "labels",
				"metamodel.labels-changed", "reimport", "metamodel.reimport-policy-changed", "configuration",
				"metamodel.configuration-updated");
		expected.forEach((kind, eventType) -> {
			JsonNode page = ok(call(HttpMethod.GET, CHANGES + "?kind=" + kind, null, this.kurt));
			assertThat(page.path("items")).as(kind).isNotEmpty().allMatch((item) -> item.path("kind").asString().equals(kind));
			assertThat(page.path("items").get(0).path("eventType").asString()).as(kind).isEqualTo(eventType);
		});
		// a mapping names its metatype, an enum value itself
		JsonNode mapping = ok(call(HttpMethod.GET, CHANGES + "?kind=mapping", null, this.kurt)).path("items").get(0);
		assertThat(mapping.path("elementName").asString()).startsWith("Kinds ");
		JsonNode value = ok(call(HttpMethod.GET, CHANGES + "?kind=enum-value", null, this.kurt)).path("items").get(0);
		assertThat(value.path("elementName").asString()).isEqualTo("high");
	}

	@Test
	void aPeriodAndPaging() {
		for (int i = 0; i < 3; i++) {
			createMetatype("Paged " + i + " " + suffix(), this.kurt);
		}
		JsonNode first = ok(call(HttpMethod.GET, CHANGES + "?kind=metatype&pageSize=2", null, this.kurt));
		assertThat(first.path("items")).hasSize(2);
		assertThat(first.path("page").path("totalPages").asInt()).isGreaterThanOrEqualTo(2);
		JsonNode second = ok(call(HttpMethod.GET, CHANGES + "?kind=metatype&pageSize=2&page=1", null, this.kurt));
		assertThat(second.path("items").get(0).path("id").asLong())
			.isLessThan(first.path("items").get(1).path("id").asLong());
		JsonNode today = ok(call(HttpMethod.GET, CHANGES + "?kind=metatype&from=" + LocalDate.now(ZoneOffset.UTC)
				+ "&to=" + LocalDate.now(ZoneOffset.UTC), null, this.kurt));
		assertThat(today.path("items")).isNotEmpty();
		JsonNode past = ok(call(HttpMethod.GET, CHANGES + "?kind=metatype&from=2020-01-01&to=2020-01-31", null, this.kurt));
		assertThat(past.path("items")).isEmpty();
	}

	@Test
	void aSystemAdminAccountAndTheSystem() {
		TestSession admin = this.principals.systemAdmin();
		UUID account = this.jdbc.queryForObject("SELECT system_admin_account_id FROM sessions WHERE id = ?", UUID.class,
				admin.sessionId());
		String login = this.jdbc.queryForObject("SELECT login_name FROM system_admin_accounts WHERE id = ?",
				String.class, account);
		this.transactions.executeWithoutResult((status) -> {
			this.audit.append(new AuditEvent("metamodel.labels-changed", AuditEvent.Category.CHANGE,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
					AuditEvent.Actor.systemAdminAccount(account), "metamodel-configuration", INSTANCE,
					Map.of("locale", "de")));
			this.audit.append(new AuditEvent("metamodel.mapping-effective", AuditEvent.Category.CHANGE,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.JOB, AuditEvent.Actor.system(),
					"metatype", UUID.randomUUID().toString(), Map.of()));
		});
		JsonNode labels = ok(call(HttpMethod.GET, CHANGES + "?actorRef=" + account, null, this.kurt)).path("items").get(0);
		assertThat(labels.path("actor").path("actorType").asString()).isEqualTo("system-admin-account");
		assertThat(labels.path("actor").path("actorName").asString()).isEqualTo(login);
		assertThat(labels.path("elementName").asString()).isEqualTo("Instance metamodel");
		JsonNode system = ok(call(HttpMethod.GET, CHANGES + "?kind=mapping", null, this.kurt)).path("items").get(0);
		assertThat(system.path("actor").path("actorType").asString()).isEqualTo("system");
		assertThat(system.path("actor").path("actorName").isNull()).isTrue();
		assertThat(system.path("elementName").isNull()).isTrue();
	}

	@Test
	void invalidParameters() {
		for (String query : List.of("?kind=entity", "?actorRef=kurt", "?from=2026-02-01&to=2026-01-01", "?pageSize=0",
				"?batchId=x")) {
			assertThat(call(HttpMethod.GET, CHANGES + query, null, this.kurt)).as(query).hasStatus(HttpStatus.BAD_REQUEST);
		}
	}

	@Test
	void theIndexOfTheMetamodelEvents() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				var rs = statement.executeQuery(
						"SELECT indexdef FROM pg_indexes WHERE schemaname = 'audit' AND indexname = 'events_metamodel_occurred'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).contains("occurred_at DESC").contains("'metamodel.%'");
		}
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			org.assertj.core.api.Assertions.assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM audit.events WHERE false"))
				.withMessageContaining("permission denied");
		}
	}

	// Change and audit event are atomic

	@Test
	void auditTrailNotWritable() throws SQLException {
		String name = "Not audited " + suffix();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.execute("REVOKE INSERT ON audit.events FROM oea_app");
			try {
				// the failed audit insert fails the request; MockMvc hands the exception through
				org.assertj.core.api.Assertions.assertThatException()
					.isThrownBy(() -> createMetatype(name, this.kurt).getResponse());
			}
			finally {
				statement.execute("GRANT INSERT ON audit.events TO oea_app");
			}
		}
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE name = ?", Integer.class, name))
			.isZero();
	}

	// helpers

	private TestSession named(TestSession session, String name) {
		this.jdbc.update("UPDATE persons SET name = ? WHERE id = ?", name, personId(session));
		return session;
	}

	private Long personId(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	private String name(TestSession session) {
		return this.jdbc.queryForObject("SELECT name FROM persons WHERE id = ?", String.class, personId(session));
	}

	private MvcTestResult createMetatype(String name, TestSession session) {
		return call(HttpMethod.POST, "/metamodel/metatypes", object().put("name", name).put("configId", INSTANCE),
				session);
	}

	private static JsonNode event(JsonNode page, String eventType, UUID target) {
		for (JsonNode item : page.path("items")) {
			if (item.path("eventType").asString().equals(eventType)
					&& (target == null || item.path("targetId").asString().equals(target.toString()))) {
				return item;
			}
		}
		throw new AssertionError("no " + eventType + " in " + page);
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

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.path("id").asString());
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
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
			return Files.createTempDirectory("oea-metamodel-changes").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
