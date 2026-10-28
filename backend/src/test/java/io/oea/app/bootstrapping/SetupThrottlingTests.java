package io.oea.app.bootstrapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.bootstrapping.BootstrapRejectedException.Reason;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.time.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** Spec identity/bootstrapping: Throttling of the setup token (REQ-168 AC3, issue #61). */
class SetupThrottlingTests {

	private static final String PASSWORD = "correct horse battery staple";

	private static final String ATTACKER = "203.0.113.7";

	private final MutableClock clock = new MutableClock();

	private OeaDatabase postgres;

	private ConfigurableApplicationContext context;

	private Bootstrapping bootstrapping;

	private Path tokenFile;

	@TempDir
	Path run;

	@BeforeEach
	void freshInstance() {
		this.postgres = OeaDatabases.freshMigrated();
		this.tokenFile = this.run.resolve("setup-token");
		this.context = OeaApplications.start(this.postgres, this.tokenFile, WebApplicationType.NONE, this.clock);
		this.bootstrapping = this.context.getBean(Bootstrapping.class);
	}

	@AfterEach
	void stop() {
		this.context.close();
		this.postgres.stop();
	}

	@Test
	void addressIsThrottledAfterOneHundredWrongTokens() throws Exception {
		wrongTokens(ATTACKER, 100);

		assertThatExceptionOfType(BootstrapRejectedException.class)
			.isThrownBy(() -> this.bootstrapping.bootstrap(correct(), ATTACKER))
			.satisfies((ex) -> {
				assertThat(ex.getReason()).isEqualTo(Reason.THROTTLED);
				assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 601L);
			});
		assertThat(this.bootstrapping.setupRequired()).isTrue();
		assertThat(single("SELECT count(*)::text FROM audit.events WHERE event_type = 'auth.throttled' "
				+ "AND details ->> 'scope' = 'address'")).isEqualTo("1");
		assertThat(single("SELECT count(*)::text FROM audit.events WHERE event_type = 'system.bootstrap-rejected' "
				+ "AND details ->> 'reason' = 'throttled' AND outcome = 'denied'")).isEqualTo("1");
		assertThat(single("SELECT count(*)::text FROM audit.events WHERE details::text LIKE '%" + token() + "%'"))
			.as("the setup token is never recorded")
			.isEqualTo("0");
	}

	@Test
	void otherAddressesAreUnaffected() throws Exception {
		wrongTokens(ATTACKER, 100);
		BootstrapResult result = this.bootstrapping.bootstrap(correct(), "198.51.100.20");
		assertThat(result.loginName()).isEqualTo("admin");
	}

	@Test
	void setupIsAcceptedAgainAfterTheWaitingTime() throws Exception {
		wrongTokens(ATTACKER, 100);
		long wait = assertThatExceptionOfType(BootstrapRejectedException.class)
			.isThrownBy(() -> this.bootstrapping.bootstrap(correct(), ATTACKER))
			.actual()
			.getRetryAfterSeconds();
		this.clock.advance(Duration.ofSeconds(wait));
		assertThat(this.bootstrapping.bootstrap(correct(), ATTACKER).loginName()).isEqualTo("admin");
	}

	@Test
	void otherRejectionsDoNotCount() throws Exception {
		for (int i = 0; i < 100; i++) {
			assertThatExceptionOfType(BootstrapRejectedException.class)
				.isThrownBy(() -> this.bootstrapping.bootstrap(new BootstrapCommand(token(), "Admin!", PASSWORD,
						"acme-corp"), ATTACKER))
				.satisfies((ex) -> assertThat(ex.getReason()).isEqualTo(Reason.VALIDATION_FAILED));
		}
		assertThat(this.bootstrapping.bootstrap(correct(), ATTACKER).loginName()).isEqualTo("admin");
	}

	private void wrongTokens(String address, int count) {
		for (int i = 0; i < count; i++) {
			int attempt = i;
			assertThatExceptionOfType(BootstrapRejectedException.class)
				.isThrownBy(() -> this.bootstrapping.bootstrap(
						new BootstrapCommand("wrong-token-" + attempt, "admin", PASSWORD, "acme-corp"), address))
				.satisfies((ex) -> assertThat(ex.getReason()).isEqualTo(Reason.INVALID_SETUP_TOKEN));
		}
	}

	private BootstrapCommand correct() throws Exception {
		return new BootstrapCommand(token(), "admin", PASSWORD, "acme-corp");
	}

	private String token() throws Exception {
		return Files.readString(this.tokenFile).strip();
	}

	private String single(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

}
