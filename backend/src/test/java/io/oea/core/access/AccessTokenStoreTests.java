package io.oea.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.core.access.ServiceAccountStore.Actor;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The token store of design D2 and D7 of s12-client-credentials-token on the runtime role: a
 * valid token until its expiry, revoked tokens and tokens of deactivated accounts not found, the
 * revocation of all tokens of a client and the cleanup boundary.
 */
class AccessTokenStoreTests {

	private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

	private static final Actor ADMIN = new Actor(null, null);

	private static OeaDatabase postgres;

	private static JdbcTemplate app;

	private static AccessTokenStore tokens;

	private static ServiceAccountStore accounts;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		tokens = new AccessTokenStore(app);
		accounts = new ServiceAccountStore(app);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void validUntilTheExpiry() {
		Account account = account("store-valid");
		byte[] hash = issue(account, "valid", NOW);
		Instant expiry = NOW.plus(AccessTokenStore.LIFETIME);
		assertThat(tokens.findValid(hash, expiry.minusSeconds(1))).hasValueSatisfying((token) -> {
			assertThat(token.clientId()).isEqualTo(account.client());
			assertThat(token.name()).isEqualTo("store-valid");
			assertThat(token.expiresAt()).isEqualTo(expiry);
		});
		assertThat(tokens.findValid(hash, expiry)).isEmpty();
	}

	@Test
	void revokedTokensAndDeactivatedAccountsAreNotFound() {
		Account account = account("store-revoked");
		byte[] first = issue(account, "first", NOW);
		byte[] second = issue(account, "second", NOW.plusSeconds(10));
		assertThat(tokens.revokeAll(account.client(), "rotation", NOW.plusSeconds(20))).hasSize(2);
		assertThat(tokens.findValid(first, NOW.plusSeconds(30))).isEmpty();
		assertThat(tokens.findValid(second, NOW.plusSeconds(30))).isEmpty();
		// a revoked token is not revoked again
		assertThat(tokens.revokeAll(account.client(), "deactivation", NOW.plusSeconds(40))).isEmpty();

		Account other = account("store-deactivated");
		byte[] third = issue(other, "third", NOW);
		accounts.deactivate(other.client(), other.person(), NOW.plusSeconds(5), ADMIN);
		assertThat(tokens.findValid(third, NOW.plusSeconds(10))).isEmpty();
	}

	@Test
	void expiredTokensAreNotRevoked() {
		Account account = account("store-expired");
		issue(account, "expired", NOW);
		assertThat(tokens.revokeAll(account.client(), "rotation", NOW.plus(AccessTokenStore.LIFETIME))).isEmpty();
	}

	@Test
	void cleanupBoundary() {
		Account account = account("store-cleanup");
		Instant now = NOW.plus(Duration.ofDays(30));
		// expired exactly one day ago, and one day minus one minute ago
		byte[] old = issue(account, "old", now.minus(Duration.ofDays(1)).minus(AccessTokenStore.LIFETIME));
		byte[] recent = issue(account, "recent",
				now.minus(Duration.ofDays(1)).plusSeconds(60).minus(AccessTokenStore.LIFETIME));
		// the tokens of the other tests expired earlier and are deleted as well
		assertThat(tokens.deleteExpiredBefore(now.minus(Duration.ofDays(1)))).isPositive();
		assertThat(count(old)).isZero();
		assertThat(count(recent)).isOne();
	}

	@Test
	void markUsedSetsTheLastUse() {
		Account account = account("store-used");
		tokens.markUsed(account.credential(), NOW);
		assertThat(accounts.find(account.client(), NOW).orElseThrow().lastUsedAt()).isEqualTo(NOW);
	}

	private record Account(UUID client, long person, UUID credential) {
	}

	private static Account account(String name) {
		long person = accounts.insertAccount(name, null, NOW);
		UUID client = UUID.randomUUID();
		accounts.insertSecret(client, person, "hash", NOW, NOW.plus(Duration.ofDays(90)), ADMIN);
		return new Account(client, person, accounts.activeSecret(client).orElseThrow().id());
	}

	private static byte[] issue(Account account, String token, Instant at) {
		byte[] hash = sha256(token + account.client());
		tokens.insert(UUID.randomUUID(), hash, account.client(), account.credential(), at);
		return hash;
	}

	private static int count(byte[] hash) {
		Integer count = app.queryForObject("SELECT count(*) FROM access_tokens WHERE token_hash = ?", Integer.class, hash);
		return (count != null) ? count : 0;
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
