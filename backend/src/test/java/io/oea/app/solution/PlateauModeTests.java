package io.oea.app.solution;

import static io.oea.app.solution.PlateausTests.PLATEAUS;
import static io.oea.app.solution.PlateausTests.assertInvalid;
import static io.oea.app.solution.PlateausTests.body;
import static io.oea.app.solution.PlateausTests.created;
import static io.oea.app.solution.PlateausTests.plateau;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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

/**
 * Spec solution/plateaus from an instance without plateaus (US-084 AC1, UC-11 A1, E1; design D2
 * of us-084-plateaus): one test, because the first baseline changes the instance for good.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlateauModeTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = PlateausTests.tempDirectory() + "/setup-token";

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

	@Test
	void firstBaselineTurnsPlateauModeOn() {
		TestSession kurt = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");

		// a target without a baseline (UC-11 E1)
		MvcTestResult target = call(plateau("Target 2027", "target").put("basis", "catalog"), kurt);
		assertInvalid(target, "status");
		assertThat(body(target).path("detail").asString())
			.isEqualTo("No baseline plateau exists. Create an initial baseline first.");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM plateaus", Integer.class)).isZero();

		// the initial baseline (US-084 AC1)
		JsonNode baseline = created(call(plateau("Baseline 2026-Q2", "baseline"), kurt));
		assertThat(baseline.path("status").asString()).isEqualTo("baseline");
		assertThat(baseline.path("succeeds").isNull()).isTrue();
		assertThat(baseline.path("basis").isNull()).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'name' FROM audit.events WHERE event_type = 'plateau.created' AND target_id = ?""",
				String.class, baseline.path("id").asString())).isEqualTo("Baseline 2026-Q2");

		// now a target can follow (US-084 AC2)
		created(call(plateau("Target 2027", "target").put("succeeds", baseline.path("id").asString())
			.put("basis", "catalog"), kurt));
	}

	private MvcTestResult call(JsonNode body, TestSession session) {
		return this.mvc.method(HttpMethod.POST)
			.uri(PLATEAUS)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

}
