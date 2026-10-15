package io.oea.app.bootstrapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.bootstrapping.BootstrapRejectedException.Reason;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabases;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec identity/bootstrapping (US-013 AC1, US-015, US-016, US-017 AC2) against a fresh instance. */
class BootstrappingTests {

	private static final String PASSWORD = "correct horse battery staple";

	private PostgreSQLContainer postgres;

	private ConfigurableApplicationContext context;

	private Bootstrapping bootstrapping;

	private Path tokenFile;

	@TempDir
	Path run;

	@BeforeEach
	void freshInstance() {
		this.postgres = OeaDatabases.freshMigrated();
		this.tokenFile = this.run.resolve("setup-token");
		this.context = OeaApplications.start(this.postgres, this.tokenFile, WebApplicationType.NONE);
		this.bootstrapping = this.context.getBean(Bootstrapping.class);
	}

	@AfterEach
	void stop() {
		this.context.close();
		this.postgres.stop();
	}

	@Test
	void bootstrappingCreatesTheSystemAdminAccount() throws Exception {
		assertThat(this.bootstrapping.setupRequired()).isTrue();

		BootstrapResult result = this.bootstrapping.bootstrap(command(token(), "admin", PASSWORD, "acme-corp"));

		assertThat(result.loginName()).isEqualTo("admin");
		assertThat(this.bootstrapping.setupRequired()).isFalse();
		assertThat(single("SELECT login_name || ':' || status FROM system_admin_accounts")).isEqualTo("admin:bootstrapped");
		assertThat(single("SELECT instance_slug FROM system_settings")).isEqualTo("acme-corp");
		assertThat(single("""
				SELECT p.password_hash FROM password_credentials p JOIN local_credentials c ON c.id = p.credential_id
				WHERE c.system_admin_account_id = '%s' AND c.type = 'password' AND p.origin = 'self-set'
				""".formatted(result.systemAdminAccountId()))).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
		assertThat(single("SELECT count(*)::text FROM bootstrap_setup_tokens")).isEqualTo("0");
		assertThat(this.tokenFile).doesNotExist();
		assertThat(single("""
				SELECT outcome || ':' || actor_type || ':' || actor_admin_id || ':' || (details ->> 'mode')
				FROM audit.events WHERE event_type = 'system.bootstrapped'"""))
			.isEqualTo("success:system-admin-account:" + result.systemAdminAccountId() + ":local");
	}

	@Test
	void passwordOutsideTheLengthLimitsIsRejected() throws Exception {
		String token = token();
		for (String password : List.of("x".repeat(11), "x".repeat(129))) {
			assertThatExceptionOfType(BootstrapRejectedException.class)
				.isThrownBy(() -> this.bootstrapping.bootstrap(command(token, "admin", password, "acme-corp")))
				.satisfies((ex) -> {
					assertThat(ex.getReason()).isEqualTo(Reason.VALIDATION_FAILED);
					assertThat(ex.getInvalidFields()).containsExactly("password");
				});
		}
		assertThat(single("SELECT count(*)::text FROM system_admin_accounts")).isEqualTo("0");
		assertThat(this.bootstrapping.setupRequired()).isTrue();
		assertThat(rejections()).containsExactly("validation-failed:[\"password\"]", "validation-failed:[\"password\"]");
	}

	@Test
	void setupWithoutValidTokenIsRejected() throws Exception {
		String token = token();
		assertRejected(command(null, "admin", PASSWORD, "acme-corp"), Reason.INVALID_SETUP_TOKEN);
		assertRejected(command("wrong-" + token, "admin", PASSWORD, "acme-corp"), Reason.INVALID_SETUP_TOKEN);
		superuser("UPDATE bootstrap_setup_tokens SET expires_at = now() - interval '1 second'");
		assertRejected(command(token, "admin", PASSWORD, "acme-corp"), Reason.INVALID_SETUP_TOKEN);

		assertThat(single("SELECT count(*)::text FROM system_admin_accounts")).isEqualTo("0");
		assertThat(rejections()).containsExactly("invalid-setup-token:null", "invalid-setup-token:null",
				"invalid-setup-token:null");
		assertThat(auditDetailsContain(token)).isFalse();
	}

	@Test
	void repeatedBootstrappingIsRejected() throws Exception {
		String token = token();
		this.bootstrapping.bootstrap(command(token, "admin", PASSWORD, "acme-corp"));

		assertRejected(command(token, "intruder", "another long password", "evil-corp"), Reason.ALREADY_BOOTSTRAPPED);

		assertThat(single("SELECT string_agg(login_name, ',') FROM system_admin_accounts")).isEqualTo("admin");
		assertThat(single("SELECT instance_slug FROM system_settings")).isEqualTo("acme-corp");
		assertThat(rejections()).containsExactly("already-bootstrapped:null");
		assertThat(auditDetailsContain(PASSWORD)).isFalse();
	}

	@Test
	void exactlyOneOfTwoSimultaneousAttemptsSucceeds() throws Exception {
		String token = token();
		CountDownLatch start = new CountDownLatch(1);
		Callable<String> attempt = () -> {
			start.await();
			try {
				this.bootstrapping.bootstrap(command(token, "admin", PASSWORD, "acme-corp"));
				return "success";
			}
			catch (BootstrapRejectedException ex) {
				return ex.getReason().code();
			}
		};
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<String>> results = new ArrayList<>(List.of(executor.submit(attempt), executor.submit(attempt)));
			start.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> result : results) {
				outcomes.add(result.get());
			}
			assertThat(outcomes).containsExactlyInAnyOrder("success", "already-bootstrapped");
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(single("SELECT count(*)::text FROM system_admin_accounts")).isEqualTo("1");
	}

	private void assertRejected(BootstrapCommand command, Reason reason) {
		assertThatExceptionOfType(BootstrapRejectedException.class).isThrownBy(() -> this.bootstrapping.bootstrap(command))
			.satisfies((ex) -> assertThat(ex.getReason()).isEqualTo(reason));
	}

	private String token() throws Exception {
		return Files.readString(this.tokenFile).strip();
	}

	private static BootstrapCommand command(String token, String loginName, String password, String slug) {
		return new BootstrapCommand(token, loginName, password, slug);
	}

	private List<String> rejections() throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT (details ->> 'reason') || ':' || coalesce((details -> 'fields')::text, 'null'),
						       outcome, actor_type, details ->> 'mode'
						FROM audit.events WHERE event_type = 'system.bootstrap-rejected' ORDER BY chain_seq""")) {
			while (rs.next()) {
				assertThat(rs.getString(2)).isEqualTo("denied");
				assertThat(rs.getString(3)).isEqualTo("unauthenticated");
				assertThat(rs.getString(4)).isEqualTo("local");
				rows.add(rs.getString(1));
			}
		}
		return rows;
	}

	private boolean auditDetailsContain(String secret) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres);
				var select = su.prepareStatement("SELECT count(*) FROM audit.events WHERE strpos(details::text, ?) > 0")) {
			select.setString(1, secret);
			try (ResultSet rs = select.executeQuery()) {
				rs.next();
				return rs.getInt(1) > 0;
			}
		}
	}

	private String single(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			assertThat(rs.next()).as(sql).isTrue();
			return rs.getString(1);
		}
	}

	private void superuser(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}
