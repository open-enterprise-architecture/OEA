package io.oea.app.entity;

import io.oea.app.security.OeaPrincipal;
import io.oea.audit.AuditEvent;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.TypedValue;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The batch write of entities (design D4; REQ-153, REQ-154, US-141 AC1): limit, strategies by
 * {@code externalId}, duplicates within the batch, and atomicity. With {@code atomic=false}
 * every item runs in its own transaction; with {@code atomic=true} all items are checked
 * first and written in one transaction, or none.
 */
@Service
public class EntityBatch {

	private final JdbcTemplate jdbc;

	private final EntityStore store;

	private final EntityWriter writer;

	private final Clock clock;

	private final EntityBatchProperties properties;

	private final TransactionTemplate transaction;

	public EntityBatch(JdbcTemplate jdbc, EntityStore store, EntityWriter writer, Clock clock,
			EntityBatchProperties properties, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.store = store;
		this.writer = writer;
		this.clock = clock;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public enum Strategy {

		UPSERT, CREATE_ONLY, SKIP_EXISTING

	}

	/**
	 * A batch request. Connections and {@code dryRun} are part of the contract but not supported
	 * yet (US-141 AC2, AC3).
	 */
	public record Request(List<EntityInput> entities, int connections, boolean dryRun, Strategy strategy,
			boolean atomic, AuditEvent.Agent agent) {

		public Request {
			entities = (entities != null) ? List.copyOf(entities) : List.of();
		}

	}

	public enum ItemStatus {

		CREATED, UPDATED, UNCHANGED, SKIPPED, ERROR

	}

	public record ItemResult(int index, ItemStatus status, Integer entityId, String externalId, List<Issue> errors,
			List<Issue> warnings) {

		static ItemResult error(int index, String externalId, List<Issue> errors, List<Issue> warnings) {
			return new ItemResult(index, ItemStatus.ERROR, null, externalId, List.copyOf(errors),
					List.copyOf(warnings));
		}

	}

	public record Result(UUID batchId, List<ItemResult> items) {

		public long count(ItemStatus status) {
			return this.items.stream().filter((item) -> item.status() == status).count();
		}

	}

	/** An item checked and locked, ready to be written; or its final result. */
	private record Prepared(int index, StoredEntity existing, Map<UUID, TypedValue> stored,
			EntityValidator.Checked checked, ItemResult result) {
	}

	/**
	 * Writes the batch.
	 *
	 * @throws BatchRejectedException if the batch as a whole is not accepted (nothing written)
	 * @throws AtomicBatchFailedException if an atomic batch has a faulty item (nothing written)
	 */
	public Result write(OeaPrincipal principal, Request request) {
		if (request.entities().size() > this.properties.maxItems()) {
			throw new BatchRejectedException("batch-too-large",
					"A batch may contain at most " + this.properties.maxItems() + " entities.");
		}
		if (request.connections() > 0) {
			throw new BatchRejectedException("not-supported", "Connections in a batch are not supported yet.");
		}
		if (request.dryRun()) {
			throw new BatchRejectedException("not-supported", "dryRun is not supported yet.");
		}
		Map<Integer, ItemResult> duplicates = duplicates(request.entities());
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock));
		EntityWriter.WriteContext context = new EntityWriter.WriteContext(UUID.randomUUID(), request.agent());
		List<ItemResult> results = request.atomic() ? writeAtomically(principal, request, rules, duplicates, context)
				: writeEach(principal, request, rules, duplicates, context);
		return new Result(context.batchId(), results);
	}

	private List<ItemResult> writeEach(OeaPrincipal principal, Request request, MetatypeRules rules,
			Map<Integer, ItemResult> duplicates, EntityWriter.WriteContext context) {
		List<ItemResult> results = new ArrayList<>();
		for (int i = 0; i < request.entities().size(); i++) {
			if (duplicates.containsKey(i)) {
				results.add(duplicates.get(i));
				continue;
			}
			int index = i;
			EntityInput input = request.entities().get(i);
			try {
				results.add(this.transaction.execute((status) -> {
					Prepared prepared = prepare(index, input, request.strategy(), rules);
					return (prepared.result() != null) ? prepared.result() : write(principal, input, prepared, context);
				}));
			}
			catch (DuplicateKeyException ex) {
				results.add(ItemResult.error(index, input.externalId(), List.of(concurrentWrite()), List.of()));
			}
		}
		return results;
	}

	private List<ItemResult> writeAtomically(OeaPrincipal principal, Request request, MetatypeRules rules,
			Map<Integer, ItemResult> duplicates, EntityWriter.WriteContext context) {
		return this.transaction.execute((status) -> {
			List<Prepared> prepared = new ArrayList<>();
			List<Issue> errors = new ArrayList<>();
			for (int i = 0; i < request.entities().size(); i++) {
				Prepared item = duplicates.containsKey(i) ? new Prepared(i, null, Map.of(), null, duplicates.get(i))
						: prepare(i, request.entities().get(i), request.strategy(), rules);
				prepared.add(item);
				if (item.result() != null && item.result().status() == ItemStatus.ERROR) {
					String path = "entities[" + i + "]";
					item.result().errors().forEach((error) -> errors.add(error.within(path)));
				}
			}
			if (!errors.isEmpty()) {
				throw new AtomicBatchFailedException(errors);
			}
			List<ItemResult> results = new ArrayList<>();
			for (Prepared item : prepared) {
				EntityInput input = request.entities().get(item.index());
				try {
					results.add((item.result() != null) ? item.result() : write(principal, input, item, context));
				}
				catch (DuplicateKeyException ex) {
					throw new AtomicBatchFailedException(
							List.of(concurrentWrite().within("entities[" + item.index() + "]")));
				}
			}
			return results;
		});
	}

	private Prepared prepare(int index, EntityInput input, Strategy strategy, MetatypeRules rules) {
		StoredEntity existing = (input.externalId() != null)
				? this.store.lockByExternalId(input.externalId()).orElse(null) : null;
		Map<UUID, TypedValue> stored = Map.of();
		if (existing != null) {
			if (existing.deleted()) {
				return finished(index, ItemResult.error(index, input.externalId(), List.of(new Issue("externalId",
						"EXTERNAL_ID_DELETED", "The external ID belongs to a deleted entity.")), List.of()));
			}
			if (strategy == Strategy.CREATE_ONLY) {
				return finished(index, ItemResult.error(index, input.externalId(), List.of(new Issue("externalId",
						"EXTERNAL_ID_EXISTS", "An entity with this external ID exists.")), List.of()));
			}
			if (strategy == Strategy.SKIP_EXISTING) {
				return finished(index, new ItemResult(index, ItemStatus.SKIPPED, existing.id(), input.externalId(),
						List.of(), List.of()));
			}
			stored = this.store.values(existing.id());
		}
		EntityValidator.Checked checked = EntityValidator.check(input, rules, existing, stored, this.store::isActive);
		if (!checked.valid()) {
			return finished(index, ItemResult.error(index, input.externalId(), checked.errors(), checked.warnings()));
		}
		return new Prepared(index, existing, stored, checked, null);
	}

	private ItemResult write(OeaPrincipal principal, EntityInput input, Prepared prepared,
			EntityWriter.WriteContext context) {
		EntityWriter.Written written = this.writer.write(principal, prepared.checked(), prepared.existing(),
				prepared.stored(), context);
		ItemStatus status = switch (written.status()) {
			case CREATED -> ItemStatus.CREATED;
			case UPDATED -> ItemStatus.UPDATED;
			case UNCHANGED -> ItemStatus.UNCHANGED;
		};
		return new ItemResult(prepared.index(), status, written.entityId(), input.externalId(), List.of(),
				prepared.checked().warnings());
	}

	/** Later occurrences of an external ID within the batch (design D4). */
	private static Map<Integer, ItemResult> duplicates(List<EntityInput> entities) {
		Map<String, Integer> first = new HashMap<>();
		Map<Integer, ItemResult> duplicates = new HashMap<>();
		for (int i = 0; i < entities.size(); i++) {
			String externalId = entities.get(i).externalId();
			if (externalId != null && first.putIfAbsent(externalId, i) != null) {
				duplicates.put(i, ItemResult.error(i, externalId, List.of(new Issue("externalId",
						"DUPLICATE_EXTERNAL_ID", "The external ID occurs earlier in the batch (item "
								+ first.get(externalId) + ").")),
						List.of()));
			}
		}
		return duplicates;
	}

	private static Prepared finished(int index, ItemResult result) {
		return new Prepared(index, null, Map.of(), null, result);
	}

	private static Issue concurrentWrite() {
		return new Issue("externalId", "CONCURRENT_WRITE",
				"Another request created an entity with this external ID at the same time; retry the item.");
	}

}
