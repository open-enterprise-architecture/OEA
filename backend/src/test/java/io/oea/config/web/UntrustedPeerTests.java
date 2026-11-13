package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Scenarios "Forwarded header from an untrusted peer" and "Operator narrows the trusted
 * proxies" of platform/deployment (AA-01; design D2 and D3 of s11-review-hardening): the
 * operator trusts only {@code 192.0.2.0/24}, so the test client at {@code 127.0.0.1} is not a
 * trusted proxy and its {@code X-Forwarded-For} is ignored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "oea.web.trusted-proxies=192.0.2.0/24")
class UntrustedPeerTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final Path TOKEN_FILE = ProxiedInstance.tokenFile();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		ProxiedInstance.register(registry, postgres, TOKEN_FILE);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@LocalServerPort
	private int port;

	@Autowired
	private Bootstrapping bootstrapping;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void prepare() {
		ProxiedInstance.prepare(this.bootstrapping, this.jdbc, TOKEN_FILE);
	}

	@Test
	void forwardedHeaderFromAnUntrustedPeerIsIgnored() {
		ProxiedInstance.on(this.port).login("unknown-" + UUID.randomUUID(), "wrong", "203.0.113.30");
		assertThat(this.jdbc.queryForList("SELECT throttle_key FROM auth_throttle WHERE throttle_key LIKE 'address:%'",
				String.class)).containsExactly("address:127.0.0.1");
		assertThat(sourceAddresses("203.0.113.30")).isZero();
		assertThat(sourceAddresses("127.0.0.1")).isPositive();
	}

	private int sourceAddresses(String address) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'auth.login-failed' AND host(source_ip) = ?",
				Integer.class, address);
		return (count != null) ? count : 0;
	}

}
