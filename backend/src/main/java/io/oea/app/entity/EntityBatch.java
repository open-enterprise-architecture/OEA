package io.oea.app.entity;

import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
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
 * The batch write of entities and connections (design D4; REQ-153, REQ-154, US-141 AC1, AC2;
 * design D3 of us-036-connection-metatypes): limits, strategies by {@code externalId}, duplicates
 * within the batch, and atomicity. Connections are written after the entities, so that their
 * endpoints may name entities of the same batch. With {@code atomic=false} every item runs in its
 * own transaction; with {@code atomic=true} all items are checked first and written in one
 * transaction, or none.
 */
@Service
public class EntityBatch {

	private final JdbcTemplate jdbc;

	private final EntityStore store;

	private final EntityWriter writer;

	private final Clock clock;

	private final EntityBatchProperties properties;

	private final TransactionTemplate transaction;

	private final PropertyAccess access;

	public EntityBatch(JdbcTemplate jdbc, EntityStore store, EntityWriter writer, Clock clock,
			EntityBatchProperties properties, PlatformTransactionManager transactionManager, PropertyAccess access) {
		this.access = access;
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

	/** A batch request; {@code dryRun} is part of the contract but not supported yet (US-141 AC3). */
	public record Request(List<EntityInput> entities, List<ConnectionInput> connections, boolean dryRun,
			Strategy strategy, boolean atomic, AuditEvent.Agent agent) {

		public Request {
			entities = (entities != null) ? List.copyOf(entities) : List.of();
			connections = (connections != null) ? List.copyOf(connections) : List.of();
		}

	}

	public enum ItemStatus {

		CREATED, UPDATED, UNCHANGED, SKIPPED, ERROR

	}

	/** The result of an item; {@code connection} marks an item of {@code connections}. */
	public record ItemResult(int index, ItemStatus status, Integer entityId, String externalId, List<Issue> errors,
			List<Issue> warnings, boolean connection) {

		public ItemResult(int index, ItemStatus status, Integer entityId, String externalId, List<Issue> errors,
				List<Issue> warnings) {
			this(index, status, entityId, externalId, errors, warnings, false);
		}

		static ItemResult error(int index, String externalId, List<Issue> errors, List<Issue> warnings) {
			return new ItemResult(index, ItemStatus.ERROR, null, externalId, List.copyOf(errors),
					List.copyOf(warnings));
		}

		ItemResult asConnection() {
			// the connection metatype is named connectionMetatypeId in the contract
			List<Issue> renamed = this.errors.stream()
				.map((issue) -> "metatypeId".equals(issue.field())
						? new Issue("connectionMetatypeId", issue.code(), issue.message()) : issue)
				.toList();
			return new ItemResult(this.index, this.status, this.entityId, this.externalId, renamed, this.warnings,
					true);
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
		if (request.entities().size() > this.properties.maxItems()
				|| request.connections().size() > this.properties.maxItems()) {
			throw new BatchRejectedException("batch-too-large", "A batch may contain at most "
					+ this.properties.maxItems() + " entities and " + this.properties.maxItems() + " connections.");
		}
		if (request.dryRun()) {
			throw new BatchRejectedException("not-supported", "dryRun is not supported yet.");
		}
		List<EntityInput> named = new ArrayList<>(request.entities());
		request.connections().forEach((connection) -> named.add(connection.entity()));
		PropertyAccess.Snapshot access = requireWritable(principal, named);
		Map<Integer, ItemResult> duplicates = duplicates(request.entities());
		Map<Integer, ItemResult> connectionDuplicates = duplicates(
				request.connections().stream().map(ConnectionInput::entity).toList());
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock)).forWriter(writer(access));
		EntityWriter.WriteContext context = new EntityWriter.WriteContext(UUID.randomUUID(), request.agent());
		List<ItemResult> results = request.atomic()
				? writeAtomically(principal, request, rules, duplicates, connectionDuplicates, context)
				: writeEach(principal, request, rules, duplicates, connectionDuplicates, context);
		return new Result(context.batchId(), results);
	}

