package io.oea.app.bootstrapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabases;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec identity/bootstrapping: Token readable only inside the container (REQ-017, REQ-175 AC3). */
@ExtendWith(OutputCaptureExtension.class)
class SetupTokenTests {

	private PostgreSQLContainer postgres;

	@TempDir
	Path run;

	@BeforeEach
	void freshInstance() {
		this.postgres = OeaDatabases.freshMigrated();
	}

	@AfterEach
	void stop() {
		this.postgres.stop();
	}

	@Test
	void tokenIsWrittenOwnerOnlyAndNeverLogged(CapturedOutput output) throws Exception {
		Path file = this.run.resolve("oea/setup-token");
		try (ConfigurableApplicationContext context = OeaApplications.start(this.postgres, file,
				WebApplicationType.NONE)) {
			String token = Files.readString(file, StandardCharsets.US_ASCII).strip();
			assertThat(token).matches("[A-Za-z0-9_-]{43}");
			assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
			assertThat(storedHash()).isEqualTo(SetupTokens.hash(token));
			assertThat(output).contains("Setup required").contains(file.toString()).doesNotContain(token);
		}
	}

	@Test
	void restartReplacesTheToken() throws Exception {
		Path file = this.run.resolve("setup-token");
		String first;
		try (ConfigurableApplicationContext context = OeaApplications.start(this.postgres, file,
				WebApplicationType.NONE)) {
			first = Files.readString(file).strip();
		}
		try (ConfigurableApplicationContext context = OeaApplications.start(this.postgres, file,
				WebApplicationType.NONE)) {
			String second = Files.readString(file).strip();
			assertThat(second).isNotEqualTo(first);
			assertThat(storedHash()).isEqualTo(SetupTokens.hash(second));
		}
	}

	@Test
	void bootstrappedInstanceIssuesNoTokenAndRemovesAStaleFile(CapturedOutput output) throws Exception {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'acme-corp')");
		}
		Path file = this.run.resolve("setup-token");
		Files.writeString(file, "stale");
		try (ConfigurableApplicationContext context = OeaApplications.start(this.postgres, file,
				WebApplicationType.NONE)) {
			assertThat(file).doesNotExist();
			assertThat(output).doesNotContain("Setup required");
		}
	}

	private byte[] storedHash() throws Exception {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT token_hash, count(*) OVER () FROM bootstrap_setup_tokens")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(2)).isEqualTo(1);
			return rs.getBytes(1);
		}
	}

}
