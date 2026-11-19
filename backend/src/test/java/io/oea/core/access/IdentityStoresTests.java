package io.oea.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The identity stores of S17 change 3 on the runtime role, for the statements no service test pins
 * down: the throttle window, the expiry query, the challenge, the login transaction bounds, the
 * setup phase and the advisory lock of the administration paths.
 */
class IdentityStoresTests {

	private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

	private static OeaDatabase postgres;

	private static JdbcTemplate app;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asSuperuser(postgres), true))
			.update("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'store-tests')");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void throttleCountsFailuresInsideTheWindowAndRestartsAfterIt() {
		LoginThrottleStore store = new LoginThrottleStore(app);
		Duration window = Duration.ofMinutes(15);
		assertThat(store.incrementFailures("account:inside", NOW, NOW.minus(window))).isEqualTo(1);
		assertThat(store.incrementFailures("account:inside", NOW.plusSeconds(1), NOW.plusSeconds(1).minus(window)))
			.isEqualTo(2);
		// the window started exactly at the limit: still inside, not before it
		assertThat(store.incrementFailures("account:inside", NOW.plus(window), NOW)).isEqualTo(3);
		assertThat(store.incrementFailures("account:inside", NOW.plus(window).plusSeconds(1), NOW.plusSeconds(1)))
			.isEqualTo(1);
	}

	@Test
	void throttleLocksUntilTheTimeAndClears() {
		LoginThrottleStore store = new LoginThrottleStore(app);
		assertThat(store.lockedUntil("account:locked")).isNull();
		store.incrementFailures("account:locked", NOW, NOW.minusSeconds(900));
		assertThat(store.lockedUntil("account:locked")).isNull();
		store.lockUntil("account:locked", NOW.plusSeconds(30));
		assertThat(store.lockedUntil("account:locked")).isEqualTo(NOW.plusSeconds(30));
		store.delete("account:locked");
		assertThat(store.lockedUntil("account:locked")).isNull();
	}

	@Test
	void expiredAssignmentsAreLockedOnceAndNotAgainAfterRecording() {
		RoleStore store = new RoleStore(app);
		long person = person("expiry-person");
		long granter = person("expiry-granter");
		UUID ended = assignment(person, granter, NOW.minusSeconds(7200), NOW.minusSeconds(1));
		UUID endingNow = assignment(person, granter, NOW.minusSeconds(7200), NOW);
		UUID running = assignment(person, granter, NOW.minusSeconds(7200), NOW.plusSeconds(1));
		List<UUID> due = store.lockUnrecordedExpiries(NOW).stream().map(RoleStore.ExpiredAssignment::id).toList();
		assertThat(due).contains(ended, endingNow).doesNotContain(running);
		store.markExpiryRecorded(ended, NOW);
		assertThat(store.lockUnrecordedExpiries(NOW).stream().map(RoleStore.ExpiredAssignment::id))
			.contains(endingNow)
			.doesNotContain(ended);
	}

	@Test
	void roleAssignmentEndsAtItsValidTo() {
		RoleStore store = new RoleStore(app);
		long person = person("validity-person");
		long granter = person("validity-granter");
		assignment(person, granter, NOW.minusSeconds(7200), NOW);
		assertThat(store.isAssigned(person, "system-admin", NOW.minusSeconds(1))).isTrue();
		assertThat(store.isAssigned(person, "system-admin", NOW)).isFalse();
	}

	@Test
	void challengeIsConsumedOnce() {
		PasskeyStore store = new PasskeyStore(app);
		UUID id = UUID.randomUUID();
		byte[] value = new byte[32];
		value[0] = 7;
		store.insertChallenge(new PasskeyStore.Challenge(id, value, null, null, null, NOW.plusSeconds(300)), "login",
				NOW);
		assertThat(store.consumeChallenge(id, "registration")).isEmpty();
		PasskeyStore.Challenge consumed = store.consumeChallenge(id, "login").orElseThrow();
		assertThat(consumed.value()).isEqualTo(value);
		assertThat(consumed.expiresAt()).isEqualTo(NOW.plusSeconds(300));
		assertThat(store.consumeChallenge(id, "login")).isEmpty();
	}

	@Test
	void expiredChallengesAreDeletedAtTheirExpiry() {
		PasskeyStore store = new PasskeyStore(app);
		UUID expiring = UUID.randomUUID();
		UUID later = UUID.randomUUID();
		store.insertChallenge(new PasskeyStore.Challenge(expiring, new byte[32], null, null, null, NOW), "login",
				NOW.minusSeconds(60));
		store.insertChallenge(new PasskeyStore.Challenge(later, new byte[32], null, null, null, NOW.plusSeconds(1)),
				"login", NOW.minusSeconds(60));
		store.deleteExpiredChallenges(NOW);
		assertThat(store.consumeChallenge(expiring, "login")).isEmpty();
		assertThat(store.consumeChallenge(later, "login")).isPresent();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 4 })
	void loginTransactionIsOpenBelowFiveWrongCodes(int failed) {
		LoginStateStore store = new LoginStateStore(app);
		byte[] hash = transaction("open-" + failed, NOW.plusSeconds(60), failed);
		assertThat(store.lockOpenTransaction(hash, NOW, 5)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(ints = { 5, 6 })
	void loginTransactionIsClosedAtFiveWrongCodes(int failed) {
		LoginStateStore store = new LoginStateStore(app);
		byte[] hash = transaction("closed-" + failed, NOW.plusSeconds(60), failed);
		assertThat(store.lockOpenTransaction(hash, NOW, 5)).isEmpty();
	}

	@Test
	void loginTransactionExpiresAtItsTime() {
		LoginStateStore store = new LoginStateStore(app);
		byte[] hash = transaction("expiry", NOW.plusSeconds(1), 0);
		assertThat(store.lockOpenTransaction(hash, NOW, 5)).isPresent();
		assertThat(store.lockOpenTransaction(hash, NOW.plusSeconds(1), 5)).isEmpty();
	}

	@Test
	void wrongCodesAreCountedAndTheTransactionIsDeleted() {
		LoginStateStore store = new LoginStateStore(app);
		byte[] hash = transaction("counted", NOW.plusSeconds(60), 0);
		assertThat(store.incrementFailedCodes(hash)).isEqualTo(1);
		assertThat(store.incrementFailedCodes(hash)).isEqualTo(2);
		store.deleteTransaction(hash);
		assertThat(store.lockOpenTransaction(hash, NOW, 5)).isEmpty();
	}

	@Test
	void setupPhaseEndsOnceAndStaysEnded() {
		IdentitySettingsStore store = new IdentitySettingsStore(app);
		app.update("UPDATE system_settings SET setup_phase_ended_at = NULL WHERE id = 1");
		assertThat(store.setupPhaseEnded()).isFalse();
		assertThat(store.lockSetupPhaseEnded()).isFalse();
		store.endSetupPhase(NOW, "role-assignment");
		assertThat(store.setupPhaseEnded()).isTrue();
		assertThat(store.lockSetupPhaseEnded()).isTrue();
	}

	@Test
	void passwordHistoryListsTheActivePasswordFirstThenTheRevokedOnes() {
		CredentialStore store = new CredentialStore(app);
		long person = person("history-person");
		UUID first = UUID.randomUUID();
		store.insertActivePassword(first, person, null, NOW.minusSeconds(100));
		store.insertPasswordHash(first, "$argon2id$first");
		store.revoke(first, NOW.minusSeconds(50), "superseded", null, null);
		UUID second = UUID.randomUUID();
		store.insertActivePassword(second, person, null, NOW.minusSeconds(40));
		store.insertPasswordHash(second, "$argon2id$second");
		assertThat(store.passwordHistory(person, null, 2)).containsExactly("$argon2id$second", "$argon2id$first");
		assertThat(store.passwordHistory(person, null, 1)).containsExactly("$argon2id$second");
		assertThat(store.hasActivePassword(person)).isTrue();
	}

	@Test
	void advisoryLockIsTakenInsideATransactionAndPathCountsAreReadable() {
		AdministrationPathStore store = new AdministrationPathStore(app);
		store.lockPaths(0x0EA5_0D07L);
		assertThat(store.localAccountPaths()).isGreaterThanOrEqualTo(0);
		assertThat(store.mappingPaths(null)).isGreaterThanOrEqualTo(0);
		assertThat(store.personPaths(null, null)).isGreaterThanOrEqualTo(0);
		assertThat(store.isUsableMapping(UUID.randomUUID())).isFalse();
	}

	private static long person(String name) {
		return app.queryForObject("INSERT INTO persons (name, status) VALUES (?, 'active') RETURNING id", Long.class,
				name);
	}

	private static UUID assignment(long person, long granter, Instant validFrom, Instant validTo) {
		UUID id = UUID.randomUUID();
		app.update("""
				INSERT INTO role_assignments (id, person_id, role_id, valid_from, valid_to, granted_at,
				                              granted_by_person_id)
				VALUES (?, ?, 'system-admin', ?, ?, ?, ?)""", id, person, Timestamp.from(validFrom),
				Timestamp.from(validTo), Timestamp.from(validFrom), granter);
		return id;
	}

	private static byte[] transaction(String name, Instant expiresAt, int failedCodes) {
		long person = person("transaction-" + name);
		byte[] hash = new byte[32];
		byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, hash, 0, Math.min(bytes.length, 32));
		new LoginStateStore(app).insertTransaction(hash, null, person, NOW.minusSeconds(60), expiresAt);
		app.update("UPDATE login_transactions SET failed_codes = ? WHERE token_hash = ?", failedCodes, hash);
		return hash;
	}

}
