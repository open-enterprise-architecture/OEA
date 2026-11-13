package io.oea.app.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spec solution/solution-conflicts "Conflicts of a delta" and "Content of a conflict" (US-044 AC1,
 * AC2, AC5, REQ-042 AC1–AC4; design D0 of us-044-conflict-warning).
 */
@SpringBootTest
class SolutionConflictsTests {

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
	private SolutionConflicts conflicts;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private long michael;

	private long maria;

	private UUID application;

	private int crm;

	private int erp;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = personOf(principals.personWithIdpRole("solution-architect").sessionId());
		this.maria = personOf(principals.personWithIdpRole("solution-architect").sessionId());
		this.application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, false) RETURNING id""", UUID.class,
				"Application " + suffix());
		this.crm = entity("CRM-Legacy");
		this.erp = entity("ERP-Core");
	}

	@Test
	void conflictNamesTheOtherSolutionItsOwnerAndDeltaType() throws SQLException {
		UUID a = solution("Solution A", this.maria);
		setStatus(a, "proposed");
		delta(a, "retiring", this.crm);
		UUID b = solution("Solution B", this.michael);
		delta(b, "modified", this.crm);

		List<SolutionConflicts.Conflict> found = this.conflicts.of(b, List.of(this.crm)).get(this.crm);

		assertThat(found).containsExactly(new SolutionConflicts.Conflict(a, name(a), "proposed", this.maria,
				personName(this.maria), SolutionDeltas.Type.RETIRING));
	}

	@ParameterizedTest
	@ValueSource(strings = { "draft", "proposed", "approved", "in-progress" })
	void everyActiveStatusConflicts(String status) throws SQLException {
		UUID a = solution("Solution A", this.maria);
		setStatus(a, status);
		delta(a, "modified", this.crm);
		UUID b = solution("Solution B", this.michael);

		assertThat(this.conflicts.of(b, List.of(this.crm)).get(this.crm)).extracting(SolutionConflicts.Conflict::status)
			.containsExactly(status);
	}

	@ParameterizedTest
	@ValueSource(strings = { "implemented", "archived" })
	void noConflictWithACompletedSolution(String status) throws SQLException {
		UUID a = solution("Solution A", this.maria);
		delta(a, "modified", this.crm);
		setStatus(a, status);
		UUID b = solution("Solution B", this.michael);
		delta(b, "modified", this.crm);

		assertThat(this.conflicts.of(b, List.of(this.crm))).isEmpty();
		assertThat(this.conflicts.conflicted(b)).isZero();
	}

	@Test
	void noConflictWithTheSolutionItselfOrAnotherEntity() {
		UUID a = solution("Solution A", this.maria);
		delta(a, "modified", this.erp);
		UUID b = solution("Solution B", this.michael);
		delta(b, "retiring", this.crm);

		assertThat(this.conflicts.of(b, List.of(this.crm))).isEmpty();
		assertThat(this.conflicts.conflicted(b)).isZero();
	}

	@Test
	void newDeltasNeverConflict() {
		UUID a = solution("Solution A", this.maria);
		UUID b = solution("Solution B", this.michael);
		for (UUID solution : List.of(a, b)) {
			this.jdbc.update("""
					INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
					VALUES (gen_random_uuid(), ?, 'new', ?, 'Warehouse Cloud', ?)""", solution, this.application,
					this.michael);
		}

		assertThat(this.conflicts.conflicted(b)).isZero();
	}

	@Test
	void numberOfConflictsZeroOneTwo() {
		UUID c = solution("Solution C", this.michael);
		delta(c, "modified", this.crm);
		assertThat(this.conflicts.of(c, List.of(this.crm))).isEmpty();

		UUID b = solution("solution b", this.maria);
		delta(b, "modified", this.crm);
		assertThat(this.conflicts.of(c, List.of(this.crm)).get(this.crm)).hasSize(1);

		UUID a = solution("Solution A", this.maria);
		delta(a, "retiring", this.crm);
		// ordered by name, case-insensitive: "Solution A" before "solution b"
		assertThat(this.conflicts.of(c, List.of(this.crm)).get(this.crm))
			.extracting(SolutionConflicts.Conflict::solutionId)
			.containsExactly(a, b);
		assertThat(this.conflicts.conflicted(c)).isEqualTo(1);
	}

	@Test
	void conflictsPerEntityOfASet() {
		UUID a = solution("Solution A", this.maria);
		delta(a, "modified", this.crm);
		delta(a, "modified", this.erp);
		UUID b = solution("Solution B", this.michael);
		delta(b, "modified", this.crm);
		delta(b, "retiring", this.erp);

		Map<Integer, List<SolutionConflicts.Conflict>> found = this.conflicts.of(b, List.of(this.crm, this.erp));

		assertThat(found).containsOnlyKeys(this.crm, this.erp);
		assertThat(this.conflicts.conflicted(b)).isEqualTo(2);
	}

	@Test
	void emptySetReadsNothing() {
		assertThat(this.conflicts.of(UUID.randomUUID(), List.of())).isEmpty();
	}

	private UUID solution(String name, long owner) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), owner, owner);
	}

	private void delta(UUID solution, String type, int entity) {
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?, ?)""", solution, type, entity, this.michael);
	}

	private int entity(String name) {
		return this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				this.application, name, this.michael);
	}

	private String name(UUID solution) {
		return this.jdbc.queryForObject("SELECT name FROM solutions WHERE id = ?", String.class, solution);
	}

	private String personName(long person) {
		return this.jdbc.queryForObject("SELECT name FROM persons WHERE id = ?", String.class, person);
	}

	private long personOf(UUID session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session);
	}

	private static void setStatus(UUID solution, String status) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.execute("UPDATE solutions SET status = '" + status + "' WHERE id = '" + solution + "'");
		}
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-conflicts").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
