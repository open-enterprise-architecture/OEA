package io.oea.core.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.core.access.ServiceAccountStore.Account;
import io.oea.core.access.ServiceAccountStore.Actor;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The store of design D1 and D5 of us-142-service-accounts on the runtime role: secret versions
 * with one active row per client ID, rotation, deactivation, the roles of the read and the list
 * filter.
 */
class ServiceAccountStoreTests {

	private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

	private static final Actor ADMIN = new Actor(null, null);

	private static OeaDatabase postgres;

	private static JdbcTemplate app;

	private static ServiceAccountStore store;

	private static long granter;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		app = new JdbcTemplate(new SingleConnectionDataSource(OeaDatabases.asRole(postgres, "oea_app"), true));
		store = new ServiceAccountStore(app);
		granter = app.queryForObject("INSERT INTO persons (name, status) VALUES ('Max', 'active') RETURNING id",
				Long.class);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void createdAccountIsActiveWithItsSecret() {
		UUID client = create("store-created", "Imports the CMDB");
		Account account = store.find(client, NOW).orElseThrow();
		assertThat(account.name()).isEqualTo("store-created");
		assertThat(account.description()).isEqualTo("Imports the CMDB");
		assertThat(account.active()).isTrue();
		assertThat(account.secretExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
		assertThat(account.rotatedAt()).isNull();
		assertThat(account.lastUsedAt()).isNull();
		assertThat(store.activeSecret(client)).hasValueSatisfying((secret) -> assertThat(secret.hash()).isEqualTo("hash-1"));
	}

	@Test
	void twoRotationsKeepOneActiveSecret() {
		UUID client = create("store-rotated", null);
		long person = store.lock(client).orElseThrow();
		for (int n = 2; n <= 3; n++) {
			Instant at = NOW.plus(Duration.ofHours(n));
			assertThat(store.rotateActive(client, at, ADMIN)).isEqualTo(1);
			store.insertSecret(client, person, "hash-" + n, at, at.plus(Duration.ofDays(30)), ADMIN);
		}
		assertThat(store.secretStatuses(client)).containsExactly("rotated", "rotated", "active");
		assertThat(store.activeSecret(client)).hasValueSatisfying((secret) -> assertThat(secret.hash()).isEqualTo("hash-3"));
		Account account = store.find(client, NOW).orElseThrow();
		assertThat(account.rotatedAt()).isEqualTo(NOW.plus(Duration.ofHours(3)));
		assertThat(account.secretExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(3)).plus(Duration.ofDays(30)));
	}

	@Test
	void deactivationLeavesNoActiveSecret() {
		UUID client = create("store-deactivated", null);
		store.deactivate(client, store.lock(client).orElseThrow(), NOW, ADMIN);
		assertThat(store.secretStatuses(client)).containsExactly("revoked");
		assertThat(store.activeSecret(client)).isEmpty();
		Account account = store.find(client, NOW).orElseThrow();
		assertThat(account.status()).isEqualTo("inactive");
		assertThat(account.secretExpiresAt()).isNull();
	}

	@Test
	void listFilterByStatus() {
		UUID active = create("store-filter-active", null);
		UUID inactive = create("store-filter-inactive", null);
		store.deactivate(inactive, store.lock(inactive).orElseThrow(), NOW, ADMIN);
		assertThat(store.list(true, NOW)).extracting(Account::clientId).contains(active).doesNotContain(inactive);
		assertThat(store.list(false, NOW)).extracting(Account::clientId).contains(inactive).doesNotContain(active);
		assertThat(store.list(null, NOW)).extracting(Account::clientId).contains(active, inactive);
	}

	@Test
	void rolesAreTheValidGrantedAssignments() {
		UUID client = create("store-roles", null);
		long person = store.lock(client).orElseThrow();
		assign(person, "viewer", NOW.minusSeconds(1), null, "granted");
		assign(person, "auditor", NOW.minusSeconds(60), NOW, "granted");
		assign(person, "integration-writer", NOW.plusSeconds(1), null, "granted");
		assertThat(store.find(client, NOW).orElseThrow().roles()).containsExactly("viewer");
	}

	@Test
	void namesAreUniqueIgnoringCase() {
		create("store-unique", null);
		assertThatExceptionOfType(DuplicateKeyException.class)
			.isThrownBy(() -> store.insertAccount("Store-Unique", null, NOW));
	}

	@Test
	void unknownClientIsNotFound() {
		assertThat(store.find(UUID.randomUUID(), NOW)).isEmpty();
		assertThat(store.lock(UUID.randomUUID())).isEmpty();
	}

	private static UUID create(String name, String description) {
		long person = store.insertAccount(name, description, NOW);
		UUID client = UUID.randomUUID();
		store.insertSecret(client, person, "hash-1", NOW, NOW.plus(Duration.ofDays(90)), ADMIN);
		return client;
	}

	private static void assign(long person, String role, Instant validFrom, Instant validTo, String status) {
		app.update("""
				INSERT INTO role_assignments (id, person_id, role_id, valid_from, valid_to, granted_at, status,
				                              granted_by_person_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), person, role, Timestamp.from(validFrom),
				(validTo != null) ? Timestamp.from(validTo) : null, Timestamp.from(NOW.minusSeconds(3600)), status,
				granter);
	}

}
