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
import java.time.LocalDate;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec metamodel/property-mappings (necessity by effective date, entries taking effect),
 * metamodel/package-import and metamodel/metatype-catalog of us-137-mapping-effective-date, on an
 * own database with the starter package. The scheduled check is called directly; its interval is
 * set far beyond the test run. A date that is reached is simulated by moving the date of the entry
 * to today, because the sessions of the test principals follow the real time.
 */
@SpringBootTest(properties = "oea.metamodel.effective-date-check-interval=PT24H")
@AutoConfigureMockMvc
class MappingEffectiveDateTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

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
	private MappingEffectiveDates effectiveDates;

	private TestSession admin;

	private TestSession architect;

	private UUID component;

	private UUID status;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.architect = principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes WHERE name = 'Application Component'") == 0) {
			assertThat(call(HttpMethod.POST, "/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", null,
					this.admin)).hasStatus(HttpStatus.OK);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		// entries left over by another test take effect now, so each test sees only its own
		this.effectiveDates.applyDueEntries();
	}

	// metamodel/property-mappings: necessity by effective date, entries taking effect

	@Test
	void mandatoryFromADateAndEntitiesMarkedOnTheDate() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		assertThat(map(zone, version, "optional", today().minusDays(30))).hasStatus(HttpStatus.CREATED);
		batch(entity(zone, "Z1"), entity(zone, "Z2"));
		MvcTestResult future = map(zone, version, "mandatory", today().plusDays(1));
		assertThat(future).hasStatus(HttpStatus.CREATED);
		assertThat(body(future).path("markedEntityCount").asInt()).isZero();
		assertThat(body(future).path("state").asString()).isEqualTo("future");
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + version + "'"))
			.isEqualTo(2);
		assertThat(necessity(zone, version)).isEqualTo("optional");
		assertThat(this.effectiveDates.applyDueEntries()).isZero();
		assertThat(marks(version)).isZero();

		reachDate(future);
		assertThat(necessity(zone, version)).isEqualTo("mandatory");
		assertThat(this.effectiveDates.applyDueEntries()).isOne();
		assertThat(marks(version)).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'entitiesMarked' FROM audit.events
				WHERE event_type = 'metamodel.mapping-effective' AND details ->> 'propertyDefId' = ?""", String.class,
				version.toString())).isEqualTo("2");
		// each entry takes effect once
		assertThat(this.effectiveDates.applyDueEntries()).isZero();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.mapping-effective' "
				+ "AND details ->> 'propertyDefId' = '" + version + "'")).isOne();
	}

	@Test
	void sameDateTwice() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		map(zone, version, "optional", today().minusDays(10));
		assertThat(map(zone, version, "mandatory", today().plusDays(5))).hasStatus(HttpStatus.CREATED);
		MvcTestResult again = map(zone, version, "warning", today().plusDays(5));
		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(again)).isEqualTo("urn:oea:problem:conflict");
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE property_def_id = '" + version + "'"))
			.isEqualTo(2);
	}

	@Test
	void historyOfTheEntries() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		map(zone, version, "optional", today().minusDays(20));
		map(zone, version, "warning", today().minusDays(10));
		map(zone, version, "mandatory", today().plusDays(10));
		JsonNode current = body(call(HttpMethod.GET, "/metamodel/metatypes/" + zone + "/property-mappings", null,
				this.architect));
		assertThat(current).hasSize(1);
		assertThat(current.get(0).path("necessity").asString()).isEqualTo("warning");
		assertThat(current.get(0).path("state").asString()).isEqualTo("active");
		JsonNode history = body(call(HttpMethod.GET,
				"/metamodel/metatypes/" + zone + "/property-mappings?includeHistory=true", null, this.architect));
		assertThat(history).extracting((entry) -> entry.path("state").asString())
			.containsExactly("past", "active", "future");
		assertThat(body(call(HttpMethod.GET, "/metamodel/metatypes/" + zone, null, this.architect))
			.path("propertyMappings")).hasSize(1);
	}

	@Test
	void aPastDateTakesEffectAtOnce() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		map(zone, version, "optional", today().minusDays(20));
		batch(entity(zone, "Z1"));
		MvcTestResult past = map(zone, version, "mandatory", today().minusDays(5));
		assertThat(body(past).path("markedEntityCount").asInt()).isOne();
		assertThat(marks(version)).isOne();
		assertThat(this.jdbc.queryForObject(
				"SELECT effective_applied_at IS NOT NULL FROM metatype_property_mappings WHERE id = ?", Boolean.class,
				UUID.fromString(body(past).path("id").asString()))).isTrue();
	}

	@Test
	void overrideOfAPackageMapping() {
		String externalId = "override-" + UUID.randomUUID();
		JsonNode written = batch(entity(this.component, "Without status").put("externalId", externalId));
		int entity = written.path("items").get(0).path("id").asInt();
		MvcTestResult override = map(this.component, this.status, "mandatory", today().minusDays(1));
		assertThat(override).hasStatus(HttpStatus.CREATED);
		assertThat(body(override).path("scope").asString()).isEqualTo("organization");
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE entity_id = " + entity)).isOne();
		assertThat(necessity(this.component, this.status)).isEqualTo("mandatory");
		// the package row is unchanged
		assertThat(count("""
				SELECT count(*) FROM metatype_property_mappings
				WHERE metatype_id = '%s' AND property_def_id = '%s' AND scope = 'imported'"""
			.formatted(this.component, this.status))).isOne();
		removeEntry(this.component, UUID.fromString(body(override).path("id").asString()));
		assertThat(necessity(this.component, this.status)).isEqualTo("optional");
		assertThat(count("SELECT count(*) FROM entity_mandatory_marks WHERE entity_id = " + entity)).isZero();
	}

	@Test
	void anEntryOnASubtypeIsRejected() {
		UUID base = metatype("Base");
		UUID sub = metatypeWithBase("Sub", base);
		UUID version = property("Version");
		map(base, version, "optional", today().minusDays(10));
		MvcTestResult onSub = map(sub, version, "mandatory", today().plusDays(1));
		assertThat(onSub).hasStatus(HttpStatus.CONFLICT);
		assertThat(type(onSub)).isEqualTo("urn:oea:problem:conflict");
	}

	@Test
	void removingOneOfSeveralEntries() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		map(zone, version, "optional", today().minusDays(30));
		batch(entity(zone, "Z1"), entity(zone, "Z2", version, "1.0"));
		MvcTestResult mandatory = map(zone, version, "mandatory", today().minusDays(1));
		assertThat(marks(version)).isOne();
		removeEntry(zone, UUID.fromString(body(mandatory).path("id").asString()));
		assertThat(necessity(zone, version)).isEqualTo("optional");
		assertThat(marks(version)).isZero();
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE property_def_id = '" + version + "'"))
			.isOne();
	}

	@Test
	void marksAreClearedWhenAPropertyIsNoLongerMandatory() {
		UUID zone = metatype("Zone");
		UUID version = property("Version");
		map(zone, version, "optional", today().minusDays(30));
		batch(entity(zone, "Z1"));
		map(zone, version, "mandatory", today().minusDays(10));
		assertThat(marks(version)).isOne();
		MvcTestResult warning = map(zone, version, "warning", today().plusDays(1));
		assertThat(marks(version)).isOne();
		reachDate(warning);
		this.effectiveDates.applyDueEntries();
		assertThat(marks(version)).isZero();
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'marksCleared' FROM audit.events
				WHERE event_type = 'metamodel.mapping-effective' AND details ->> 'propertyDefId' = ?""", String.class,
				version.toString())).isEqualTo("1");
	}

	// metamodel/package-import: organization entries on package mappings

	@Test
	void overrideOfAPackageNecessityKept() {
		String packageId = "override-keep-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID type = typeOfPackage(packageId);
		UUID property = propertyOfPackage(packageId);
		map(type, property, "mandatory", today().minusDays(2));
		importPackage(TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0"));
		assertThat(necessity(type, property)).isEqualTo("mandatory");
		assertThat(count("""
				SELECT count(*) FROM metatype_property_mappings WHERE metatype_id = '%s' AND scope = 'organization'"""
			.formatted(type))).isOne();
	}

	@Test
	void anOrganizationEntryWinsOnASharedDate() {
		String packageId = "shared-date-" + UUID.randomUUID().toString().substring(0, 8);
		ObjectNode pkg = TestPackages.pkg(packageId, 1);
		// the package maps its property from 2026-01-01; a re-import moves the date to tomorrow
		importPackage(pkg);
		UUID type = typeOfPackage(packageId);
		UUID property = propertyOfPackage(packageId);
		LocalDate tomorrow = today().plusDays(1);
		map(type, property, "mandatory", tomorrow);
		ObjectNode update = TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0");
		ObjectNode mapping = (ObjectNode) update.withArray("metatypes").get(0).withArray("propertyMappings").get(0);
		mapping.put("validFrom", tomorrow.toString());
		importPackage(update);
		assertThat(this.jdbc.queryForObject("""
				SELECT scope FROM metatype_property_mappings WHERE metatype_id = ? AND valid_from = ?""", String.class,
				type, java.sql.Date.valueOf(tomorrow))).isEqualTo("organization");
		assertThat(this.jdbc.queryForObject("""
				SELECT details -> 'skippedPackageEntries' ->> 0 FROM audit.events
				WHERE event_type = 'metamodel.package-imported' AND details ->> 'packageId' = ?
				  AND details ->> 'version' = '1.1.0'""", String.class, packageId))
			.isEqualTo(mapping.path("id").asString());
	}

	@Test
	void overridesAreRemovedWithTheSettingRemove() {
		String packageId = "override-remove-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID type = typeOfPackage(packageId);
		UUID property = propertyOfPackage(packageId);
		map(type, property, "mandatory", today().minusDays(2));
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + type + "/reimport-policy",
				JsonMapper.shared().createObjectNode().put("organizationMappings", "remove"), this.admin))
			.hasStatus(HttpStatus.OK);
		importPackage(TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0"));
		assertThat(necessity(type, property)).isEqualTo("optional");
		assertThat(count("""
				SELECT count(*) FROM metatype_property_mappings WHERE metatype_id = '%s' AND scope = 'organization'"""
			.formatted(type))).isZero();
	}

	// helpers

	private static LocalDate today() {
		return LocalDate.now(java.time.ZoneOffset.UTC);
	}

	/** The date of the created entry is reached: it moves to today, still to be applied. */
	private void reachDate(MvcTestResult created) {
		this.jdbc.update("UPDATE metatype_property_mappings SET valid_from = ? WHERE id = ?",
				java.sql.Date.valueOf(today()), UUID.fromString(body(created).path("id").asString()));
	}

	private UUID metatype(String name) {
		return metatypeWithBase(name, null);
	}

	private UUID metatypeWithBase(String name, UUID base) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", unique(name)).put("configId", CONFIG);
		if (base != null) {
			body.put("baseMetatypeId", base.toString());
		}
		return id(created(call(HttpMethod.POST, "/metamodel/metatypes", body, this.admin)));
	}

	private UUID property(String name) {
		return id(created(call(HttpMethod.POST, "/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", unique(name)).put("dataType", "string"), this.admin)));
	}

	private MvcTestResult map(UUID metatype, UUID property, String necessity, LocalDate validFrom) {
		return call(HttpMethod.POST, "/metamodel/metatypes/" + metatype + "/property-mappings",
				JsonMapper.shared()
					.createObjectNode()
					.put("propertyDefId", property.toString())
					.put("necessity", necessity)
					.put("validFrom", validFrom.toString()),
				this.admin);
	}

	private void removeEntry(UUID metatype, UUID entry) {
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + metatype + "/property-mappings/" + entry, null,
				this.admin)).hasStatus(HttpStatus.NO_CONTENT);
	}

	/** The necessity in effect today, as the API returns it. */
	private String necessity(UUID metatype, UUID property) {
		for (JsonNode mapping : body(
				call(HttpMethod.GET, "/metamodel/metatypes/" + metatype + "/property-mappings", null, this.architect))) {
			if (mapping.path("propertyDefinition").path("id").asString().equals(property.toString())) {
				return mapping.path("necessity").asString();
			}
		}
		return null;
	}

	private int marks(UUID property) {
		return count("SELECT count(*) FROM entity_mandatory_marks WHERE property_def_id = '" + property + "'");
	}

	private void importPackage(ObjectNode pkg) {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", pkg, this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private UUID typeOfPackage(String packageId) {
		return this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = ?", UUID.class, packageId + " Type 1");
	}

	private UUID propertyOfPackage(String packageId) {
		return this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = ?", UUID.class,
				packageId + " Status");
	}

	private static ObjectNode entity(UUID metatype, String name) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("metatypeId", metatype.toString());
	}

	private static ObjectNode entity(UUID metatype, String name, UUID property, String value) {
		ObjectNode entity = entity(metatype, name);
		entity.putArray("properties").addObject().put("propertyDefId", property.toString()).put("value", value);
		return entity;
	}

	private JsonNode batch(ObjectNode... entities) {
		ArrayNode items = JsonMapper.shared().createArrayNode();
		for (ObjectNode entity : entities) {
			items.add(entity);
		}
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.set("entities", items);
		MvcTestResult result = call(HttpMethod.POST, "/entities/batch", request, this.architect);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
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
			return JsonMapper.shared().readTree(content(result));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-effective-dates").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
