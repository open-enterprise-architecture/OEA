package io.oea.api.entity;

import io.oea.api.BatchValidationErrors;
import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.EntitiesWriteApi;
import io.oea.api.generated.model.BatchWriteItemResult;
import io.oea.api.generated.model.BatchWriteRequest;
import io.oea.api.generated.model.BatchWriteResponse;
import io.oea.api.generated.model.BatchWriteResponseSummary;
import io.oea.api.generated.model.ConnectionDetail;
import io.oea.api.generated.model.ConnectionEndpoint;
import io.oea.api.generated.model.ConnectionWrite;
import io.oea.api.generated.model.EntityWrite;
import io.oea.api.generated.model.MetatypeSummary;
import io.oea.app.entity.ConnectionInput;
import io.oea.app.entity.ConnectionRejectedException;
import io.oea.app.entity.EntityBatch;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.Issue;
import io.oea.app.metamodel.PropertyVisibility;
import io.oea.app.security.OeaPrincipal;
import io.oea.audit.AuditEvent;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** The batch write of entities (US-141 AC1, design D4 of us-141-entity-batch-upsert). */
@RestController
public class EntityBatchController implements EntitiesWriteApi {

	/** Path of the batch write. */
	public static final String BATCH_PATH = "/api/v1/entities/batch";

	private static final int AGENT_FIELD_MAX_LENGTH = 100;

	private final EntityBatch batch;

	private final PropertyVisibility visibility;

	public EntityBatchController(EntityBatch batch, PropertyVisibility visibility) {
		this.batch = batch;
		this.visibility = visibility;
	}

	@Override
	@ContractViolationResponse(shape = ContractViolationResponse.Shape.BATCH_ERRORS, status = 422,
			title = "Batch rejected", detail = "The batch does not meet the contract; no item was processed.")
	@ConditionsEnforcedByHandler(value = "PropertyVisibility.requireScopeAssignment: a connection of a scoping type "
			+ "needs visibility-scope:assign; the whole batch is rejected", probeRole = "integration-writer",
			probeBody = """
					{"connections": [{"connectionMetatypeId": "00000000-0000-4000-8000-0000000c0de1",
					 "sourceId": 1, "targetId": 2}]}""")
	public ResponseEntity<BatchWriteResponse> batchWriteEntities(BatchWriteRequest request, Boolean dryRun,
			String xAgentId, String xAgentRun) {
		EntityBatch.Strategy strategy = switch (request.getStrategy()) {
			case UPSERT -> EntityBatch.Strategy.UPSERT;
			case CREATE_ONLY -> EntityBatch.Strategy.CREATE_ONLY;
			case SKIP_EXISTING -> EntityBatch.Strategy.SKIP_EXISTING;
		};
		boolean atomic = Boolean.TRUE.equals(request.getAtomic());
		if (request.getConnections() != null) {
			this.visibility.requireScopeAssignment(principal(),
					request.getConnections().stream().map(ConnectionWrite::getConnectionMetatypeId).toList());
		}
		List<EntityInput> entities = (request.getEntities() != null)
				? request.getEntities().stream().map(EntityBatchController::input).toList() : List.of();
		List<ConnectionInput> connections = (request.getConnections() != null)
				? request.getConnections().stream().map(EntityBatchController::connection).toList() : List.of();
		EntityBatch.Result result = this.batch.write(principal(), new EntityBatch.Request(entities, connections,
				Boolean.TRUE.equals(dryRun), strategy, atomic,
				new AuditEvent.Agent(informational(xAgentId), informational(xAgentRun))));
		BatchWriteResponseSummary summary = new BatchWriteResponseSummary()
			.created((int) result.count(EntityBatch.ItemStatus.CREATED))
			.updated((int) result.count(EntityBatch.ItemStatus.UPDATED))
			.unchanged((int) result.count(EntityBatch.ItemStatus.UNCHANGED))
			.skipped((int) result.count(EntityBatch.ItemStatus.SKIPPED))
			.errors((int) result.count(EntityBatch.ItemStatus.ERROR));
		return ResponseEntity.ok(new BatchWriteResponse(request.getStrategy().getValue(), atomic, summary,
				result.items().stream().map(EntityBatchController::item).toList()));
	}

