package io.oea.steps.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.audit.verify.AuditVerifyCommand;
import io.oea.testsupport.audit.ChainTampering;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Steps for features/audit/hash-chain.feature. Tampering needs a database of its own, so
 * the shared test database stays intact.
 */
public class HashChainSteps {

	private PostgreSQLContainer postgres;

	private long checkpointSeq;

	private long tamperedSeq;

	private Path checkpointLog;

	private int exitCode;

	private String output;

	@Given("a separate OEA database whose audit trail contains {int} events")
	public void separateDatabase(int events) throws SQLException {
		this.postgres = OeaDatabases.freshMigrated();
		try (Connection app = OeaDatabases.asRole(this.postgres, "oea_app")) {
			ChainTampering.appendEvents(app, "test.feature", events);
		}
	}

	@Given("a checkpoint of the chain head was exported to the application log")
	public void checkpointExported() throws SQLException, IOException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			this.checkpointSeq = ChainTampering.headSeq(su);
			this.checkpointLog = ChainTampering.checkpointLog(su, this.checkpointSeq,
					Files.createTempFile("oea-checkpoints", ".log"));
		}
	}

	@When("a superuser changes one audit event directly in the database")
	public void changeOneEvent() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			this.tamperedSeq = ChainTampering.headSeq(su) - 2;
			try (Statement statement = su.createStatement()) {
				statement.executeUpdate(
						"UPDATE audit.events SET event_type = 'manipulated' WHERE chain_seq = " + this.tamperedSeq);
			}
		}
	}

	@When("a superuser deletes the newest records and rewrites the chain head")
	public void deleteNewest() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			ChainTampering.truncateFrom(su, this.checkpointSeq - 1);
		}
	}

	@When("a superuser changes one audit event before the checkpoint and recomputes the chain")
	public void changeAndRecompute() throws SQLException {
		changeOneEvent();
		try (Connection su = OeaDatabases.asSuperuser(this.postgres)) {
			ChainTampering.recomputeFrom(su, this.tamperedSeq);
		}
	}

	@When("the verification command runs")
	public void verify() {
		run(null);
	}

	@When("the verification command runs with the exported checkpoint")
	public void verifyWithCheckpoint() {
		run(this.checkpointLog);
	}

	@Then("it reports the first broken record with its chain sequence number")
	public void reportsFirstBrokenRecord() {
		assertThat(this.output).contains("first broken link: chain_seq " + this.tamperedSeq);
	}

	@Then("it reports that the database chain head is behind the checkpoint")
	public void reportsBehind() {
		assertThat(this.output).contains("is behind checkpoint chain_seq " + this.checkpointSeq);
	}

	@Then("it reports that the chain diverges from the checkpoint")
	public void reportsDivergence() {
		assertThat(this.output).contains("chain diverges from checkpoint at chain_seq " + this.checkpointSeq);
	}

	@Then("it exits with a failure status")
	public void failureStatus() {
		assertThat(this.exitCode).isEqualTo(1);
	}

	@After("@REQ-174")
	public void stopDatabase() throws IOException {
		if (this.postgres != null) {
			this.postgres.stop();
		}
		if (this.checkpointLog != null) {
			Files.deleteIfExists(this.checkpointLog);
		}
	}

	private void run(Path checkpoints) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		String url = "--oea.audit-verify.url=" + this.postgres.getJdbcUrl();
		String password = "--oea.audit-verify.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_auditor");
		this.exitCode = (checkpoints != null) ? AuditVerifyCommand.run(out, url, password, "--checkpoints=" + checkpoints)
				: AuditVerifyCommand.run(out, url, password);
		this.output = buffer.toString(StandardCharsets.UTF_8);
	}

}
