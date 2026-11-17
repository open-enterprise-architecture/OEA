package io.oea.performance;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-039 Definition of Done, realigned in us-039-solution-baseline: a page of the baseline with a
 * search term over 10,000 entities answers within 500 ms (median of 5 requests after a warm-up).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionLandscapeLatencyIT {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

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

	private TestSession michael;

	private long michaelId;

	private String solution;

	private UUID application;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		// Every test starts from an empty landscape.
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE deleted_at IS NULL",
				this.michaelId);
		this.application = metatype("Application", false);
		this.solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, "Baseline " + UUID.randomUUID(), this.michaelId,
				this.michaelId);
	}

	// Performance (US-039 Definition of Done, realigned)

	@Test
	void pageOverTenThousandEntitiesWithin500Milliseconds() {
		this.jdbc.update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT ?, 'System ' || g, ? FROM generate_series(1, 10000) g""", this.application, this.michaelId);
		String query = "&q=system%2099&pageSize=50";
		assertThat(ok(landscape(query)).path("page").path("totalElements").asInt()).isEqualTo(111);
		List<Long> millis = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			long started = System.nanoTime();
			ok(landscape(query));
			millis.add((System.nanoTime() - started) / 1_000_000);
		}
		Collections.sort(millis);
		assertThat(millis.get(2)).as("median of %s ms", millis).isLessThan(500);
	}

	// Performance of the basis of a plateau (design D4 of us-084-plateaus)

	@Test
	void basisOfAPredecessorOverFiftyThousandEntitiesWithin500Milliseconds() {
		this.jdbc.update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT ?, 'System ' || g, ? FROM generate_series(1, 50000) g""", this.application, this.michaelId);
		this.jdbc.update("""
				INSERT INTO plateaus (id, name, status, created_by) VALUES (gen_random_uuid(), 'Baseline', 'baseline', ?)
				ON CONFLICT DO NOTHING""", this.michaelId);
		String first = plateau("Target 2027", "(SELECT id FROM plateaus WHERE status = 'baseline')", "catalog");
		String second = plateau("Target 2028", "'" + first + "'::uuid", "predecessor");
		String third = plateau("Target 2029", "'" + second + "'::uuid", "predecessor");
		// 1,000 deltas in the two layers Target 2027 and Target 2028, in two solutions each
		int offset = 0;
		for (String layer : List.of(first, second)) {
			for (int solution = 0; solution < 2; solution++) {
				String id = this.jdbc.queryForObject("""
						INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
						SELECT gen_random_uuid(), ?, ?, ?, succeeds, id FROM plateaus WHERE id = ?::uuid
						RETURNING id::text""", String.class, "Layer " + UUID.randomUUID(), this.michaelId, this.michaelId,
						layer);
				deltas(id, offset, 250);
				offset += 250;
			}
		}
		String solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				SELECT gen_random_uuid(), ?, ?, ?, succeeds, id FROM plateaus WHERE id = ?::uuid RETURNING id::text""",
				String.class, "Measured " + UUID.randomUUID(), this.michaelId, this.michaelId, third);
		String path = "/api/v1/solutions/" + solution + "/landscape?q=system%2099&pageSize=50";
		assertThat(ok(call(path, this.michael)).path("page").path("totalElements").asInt()).isPositive();
		List<Long> millis = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			long started = System.nanoTime();
			ok(call(path, this.michael));
			millis.add((System.nanoTime() - started) / 1_000_000);
		}
		Collections.sort(millis);
		assertThat(millis.get(18)).as("p95 of %s ms", millis).isLessThan(500);
	}

	/** {@code count} deltas: per four entities a rename, a description, a retirement and a new entity. */
	private void deltas(String solution, int offset, int count) {
		for (int i = offset; i < offset + count; i++) {
			int entity = this.jdbc.queryForObject("SELECT min(id) FROM entities WHERE deleted_at IS NULL", Integer.class)
					+ i;
			String type = switch (i % 4) {
				case 2 -> "retiring";
				case 3 -> "new";
				default -> "modified";
			};
			String delta = this.jdbc.queryForObject("""
					INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, created_by)
					VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, ?) RETURNING id::text""", String.class, solution, type,
					"new".equals(type) ? null : entity, "new".equals(type) ? this.application : null,
					"new".equals(type) ? "Proposed " + i : null, this.michaelId);
			if ("modified".equals(type)) {
				this.jdbc.update("""
						INSERT INTO entity_delta_property_values (id, delta_id, system_field, after_value_text)
						VALUES (gen_random_uuid(), ?::uuid, ?, ?)""", delta, (i % 4 == 0) ? "name" : "description",
						"Changed " + i);
			}
		}
	}

	private String plateau(String name, String succeeds, String basis) {
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, created_by)
				VALUES (gen_random_uuid(), ?, 'target', %s, ?, ?) RETURNING id::text""".formatted(succeeds), String.class,
				name + " " + UUID.randomUUID(), basis, this.michaelId);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class,
				name + " " + UUID.randomUUID().toString().substring(0, 8), connection);
	}

	private String path(String query) {
		return "/api/v1/solutions/" + this.solution + "/landscape?" + (query.isEmpty() ? "" : query.substring(1));
	}

	private MvcTestResult landscape(String query) {
		return call(path(query), this.michael);
	}

	private MvcTestResult call(String path, TestSession session) {
		return this.mvc.get()
			.uri(UriComponentsBuilder.fromUriString(path).build(true).toUri())
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return JsonMapper.shared().readTree(content(result));
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-landscape-latency").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
