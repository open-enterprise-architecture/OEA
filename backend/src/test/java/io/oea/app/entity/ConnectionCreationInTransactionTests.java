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
import org.assertj.core.groups.Tuple;
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
 * {@link EntityBatch#createConnectionsInTransaction} (design D3 step 4 of us-074-diagram-connections):
 * the creation of new connections in the transaction of the caller, with the connection steps of an
 * atomic batch create, on an own database. A mandatory property without default value is a warning
 * there, an error in the batch.
 */
@SpringBootTest
class ConnectionCreationInTransactionTests {

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

	private UUID component;

	private UUID process;

	private UUID flow;

	private UUID incompleteFlow;

	private int erp;

	private int crm;

	private int approval;

	private String tag;

	@BeforeEach
	void instance() {
		this.tag = UUID.randomUUID().toString().substring(0, 8);
		long account = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', ?, 'active')
				RETURNING id""", Long.class, "Flow Writer " + this.tag);
		this.principal = OeaPrincipal.serviceAccount(account, UUID.randomUUID(), "Flow Writer " + this.tag,
				Instant.now());
		this.component = metatype("Component " + this.tag, false);
		this.process = metatype("Process " + this.tag, false);
		// "Flow": from a component to a component, from a component to a process
		this.flow = metatype("Flow " + this.tag, true);
		this.incompleteFlow = metatype("Incomplete Flow " + this.tag, true);
		for (UUID type : List.of(this.flow, this.incompleteFlow)) {
			this.jdbc.update("""
					INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
					VALUES (?, 'source', ?), (?, 'target', ?), (?, 'target', ?)""", type, this.component, type,
					this.component, type, this.process);
		}
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string') RETURNING id""", UUID.class, "Protocol " + this.tag,
				this.tag);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'mandatory', DATE '2026-01-01', 'organization')""", this.incompleteFlow, property);
		this.erp = entity("SAP ERP " + this.tag, this.component);
		this.crm = entity("CRM Cloud " + this.tag, this.component);
		this.approval = entity("Approval " + this.tag, this.process);
	}

	@Test
	void failsWithoutASurroundingTransaction() {
		assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(() -> this.batch
			.createConnectionsInTransaction(this.principal, List.of(input(this.flow, this.erp, this.crm)),
					AuditEvent.Agent.NONE));
		assertThat(connections(this.flow)).isZero();
	}

	@Test
	void createsAConnectionBetweenExistingEntitiesAuditedWithTheBatchIdAndNamedAfterItsEndpoints() {
		EntityBatch.Result result = inTransaction(() -> this.batch.createConnectionsInTransaction(this.principal,
				List.of(input(this.flow, this.erp, this.crm), input(this.flow, this.crm, this.approval)),
				new AuditEvent.Agent("canvas", "run-1")));
		assertThat(result.items()).extracting(EntityBatch.ItemResult::status)
			.containsExactly(EntityBatch.ItemStatus.CREATED, EntityBatch.ItemStatus.CREATED);
		int first = result.items().get(0).entityId();
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, first))
			.isEqualTo("SAP ERP " + this.tag + " → CRM Cloud " + this.tag);
		assertThat(this.jdbc.queryForObject("""
				SELECT source_entity_id || '>' || target_entity_id FROM entity_connections WHERE entity_id = ?""",
				String.class, first)).isEqualTo(this.erp + ">" + this.crm);
		assertThat(this.jdbc.queryForList("""
				SELECT details ->> 'batchId' FROM audit.events WHERE event_type = 'entity.created'
				AND entity_id IN (?, ?)""", String.class, first, result.items().get(1).entityId()))
			.containsExactly(result.batchId().toString(), result.batchId().toString());
		assertThat(this.jdbc.queryForObject(
				"SELECT agent_label || '|' || agent_run FROM audit.events WHERE event_type = 'entity.created' AND entity_id = ?",
				String.class, first)).isEqualTo("canvas|run-1");
	}

	@Test
	void aGivenNameIsKeptAndABlankOneIsReplaced() {
		EntityBatch.Result result = inTransaction(() -> this.batch.createConnectionsInTransaction(this.principal,
				List.of(input(this.flow, "Order flow", this.erp, this.crm), input(this.flow, "  ", this.erp, this.crm)),
				AuditEvent.Agent.NONE));
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class,
				result.items().get(0).entityId())).isEqualTo("Order flow");
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class,
				result.items().get(1).entityId())).isEqualTo("SAP ERP " + this.tag + " → CRM Cloud " + this.tag);
	}

	@Test
	void aMissingMandatoryValueIsAWarningHereAndAnErrorInTheBatch() {
		EntityBatch.Result result = inTransaction(() -> this.batch.createConnectionsInTransaction(this.principal,
				List.of(input(this.incompleteFlow, this.erp, this.crm)), AuditEvent.Agent.NONE));
		EntityBatch.ItemResult item = result.items().get(0);
		assertThat(item.status()).isEqualTo(EntityBatch.ItemStatus.CREATED);
		assertThat(item.warnings()).extracting(Issue::code).containsExactly("MANDATORY_MISSING");
		assertThat(connections(this.incompleteFlow)).isOne();
		// the batch keeps rejecting it
		EntityBatch.Result strict = this.batch.write(this.principal,
				new EntityBatch.Request(List.of(), List.of(input(this.incompleteFlow, this.crm, this.erp)), false,
						EntityBatch.Strategy.CREATE_ONLY, false, AuditEvent.Agent.NONE));
		assertThat(strict.items().get(0).status()).isEqualTo(EntityBatch.ItemStatus.ERROR);
		assertThat(strict.items().get(0).errors()).extracting(Issue::code).contains("MANDATORY_MISSING");
		assertThat(connections(this.incompleteFlow)).isOne();
	}

	@Test
	void everyFaultyInputIsCollectedWithItsPathAndNothingIsWritten() {
		int ghost = 999_999_999;
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.createConnectionsInTransaction(this.principal,
						List.of(input(this.flow, this.erp, this.crm),
								// a process may be the target of "Flow" but not its source
								input(this.flow, this.approval, this.crm), input(this.flow, ghost, this.crm),
								input(this.flow, this.erp, ghost)),
						AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::field, Issue::code)
				.containsExactly(Tuple.tuple("connections[1].sourceId", "SOURCE_NOT_ALLOWED"),
						Tuple.tuple("connections[2].sourceId", "SOURCE_NOT_FOUND"),
						Tuple.tuple("connections[3].targetId", "TARGET_NOT_FOUND")));
		assertThat(connections(this.flow)).isZero();
	}

	@Test
	void aTargetThatTheTypeDoesNotAllowIsRejected() {
		UUID narrow = metatype("Narrow " + this.tag, true);
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, 'source', ?), (?, 'target', ?)""", narrow, this.component, narrow, this.component);
		assertThatExceptionOfType(AtomicBatchFailedException.class).isThrownBy(() -> inTransaction(
				() -> this.batch.createConnectionsInTransaction(this.principal,
						List.of(input(narrow, this.erp, this.approval)), AuditEvent.Agent.NONE)))
			.satisfies((ex) -> assertThat(ex.getErrors()).extracting(Issue::field, Issue::code)
				.containsExactly(Tuple.tuple("connections[0].targetId", "TARGET_NOT_ALLOWED")));
		assertThat(connections(narrow)).isZero();
	}

	@Test
	void aRollbackOfTheCallerRemovesTheCreatedConnectionsAndTheirAudit() {
		new TransactionTemplate(this.transactions).executeWithoutResult((status) -> {
			this.batch.createConnectionsInTransaction(this.principal, List.of(input(this.flow, this.erp, this.crm)),
					AuditEvent.Agent.NONE);
			assertThat(connections(this.flow)).isOne();
			status.setRollbackOnly();
		});
		assertThat(connections(this.flow)).isZero();
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' AND entity_name LIKE ?""",
				Integer.class, "SAP ERP " + this.tag + " →%")).isZero();
	}

	@Test
	void theBatchApiIsUnchanged() {
		EntityBatch.Result result = this.batch.write(this.principal,
				new EntityBatch.Request(List.of(), List.of(input(this.flow, this.erp, this.crm)), false,
						EntityBatch.Strategy.CREATE_ONLY, true, AuditEvent.Agent.NONE));
		assertThat(result.items().get(0).status()).isEqualTo(EntityBatch.ItemStatus.CREATED);
		assertThat(connections(this.flow)).isOne();
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(this.transactions).execute((status) -> action.get());
	}

	private static ConnectionInput input(UUID metatype, int source, int target) {
		return input(metatype, null, source, target);
	}

	private static ConnectionInput input(UUID metatype, String name, int source, int target) {
		return new ConnectionInput(new EntityInput(name, metatype, null, null, null, null, null, List.of()), source,
				null, target, null);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name, connection);
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.principal.personId());
	}

	private int connections(UUID metatype) {
		return this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE metatype_id = ? AND deleted_at IS NULL",
				Integer.class, metatype);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-connection-creation").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