	private List<ItemResult> writeEach(OeaPrincipal principal, Request request, MetatypeRules rules,
			Map<Integer, ItemResult> duplicates, Map<Integer, ItemResult> connectionDuplicates,
			EntityWriter.WriteContext context) {
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
		for (int i = 0; i < request.connections().size(); i++) {
			if (connectionDuplicates.containsKey(i)) {
				results.add(connectionDuplicates.get(i).asConnection());
				continue;
			}
			int index = i;
			ConnectionInput input = request.connections().get(i);
			try {
				results.add(this.transaction.execute((status) -> {
					PreparedConnection prepared = prepareConnection(index, input, request.strategy(), rules, Map.of());
					return (prepared.result() != null) ? prepared.result()
							: writeConnection(principal, input, prepared, context);
				}));
			}
			catch (DuplicateKeyException ex) {
				results.add(ItemResult.error(index, input.entity().externalId(), List.of(concurrentWrite()), List.of())
					.asConnection());
			}
		}
		return results;
	}

	/**
	 * Creates one connection in its own transaction (createConnection, us-036-connection-metatypes
	 * D3); an existing external ID is an error as with {@code create-only}.
	 */
	/**
	 * The access of the writer; a value of a {@code role-restricted} property, set or removed,
	 * without a write role rejects the whole request before anything is written (403, audited;
	 * design D3 of us-126-role-restricted-enforcement).
	 */
	private PropertyAccess.Snapshot requireWritable(OeaPrincipal principal, List<EntityInput> inputs) {
		PropertyAccess.Snapshot access = this.access.of(principal);
		for (EntityInput input : inputs) {
			for (EntityInput.PropertyInput property : input.properties()) {
				UUID id = property.propertyDefId();
				if (id == null || !access.restricted(id)) {
					continue;
				}
				if (access.connectionScoped(id)) {
					// only an existing entity in the writer's scope (design D6 of us-127-connection-scoped-visibility)
					Integer entity = (input.externalId() != null) ? this.store.endpointByExternalId(input.externalId())
						.filter((found) -> !found.deleted())
						.map(EntityStore.Endpoint::id)
						.orElse(null) : null;
					if (entity == null || !access.writableFor(id, entity)) {
						throw new PermissionDeniedException();
					}
				}
				else if (!access.writable(id)) {
					throw new PermissionDeniedException();
				}
			}
		}
		return access;
	}

	/** Who may write a restricted property of an entity, for the validator (us-126 D3, us-127 D6). */
	private static java.util.function.BiPredicate<UUID, Integer> writer(PropertyAccess.Snapshot access) {
		return (property, entity) -> access.connectionScoped(property)
				? entity != null && access.writableFor(property, entity) : access.writable(property);
	}

