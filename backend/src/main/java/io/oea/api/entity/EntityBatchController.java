package io.oea.api.entity;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.EntitiesWriteApi;
import io.oea.api.generated.model.BatchWriteItemResult;
import io.oea.api.generated.model.BatchWriteRequest;
import io.oea.api.generated.model.BatchWriteResponse;
import io.oea.api.generated.model.BatchWriteResponseSummary;
import io.oea.api.generated.model.EntityWrite;
import io.oea.api.generated.model.ValidationError;
import io.oea.app.entity.EntityBatch;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.Issue;
import io.oea.app.security.OeaPrincipal;
import io.oea.audit.AuditEvent;
import io.oea.config.security.ConditionsNotReachable;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
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

	public EntityBatchController(EntityBatch batch) {
		this.batch = batch;
	}

	@Override
	@ContractViolationResponse(shape = ContractViolationResponse.Shape.BATCH_ERRORS, status = 422,
			title = "Batch rejected", detail = "The batch does not meet the contract; no item was processed.")
	@ConditionsNotReachable("batches with connections are rejected with 422 until US-141 AC2")
	public ResponseEntity<BatchWriteResponse> batchWriteEntities(BatchWriteRequest request, Boolean dryRun,
			String xAgentId, String xAgentRun) {
		EntityBatch.Strategy strategy = switch (request.getStrategy()) {
			case UPSERT -> EntityBatch.Strategy.UPSERT;
			case CREATE_ONLY -> EntityBatch.Strategy.CREATE_ONLY;
			case SKIP_EXISTING -> EntityBatch.Strategy.SKIP_EXISTING;
		};
		boolean atomic = Boolean.TRUE.equals(request.getAtomic());
		List<EntityInput> entities = (request.getEntities() != null)
				? request.getEntities().stream().map(EntityBatchController::input).toList() : List.of();
		int connections = (request.getConnections() != null) ? request.getConnections().size() : 0;
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
			.itemType(BatchWriteItemResult.ItemTypeEnum.ENTITY)
			.id(item.entityId())
			.externalId(item.externalId())
			.errors(item.errors().stream().map(EntityBatchController::validationError).toList())
			.warnings(item.warnings().stream().map(EntityBatchController::validationError).toList());
		if (!item.errors().isEmpty()) {
			result.error(item.errors().stream().map(Issue::message).collect(Collectors.joining(" ")));
		}
		return result;
	}

	public static ValidationError validationError(Issue issue) {
		return new ValidationError(issue.field(), issue.message()).code(issue.code());
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
