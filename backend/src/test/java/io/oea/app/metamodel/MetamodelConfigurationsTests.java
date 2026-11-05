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
 * Specs metamodel/solution-extensions and metamodel/lock-mode of
 * us-037-solution-extensions-and-lock-mode, on an own database with the starter package.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelConfigurationsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String INSTANCE = PackageImporter.INSTANCE_CONFIGURATION.toString();

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

	private UUID component;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.architect = principals.personWithIdpRole("enterprise-architect");
		this.viewer = principals.personWithIdpRole("viewer");
		if (count("SELECT count(*) FROM metatypes WHERE name = 'Application Component'") == 0) {
			assertThat(call(HttpMethod.POST, "/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", null,
					this.admin)).hasStatus(HttpStatus.OK);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
	}

	/** Every test starts and ends with an unlocked instance. */
	@AfterEach
	void unlock() {
		this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'gui-and-import' WHERE id = ?::uuid", INSTANCE);
	}

	// metamodel/solution-extensions – Extension configurations

	@Test
	void extensionForCloudMigration2027() {
		UUID solution = UUID.randomUUID();
		JsonNode created = created(call(HttpMethod.POST, "/metamodel/configurations",
				extension(solution, unique("Cloud Migration 2027")).put("editMode", "gui-and-import"), this.architect));
		UUID id = id(created);
		JsonNode listed = find(body(call(HttpMethod.GET, "/metamodel/configurations", null, this.viewer)), id);
		assertThat(listed.path("scope").asString()).isEqualTo("solution");
		assertThat(listed.path("parentId").asString()).isEqualTo(INSTANCE);
		assertThat(listed.path("solutionId").asString()).isEqualTo(solution.toString());
		assertThat(listed.path("editMode").asString()).isEqualTo("gui-and-import");
		assertThat(listed.path("metatypeCount").asInt()).isZero();
		JsonNode first = body(call(HttpMethod.GET, "/metamodel/configurations", null, this.viewer)).get(0);
		assertThat(first.path("id").asString()).isEqualTo(INSTANCE);
		assertThat(first.path("scope").asString()).isEqualTo("instance");
		assertThat(first.path("name").asString()).isEqualTo("Instance metamodel");
		assertThat(first.path("parentId").isNull()).isTrue();
		assertThat(audit("metamodel.configuration-created", id)).isOne();
	}

	@Test
	void creatingAnExtensionWithoutPermission() {
		int before = count("SELECT count(*) FROM metamodel_configurations");
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/configurations",
				extension(UUID.randomUUID(), unique("Viewer")), this.viewer);
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM metamodel_configurations")).isEqualTo(before);
	}

	@Test
	void extensionNamesAreUnique() {
		String name = unique("Twice");
		created(call(HttpMethod.POST, "/metamodel/configurations", extension(UUID.randomUUID(), name), this.architect));
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/configurations",
				extension(UUID.randomUUID(), name.toUpperCase()), this.architect);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
		MvcTestResult instance = call(HttpMethod.POST, "/metamodel/configurations",
				extension(UUID.randomUUID(), "instance METAMODEL"), this.architect);
		assertThat(type(instance)).isEqualTo("urn:oea:problem:name-taken");
	}

	@Test
	void deletingExtensions() {
		UUID empty = newExtension();
		assertThat(call(HttpMethod.DELETE, "/metamodel/configurations/" + empty, null, this.architect))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM metamodel_configurations WHERE id = '" + empty + "'")).isZero();
		assertThat(audit("metamodel.configuration-deleted", empty)).isOne();

		UUID used = newExtension();
		created(createMetatype(unique("Used"), used, null, this.architect));
		MvcTestResult inUse = call(HttpMethod.DELETE, "/metamodel/configurations/" + used, null, this.architect);
		assertThat(inUse).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(inUse)).isEqualTo("urn:oea:problem:in-use");

		MvcTestResult instance = call(HttpMethod.DELETE, "/metamodel/configurations/" + INSTANCE, null, this.admin);
		assertThat(instance).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(instance)).isEqualTo("urn:oea:problem:read-only-metatype");
		assertThat(call(HttpMethod.DELETE, "/metamodel/configurations/" + UUID.randomUUID(), null, this.architect))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void renamingWithAStaleVersion() {
		UUID extension = newExtension();
		String name = unique("Renamed");
		MvcTestResult renamed = call(HttpMethod.PUT, "/metamodel/configurations/" + extension, change(name, "gui-and-import"),
				this.architect, "\"1\"");
		assertThat(renamed).hasStatus(HttpStatus.OK);
		assertThat(renamed.getResponse().getHeader("ETag")).isEqualTo("\"2\"");
		assertThat(body(renamed).path("name").asString()).isEqualTo(name);
		MvcTestResult stale = call(HttpMethod.PUT, "/metamodel/configurations/" + extension,
				change(unique("Stale"), "gui-and-import"), this.architect, "\"1\"");
		assertThat(stale).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(body(stale).path("currentVersion").asInt()).isEqualTo(2);
		assertThat(audit("metamodel.configuration-updated", extension)).isOne();
		assertThat(audit("metamodel.edit-mode-changed", extension)).isZero();
	}

	// metamodel/solution-extensions – Metatypes of an extension

	@Test
	void cloudServiceOnlyInTheExtension() {
		UUID extension = newExtension();
		String name = unique("CloudService");
		JsonNode created = created(createMetatype(name, extension, null, this.architect));
		assertThat(created.path("configId").asString()).isEqualTo(extension.toString());
		assertThat(names(body(call(HttpMethod.GET, "/metamodel/metatypes", null, this.viewer)))).doesNotContain(name)
			.contains("Application Component");
		assertThat(names(body(call(HttpMethod.GET, "/metamodel/metatypes?configId=" + INSTANCE, null, this.viewer))))
			.doesNotContain(name);
		MvcTestResult single = call(HttpMethod.GET, "/metamodel/metatypes/" + id(created), null, this.viewer);
		assertThat(body(single).path("configId").asString()).isEqualTo(extension.toString());
		assertThat(audit("metamodel.metatype-created", id(created))).isOne();
	}

	@Test
	void bothGroupsInTheEffectiveMetamodel() {
		UUID extension = newExtension();
		String name = unique("CloudService");
		created(createMetatype(name, extension, this.component, this.architect));
		JsonNode effective = body(call(HttpMethod.GET, "/metamodel/metatypes?configId=" + extension, null, this.viewer));
		JsonNode component = byName(effective, "Application Component");
		JsonNode cloud = byName(effective, name);
		assertThat(component.path("configId").asString()).isEqualTo(INSTANCE);
		assertThat(cloud.path("configId").asString()).isEqualTo(extension.toString());
		// the extension type inherits the mappings of its instance base type
		assertThat(cloud.path("propertyMappings").size()).isEqualTo(component.path("propertyMappings").size());
		assertThat(call(HttpMethod.GET, "/metamodel/metatypes?configId=" + UUID.randomUUID(), null, this.viewer))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void instanceTypeRedefined() {
		UUID extension = newExtension();
		int before = count("SELECT count(*) FROM metatypes");
		MvcTestResult result = createMetatype("application COMPONENT", extension, null, this.architect);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:name-taken");
		assertThat(count("SELECT count(*) FROM metatypes")).isEqualTo(before);
	}

	@Test
	void namesAcrossConfigurations() {
		UUID first = newExtension();
		UUID second = newExtension();
		String name = unique("Trial");
		created(createMetatype(name, first, null, this.architect));
		// twice in the same extension
		assertThat(type(createMetatype(name, first, null, this.architect))).isEqualTo("urn:oea:problem:name-taken");
		// another extension may try out the same name
		created(createMetatype(name, second, null, this.architect));
		// an instance metatype never takes a name of an extension
		MvcTestResult instance = createMetatype(name, PackageImporter.INSTANCE_CONFIGURATION, null, this.admin);
		assertThat(instance).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(instance)).isEqualTo("urn:oea:problem:name-taken");
	}

	@Test
	void aPackageCannotTakeANameOfAnExtension() {
		UUID extension = newExtension();
		String packageId = "test-extension-" + UUID.randomUUID().toString().substring(0, 8);
		created(createMetatype(packageId + " Type 1", extension, null, this.architect));
		MvcTestResult imported = call(HttpMethod.POST, "/admin/metamodel/import", TestPackages.pkg(packageId, 1),
				this.admin);
		assertThat(imported).as(() -> content(imported)).hasStatus(HttpStatus.CONFLICT);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + packageId + "'")).isZero();
	}

	@Test
	void baseTypesAcrossTheBoundary() {
		UUID first = newExtension();
		UUID second = newExtension();
		UUID trial = id(created(createMetatype(unique("Trial"), first, this.component, this.architect)));
		created(createMetatype(unique("Sub"), first, trial, this.architect));
		MvcTestResult other = createMetatype(unique("Other"), second, trial, this.architect);
		assertThat(other).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(other).path("invalidFields").get(0).asString()).isEqualTo("baseMetatypeId");
		MvcTestResult instance = createMetatype(unique("Instance"), PackageImporter.INSTANCE_CONFIGURATION, trial,
				this.admin);
		assertThat(instance).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(instance).path("invalidFields").get(0).asString()).isEqualTo("baseMetatypeId");
	}

	@Test
	void connectionEndpointsAcrossTheBoundary() {
		UUID first = newExtension();
		UUID second = newExtension();
		UUID trial = id(created(createMetatype(unique("Trial"), first, null, this.architect)));
		ObjectNode allowed = metatype(unique("Runs on"), first, null).put("isConnection", true);
		allowed.putArray("allowedSourceMetatypeIds").add(trial.toString());
		allowed.putArray("allowedTargetMetatypeIds").add(this.component.toString());
		created(call(HttpMethod.POST, "/metamodel/metatypes", allowed, this.architect));
		ObjectNode foreign = metatype(unique("Foreign"), second, null).put("isConnection", true);
		foreign.putArray("allowedSourceMetatypeIds").add(trial.toString());
		MvcTestResult result = call(HttpMethod.POST, "/metamodel/metatypes", foreign, this.architect);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("allowedSourceMetatypeIds");
	}

	@Test
	void aMetatypeKeepsItsConfiguration() {
		UUID extension = newExtension();
		String name = unique("Fixed");
		UUID trial = id(created(createMetatype(name, extension, null, this.architect)));
		MvcTestResult moved = call(HttpMethod.PUT, "/metamodel/metatypes/" + trial,
				metatype(name, PackageImporter.INSTANCE_CONFIGURATION, null), this.architect);
		assertThat(moved).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(moved).path("invalidFields").get(0).asString()).isEqualTo("configId");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + trial, metatype(name + " 2", extension, null),
				this.architect)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + trial, null, this.architect))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void unknownConfigurationOnCreation() {
		MvcTestResult result = createMetatype(unique("Nowhere"), UUID.randomUUID(), null, this.architect);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("configId");
	}

	@Test
	void architectsMaintainOnlyExtensions() {
		UUID extension = newExtension();
		UUID trial = id(created(createMetatype(unique("Mapped"), extension, null, this.architect)));
		// mappings map organization property definitions, maintained in the instance
		UUID property = id(created(call(HttpMethod.POST, "/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", unique("Hosting")).put("dataType", "string"),
				this.admin)));
		MvcTestResult mapped = call(HttpMethod.POST, "/metamodel/metatypes/" + trial + "/property-mappings",
				JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("necessity",
						"optional"),
				this.architect);
		assertThat(mapped).as(() -> content(mapped)).hasStatus(HttpStatus.CREATED);
		assertThat(call(HttpMethod.DELETE,
				"/metamodel/metatypes/" + trial + "/property-mappings/" + body(mapped).path("id").asString(), null,
				this.architect)).hasStatus(HttpStatus.NO_CONTENT);

		// the instance metamodel needs metamodel:write
		MvcTestResult instance = createMetatype(unique("Instance"), PackageImporter.INSTANCE_CONFIGURATION, null,
				this.architect);
		assertThat(instance).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(type(instance)).isEqualTo("urn:oea:problem:forbidden");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + this.component,
				metatype("Application Component", PackageImporter.INSTANCE_CONFIGURATION, null), this.architect))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.POST, "/metamodel/metatypes/" + this.component + "/property-mappings",
				JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("necessity",
						"optional"),
				this.architect)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.PUT, "/metamodel/configurations/" + INSTANCE, change("Instance metamodel", "import-only"),
				this.architect)).hasStatus(HttpStatus.FORBIDDEN);
		// metamodel administrators maintain extensions too
		created(createMetatype(unique("By admin"), extension, null, this.admin));
	}

	@Test
	void twoArchitectsMaintainOneExtension() {
		UUID extension = newExtension();
		TestSession other = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		UUID first = id(created(createMetatype(unique("First"), extension, null, this.architect)));
		UUID second = id(created(createMetatype(unique("Second"), extension, null, other)));
		assertThat(this.jdbc.queryForObject("""
				SELECT count(DISTINCT actor_person_id) FROM audit.events
				WHERE event_type = 'metamodel.metatype-created' AND target_id IN (?, ?)""", Integer.class,
				first.toString(), second.toString())).isEqualTo(2);
		assertThat(find(body(call(HttpMethod.GET, "/metamodel/configurations", null, this.viewer)), extension)
			.path("metatypeCount")
			.asInt()).isEqualTo(2);
	}

	@Test
	void entityOfAnExtensionMetatype() {
		UUID extension = newExtension();
		UUID cloud = id(created(createMetatype(unique("CloudService"), extension, null, this.architect)));
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.putArray("entities").addObject().put("name", "AWS Lambda").put("metatypeId", cloud.toString());
		MvcTestResult result = call(HttpMethod.POST, "/entities/batch", request, this.architect);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("items").get(0).path("errors").get(0).path("code").asString())
			.isEqualTo("METATYPE_NOT_FOUND");
	}

	// metamodel/lock-mode

	@Test
	void instanceLocked() {
		MvcTestResult locked = call(HttpMethod.PUT, "/metamodel/configurations/" + INSTANCE,
				change("Instance metamodel", "import-only"), this.admin);
		assertThat(locked).as(() -> content(locked)).hasStatus(HttpStatus.OK);
		assertThat(find(body(call(HttpMethod.GET, "/metamodel/configurations", null, this.viewer)),
				PackageImporter.INSTANCE_CONFIGURATION)
			.path("editMode")
			.asString()).isEqualTo("import-only");
		Long actor = this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'metamodel.edit-mode-changed'
				    AND target_id = ? ORDER BY id DESC LIMIT 1""", Long.class, INSTANCE);
		assertThat(actor).isEqualTo(this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.admin.sessionId()));
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'after' FROM audit.events WHERE event_type = 'metamodel.edit-mode-changed'
				    AND target_id = ? ORDER BY id DESC LIMIT 1""", String.class, INSTANCE)).isEqualTo("import-only");
	}

	@Test
	void writesRejectedInALockedInstance() {
		lockInstance();
		int before = count("SELECT count(*) FROM metatypes");
		UUID category = this.jdbc.queryForObject("SELECT id FROM property_categories LIMIT 1", UUID.class);
		UUID property = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		List<MvcTestResult> rejected = new ArrayList<>();
		rejected.add(createMetatype(unique("Locked"), PackageImporter.INSTANCE_CONFIGURATION, null, this.admin));
		rejected.add(call(HttpMethod.PUT, "/metamodel/metatypes/" + this.component,
				metatype("Application Component", PackageImporter.INSTANCE_CONFIGURATION, null), this.admin));
		rejected.add(call(HttpMethod.DELETE, "/metamodel/metatypes/" + this.component, null, this.admin));
		rejected.add(call(HttpMethod.POST, "/metamodel/metatypes/" + this.component + "/property-mappings",
				JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("necessity",
						"optional"),
				this.admin));
		rejected.add(call(HttpMethod.POST, "/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", unique("Locked")).put("dataType", "string"),
				this.admin));
		rejected.add(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + property, null, this.admin));
		rejected.add(call(HttpMethod.POST, "/metamodel/property-definitions/" + property + "/enum-values",
				JsonMapper.shared().createObjectNode().put("value", "Locked"), this.admin));
		rejected.add(call(HttpMethod.POST, "/metamodel/property-categories",
				JsonMapper.shared().createObjectNode().put("name", unique("Locked")), this.admin));
		rejected.add(call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				JsonMapper.shared().createObjectNode().put("name", unique("Locked")), this.admin));
		rejected.add(call(HttpMethod.DELETE, "/metamodel/property-categories/" + category, null, this.admin));
		ObjectNode labels = JsonMapper.shared().createObjectNode();
		labels.putObject("labels");
		rejected.add(call(HttpMethod.PUT, "/metamodel/labels/de", labels, this.admin));
		for (MvcTestResult result : rejected) {
			assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(type(result)).isEqualTo("urn:oea:problem:metamodel-locked");
		}
		assertThat(count("SELECT count(*) FROM metatypes")).isEqualTo(before);
	}

	@Test
	void whatStaysPossibleInALockedInstance() {
		lockInstance();
		String packageId = "test-locked-" + UUID.randomUUID().toString().substring(0, 8);
		MvcTestResult imported = call(HttpMethod.POST, "/admin/metamodel/import", TestPackages.pkg(packageId, 1),
				this.admin);
		assertThat(imported).as(() -> content(imported)).hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + this.component + "/reimport-policy",
				JsonMapper.shared().createObjectNode().put("organizationMappings", "keep"), this.admin))
			.hasStatus(HttpStatus.OK);
		assertThat(call(HttpMethod.PUT, "/metamodel/settings/reimport-policy",
				JsonMapper.shared().createObjectNode().put("organizationMappings", "keep"), this.admin))
			.hasStatus(HttpStatus.OK);
		// an extension in gui-and-import stays editable
		UUID extension = newExtension();
		created(createMetatype(unique("Still editable"), extension, null, this.architect));
		// unlocking
		assertThat(call(HttpMethod.PUT, "/metamodel/configurations/" + INSTANCE,
				change("Instance metamodel", "gui-and-import"), this.admin)).hasStatus(HttpStatus.OK);
		created(createMetatype(unique("Unlocked"), PackageImporter.INSTANCE_CONFIGURATION, null, this.admin));
	}

	@Test
	void lockedExtension() {
		UUID extension = id(created(call(HttpMethod.POST, "/metamodel/configurations",
				extension(UUID.randomUUID(), unique("Locked")).put("editMode", "import-only"), this.architect)));
		MvcTestResult result = createMetatype(unique("Locked"), extension, null, this.architect);
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(type(result)).isEqualTo("urn:oea:problem:metamodel-locked");
		// the instance stays editable
		created(createMetatype(unique("Instance"), PackageImporter.INSTANCE_CONFIGURATION, null, this.admin));
	}

	// helpers

	private void lockInstance() {
		assertThat(call(HttpMethod.PUT, "/metamodel/configurations/" + INSTANCE,
				change("Instance metamodel", "import-only"), this.admin)).hasStatus(HttpStatus.OK);
	}

	private UUID newExtension() {
		return id(created(call(HttpMethod.POST, "/metamodel/configurations",
				extension(UUID.randomUUID(), unique("Extension")), this.architect)));
	}

	private static ObjectNode extension(UUID solution, String name) {
		return JsonMapper.shared().createObjectNode().put("solutionId", solution.toString()).put("name", name);
	}

	private static ObjectNode change(String name, String editMode) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("editMode", editMode);
	}

	private MvcTestResult createMetatype(String name, UUID config, UUID base, TestSession session) {
		return call(HttpMethod.POST, "/metamodel/metatypes", metatype(name, config, base), session);
	}

	private static ObjectNode metatype(String name, UUID config, UUID base) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("configId", config.toString());
		if (base != null) {
			body.put("baseMetatypeId", base.toString());
		}
		return body;
	}

	private static JsonNode find(JsonNode list, UUID id) {
		for (JsonNode node : list) {
			if (node.path("id").asString().equals(id.toString())) {
				return node;
			}
		}
		throw new AssertionError("no " + id + " in " + list);
	}

	private static JsonNode byName(JsonNode list, String name) {
		for (JsonNode node : list) {
			if (node.path("name").asString().equals(name)) {
				return node;
			}
		}
		throw new AssertionError("no " + name + " in the list");
	}

	private static List<String> names(JsonNode list) {
		List<String> names = new ArrayList<>();
		list.forEach((node) -> names.add(node.path("name").asString()));
		return names;
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
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private int audit(String eventType, UUID target) {
		return count("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
				+ target + "'");
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
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-metamodel-configurations").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
