package io.oea.app.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.security.OeaPrincipal;
import io.oea.audit.AuditEvent;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link EntityBatch#updateInTransaction} (design D2 of us-074-diagram-entity-details, issue #69):
 * the update of existing entities with the expected version in the transaction of the caller, with
 * the steps of an atomic batch update, on an own database.
 */
@SpringBootTest
class EntityUpdateInTransactionTests {

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
	private EntityBatch batch;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactions;

	private OeaPrincipal principal;

	private long account;

	private UUID plain;

	@BeforeEach
	void instance() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		this.account = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', ?, 'active')
				RETURNING id""", Long.class, "Update Writer " + tag);
		this.principal = OeaPrincipal.serviceAccount(this.account, UUID.randomUUID(), "Update Writer " + tag,
				Instant.now());
		this.plain = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?) RETURNING id""",
				UUID.class, "Plain " + tag);
	}

	@Test
	void failsWithoutASurroundingTransaction() {
		int id = entity("No Transaction");
		assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(
				() -> this.batch.updateInTransaction(this.principal, List.of(update(id, 1, "Changed")), AuditEvent.Agent.NONE));
		assertThat(name(id)).isEqualTo("No Transaction");
	}

	@Test
	void updatesWithOneBatchIdTheNextVersionAndTheAgentFields() {
		int first = entity("First");
		int second = entity("Second");
		EntityBatch.Result result = inTransaction(() -> this.batch.updateInTransaction(this.principal,
				List.of(update(first, 1, "First Changed"), update(second, 1, "Second Changed")),
				new AuditEvent.Agent("canvas", "run-2")));
		assertThat(result.items()).extracting(EntityBatch.ItemResult::status)
			.containsExactly(EntityBatch.ItemStatus.UPDATED, EntityBatch.ItemStatus.UPDATED);
		assertThat(version(first)).isEqualTo(2);
		assertThat(name(second)).isEqualTo("Second Changed");
		assertThat(this.jdbc.queryForList("""
				SELECT details ->> 'batchId' FROM audit.events WHERE event_type = 'entity.updated'
				AND entity_id IN (?, ?)""", String.class, first, second))
			.containsExactly(result.batchId().toString(), result.batchId().toString());
		assertThat(this.jdbc.queryForObject(
				"SELECT agent_label || '|' || agent_run FROM audit.events WHERE event_type = 'entity.updated' AND entity_id = ?",
				String.class, first)).isEqualTo("canvas|run-2");
	}

	@Test
	void anUpdateWithoutAnEffectiveChangeIsUnchangedButItsVersionIsChecked() {
		int id = entity("Same");
		EntityBatch.Result result = inTransaction(() -> this.batch.updateInTransaction(this.principal,
				List.of(update(id, 1, "Same")), AuditEvent.Agent.NONE));
		assertThat(result.items().get(0).status()).isEqualTo(EntityBatch.ItemStatus.UNCHANGED);
		assertThat(version(id)).isOne();
		assertThatExceptionOfType(EntityVersionConflictException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.updateInTransaction(this.principal, List.of(update(id, 2, "Same")), AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getConflicts()).containsExactly(
					new EntityVersionConflictException.Conflict(0, id, 1)));
	}

	@Test
	void aStaleVersionNamesEveryStaleEntityAndWritesNothing() {
		int first = entity("Stale One");
		int second = entity("Stale Two");
		int third = entity("Fresh");
		this.jdbc.update("UPDATE entities SET version = 3 WHERE id IN (?, ?)", first, second);
		assertThatExceptionOfType(EntityVersionConflictException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.updateInTransaction(this.principal, List.of(update(first, 1, "A"), update(third, 1, "B"),
						update(second, 2, "C")), AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getConflicts()).extracting(EntityVersionConflictException.Conflict::index,
					EntityVersionConflictException.Conflict::entityId,
					EntityVersionConflictException.Conflict::currentVersion)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(0, first, 3),
						org.assertj.core.groups.Tuple.tuple(2, second, 3)));
		assertThat(name(third)).isEqualTo("Fresh");
		assertThat(name(first)).isEqualTo("Stale One");
	}

	@Test
	void aDeletedOrUnknownEntityAndABlankNameGiveIssuesWithTheirPaths() {
		int live = entity("Live");
		int gone = entity("Gone");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.account, gone);
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.updateInTransaction(this.principal,
						List.of(update(live, 1, "Renamed"), update(gone, 1, "X"), update(Integer.MAX_VALUE, 1, "Y")),
						AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::field, Issue::code)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("entityUpdates[1].entityId", "ENTITY_NOT_FOUND"),
						org.assertj.core.groups.Tuple.tuple("entityUpdates[2].entityId", "ENTITY_NOT_FOUND")));
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.updateInTransaction(this.principal, List.of(update(live, 1, "  ")),
						AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::field, Issue::code)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("entityUpdates[0].name", "REQUIRED")));
		assertThat(name(live)).isEqualTo("Live");
	}

	@Test
	void aStructuringAidIsNotChangedHere() {
		int aid = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, 'An Aid', ?) RETURNING id""",
				Integer.class, io.oea.core.metamodel.BuiltInMetatypes.STRUCTURING_AID, this.account);
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.updateInTransaction(this.principal, List.of(update(aid, 1, "Renamed Aid")),
						AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::code).containsExactly("METATYPE_NOT_ALLOWED"));
	}

	@Test
	void aRollbackOfTheCallerRemovesTheChangeAndItsAudit() {
		int id = entity("Rolled Back");
		new TransactionTemplate(this.transactions).executeWithoutResult((status) -> {
			this.batch.updateInTransaction(this.principal, List.of(update(id, 1, "Rolled Back Changed")),
					AuditEvent.Agent.NONE);
			assertThat(name(id)).isEqualTo("Rolled Back Changed");
			status.setRollbackOnly();
		});
		assertThat(name(id)).isEqualTo("Rolled Back");
		assertThat(version(id)).isOne();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.updated' AND entity_id = ?",
				Integer.class, id)).isZero();
	}

	@Test
	void noUpdatesWriteNothing() {
		assertThat(inTransaction(() -> this.batch.updateInTransaction(this.principal, List.of(), AuditEvent.Agent.NONE))
			.items()).isEmpty();
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(this.transactions).execute((status) -> action.get());
	}

	private static EntityBatch.EntityUpdate update(int id, int version, String name) {
		return new EntityBatch.EntityUpdate(id, version, name, null, List.of());
	}

	private int entity(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				this.plain, name, this.account);
	}

	private String name(int id) {
		return this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, id);
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-entity-update").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