	@Override
	@ContractViolationResponse(shape = ContractViolationResponse.Shape.BATCH_ERRORS, status = 422,
			title = "Connection rejected", detail = "The connection does not meet the contract; nothing was written.")
	@ConditionsEnforcedByHandler(value = "PropertyVisibility.requireScopeAssignment: a connection of a scoping type "
			+ "needs visibility-scope:assign", probeRole = "integration-writer", probeBody = """
					{"connectionMetatypeId": "00000000-0000-4000-8000-0000000c0de1", "sourceId": 1, "targetId": 2}""")
	public ResponseEntity<ConnectionDetail> createConnection(ConnectionWrite request) {
		this.visibility.requireScopeAssignment(principal(), List.of(request.getConnectionMetatypeId()));
		EntityBatch.ItemResult result = this.batch.createConnection(principal(), connection(request),
				new AuditEvent.Agent(null, null));
		if (result.status() == EntityBatch.ItemStatus.ERROR) {
			throw new ConnectionRejectedException(result.errors());
		}
		EntityBatch.Connection connection = this.batch.connection(result.entityId()).orElseThrow();
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new ConnectionDetail(connection.id(), connection.name(), summary(connection.metatype()),
					connection.version(), endpoint(connection.source()), endpoint(connection.target()))
				.externalId(connection.externalId()));
	}

	private static ConnectionEndpoint endpoint(EntityBatch.ConnectionEnd end) {
		return new ConnectionEndpoint(end.id(), end.name(), summary(end.metatype()));
	}

	private static MetatypeSummary summary(EntityBatch.MetatypeName metatype) {
		return new MetatypeSummary(metatype.id(), metatype.name(),
				(metatype.abbreviation() != null) ? metatype.abbreviation() : "");
	}

	private static ConnectionInput connection(ConnectionWrite connection) {
		List<EntityInput.PropertyInput> properties = (connection.getProperties() != null) ? connection.getProperties()
			.stream()
			.map((property) -> new EntityInput.PropertyInput(property.getPropertyDefId(), property.getValue()))
			.toList() : List.of();
		return new ConnectionInput(new EntityInput(connection.getName(), connection.getConnectionMetatypeId(),
				connection.getExternalId(), connection.getDescription(), null, null, null, properties),
				connection.getSourceId(), connection.getSourceExternalId(), connection.getTargetId(),
				connection.getTargetExternalId());
	}

	private static EntityInput input(EntityWrite entity) {
		List<EntityInput.PropertyInput> properties = (entity.getProperties() != null) ? entity.getProperties()
			.stream()
			.map((property) -> new EntityInput.PropertyInput(property.getPropertyDefId(), property.getValue()))
			.toList() : List.of();
		return new EntityInput(entity.getName(), entity.getMetatypeId(), entity.getExternalId(),
				entity.getDescription(), entity.getIsLogical(), entity.getLifecycle(), entity.getParentId(),
				properties);
	}

	private static BatchWriteItemResult item(EntityBatch.ItemResult item) {
		BatchWriteItemResult result = new BatchWriteItemResult(item.index(),
				BatchWriteItemResult.StatusEnum.fromValue(item.status().name().toLowerCase(Locale.ROOT)))
			.itemType(item.connection() ? BatchWriteItemResult.ItemTypeEnum.CONNECTION
					: BatchWriteItemResult.ItemTypeEnum.ENTITY)
			.id(item.entityId())
			.externalId(item.externalId())
			.errors(item.errors().stream().map(BatchValidationErrors::validationError).toList())
			.warnings(item.warnings().stream().map(BatchValidationErrors::validationError).toList());
		if (!item.errors().isEmpty()) {
			result.error(item.errors().stream().map(Issue::message).collect(Collectors.joining(" ")));
		}
		return result;
	}

	/**
	 * ADR-033 §7: agent headers are informational only; control characters are removed and the
	 * value is limited to 100 characters.
	 */
	static String informational(String header) {
		if (header == null) {
			return null;
		}
		String cleaned = header.replaceAll("\\p{Cntrl}", "").strip();
		if (cleaned.isEmpty()) {
			return null;
		}
		return (cleaned.length() > AGENT_FIELD_MAX_LENGTH) ? cleaned.substring(0, AGENT_FIELD_MAX_LENGTH) : cleaned;
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
