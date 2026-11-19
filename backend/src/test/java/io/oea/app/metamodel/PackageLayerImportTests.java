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
 * Spec metamodel/architecture-layers, requirement "Layers in packages" (change
 * s17-architecture-layers), on an own database: import, references, limits, conflicts and
 * re-imports of packages with layers, and the starter package 1.4.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PackageLayerImportTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String STARTER = "oea-starter-togaf-classic";

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

	private TestSession viewer;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.viewer = principals.personWithIdpRole("viewer");
		// every test starts without layers; the metatypes of earlier packages stay without a layer
		this.jdbc.update("UPDATE metatypes SET architecture_layer_id = NULL");
		this.jdbc.update("DELETE FROM i18n_entries WHERE entity_type = 'architecture_layer'");
		this.jdbc.update("DELETE FROM architecture_layers");
	}

	@Test
	void importLayersWithTheirLabelsAndReferences() {
		ObjectNode pkg = pkg("layers-import", 2, "Business", "Technology");
		reference(pkg, 0, 0);
		reference(pkg, 1, 1);
		String key = "architecture-layer." + TestPackages.uuid("layers-import", "layer1") + ".name";
		pkg.putObject("i18nBundles").putObject("de").put(key, "Technologie");
		JsonNode result = importPackage(pkg);
		assertThat(result.path("layersCreated").asInt()).isEqualTo(2);
		assertThat(result.path("layersUpdated").asInt()).isZero();
		assertThat(result.path("layersDeleted").asInt()).isZero();
		assertThat(result.path("layerAssignmentsCleared").asInt()).isZero();
		assertThat(result.path("labelCount").asInt()).isOne();

		JsonNode layers = body(get("/metamodel/architecture-layers", this.viewer));
		assertThat(layers.valueStream().map((layer) -> layer.path("name").asString())).containsExactly("Business",
				"Technology");
		assertThat(layers.valueStream().map((layer) -> layer.path("scope").asString())).containsOnly("imported");
		assertThat(layers.get(1).path("sourcePackage").path("packageId").asString()).isEqualTo("layers-import");
		assertThat(layers.get(1).path("sourcePackage").path("version").asString()).isEqualTo("1.0.0");
		assertThat(layers.get(0).path("metatypeCount").asInt()).isOne();
		assertThat(layerOf("layers-import Type 1")).isEqualTo("Business");
		assertThat(layerOf("layers-import Type 2")).isEqualTo("Technology");
		assertThat(body(get("/i18n/de", this.viewer)).path(key).asString()).isEqualTo("Technologie");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'architectureLayersCreated' FROM audit.events
				WHERE event_type = 'metamodel.package-imported' AND details ->> 'packageId' = 'layers-import'""",
				String.class)).isEqualTo("2");
		// no event per layer: the package event carries the counts
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.layer-%'
				AND details ->> 'name' IN ('Business', 'Technology')""", Integer.class)).isZero();
	}

	@Test
	void theOrderOfThePackageIsTheOrderOfTheLayers() {
		ObjectNode pkg = pkg("layers-order", 1, "Third", "First", "Second");
		pkg.withArray("architectureLayers").get(0).withObject("").put("sortOrder", 2);
		pkg.withArray("architectureLayers").get(1).withObject("").put("sortOrder", 0);
		pkg.withArray("architectureLayers").get(2).withObject("").put("sortOrder", 1);
		importPackage(pkg);
		JsonNode layers = body(get("/metamodel/architecture-layers", this.viewer));
		assertThat(layers.valueStream().map((layer) -> layer.path("name").asString())).containsExactly("First",
				"Second", "Third");
		assertThat(layers.valueStream().map((layer) -> layer.path("sortOrder").asInt())).containsExactly(0, 1, 2);
	}

	@Test
	void aReferenceToALayerOutsideThePackageIsRejected() {
		ObjectNode pkg = pkg("layers-outside", 1, "Business");
		((ObjectNode) pkg.path("metatypes").get(0)).put("architectureLayerId", UUID.randomUUID().toString());
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(content(result)).contains("/metatypes/0/architectureLayerId");
		assertNothingStored("layers-outside");
		// the layer of another package is outside as well
		importPackage(pkg("layers-owner", 1, "Business"));
		ObjectNode foreign = pkg("layers-foreign", 1);
		((ObjectNode) foreign.path("metatypes").get(0)).put("architectureLayerId",
				TestPackages.uuid("layers-owner", "layer0"));
		assertThat(post(foreign)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertNothingStored("layers-foreign");
	}

	@ParameterizedTest
	@CsvSource({ "20, 0, 200", "21, 0, 422", "19, 1, 200", "20, 1, 422", "1, 19, 200", "2, 19, 422" })
	void numberOfLayersAtTheLimit(int inPackage, int organization, int status) {
		for (int i = 0; i < organization; i++) {
			assertThat(call(HttpMethod.POST, "/metamodel/architecture-layers",
					JsonMapper.shared().createObjectNode().put("name", "Organization " + i), this.admin))
				.hasStatus(HttpStatus.CREATED);
		}
		String[] names = new String[inPackage];
		for (int i = 0; i < inPackage; i++) {
			names[i] = "Imported " + i;
		}
		String packageId = "layers-limit-" + inPackage + "-" + organization;
		MvcTestResult result = post(pkg(packageId, 1, names));
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(status);
		if (status == 200) {
			assertThat(count("SELECT count(*) FROM architecture_layers WHERE source_package_id = '" + packageId + "'"))
				.isEqualTo(inPackage);
		}
		else {
			assertNothingStored(packageId);
			assertThat(count("SELECT count(*) FROM architecture_layers")).isEqualTo(organization);
		}
	}

	@Test
	void reimportReplacesItsLayersAndKeepsOrganizationLayers() {
		assertThat(call(HttpMethod.POST, "/metamodel/architecture-layers",
				JsonMapper.shared().createObjectNode().put("name", "Strategy"), this.admin)).hasStatus(HttpStatus.CREATED);
		ObjectNode pkg = pkg("layers-reimport", 2, "Business", "Motivation");
		reference(pkg, 0, 0);
		reference(pkg, 1, 1);
		importPackage(pkg);
		// an organization metatype also has the layer that the new version drops
		UUID organizationType = UUID.fromString(this.jdbc.queryForObject("""
				INSERT INTO metatypes (id, config_id, name, scope, architecture_layer_id)
				SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Org Type', 'organization', id
				FROM architecture_layers WHERE name = 'Motivation' RETURNING id::text""", String.class));
		int typeVersion = this.jdbc.queryForObject("SELECT version FROM metatypes WHERE id = ?", Integer.class,
				organizationType);

		ObjectNode next = pkg("layers-reimport", 2, "Business");
		reference(next, 0, 0);
		next.put("packageVersion", "2.0.0");
		JsonNode result = importPackage(next);
		assertThat(result.path("layersCreated").asInt()).isZero();
		assertThat(result.path("layersUpdated").asInt()).isZero();
		assertThat(result.path("layersDeleted").asInt()).isOne();
		assertThat(result.path("layerAssignmentsCleared").asInt()).isEqualTo(2);

		assertThat(layerNames()).containsExactly("Strategy", "Business");
		assertThat(layerOf("layers-reimport Type 1")).isEqualTo("Business");
		assertThat(layerOf("layers-reimport Type 2")).isNull();
		assertThat(layerOf("Org Type")).isNull();
		assertThat(this.jdbc.queryForObject("SELECT version FROM metatypes WHERE id = ?", Integer.class,
				organizationType)).isGreaterThan(typeVersion);
		assertThat(this.jdbc.queryForObject("SELECT source_package_version FROM architecture_layers WHERE name = 'Business'",
				String.class)).isEqualTo("2.0.0");
		assertThat(this.jdbc.queryForObject("SELECT scope FROM architecture_layers WHERE name = 'Strategy'", String.class))
			.isEqualTo("organization");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'layerAssignmentsCleared' FROM audit.events WHERE event_type = 'metamodel.package-imported'
				AND details ->> 'version' = '2.0.0' AND details ->> 'packageId' = 'layers-reimport'""", String.class))
			.isEqualTo("2");
	}

	@Test
	void reimportOfAnUnchangedPackageChangesNoLayer() {
		ObjectNode pkg = pkg("layers-same", 1, "Business", "Data");
		reference(pkg, 0, 0);
		importPackage(pkg);
		int version = this.jdbc.queryForObject("SELECT version FROM architecture_layers WHERE name = 'Data'", Integer.class);
		JsonNode result = importPackage(pkg);
		assertThat(result.path("layersCreated").asInt()).isZero();
		assertThat(result.path("layersUpdated").asInt()).isZero();
		assertThat(result.path("layersDeleted").asInt()).isZero();
		assertThat(this.jdbc.queryForObject("SELECT version FROM architecture_layers WHERE name = 'Data'", Integer.class))
			.isEqualTo(version);
		// a rename and a swap of names in a new version does not collide
		ObjectNode swapped = pkg("layers-same", 1, "Data", "Business");
		reference(swapped, 0, 1);
		JsonNode swap = importPackage(swapped);
		assertThat(swap.path("layersUpdated").asInt()).isEqualTo(2);
		assertThat(layerNames()).containsExactly("Data", "Business");
	}

	@Test
	void aNameOrIdOfAnotherOriginIsAConflict() {
		assertThat(call(HttpMethod.POST, "/metamodel/architecture-layers",
				JsonMapper.shared().createObjectNode().put("name", "Business"), this.admin)).hasStatus(HttpStatus.CREATED);
		MvcTestResult organization = post(pkg("layers-conflict-a", 1, "business"));
		assertThat(organization).as(() -> content(organization)).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(organization).path("type").asString()).isEqualTo("urn:oea:problem:package-conflict");
		JsonNode entry = body(organization).path("conflictList").get(0);
		assertThat(entry.path("element").asString()).isEqualTo("architecture layer business");
		assertThat(entry.path("reason").asString()).isEqualTo("organization");
		assertNothingStored("layers-conflict-a");

		importPackage(pkg("layers-conflict-owner", 1, "Technology"));
		MvcTestResult otherPackage = post(pkg("layers-conflict-b", 1, "TECHNOLOGY"));
		assertThat(otherPackage).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(otherPackage).path("conflictList").get(0).path("reason").asString()).isEqualTo("other-package");
		assertNothingStored("layers-conflict-b");

		// the same ID in another package
		ObjectNode sameId = pkg("layers-conflict-c", 1, "Different Name");
		sameId.withArray("architectureLayers").get(0).withObject("").put("id",
				TestPackages.uuid("layers-conflict-owner", "layer0"));
		MvcTestResult byId = post(sameId);
		assertThat(byId).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(byId).path("conflictList").get(0).path("reason").asString()).isEqualTo("other-package");
		assertNothingStored("layers-conflict-c");
		assertThat(layerNames()).containsExactly("Business", "Technology");
		assertThat(count("SELECT count(*) FROM architecture_layers WHERE source_package_id = 'layers-conflict-owner'"))
			.isOne();
	}

	@Test
	void aLabelOfALayerOutsideThePackageIsRejected() {
		ObjectNode pkg = pkg("layers-label", 1, "Business");
		pkg.putObject("i18nBundles").putObject("de").put("architecture-layer." + UUID.randomUUID() + ".name", "Fremd");
		MvcTestResult result = post(pkg);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertNothingStored("layers-label");
	}

	@ParameterizedTest
	@CsvSource({ "100, 200", "101, 422" })
	void layerNameOfAPackageAtTheLimit(int length, int status) {
		String packageId = "layers-name-" + length;
		MvcTestResult result = post(pkg(packageId, 1, "n".repeat(length)));
		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isEqualTo(status);
		if (status != 200) {
			assertNothingStored(packageId);
		}
	}

	@Test
	void invalidLayersOfAPackage() {
		ObjectNode blank = pkg("layers-blank", 1, " ");
		assertThat(post(blank)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		ObjectNode duplicate = pkg("layers-duplicate", 1, "Business", "BUSINESS");
		assertThat(post(duplicate)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		ObjectNode sameId = pkg("layers-same-id", 1, "Business", "Data");
		sameId.withArray("architectureLayers").get(1).withObject("").put("id", TestPackages.uuid("layers-same-id", "layer0"));
		assertThat(post(sameId)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		ObjectNode negative = pkg("layers-negative", 1, "Business");
		negative.withArray("architectureLayers").get(0).withObject("").put("sortOrder", -1);
		assertThat(post(negative)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		for (String id : List.of("layers-blank", "layers-duplicate", "layers-same-id", "layers-negative")) {
			assertNothingStored(id);
		}
		assertThat(count("SELECT count(*) FROM architecture_layers")).isZero();
	}

	// the starter package 1.4.0

	@Test
	void starterPackageBringsFourLayers() {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/starter-packages/" + STARTER + "/import", null,
				this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("layersCreated").asInt()).isEqualTo(4);
		assertThat(layerNames()).containsExactly("Business", "Data", "Application", "Technology");
		assertThat(body(get("/metamodel/architecture-layers", this.viewer)).valueStream()
			.map((layer) -> layer.path("scope").asString())).containsOnly("imported");
		assertThat(layerOf("Application Component")).isEqualTo("Application");
		assertThat(layerOf("Application Service")).isEqualTo("Application");
		assertThat(layerOf("Data Object")).isEqualTo("Data");
		assertThat(layerOf("Technology Component")).isEqualTo("Technology");
		assertThat(layerOf("Technology Service")).isEqualTo("Technology");
		assertThat(layerOf("System Software")).isEqualTo("Technology");
		assertThat(layerOf("Network Node")).isEqualTo("Technology");
		assertThat(layerOf("Composition")).isNull();
		assertThat(find(body(get("/metamodel/architecture-layers", this.viewer)), "Technology").path("metatypeCount")
			.asInt()).isEqualTo(4);
		// German labels of the layers
		JsonNode german = body(get("/i18n/de", this.viewer));
		for (String name : List.of("Geschäft", "Daten", "Anwendung", "Technologie")) {
			assertThat(german.valueStream().map(JsonNode::asString)).contains(name);
		}
		// importing again changes no layer
		JsonNode again = body(call(HttpMethod.POST, "/admin/metamodel/starter-packages/" + STARTER + "/import", null,
				this.admin));
		assertThat(again.path("layersCreated").asInt()).isZero();
		assertThat(again.path("layersUpdated").asInt()).isZero();
	}

	@Test
	void starterPackageConflictsWithAnOrganizationLayerOfTheSameName() {
		this.jdbc.update("UPDATE metamodel_package_imports SET package_id = package_id");
		assertThat(call(HttpMethod.POST, "/metamodel/architecture-layers",
				JsonMapper.shared().createObjectNode().put("name", "Business"), this.admin)).hasStatus(HttpStatus.CREATED);
		int metatypes = count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + STARTER + "'");
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/starter-packages/" + STARTER + "/import", null,
				this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:package-conflict");
		assertThat(body(result).path("conflictList").get(0).path("element").asString())
			.isEqualTo("architecture layer Business");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + STARTER + "'"))
			.isEqualTo(metatypes);
		assertThat(layerNames()).containsExactly("Business");
	}

	@Test
	void starterPackageKeepsAnOrganizationLayerOnAReimport() {
		assertThat(call(HttpMethod.POST, "/admin/metamodel/starter-packages/" + STARTER + "/import", null, this.admin))
			.hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.POST, "/metamodel/architecture-layers",
				JsonMapper.shared().createObjectNode().put("name", "Strategy"), this.admin)).hasStatus(HttpStatus.CREATED);
		JsonNode strategy = find(body(get("/metamodel/architecture-layers", this.viewer)), "Strategy");
		assertThat(call(HttpMethod.POST, "/admin/metamodel/starter-packages/" + STARTER + "/import", null, this.admin))
			.hasStatus(HttpStatus.OK);
		JsonNode after = find(body(get("/metamodel/architecture-layers", this.viewer)), "Strategy");
		assertThat(after.path("id")).isEqualTo(strategy.path("id"));
		assertThat(after.path("version")).isEqualTo(strategy.path("version"));
		assertThat(after.path("scope").asString()).isEqualTo("organization");
		assertThat(layerNames()).contains("Strategy", "Business", "Data", "Application", "Technology");
	}

	// helpers

	/** A package with {@code metatypes} metatypes and the given layers with the sort order of their position. */
	private static ObjectNode pkg(String packageId, int metatypes, String... layerNames) {
		ObjectNode pkg = TestPackages.pkg(packageId, metatypes);
		ArrayNode layers = pkg.putArray("architectureLayers");
		for (int i = 0; i < layerNames.length; i++) {
			layers.addObject().put("id", TestPackages.uuid(packageId, "layer" + i)).put("name", layerNames[i])
				.put("sortOrder", i);
		}
		return pkg;
	}

	private static void reference(ObjectNode pkg, int metatype, int layer) {
		((ObjectNode) pkg.path("metatypes").get(metatype)).put("architectureLayerId",
				TestPackages.uuid(pkg.path("packageId").asString(), "layer" + layer));
	}

	private String layerOf(String metatype) {
		return this.jdbc.query("""
				SELECT l.name FROM metatypes m LEFT JOIN architecture_layers l ON l.id = m.architecture_layer_id
				WHERE m.name = ?""", (rs, n) -> rs.getString(1), metatype).get(0);
	}

	private List<String> layerNames() {
		return this.jdbc.queryForList("SELECT name FROM architecture_layers ORDER BY sort_order", String.class);
	}

	private void assertNothingStored(String packageId) {
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + packageId + "'")).isZero();
		assertThat(count("SELECT count(*) FROM architecture_layers WHERE source_package_id = '" + packageId + "'"))
			.isZero();
		assertThat(count("SELECT count(*) FROM metamodel_package_imports WHERE package_id = '" + packageId + "'"))
			.isZero();
	}

	private JsonNode importPackage(ObjectNode pkg) {
		MvcTestResult result = post(pkg);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private MvcTestResult post(ObjectNode pkg) {
		return call(HttpMethod.POST, "/admin/metamodel/import", pkg, this.admin);
	}

	private MvcTestResult get(String path, TestSession session) {
		return call(HttpMethod.GET, path, null, session);
	}

	private static JsonNode find(JsonNode list, String name) {
		for (JsonNode node : list) {
			if (name.equals(node.path("name").asString())) {
				return node;
			}
		}
		throw new AssertionError("no element " + name);
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

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
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
			return Files.createTempDirectory("oea-layer-packages").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
