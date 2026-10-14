package io.oea.steps.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Steps for features/platform/database-roles.feature, executed through the application's data source. */
public class DatabaseRoleSteps {

	private static final String AUDIT_SNAPSHOT = """
			SELECT count(*) || ':' || coalesce(md5(string_agg(e::text, '|' ORDER BY id)), '')
			FROM audit.events e""";

	private final JdbcTemplate jdbc;

	private String runtimeRole;

	private String snapshotBefore;

	private DataAccessException failure;

	public DatabaseRoleSteps(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Given("the application is connected to the database with its runtime role")
	public void connectedWithRuntimeRole() {
		this.runtimeRole = this.jdbc.queryForObject("SELECT current_user", String.class);
		assertThat(this.runtimeRole).isEqualTo("oea_app");
	}

	@Given("an audit event exists")
	public void anAuditEventExists() {
		// Chain values are supplied explicitly until the hash-chain trigger exists (change 2).
		this.jdbc.update("""
				INSERT INTO audit.events (event_type, event_category, severity, outcome, source, actor_type,
				                          chain_seq, hash_version, prev_hash, record_hash)
				SELECT 'test.fixture', 'system', 'info', 'success', 'Job', 'system',
				       coalesce(max(chain_seq), 0) + 1, 1, decode(repeat('00', 32), 'hex'), sha256('fixture'::bytea)
				FROM audit.events""");
		this.snapshotBefore = this.jdbc.queryForObject(AUDIT_SNAPSHOT, String.class);
	}

	@When("the runtime role executes {string}")
	public void runtimeRoleExecutes(String statement) {
		try {
			this.jdbc.execute(statement);
		}
		catch (DataAccessException ex) {
			this.failure = ex;
		}
	}

	@Then("the database rejects the statement with {string}")
	public void databaseRejects(String message) {
		assertThat(this.failure).as("statement must be rejected").isNotNull();
		assertThat(this.failure.getMostSpecificCause().getMessage()).contains(message);
	}

	@Then("no audit record has changed")
	public void noAuditRecordChanged() {
		assertThat(this.jdbc.queryForObject(AUDIT_SNAPSHOT, String.class)).isEqualTo(this.snapshotBefore);
	}

	@Then("the owner of {string} is a role different from the runtime role")
	public void ownerDiffers(String table) {
		String owner = this.jdbc.queryForObject("SELECT tableowner FROM pg_tables WHERE schemaname || '.' || tablename = ?",
				String.class, table);
		assertThat(owner).isNotNull().isNotEqualTo(this.runtimeRole);
	}

}
