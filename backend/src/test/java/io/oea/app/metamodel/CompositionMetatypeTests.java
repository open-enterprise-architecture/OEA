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
import org.junit.jupiter.params.ParameterizedTest;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs metamodel/connection-metatypes "Composition mark" and repository/entity-hierarchy
 * "Built-in metatype Structuring aid" (us-135-component-hierarchy D3, D4) on an own database:
 * marking, the packages and the starter package (1.4.0). At most one metatype may be marked, so
 * every test starts without a mark.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompositionMetatypeTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

	private static final String STRUCTURING_AID = "c3f523b8-f07f-5475-9ab0-6f2ea39b537e";

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

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.architect = principals.personWithIdpRole("enterprise-architect");
		this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE is_composition");
	}

	// Composition mark

	@Test
	void markAConnectionMetatypeAsComposition() {
		UUID composedOf = connection("Composed of " + suffix(), false);
		JsonNode before = body(call(HttpMethod.GET, "/metamodel/metatypes/" + composedOf, null, this.admin));
		assertThat(before.path("isComposition").asBoolean()).isFalse();

		MvcTestResult marked = update(composedOf, connectionBody(before.path("name").asString(), true));

		assertThat(marked).as(() -> content(marked)).hasStatus(HttpStatus.OK);
		JsonNode read = body(call(HttpMethod.GET, "/metamodel/metatypes/" + composedOf, null, this.admin));
		assertThat(read.path("isComposition").asBoolean()).isTrue();
		assertThat(read.path("version").asInt()).isEqualTo(before.path("version").asInt() + 1);
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'changedFields' LIKE '%isComposition%' FROM audit.events
				WHERE event_type = 'metamodel.metatype-updated' AND target_id = ?""", Boolean.class,
				composedOf.toString())).isTrue();
	}

	@Test
	void markOnCreation() {
		JsonNode created = created(call(HttpMethod.POST, "/metamodel/metatypes",
				connectionBody("Contains " + suffix(), true), this.admin));

		assertThat(created.path("isComposition").asBoolean()).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'isComposition' FROM audit.events WHERE event_type = 'metamodel.metatype-created'
				AND target_id = ?""", String.class, created.path("id").asString())).isEqualTo("true");
	}

	@Test
	void aSecondMarkIsRejectedAndTheMarkMovesAfterUnmarking() {
		String firstName = "Composition " + suffix();
		UUID first = id(created(call(HttpMethod.POST, "/metamodel/metatypes", connectionBody(firstName, true),
				this.admin)));
		String secondName = "Composed of " + suffix();
		UUID second = connection(secondName, false);

		MvcTestResult rejected = update(second, connectionBody(secondName, true));

		assertInvalid(rejected, "isComposition");
		assertThat(marked()).containsExactly(first);
		assertInvalid(call(HttpMethod.POST, "/metamodel/metatypes", connectionBody("Third " + suffix(), true),
				this.admin), "isComposition");

		assertThat(update(first, connectionBody(firstName, false))).hasStatus(HttpStatus.OK);
		assertThat(update(second, connectionBody(secondName, true))).hasStatus(HttpStatus.OK);
		assertThat(marked()).containsExactly(second);
	}

	@Test
	void anElementMetatypeCannotBeMarked() {
		ObjectNode element = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Element " + suffix())
			.put("configId", CONFIG)
			.put("isComposition", true);

		assertInvalid(call(HttpMethod.POST, "/metamodel/metatypes", element, this.admin), "isComposition");

		UUID existing = created(call(HttpMethod.POST, "/metamodel/metatypes",
				JsonMapper.shared().createObjectNode().put("name", "Plain " + suffix()).put("configId", CONFIG),
				this.admin)).path("id").asString().transform(UUID::fromString);
		element.put("name", name(existing));
		assertInvalid(update(existing, element), "isComposition");
		assertThat(marked()).isEmpty();
	}

	@Test
	void aMetatypeOfASolutionExtensionCannotBeMarked() {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by)
				SELECT gen_random_uuid(), ?, id, id FROM persons ORDER BY id LIMIT 1 RETURNING id""", UUID.class,
				"Solution " + suffix());
		UUID extension = UUID.fromString(created(call(HttpMethod.POST, "/metamodel/configurations",
				JsonMapper.shared()
					.createObjectNode()
					.put("solutionId", solution.toString())
					.put("name", "Extension " + suffix()),
				this.architect)).path("id").asString());
		ObjectNode body = connectionBody("Extension link " + suffix(), true).put("configId", extension.toString());

		assertInvalid(call(HttpMethod.POST, "/metamodel/metatypes", body, this.architect), "isComposition");
		assertThat(marked()).isEmpty();
	}

	// Packages

	@Test
	void aPackageWithOneCompositionMetatypeImportsMarked() {
		ObjectNode pkg = TestPackages.pkg("comp-one", 2);
		ObjectNode link = connectionOf(pkg, 1, true);

		assertThat(importPackage(pkg)).as(() -> "import").hasStatus(HttpStatus.OK);

		assertThat(marked()).containsExactly(UUID.fromString(link.path("id").asString()));
	}

	@Test
	void twoCompositionMetatypesInOnePackageAreRejected() {
		ObjectNode pkg = TestPackages.pkg("comp-two", 3);
		connectionOf(pkg, 1, true);
		connectionOf(pkg, 2, true);

		MvcTestResult result = importPackage(pkg);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/metatypes/2/isComposition");
		assertThat(marked()).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id = 'comp-two'",
				Integer.class)).isZero();
	}

	@Test
	void aCompositionMetatypeOfAPackageMustBeAConnection() {
		ObjectNode pkg = TestPackages.pkg("comp-element", 1);
		((ObjectNode) pkg.withArray("metatypes").get(0)).put("isComposition", true);

		MvcTestResult result = importPackage(pkg);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/metatypes/0/isComposition");
	}

	@Test
	void aPackageCannotMarkWhileAnotherMetatypeIsMarked() {
		created(call(HttpMethod.POST, "/metamodel/metatypes", connectionBody("Mine " + suffix(), true), this.admin));
		ObjectNode pkg = TestPackages.pkg("comp-collide", 2);
		connectionOf(pkg, 1, true);

		MvcTestResult result = importPackage(pkg);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo("/metatypes/1/isComposition");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id = 'comp-collide'",
				Integer.class)).isZero();
	}

	@Test
	void aReimportMovesOrRemovesTheMarkOfItsOwnPackage() {
		ObjectNode first = TestPackages.pkg("comp-move", 3);
		ObjectNode a = connectionOf(first, 1, true);
		ObjectNode b = connectionOf(first, 2, false);
		assertThat(importPackage(first)).hasStatus(HttpStatus.OK);
		assertThat(marked()).containsExactly(UUID.fromString(a.path("id").asString()));

		// the mark moves to the other connection metatype of the package
		ObjectNode second = TestPackages.pkg("comp-move", 3);
		connectionOf(second, 1, false);
		connectionOf(second, 2, true);
		assertThat(importPackage(second)).hasStatus(HttpStatus.OK);
		assertThat(marked()).containsExactly(UUID.fromString(b.path("id").asString()));

		// an unchanged re-import keeps it
		assertThat(importPackage(second)).hasStatus(HttpStatus.OK);
		assertThat(marked()).containsExactly(UUID.fromString(b.path("id").asString()));

		// a package without the mark removes it
		ObjectNode third = TestPackages.pkg("comp-move", 3);
		connectionOf(third, 1, false);
		connectionOf(third, 2, false);
		assertThat(importPackage(third)).hasStatus(HttpStatus.OK);
		assertThat(marked()).isEmpty();
	}

	@Test
	void aReimportThatMarksCountsAsAnUpdate() {
		ObjectNode first = TestPackages.pkg("comp-count", 2);
		connectionOf(first, 1, false);
		assertThat(importPackage(first)).hasStatus(HttpStatus.OK);
		ObjectNode second = TestPackages.pkg("comp-count", 2);
		connectionOf(second, 1, true);

		JsonNode result = body(importPackage(second));

		assertThat(result.path("updated").asInt()).isEqualTo(1);
	}

	// Starter package (1.4.0)

	@Test
	void theStarterPackageBringsTheComposition() {
		MvcTestResult imported = call(HttpMethod.POST,
				"/metamodel/starter-packages/oea-starter-togaf-classic/import", null, this.admin);
		assertThat(imported).as(() -> content(imported)).hasStatus(HttpStatus.OK);

		UUID composition = marked().get(0);
		JsonNode metatype = body(call(HttpMethod.GET, "/metamodel/metatypes/" + composition, null, this.admin));
		assertThat(metatype.path("name").asString()).isEqualTo("Composition");
		assertThat(metatype.path("abbreviation").asString()).isEqualTo("CO");
		assertThat(metatype.path("isConnection").asBoolean()).isTrue();
		assertThat(metatype.path("isComposition").asBoolean()).isTrue();
		assertThat(metatype.path("scope").asString()).isEqualTo("imported");
		assertThat(metatype.path("sourcePackage").path("version").asString()).isEqualTo("1.4.0");
		assertThat(metatype.path("connectionConstraints").size()).as("no endpoint restriction").isZero();
		assertThat(this.jdbc.queryForObject("""
				SELECT value FROM i18n_entries WHERE entity_type = 'metatype' AND entity_id = ? AND locale = 'de'
				AND field = 'name'""", String.class, composition)).isEqualTo("Komposition");
		UUID application = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		UUID technology = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Technology Component'",
				UUID.class);
		JsonNode allowed = body(call(HttpMethod.GET, "/metamodel/allowed-connections?sourceMetatypeId=" + application
				+ "&targetMetatypeId=" + technology, null, this.admin));
		assertThat(allowed.path("options").toString()).contains(composition.toString());
		JsonNode withAid = body(call(HttpMethod.GET, "/metamodel/allowed-connections?sourceMetatypeId="
				+ STRUCTURING_AID + "&targetMetatypeId=" + technology, null, this.admin));
		assertThat(withAid.path("allowed").asBoolean()).isFalse();
	}

	@Test
	void importingTheStarterPackageAgainKeepsTheMark() {
		String path = "/metamodel/starter-packages/oea-starter-togaf-classic/import";
		assertThat(call(HttpMethod.POST, path, null, this.admin)).hasStatus(HttpStatus.OK);
		UUID composition = marked().get(0);

		assertThat(call(HttpMethod.POST, path, null, this.admin)).hasStatus(HttpStatus.OK);

		assertThat(marked()).containsExactly(composition);
	}

	// The built-in metatype Structuring aid

	@Test
	void theStructuringAidIsListedAsBuiltIn() {
		JsonNode list = body(call(HttpMethod.GET, "/metamodel/metatypes", null, this.admin));
		JsonNode aid = null;
		for (JsonNode metatype : list) {
			if (STRUCTURING_AID.equals(metatype.path("id").asString())) {
				aid = metatype;
			}
		}
		assertThat(aid).isNotNull();
		assertThat(aid.path("scope").asString()).isEqualTo("built-in");
		assertThat(aid.path("name").asString()).isEqualTo("Structuring aid");
		assertThat(aid.path("isConnection").asBoolean()).isFalse();
		assertThat(aid.path("propertyMappings").size()).isZero();
	}

	@Test
	void theStructuringAidCannotBeChangedDeletedOrMapped() {
		UUID aid = UUID.fromString(STRUCTURING_AID);
		MvcTestResult renamed = update(aid, JsonMapper.shared().createObjectNode().put("name", "Folder").put("configId", CONFIG));
		assertThat(renamed).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(renamed).path("type").asString()).isEqualTo("urn:oea:problem:read-only-metatype");

		MvcTestResult deleted = call(HttpMethod.DELETE, "/metamodel/metatypes/" + aid, null, this.admin);
		assertThat(deleted).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(deleted).path("type").asString()).isEqualTo("urn:oea:problem:read-only-metatype");

		UUID property = created(call(HttpMethod.POST, "/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", "Note " + suffix()).put("dataType", "string"),
				this.admin)).path("id").asString().transform(UUID::fromString);
		MvcTestResult mapped = call(HttpMethod.POST, "/metamodel/metatypes/" + aid + "/property-mappings",
				JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("necessity",
						"optional"),
				this.admin);
		assertThat(mapped).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(mapped).path("type").asString()).isEqualTo("urn:oea:problem:read-only-metatype");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatype_property_mappings WHERE metatype_id = ?::uuid",
				Integer.class, STRUCTURING_AID)).isZero();
	}

	@Test
	void theStructuringAidIsNeitherABaseTypeNorAnAllowedEndpoint() {
		ObjectNode base = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Child " + suffix())
			.put("configId", CONFIG)
			.put("baseMetatypeId", STRUCTURING_AID);
		assertInvalid(call(HttpMethod.POST, "/metamodel/metatypes", base, this.admin), "baseMetatypeId");

		for (String field : new String[] { "allowedSourceMetatypeIds", "allowedTargetMetatypeIds" }) {
			ObjectNode link = connectionBody("Link " + suffix(), false);
			link.putArray(field).add(STRUCTURING_AID);
			assertInvalid(call(HttpMethod.POST, "/metamodel/metatypes", link, this.admin), field);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "base", "source", "target" })
	void aPackageCannotUseTheStructuringAid(String use) {
		ObjectNode pkg = TestPackages.pkg("comp-aid-" + use, 2);
		ObjectNode type = (ObjectNode) pkg.withArray("metatypes").get(1);
		String pointer;
		if ("base".equals(use)) {
			type.put("baseMetatypeId", STRUCTURING_AID);
			pointer = "/metatypes/1/baseMetatypeId";
		}
		else {
			type.put("isConnection", true).remove("propertyMappings");
			type.putArray("connectionConstraints").addObject().put("direction", use).putObject("allowedMetatype")
				.put("id", STRUCTURING_AID).put("name", "Structuring aid").put("abbreviation", "");
			pointer = "/metatypes/1/connectionConstraints";
		}

		MvcTestResult result = importPackage(pkg);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("pointer").asString()).isEqualTo(pointer);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id = ?", Integer.class,
				"comp-aid-" + use)).isZero();
	}

	// Helpers

	/** Marks the {@code number}-th metatype of the package as a connection metatype; returns it. */
	private static ObjectNode connectionOf(ObjectNode pkg, int index, boolean composition) {
		ObjectNode type = (ObjectNode) pkg.withArray("metatypes").get(index);
		type.put("isConnection", true).remove("propertyMappings");
		if (composition) {
			type.put("isComposition", true);
		}
		return type;
	}

	private MvcTestResult importPackage(ObjectNode pkg) {
		return call(HttpMethod.POST, "/metamodel/import", pkg, this.admin);
	}

	private UUID connection(String name, boolean composition) {
		return UUID.fromString(created(call(HttpMethod.POST, "/metamodel/metatypes", connectionBody(name, composition),
				this.admin)).path("id").asString());
	}

	private static ObjectNode connectionBody(String name, boolean composition) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("configId", CONFIG)
			.put("isConnection", true)
			.put("isComposition", composition);
	}

	private MvcTestResult update(UUID id, ObjectNode body) {
		return call(HttpMethod.PUT, "/metamodel/metatypes/" + id, body, this.admin);
	}

	private java.util.List<UUID> marked() {
		return this.jdbc.queryForList("SELECT id FROM metatypes WHERE is_composition", UUID.class);
	}

	private String name(UUID id) {
		return this.jdbc.queryForObject("SELECT name FROM metatypes WHERE id = ?", String.class, id);
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

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.path("id").asString());
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).contains("\"" + field + "\"");
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
		return JsonMapper.shared().readTree(content(result));
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-composition").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