	public ItemResult createConnection(OeaPrincipal principal, ConnectionInput input, AuditEvent.Agent agent) {
		PropertyAccess.Snapshot access = requireWritable(principal, List.of(input.entity()));
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock)).forWriter(writer(access));
		EntityWriter.WriteContext context = new EntityWriter.WriteContext(UUID.randomUUID(), agent);
		try {
			return this.transaction.execute((status) -> {
				PreparedConnection prepared = prepareConnection(0, input, Strategy.CREATE_ONLY, rules, Map.of());
				return (prepared.result() != null) ? prepared.result()
						: writeConnection(principal, input, prepared, context);
			});
		}
		catch (DuplicateKeyException ex) {
			return ItemResult.error(0, input.entity().externalId(), List.of(concurrentWrite()), List.of())
				.asConnection();
		}
	}

	private List<ItemResult> writeAtomically(OeaPrincipal principal, Request request, MetatypeRules rules,
			Map<Integer, ItemResult> duplicates, Map<Integer, ItemResult> connectionDuplicates,
			EntityWriter.WriteContext context) {
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
			// connections may name the entities of this batch that are about to be created
			Map<String, Pending> pending = new HashMap<>();
			for (Prepared item : prepared) {
				if (item.result() == null && item.existing() == null && item.checked().fields().externalId() != null) {
					pending.put(item.checked().fields().externalId(),
							new Pending(item.checked().fields().metatypeId(), item.checked().fields().name()));
				}
			}
			for (int i = 0; i < request.connections().size(); i++) {
				ConnectionInput connection = request.connections().get(i);
				PreparedConnection checked = connectionDuplicates.containsKey(i) ? null
						: prepareConnection(i, connection, request.strategy(), rules, pending);
				ItemResult result = (checked == null) ? connectionDuplicates.get(i) : checked.result();
				if (checked != null && result == null && checked.prepared().existing() == null
						&& connection.entity().externalId() != null) {
					// a later connection may start or end at this one (T-relationship, ADR-010)
					EntityStore.Fields fields = checked.prepared().checked().fields();
					pending.put(fields.externalId(), new Pending(fields.metatypeId(), fields.name()));
				}
				if (result != null && result.status() == ItemStatus.ERROR) {
					String path = "connections[" + i + "]";
					result.asConnection().errors().forEach((error) -> errors.add(error.within(path)));
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
			for (int i = 0; i < request.connections().size(); i++) {
				ConnectionInput input = request.connections().get(i);
				PreparedConnection item = prepareConnection(i, input, request.strategy(), rules, Map.of());
				if (item.result() != null && item.result().status() == ItemStatus.ERROR) {
					String path = "connections[" + i + "]";
					throw new AtomicBatchFailedException(
							item.result().errors().stream().map((error) -> error.within(path)).toList());
				}
				try {
					results.add((item.result() != null) ? item.result() : writeConnection(principal, input, item, context));
				}
				catch (DuplicateKeyException ex) {
					throw new AtomicBatchFailedException(List.of(concurrentWrite().within("connections[" + i + "]")));
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

	/** A metatype as named in answers: ID, name and abbreviation. */
	public record MetatypeName(UUID id, String name, String abbreviation) {
	}

	/** An endpoint of a written connection. */
	public record ConnectionEnd(int id, String name, MetatypeName metatype) {
	}

	/** A written connection with its endpoints (answer of createConnection). */
	public record Connection(int id, String name, MetatypeName metatype, String externalId, int version,
			ConnectionEnd source, ConnectionEnd target) {
	}

	/** The connection with this ID and its endpoints, or empty. */
	public java.util.Optional<Connection> connection(int id) {
		return this.store.connection(id).map((row) -> new Connection(row.id(), row.name(), metatypeName(row.metatypeId()),
				row.externalId(), row.version(), end(row.source()), end(row.target())));
	}

	private ConnectionEnd end(EntityStore.Endpoint endpoint) {
		return new ConnectionEnd(endpoint.id(), endpoint.name(), metatypeName(endpoint.metatypeId()));
	}

	private MetatypeName metatypeName(UUID id) {
		return this.jdbc.queryForObject("SELECT name, abbreviation FROM metatypes WHERE id = ?",
				(rs, n) -> new MetatypeName(id, rs.getString(1), rs.getString(2)), id);
	}

	/** An entity of an atomic batch that is valid and not yet written: a possible endpoint. */
	private record Pending(UUID metatypeId, String name) {
	}

	/** An endpoint as found: an existing entity, or a pending one without ID. */
	private record Resolved(Integer id, UUID metatypeId, String name) {
	}

	/** A connection checked and locked, with its endpoints; or its final result. */
	private record PreparedConnection(Prepared prepared, EntityWriter.Endpoints endpoints, ItemResult result) {
	}

	/** Checks a connection: strategy, endpoints, the connection metatype and its properties. */
	private PreparedConnection prepareConnection(int index, ConnectionInput input, Strategy strategy,
			MetatypeRules rules, Map<String, Pending> pending) {
		String externalId = input.entity().externalId();
		StoredEntity existing = (externalId != null) ? this.store.lockByExternalId(externalId).orElse(null) : null;
		Map<UUID, TypedValue> stored = Map.of();
		if (existing != null) {
			if (existing.deleted()) {
				return connectionResult(ItemResult.error(index, externalId, List.of(new Issue("externalId",
						"EXTERNAL_ID_DELETED", "The external ID belongs to a deleted entity.")), List.of()));
			}
			if (strategy == Strategy.CREATE_ONLY) {
				return connectionResult(ItemResult.error(index, externalId, List.of(new Issue("externalId",
						"EXTERNAL_ID_EXISTS", "An entity with this external ID exists.")), List.of()));
			}
			if (strategy == Strategy.SKIP_EXISTING) {
				return connectionResult(new ItemResult(index, ItemStatus.SKIPPED, existing.id(), externalId, List.of(),
						List.of()));
			}
			stored = this.store.values(existing.id());
		}
		List<Issue> errors = new ArrayList<>();
		Resolved source = resolve("source", input.sourceId(), input.sourceExternalId(), pending, errors);
		Resolved target = resolve("target", input.targetId(), input.targetExternalId(), pending, errors);
		MetatypeRules.Metatype metatype = rules.metatype(input.entity().metatypeId());
		if (metatype != null && metatype.connection()) {
			if (source != null && !rules.allows(metatype, true, source.metatypeId())) {
				errors.add(new Issue(endpointField("source", input.sourceId()), "SOURCE_NOT_ALLOWED",
						"The connection type does not allow this source."));
			}
			if (target != null && !rules.allows(metatype, false, target.metatypeId())) {
				errors.add(new Issue(endpointField("target", input.targetId()), "TARGET_NOT_ALLOWED",
						"The connection type does not allow this target."));
			}
		}
		if (existing != null && source != null && target != null) {
			int[] endpoints = this.store.connectionEndpoints(existing.id()).orElse(null);
			if (endpoints == null || !Integer.valueOf(endpoints[0]).equals(source.id())
					|| !Integer.valueOf(endpoints[1]).equals(target.id())) {
				errors.add(new Issue("source", "ENDPOINT_CHANGE_NOT_SUPPORTED",
						"The source and target of an existing connection cannot change."));
			}
		}
		EntityInput entity = input.entity();
		if (entity.name() == null || entity.name().isBlank()) {
			// a connection without a name is named after its endpoints (us-036-connection-metatypes D4)
			String name = (existing != null) ? existing.name()
					: (source != null && target != null) ? source.name() + " → " + target.name() : "connection";
			entity = new EntityInput(name.length() > EntityValidator.NAME_MAX_LENGTH
					? name.substring(0, EntityValidator.NAME_MAX_LENGTH) : name, entity.metatypeId(),
					entity.externalId(), entity.description(), entity.logical(), entity.lifecycle(), entity.parentId(),
					entity.properties());
		}
		EntityValidator.Checked checked = EntityValidator.check(entity, rules, existing, stored, this.store::isActive,
				true);
		errors.addAll(checked.errors());
		if (!errors.isEmpty()) {
			return connectionResult(ItemResult.error(index, externalId, errors, checked.warnings()));
		}
		EntityWriter.Endpoints endpoints = (source.id() != null && target.id() != null)
				? new EntityWriter.Endpoints(source.id(), target.id()) : null;
		return new PreparedConnection(new Prepared(index, existing, stored, checked, null), endpoints, null);
	}

	/** Finds an endpoint by ID or external ID, also among the pending entities of an atomic batch. */
	private Resolved resolve(String role, Integer id, String externalId, Map<String, Pending> pending,
			List<Issue> errors) {
		String code = role.toUpperCase(java.util.Locale.ROOT);
		boolean byExternalId = externalId != null && !externalId.isBlank();
		if (id == null && !byExternalId) {
			errors.add(new Issue(role, code + "_REQUIRED", "The connection needs a " + role + "."));
			return null;
		}
		if (id != null && byExternalId) {
			errors.add(new Issue(role, code + "_AMBIGUOUS",
					"Name the " + role + " either by ID or by external ID, not both."));
			return null;
		}
		java.util.Optional<EntityStore.Endpoint> found = (id != null) ? this.store.endpoint(id)
				: this.store.endpointByExternalId(externalId);
		if (found.isPresent() && !found.get().deleted()) {
			return new Resolved(found.get().id(), found.get().metatypeId(), found.get().name());
		}
		if (found.isEmpty() && byExternalId && pending.containsKey(externalId)) {
			Pending entity = pending.get(externalId);
			return new Resolved(null, entity.metatypeId(), entity.name());
		}
		errors.add(new Issue(endpointField(role, id), code + "_NOT_FOUND", "No active entity is the " + role + "."));
		return null;
	}

	private static String endpointField(String role, Integer id) {
		return (id != null) ? role + "Id" : role + "ExternalId";
	}

	private static PreparedConnection connectionResult(ItemResult result) {
		return new PreparedConnection(null, null, result.asConnection());
	}

	private ItemResult writeConnection(OeaPrincipal principal, ConnectionInput input, PreparedConnection prepared,
			EntityWriter.WriteContext context) {
		EntityWriter.Written written = this.writer.write(principal, prepared.prepared().checked(),
				prepared.prepared().existing(), prepared.prepared().stored(), context, prepared.endpoints());
		ItemStatus status = switch (written.status()) {
			case CREATED -> ItemStatus.CREATED;
			case UPDATED -> ItemStatus.UPDATED;
			case UNCHANGED -> ItemStatus.UNCHANGED;
		};
		return new ItemResult(prepared.prepared().index(), status, written.entityId(), input.entity().externalId(),
				List.of(), prepared.prepared().checked().warnings(), true);
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
