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
 * {@link EntityBatch#createInTransaction} (design D3 of us-074-diagram-canvas): the creation of new
 * entities in the transaction of the caller, with the steps of an atomic batch create, on an own
 * database. A mandatory property without default value is a warning there, an error in the batch.
 */
@SpringBootTest
class EntityCreationInTransactionTests {

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

	private UUID plain;

	private UUID withMandatory;

	@BeforeEach
	void instance() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		long account = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', ?, 'active')
				RETURNING id""", Long.class, "Canvas Writer " + tag);
		this.principal = OeaPrincipal.serviceAccount(account, UUID.randomUUID(), "Canvas Writer " + tag, Instant.now());
		this.plain = metatype("Plain " + tag);
		this.withMandatory = metatype("Mandatory " + tag);
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string') RETURNING id""", UUID.class, "Owner " + tag, tag);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'mandatory', DATE '2026-01-01', 'organization')""", this.withMandatory, property);
	}

	@Test
	void failsWithoutASurroundingTransaction() {
		assertThatExceptionOfType(IllegalTransactionStateException.class)
			.isThrownBy(() -> this.batch.createInTransaction(this.principal, List.of(input("No Transaction", this.plain)),
					AuditEvent.Agent.NONE));
		assertThat(count("No Transaction")).isZero();
	}

	@Test
	void createsEntitiesLikeAnAtomicBatchWithOneBatchIdAndTheAgentFields() {
		EntityBatch.Result result = inTransaction(() -> this.batch.createInTransaction(this.principal,
				List.of(input("First Canvas", this.plain), input("Second Canvas", this.plain)),
				new AuditEvent.Agent("canvas", "run-1")));
		assertThat(result.items()).extracting(EntityBatch.ItemResult::status)
			.containsExactly(EntityBatch.ItemStatus.CREATED, EntityBatch.ItemStatus.CREATED);
		int first = result.items().get(0).entityId();
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, first)).isOne();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_version_headers WHERE entity_id = ?",
				Integer.class, first)).isOne();
		assertThat(this.jdbc.queryForList("""
				SELECT details ->> 'batchId' FROM audit.events WHERE event_type = 'entity.created'
				AND entity_id IN (?, ?)""", String.class, first, result.items().get(1).entityId()))
			.containsExactly(result.batchId().toString(), result.batchId().toString());
		assertThat(this.jdbc.queryForObject(
				"SELECT agent_label || '|' || agent_run FROM audit.events WHERE event_type = 'entity.created' AND entity_id = ?",
				String.class, first)).isEqualTo("canvas|run-1");
		assertThat(this.jdbc.queryForList("SELECT field_name FROM entity_system_field_history WHERE entity_id = ?",
				String.class, first)).isEmpty();
	}

	@Test
	void aBlankNameAndAnUnknownMetatypeGiveTheIssuesOfTheBatchWithTheirPaths() {
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.createInTransaction(this.principal, List.of(input("Valid Canvas", this.plain),
						input("  ", this.plain), input("Unknown Type", UUID.randomUUID())), AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::field, Issue::code)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("entities[1].name", "REQUIRED"),
						org.assertj.core.groups.Tuple.tuple("entities[2].metatypeId", "METATYPE_NOT_FOUND")));
		assertThat(count("Valid Canvas")).isZero();
	}

	@Test
	void aNameOf255CharactersIsAcceptedAndOneOf256IsRejected() {
		inTransaction(() -> this.batch.createInTransaction(this.principal, List.of(input("n".repeat(255), this.plain)),
				AuditEvent.Agent.NONE));
		assertThat(count("n".repeat(255))).isOne();
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.createInTransaction(this.principal, List.of(input("m".repeat(256), this.plain)),
						AuditEvent.Agent.NONE)));
		assertThat(count("m".repeat(256))).isZero();
	}

	@Test
	void aMandatoryPropertyWithoutDefaultIsAWarningHereAndAnErrorInTheBatch() {
		EntityBatch.Result result = inTransaction(() -> this.batch.createInTransaction(this.principal,
				List.of(input("Incomplete Canvas", this.withMandatory)), AuditEvent.Agent.NONE));
		EntityBatch.ItemResult item = result.items().get(0);
		assertThat(item.status()).isEqualTo(EntityBatch.ItemStatus.CREATED);
		assertThat(item.warnings()).extracting(Issue::code).containsExactly("MANDATORY_MISSING");
		assertThat(count("Incomplete Canvas")).isOne();
		// the batch path keeps rejecting it
		EntityBatch.Result strict = this.batch.write(this.principal, new EntityBatch.Request(
				List.of(input("Incomplete Batch", this.withMandatory)), List.of(), false,
				EntityBatch.Strategy.CREATE_ONLY, false, AuditEvent.Agent.NONE));
		assertThat(strict.items().get(0).status()).isEqualTo(EntityBatch.ItemStatus.ERROR);
		assertThat(strict.items().get(0).errors()).extracting(Issue::code).containsExactly("MANDATORY_MISSING");
		assertThat(count("Incomplete Batch")).isZero();
	}

	@Test
	void aStructuringAidIsNotCreatedHere() {
		UUID aid = io.oea.core.metamodel.BuiltInMetatypes.STRUCTURING_AID;
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.createInTransaction(this.principal, List.of(input("An Aid", aid)), AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::code).containsExactly("METATYPE_NOT_ALLOWED"));
	}

	@Test
	void aRollbackOfTheCallerRemovesTheCreatedEntitiesAndTheirAudit() {
		TransactionTemplate template = new TransactionTemplate(this.transactions);
		template.executeWithoutResult((status) -> {
			this.batch.createInTransaction(this.principal, List.of(input("Rolled Back Canvas", this.plain)),
					AuditEvent.Agent.NONE);
			assertThat(count("Rolled Back Canvas")).isOne();
			status.setRollbackOnly();
		});
		assertThat(count("Rolled Back Canvas")).isZero();
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' AND entity_name = 'Rolled Back Canvas'",
				Integer.class)).isZero();
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(this.transactions).execute((status) -> action.get());
	}

	private static EntityInput input(String name, UUID metatype) {
		return new EntityInput(name, metatype, null, null, null, null, null, List.of());
	}

	private UUID metatype(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?) RETURNING id""",
				UUID.class, name);
	}

	private int count(String name) {
		return this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class, name);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-entity-creation").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
