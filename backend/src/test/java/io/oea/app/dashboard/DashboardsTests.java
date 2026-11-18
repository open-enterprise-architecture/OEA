package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

/**
 * Spec dashboard/dashboards (US-055, US-062, REQ-051, REQ-056; design D1 to D3 of
 * us-055-dashboards): creating, listing, reading, changing and deleting dashboards on an own
 * database. Kurt and Eva hold {@code dashboard:manage}, Michael is a viewer, Sandra holds
 * {@code settings:manage}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-dashboards") + "/setup-token";

	private static final String SHARED = "IT Investment Planning 2026–2030";

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
	private Dashboards dashboards;

	private DashboardTestSupport support;

	private TestSession kurt;

	private TestSession eva;

	private TestSession michael;

	private TestSession sandra;

	@BeforeEach
	void instance() {
		this.support = new DashboardTestSupport(this.mvc, this.jdbc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.eva = principals.personWithIdpRole("enterprise-architect");
		this.michael = principals.personWithIdpRole("viewer");
		this.sandra = principals.personWithIdpRoles("viewer", "system-admin");
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private List<String> listed(TestSession session) {
		Reply reply = this.support.get(DashboardTestSupport.DASHBOARDS, session);
		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		List<String> ids = new ArrayList<>();
		reply.json().forEach((summary) -> ids.add(summary.path("id").asString()));
		return ids;
	}

	@Test
	void sharedDashboardIsCreatedWithoutWidgetsAndListedForEveryReader() {
		String name = unique(SHARED);
		Reply created = this.support.create(this.kurt, name, "instance");
		assertThat(created.status()).as(created.raw()).isEqualTo(201);
		assertThat(created.etag()).isEqualTo("\"1\"");
		assertThat(created.json().path("widgets")).isEmpty();
		assertThat(created.json().path("canEdit").asBoolean()).isTrue();
		assertThat(created.json().path("owner").path("displayName").asString()).isNotEmpty();
		assertThat(listed(this.michael)).contains(created.id());
		Reply read = this.support.get(DashboardTestSupport.VIEWS + "/" + created.id(), this.michael);
		assertThat(read.status()).isEqualTo(200);
		assertThat(read.json().path("canEdit").asBoolean()).isFalse();
		assertThat(read.etag()).isEqualTo("\"1\"");
		assertThat(this.support.audited("dashboard.created", created.id())).isEqualTo(1);
	}

	@Test
	void sharedDashboardWithoutDashboardManageIsRejected() {
		Reply denied = this.support.create(this.michael, unique("Denied"), "instance");
		assertThat(denied.status()).isEqualTo(403);
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE name LIKE 'Denied%'")).isZero();
	}

	@Test
	void personalDashboardWithoutDashboardManage() {
		Reply created = this.support.create(this.michael, unique("My analysis"), "personal");
		assertThat(created.status()).as(created.raw()).isEqualTo(201);
		assertThat(created.json().path("scope").asString()).isEqualTo("personal");
		assertThat(created.json().path("canEdit").asBoolean()).isTrue();
	}

	@Test
	void serviceAccountsGetNoAccessToDashboards() {
		OeaPrincipal account = OeaPrincipal.serviceAccount(1L, UUID.randomUUID(), "robot", Instant.now());
		assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> this.dashboards.list(account));
		assertThatExceptionOfType(PermissionDeniedException.class)
			.isThrownBy(() -> this.dashboards.get(account, UUID.randomUUID()));
		assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> this.dashboards.create(account,
				new Dashboards.NewDashboard("Robot", "", Dashboards.Scope.PERSONAL)));
		assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(
				() -> this.dashboards.delete(account, UUID.randomUUID(), 1L));
	}

	@Test
	void anotherPersonsPersonalDashboardIsHidden() {
		Reply mine = this.support.created(this.michael, unique("My analysis"), "personal");
		String path = DashboardTestSupport.DASHBOARDS + "/" + mine.id();
		assertThat(listed(this.kurt)).doesNotContain(mine.id());
		assertThat(listed(this.michael)).contains(mine.id());
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id(), this.kurt).status()).isEqualTo(404);
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id() + "/data", this.kurt).status())
			.isEqualTo(404);
		assertThat(this.support.write(HttpMethod.PUT, path, this.kurt,
				DashboardTestSupport.dashboard("Taken over", "personal"), 1).status()).isEqualTo(404);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.kurt, null, 1).status()).isEqualTo(404);
		// the answer is the same as for a dashboard that does not exist
		String unknown = UUID.randomUUID().toString();
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + unknown, this.kurt).raw().replace(unknown, "ID"))
			.isEqualTo(this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id(), this.kurt)
				.raw()
				.replace(mine.id(), "ID"));
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid", mine.id())).isEqualTo(1);
	}

	@Test
	void administratorsReadPersonalDashboardsButNeverChangeThem() {
		Reply mine = this.support.created(this.michael, unique("My analysis"), "personal");
		String path = DashboardTestSupport.DASHBOARDS + "/" + mine.id();
		Reply list = this.support.get(DashboardTestSupport.DASHBOARDS, this.sandra);
		JsonNode summary = null;
		for (JsonNode candidate : list.json()) {
			if (candidate.path("id").asString().equals(mine.id())) {
				summary = candidate;
			}
		}
		assertThat(summary).isNotNull();
		assertThat(summary.path("owner").path("id").asLong()).isEqualTo(this.support.personOf(this.michael));
		assertThat(summary.path("canEdit").asBoolean()).isFalse();
		Reply read = this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id(), this.sandra);
		assertThat(read.status()).isEqualTo(200);
		assertThat(read.json().path("canEdit").asBoolean()).isFalse();
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id() + "/data", this.sandra).status())
			.isEqualTo(200);
		assertThat(this.support.write(HttpMethod.PUT, path, this.sandra,
				DashboardTestSupport.dashboard("Renamed", "personal"), 1).status()).isEqualTo(403);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.sandra, null, 1).status()).isEqualTo(403);
		assertThat(this.support
			.write(HttpMethod.POST, path + "/widgets", this.sandra,
					DashboardTestSupport.textWidget("Notes", "x", DashboardTestSupport.position(1, 1, 3, 1)), 1)
			.status()).isEqualTo(403);
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND version = 1",
				mine.id())).isEqualTo(1);
	}

	@Test
	void anotherManagerChangesAndDeletesASharedDashboard() {
		Reply shared = this.support.created(this.kurt, unique(SHARED), "instance");
		String path = DashboardTestSupport.DASHBOARDS + "/" + shared.id();
		String renamed = unique("Renamed by Eva");
		Reply changed = this.support.write(HttpMethod.PUT, path, this.eva,
				DashboardTestSupport.dashboard(renamed, "instance"), 1);
		assertThat(changed.status()).as(changed.raw()).isEqualTo(200);
		assertThat(changed.json().path("name").asString()).isEqualTo(renamed);
		assertThat(changed.etag()).isEqualTo("\"2\"");
		// a reader without dashboard:manage may not
		assertThat(this.support.write(HttpMethod.PUT, path, this.michael,
				DashboardTestSupport.dashboard("Nope", "instance"), 2).status()).isEqualTo(403);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.michael, null, 2).status()).isEqualTo(403);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.eva, null, 2).status()).isEqualTo(204);
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + shared.id(), this.kurt).status())
			.isEqualTo(404);
		assertThat(this.support.audited("dashboard.deleted", shared.id())).isEqualTo(1);
	}

	@Test
	void ownerChangesAndDeletesAPersonalDashboard() {
		Reply mine = this.support.created(this.michael, unique("My analysis"), "personal");
		String path = DashboardTestSupport.DASHBOARDS + "/" + mine.id();
		Reply changed = this.support.write(HttpMethod.PUT, path, this.michael,
				DashboardTestSupport.dashboard(unique("My renamed analysis"), "personal"), 1);
		assertThat(changed.status()).as(changed.raw()).isEqualTo(200);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.michael, null, 2).status()).isEqualTo(204);
	}

	@Test
	void scopeUpgradeNeedsDashboardManage() {
		TestSession manager = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		Reply withPermission = this.support.created(manager, unique("My analysis"), "personal");
		Reply shared = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + withPermission.id(),
				manager, DashboardTestSupport.dashboard(withPermission.json().path("name").asString(), "instance"), 1);
		assertThat(shared.status()).as(shared.raw()).isEqualTo(200);
		assertThat(shared.json().path("scope").asString()).isEqualTo("instance");
		assertThat(listed(this.michael)).contains(withPermission.id());

		Reply withoutPermission = this.support.created(this.michael, unique("My analysis"), "personal");
		Reply denied = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + withoutPermission.id(),
				this.michael,
				DashboardTestSupport.dashboard(withoutPermission.json().path("name").asString(), "instance"), 1);
		assertThat(denied.status()).isEqualTo(403);
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND scope = 'personal'",
				withoutPermission.id())).isEqualTo(1);
	}

	@Test
	void scopeUpgradeIsRejectedWithAWidgetOnAPersonalCatalog() {
		Reply mine = this.support.created(this.kurt, unique("My analysis"), "personal");
		String catalog = this.support.catalog(this.kurt, unique("Private"), "personal");
		this.support.addWidget(this.kurt, mine, DashboardTestSupport.tableWidget("Mine", catalog, null, 10,
				DashboardTestSupport.position(1, 1, 6, 2)));
		Reply rejected = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + mine.id(), this.kurt,
				DashboardTestSupport.dashboard(mine.json().path("name").asString(), "instance"), 2);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.invalidFields()).containsExactly("scope");
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND scope = 'personal'",
				mine.id())).isEqualTo(1);
	}

	@Test
	void scopeUpgradeIsRejectedWithATakenSharedName() {
		String name = unique(SHARED);
		this.support.created(this.kurt, name, "instance");
		Reply mine = this.support.created(this.eva, name.toUpperCase(), "personal");
		Reply rejected = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + mine.id(), this.eva,
				DashboardTestSupport.dashboard(name.toUpperCase(), "instance"), 1);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.json().path("type").asString()).endsWith("dashboard-name-taken");
		assertThat(rejected.invalidFields()).containsExactly("name");
	}

	@Test
	void sharedDashboardDoesNotBecomePersonal() {
		Reply shared = this.support.created(this.kurt, unique(SHARED), "instance");
		Reply rejected = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + shared.id(),
				this.kurt, DashboardTestSupport.dashboard(shared.json().path("name").asString(), "personal"), 1);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.invalidFields()).containsExactly("scope");
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND scope = 'instance'",
				shared.id())).isEqualTo(1);
	}

	@Test
	void namesAreUniqueInAScopeIgnoringCaseAndSurroundingBlanks() {
		String name = unique(SHARED);
		this.support.created(this.kurt, name, "instance");
		for (String duplicate : List.of(name, name.toLowerCase(), "  " + name.toUpperCase() + " ")) {
			Reply rejected = this.support.create(this.eva, duplicate, "instance");
			assertThat(rejected.status()).as(duplicate).isEqualTo(422);
			assertThat(rejected.json().path("type").asString()).endsWith("dashboard-name-taken");
			assertThat(rejected.json().path("detail").asString()).isEqualTo("A dashboard with this name already exists");
			assertThat(rejected.invalidFields()).containsExactly("name");
		}
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE lower(name) = lower(?)", name))
			.isEqualTo(1);
	}

	@Test
	void theSameNameInAnotherScopeOrOfAnotherOwnerIsAccepted() {
		String name = unique("Shared name");
		this.support.created(this.kurt, name, "instance");
		assertThat(this.support.create(this.kurt, name, "personal").status()).isEqualTo(201);
		assertThat(this.support.create(this.eva, name, "personal").status()).isEqualTo(201);
		// the same owner twice is not
		assertThat(this.support.create(this.eva, name, "personal").status()).isEqualTo(422);
	}

	@Test
	void renamingToATakenNameIsRejected() {
		String taken = unique(SHARED);
		this.support.created(this.kurt, taken, "instance");
		Reply other = this.support.created(this.kurt, unique("Other"), "instance");
		Reply rejected = this.support.write(HttpMethod.PUT, DashboardTestSupport.DASHBOARDS + "/" + other.id(),
				this.kurt, DashboardTestSupport.dashboard(taken, "instance"), 1);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.json().path("type").asString()).endsWith("dashboard-name-taken");
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND version = 1",
				other.id())).isEqualTo(1);
	}

	/** name length, accepted */
	@ParameterizedTest
	@CsvSource({ "1, true", "200, true", "201, false" })
	void nameLengthAtTheBoundaries(int length, boolean accepted) {
		String name = DashboardTestSupport.repeat(length - 1) + UUID.randomUUID().toString().charAt(0);
		Reply reply = this.support.create(this.kurt, name, "personal");
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(201);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.invalidFields()).containsExactly("name");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "\t\n" })
	void blankNamesAreRejected(String name) {
		Reply reply = this.support.create(this.kurt, name, "personal");
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.invalidFields()).containsExactly("name");
	}

	@ParameterizedTest
	@CsvSource({ "0, true", "1000, true", "1001, false" })
	void descriptionLengthAtTheBoundaries(int length, boolean accepted) {
		var body = DashboardTestSupport.dashboard(unique("Described"), "personal").put("description",
				"d".repeat(length));
		Reply reply = this.support.call(HttpMethod.POST, DashboardTestSupport.DASHBOARDS, this.kurt, body, null);
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(201);
			assertThat(reply.json().path("description").asString()).hasSize(length);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.invalidFields()).containsExactly("description");
		}
	}

	@Test
	void unknownScopeIsRejected() {
		Reply reply = this.support.create(this.kurt, unique("Odd"), "team");
		assertThat(reply.status()).isIn(400, 422);
	}

	@Test
	void changesNeedTheCurrentVersion() {
		Reply shared = this.support.created(this.kurt, unique(SHARED), "instance");
		String path = DashboardTestSupport.DASHBOARDS + "/" + shared.id();
		var body = DashboardTestSupport.dashboard(unique("Versioned"), "instance");
		assertThat(this.support.call(HttpMethod.PUT, path, this.kurt, body, null).status()).isEqualTo(428);
		Reply stale = this.support.write(HttpMethod.PUT, path, this.kurt, body, 2);
		assertThat(stale.status()).isEqualTo(412);
		assertThat(stale.json().path("currentVersion").asInt()).isEqualTo(1);
		assertThat(this.support.call(HttpMethod.PUT, path, this.kurt, body, "1").status()).isEqualTo(412);
		assertThat(this.support.call(HttpMethod.PUT, path, this.kurt, body, "W/\"1\"").status()).isEqualTo(412);
		assertThat(this.support.count("SELECT count(*) FROM dashboards WHERE id = ?::uuid AND version = 1",
				shared.id())).isEqualTo(1);
		// the version rises by one per write
		Reply first = this.support.write(HttpMethod.PUT, path, this.kurt, body, 1);
		assertThat(first.version()).isEqualTo(2);
		Reply second = this.support.write(HttpMethod.PUT, path, this.kurt, body, 2);
		assertThat(second.version()).isEqualTo(3);
		assertThat(this.support.call(HttpMethod.DELETE, path, this.kurt, null, null).status()).isEqualTo(428);
		assertThat(this.support.write(HttpMethod.DELETE, path, this.kurt, null, 1).status()).isEqualTo(412);
	}

	@Test
	void auditRecordsTheChangedFieldsOnly() {
		String name = unique(SHARED);
		Reply shared = this.support.created(this.kurt, name, "instance");
		String path = DashboardTestSupport.DASHBOARDS + "/" + shared.id();
		this.support.write(HttpMethod.PUT, path, this.kurt, DashboardTestSupport.dashboard(unique("Audited"), "instance"),
				1);
		List<String> updates = this.support.auditDetails("dashboard.updated", shared.id());
		assertThat(updates).hasSize(1);
		assertThat(updates.get(0)).contains(shared.id()).contains("\"name\"").contains("instance");
		assertThat(updates.get(0)).doesNotContain("Audited").doesNotContain(name);
		assertThat(this.support.auditDetails("dashboard.created", shared.id()).get(0)).doesNotContain(name);
		this.support.write(HttpMethod.DELETE, path, this.kurt, null, 2);
		assertThat(this.support.audited("dashboard.deleted", shared.id())).isEqualTo(1);
	}

	@Test
	void rejectionsAreNotAudited() {
		Reply shared = this.support.created(this.kurt, unique(SHARED), "instance");
		String path = DashboardTestSupport.DASHBOARDS + "/" + shared.id();
		this.support.write(HttpMethod.PUT, path, this.michael, DashboardTestSupport.dashboard("Denied", "instance"), 1);
		this.support.write(HttpMethod.PUT, path, this.kurt, DashboardTestSupport.dashboard("   ", "instance"), 1);
		this.support.write(HttpMethod.PUT, path, this.kurt, DashboardTestSupport.dashboard("Stale", "instance"), 7);
		assertThat(this.support.audited("dashboard.updated", shared.id())).isZero();
	}

	@Test
	void listIsOrderedByNameIgnoringCase() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		TestSession lister = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		this.support.created(lister, "b-order " + suffix, "personal");
		this.support.created(lister, "A-order " + suffix, "personal");
		this.support.created(lister, "C-order " + suffix, "personal");
		Reply list = this.support.get(DashboardTestSupport.DASHBOARDS, lister);
		List<String> names = new ArrayList<>();
		list.json().forEach((summary) -> names.add(summary.path("name").asString()));
		assertThat(names).containsSubsequence("A-order " + suffix, "b-order " + suffix, "C-order " + suffix);
	}

}
