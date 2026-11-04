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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
 * Specs metamodel/labels, metamodel/package-import (labels of a package) and platform/ui-language
 * (language setting) of us-140-dynamic-labels, on an own database with the starter package.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelLabelsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

	private static final String COMPONENT = "metatype.bcf1b833-1942-51c6-9e0b-95fd6a30677a.name";

	private static final String PLANNED = "enum-value.a3474cb3-3251-5979-82c0-1684659d9d39.label";

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

	private TestSession admin;

	private TestSession architect;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = this.principals.personWithIdpRole("metamodel-admin");
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes WHERE name = 'Application Component'") == 0) {
			MvcTestResult result = call(HttpMethod.POST,
					"/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", null, this.admin);
			assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
			assertThat(body(result).path("labelCount").asInt()).isEqualTo(17);
		}
		// labels of the organization left by another test
		this.jdbc.update("DELETE FROM i18n_entries WHERE scope = 'organization'");
	}

	// metamodel/labels: label bundle of a locale

	@Test
	void germanBundleAfterTheStarterImport() {
		UUID own = metatype("Zone");
		JsonNode bundle = body(bundle("de", this.architect, null));
		assertThat(bundle.path(COMPONENT).asString()).isEqualTo("Anwendungskomponente");
		assertThat(bundle.path(PLANNED).asString()).isEqualTo("Geplant");
		assertThat(bundle.path("property-category.54b2a532-1f0e-5c3b-9c19-148cb39e3d0a.name").asString())
			.isEqualTo("Allgemein");
		// without a German label the key carries the name in the corporate language
		assertThat(bundle.path("metatype." + own + ".name").asString()).isEqualTo(name(own));
		// English: package labels of the enum values, names otherwise
		JsonNode english = body(bundle("en", this.architect, null));
		assertThat(english.path(PLANNED).asString()).isEqualTo("Planned");
		assertThat(english.path(COMPONENT).asString()).isEqualTo("Application Component");
	}

	@Test
	void bundleRequiresASessionAndASupportedLocale() {
		assertThat(this.mvc.get().uri("/api/v1/i18n/de").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(bundle("xx", this.architect, null)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// metamodel/labels: caching of the bundle by ETag

	@Test
	void unchangedBundleAnsweredWith304() {
		MvcTestResult first = bundle("de", this.architect, null);
		assertThat(first).hasStatus(HttpStatus.OK);
		String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
		assertThat(etag).startsWith("W/\"");
		assertThat(first.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
			.isEqualTo("max-age=300, stale-while-revalidate=3600");
		MvcTestResult second = bundle("de", this.architect, etag);
		assertThat(second).hasStatus(HttpStatus.NOT_MODIFIED);
		assertThat(content(second)).isEmpty();
		assertThat(second.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(etag);
	}

	@Test
	void bundleAfterAChangeHasANewEtag() {
		UUID own = metatype("Zone");
		String etag = bundle("de", this.architect, null).getResponse().getHeader(HttpHeaders.ETAG);
		String renamed = unique("Renamed Zone");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + own,
				JsonMapper.shared().createObjectNode().put("name", renamed).put("configId", CONFIG), this.admin))
			.hasStatus(HttpStatus.OK);
		MvcTestResult after = bundle("de", this.architect, etag);
		assertThat(after).hasStatus(HttpStatus.OK);
		assertThat(after.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(etag);
		assertThat(body(after).path("metatype." + own + ".name").asString()).isEqualTo(renamed);
		// a removed label changes the ETag as well
		setLabels("de", labels(COMPONENT, "Applikation"));
		String withLabel = bundle("de", this.architect, null).getResponse().getHeader(HttpHeaders.ETAG);
		setLabels("de", labels(COMPONENT, ""));
		assertThat(bundle("de", this.architect, withLabel)).hasStatus(HttpStatus.OK);
	}

	// metamodel/labels: maintaining labels per locale, organization labels over package labels

	@Test
	void organizationLabelWinsAndRemovalRestoresThePackageLabel() {
		MvcTestResult result = setLabels("de", labels(COMPONENT, "  Applikation "));
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		JsonNode listed = entry(body(result), COMPONENT);
		assertThat(listed.path("elementType").asString()).isEqualTo("metatype");
		assertThat(listed.path("name").asString()).isEqualTo("Application Component");
		assertThat(listed.path("packageText").asString()).isEqualTo("Anwendungskomponente");
		assertThat(listed.path("organizationText").asString()).isEqualTo("Applikation");
		assertThat(body(bundle("de", this.architect, null)).path(COMPONENT).asString()).isEqualTo("Applikation");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'labelsSet' FROM audit.events WHERE event_type = 'metamodel.labels-changed'
				    AND details ->> 'locale' = 'de' ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo("1");

		ObjectNode removal = JsonMapper.shared().createObjectNode();
		removal.putObject("labels").putNull(COMPONENT);
		assertThat(call(HttpMethod.PUT, "/metamodel/labels/de", removal, this.admin)).hasStatus(HttpStatus.OK);
		assertThat(body(bundle("de", this.architect, null)).path(COMPONENT).asString())
			.isEqualTo("Anwendungskomponente");
		assertThat(entry(body(call(HttpMethod.GET, "/metamodel/labels/de", null, this.admin)), COMPONENT)
			.path("organizationText")
			.isNull()).isTrue();
	}

	@Test
	void unknownKeysRejectTheWholeChange() {
		String unknown = "metatype." + UUID.randomUUID() + ".name";
		ObjectNode body = labels(COMPONENT, "Applikation");
		body.withObjectProperty("labels").put(unknown, "Nichts").put("metatype.ac.name", "Kurz");
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/labels/de", body, this.admin);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(type(result)).isEqualTo("urn:oea:problem:validation-failed");
		assertThat(body(result).path("invalidFields").toString()).contains(unknown).contains("metatype.ac.name");
		assertThat(count("SELECT count(*) FROM i18n_entries WHERE scope = 'organization'")).isZero();
		assertThat(setLabels("de", labels(COMPONENT, "x".repeat(201)))).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(setLabels("xx", labels(COMPONENT, "Applikation"))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void settingLabelsNeedsMetamodelWrite() {
		assertThat(call(HttpMethod.PUT, "/metamodel/labels/de", labels(COMPONENT, "Applikation"), this.architect))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.GET, "/metamodel/labels/de", null, this.architect)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM i18n_entries WHERE scope = 'organization'")).isZero();
	}

	@Test
	void labelsOfDeletedElementsAreRemoved() {
		UUID zone = metatype("Zone");
		UUID trust = id(created(call(HttpMethod.POST, "/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", unique("Trust")).put("dataType", "enum"), this.admin)));
		UUID high = id(created(call(HttpMethod.POST, "/metamodel/property-definitions/" + trust + "/enum-values",
				JsonMapper.shared().createObjectNode().put("value", "high"), this.admin)));
		UUID low = id(created(call(HttpMethod.POST, "/metamodel/property-definitions/" + trust + "/enum-values",
				JsonMapper.shared().createObjectNode().put("value", "low"), this.admin)));
		UUID category = id(created(call(HttpMethod.POST, "/metamodel/property-categories",
				JsonMapper.shared().createObjectNode().put("name", unique("Security")), this.admin)));
		ObjectNode body = labels("metatype." + zone + ".name", "Zone DE");
		body.withObjectProperty("labels")
			.put("property-def." + trust + ".label", "Vertrauen")
			.put("enum-value." + high + ".label", "Hoch")
			.put("enum-value." + low + ".label", "Niedrig")
			.put("property-category." + category + ".name", "Sicherheit");
		assertThat(setLabels("de", body)).hasStatus(HttpStatus.OK);
		assertThat(labelRows(high, low, trust, zone, category)).isEqualTo(5);

		call(HttpMethod.DELETE, "/metamodel/property-definitions/" + trust + "/enum-values/" + high, null, this.admin);
		assertThat(labelRows(high)).isZero();
		call(HttpMethod.DELETE, "/metamodel/property-definitions/" + trust, null, this.admin);
		assertThat(labelRows(trust, low)).isZero();
		call(HttpMethod.DELETE, "/metamodel/property-categories/" + category, null, this.admin);
		assertThat(labelRows(category)).isZero();
		call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone, null, this.admin);
		assertThat(labelRows(zone)).isZero();
	}

	// metamodel/package-import: labels of a package

	@Test
	void reimportReplacesPackageLabelsAndKeepsOrganizationLabels() {
		String packageId = "labels-" + UUID.randomUUID().toString().substring(0, 8);
		ObjectNode pkg = TestPackages.pkg(packageId, 2);
		String type1 = pkg.path("metatypes").get(0).path("id").asString();
		String type2 = pkg.path("metatypes").get(1).path("id").asString();
		pkg.putObject("i18nBundles").putObject("de").put("metatype." + type1 + ".name", "Typ eins")
			.put("metatype." + type2 + ".name", "Typ zwei");
		JsonNode imported = importPackage(pkg);
		assertThat(imported.path("labelCount").asInt()).isEqualTo(2);
		setLabels("de", labels("metatype." + type2 + ".name", "Eigener Typ"));

		pkg.withObjectProperty("i18nBundles").putObject("de").put("metatype." + type1 + ".name", "Typ 1");
		assertThat(importPackage(pkg).path("labelCount").asInt()).isOne();
		JsonNode bundle = body(bundle("de", this.architect, null));
		assertThat(bundle.path("metatype." + type1 + ".name").asString()).isEqualTo("Typ 1");
		assertThat(bundle.path("metatype." + type2 + ".name").asString()).isEqualTo("Eigener Typ");
		assertThat(count("SELECT count(*) FROM i18n_entries WHERE package_id = '" + packageId + "'")).isOne();
	}

	@Test
	void labelOfAForeignElementOrLocaleRejectsTheImport() {
		ObjectNode foreign = TestPackages.pkg("labels-foreign", 1);
		foreign.putObject("i18nBundles").putObject("de").put(COMPONENT, "Fremd");
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", foreign, this.admin);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(content(result)).contains(COMPONENT);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'labels-foreign'")).isZero();

		ObjectNode locale = TestPackages.pkg("labels-locale", 1);
		String type = locale.path("metatypes").get(0).path("id").asString();
		locale.putObject("i18nBundles").putObject("es").put("metatype." + type + ".name", "Tipo");
		assertThat(call(HttpMethod.POST, "/admin/metamodel/import", locale, this.admin))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'labels-locale'")).isZero();
	}

	// platform/ui-language: language setting of a person

	@Test
	void languageSettingOfAPerson() {
		TestSession person = this.principals.personWithIdpRole("enterprise-architect");
		assertThat(body(call(HttpMethod.GET, "/me", null, person)).path("uiLanguage").isNull()).isTrue();
		assertThat(call(HttpMethod.PUT, "/me/preferences",
				JsonMapper.shared().createObjectNode().put("uiLanguage", "de"), person))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(body(call(HttpMethod.GET, "/me", null, person)).path("uiLanguage").asString()).isEqualTo("de");
		assertThat(call(HttpMethod.PUT, "/me/preferences",
				JsonMapper.shared().createObjectNode().put("uiLanguage", "es"), person))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(call(HttpMethod.PUT, "/me/preferences", JsonMapper.shared().createObjectNode().putNull("uiLanguage"),
				person)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(body(call(HttpMethod.GET, "/me", null, person)).path("uiLanguage").isNull()).isTrue();
		// the system admin account is no person
		assertThat(call(HttpMethod.PUT, "/me/preferences",
				JsonMapper.shared().createObjectNode().put("uiLanguage", "de"), this.principals.systemAdmin()))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult bundle(String locale, TestSession session, String ifNoneMatch) {
		var request = this.mvc.get()
			.uri("/api/v1/i18n/" + locale)
			.cookie(new Cookie(SessionCookie.NAME, session.token()));
		if (ifNoneMatch != null) {
			request = request.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
		}
		return request.exchange();
	}

	private MvcTestResult setLabels(String locale, ObjectNode body) {
		return call(HttpMethod.PUT, "/metamodel/labels/" + locale, body, this.admin);
	}

	private static ObjectNode labels(String key, String text) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putObject("labels").put(key, text);
		return body;
	}

	private static JsonNode entry(JsonNode labels, String key) {
		for (JsonNode label : labels) {
			if (key.equals(label.path("key").asString())) {
				return label;
			}
		}
		throw new AssertionError("no label " + key);
	}

	private int labelRows(UUID... ids) {
		int rows = 0;
		for (UUID id : ids) {
			rows += count("SELECT count(*) FROM i18n_entries WHERE entity_id = '" + id + "'");
		}
		return rows;
	}

	private JsonNode importPackage(ObjectNode pkg) {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", pkg, this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private UUID metatype(String name) {
		return id(created(call(HttpMethod.POST, "/metamodel/metatypes",
				JsonMapper.shared().createObjectNode().put("name", unique(name)).put("configId", CONFIG), this.admin)));
	}

	private String name(UUID metatype) {
		return this.jdbc.queryForObject("SELECT name FROM metatypes WHERE id = ?", String.class, metatype);
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
			return Files.createTempDirectory("oea-labels").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
