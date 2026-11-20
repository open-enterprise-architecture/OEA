package io.oea.app.entity;

import io.oea.app.metamodel.PropertyVisibility;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityDeletionStore;
import io.oea.core.entity.EntityDeletionStore.Row;
import io.oea.core.entity.EntityHierarchyStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.MetatypeRuleStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Undeletes an entity with the cascade its deletion took along (design D3 of s17-entity-delete;
 * ADR-019). The set is the entity, its descendants that were deleted with the same origin at the
 * same time, and the connections of that cascade with an endpoint among them; rows deleted
 * earlier or by another deletion are never part of it. Versions stay, except for a parent reset.
 * The caller has locked the entity, checked its version and its permission; the hierarchy lock
 * and the checks of the set are taken here. Shared by the operation {@code restoreDeletedEntity}
 * and the batch upsert of the external ID of a deleted entity (#70).
 */
@Service
public class EntityUndeletion {

	/** The other endpoint of a connection is still deleted. */
	public static final String ENDPOINT_DELETED = "endpoint-deleted";

	/** The child of an automatic composition connection has another parent. */
	public static final String PARENT_CHANGED = "parent-changed";

	/** The deepest level of the hierarchy; the root level is level 1 (REQ-141). */
	static final int MAX_LEVEL = 10;

	static final String PARENT_DELETED_REASON = "Parent deleted";

	static final String TOO_DEEP_REASON = "Hierarchy too deep";

	private final EntityDeletionStore deletions;

	private final EntityStore entities;

	private final EntityHierarchyStore hierarchy;

	private final PropertyVisibility visibility;

	private final MetatypeRuleStore ruleStore;

	private final AuditWriter audit;

	private final Clock clock;

	EntityUndeletion(EntityDeletionStore deletions, EntityStore entities, EntityHierarchyStore hierarchy,
			PropertyVisibility visibility, MetatypeRuleStore ruleStore, AuditWriter audit, Clock clock) {
		this.deletions = deletions;
		this.entities = entities;
		this.hierarchy = hierarchy;
		this.visibility = visibility;
		this.ruleStore = ruleStore;
		this.audit = audit;
		this.clock = clock;
	}

	/** A connection of the cascade that stays deleted, with the reason. */
	public record NotRestored(int entityId, String reason) {
	}

	/** The outcome: everything restored (the entity first), the entities placed at the top level and what stays deleted. */
	public record Result(List<Integer> restored, List<Integer> movedToTopLevel, List<NotRestored> notRestored) {
	}

	/**
	 * Restores the entity and its cascade.
	 *
	 * @param batchId the batch of an upsert that restores, {@code null} for the operation itself
	 * @throws NoSuchElementException for an unknown entity (404)
	 * @throws EntityNotDeletedException for an active entity (409)
	 * @throws ConnectionNotRestorableException for a connection with a deleted endpoint (409)
	 * @throws io.oea.app.security.PermissionDeniedException for a connection of a scoping type
	 * without {@code visibility-scope:assign} (403)
	 */
	@Transactional
	public Result restore(OeaPrincipal principal, int entityId, UUID batchId) {
		this.hierarchy.lock();
		Row root = this.deletions.find(entityId).orElseThrow(() -> new NoSuchElementException("no entity " + entityId));
		if (!root.deleted()) {
			throw new EntityNotDeletedException();
		}
		List<Row> elements = new ArrayList<>();
		List<Row> connections = new ArrayList<>();
		if (root.connection()) {
			connections.add(root);
		}
		else {
			collect(root, elements, connections);
		}
		this.visibility.requireScopeAssignment(principal, connections.stream().map(Row::metatypeId).distinct().toList());
		if (root.connection()) {
			checkConnection(root);
		}
		Set<Integer> ids = new LinkedHashSet<>();
		elements.forEach((row) -> ids.add(row.id()));
		connections.forEach((row) -> ids.add(row.id()));
		this.entities.lockAll(ids);
		Instant now = this.clock.instant();
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		this.deletions.restoreAll(elements.stream().map(Row::id).toList());
		List<Integer> moved = new ArrayList<>();
		Map<Integer, Integer> versions = new HashMap<>();
		if (!root.connection()) {
			resetParent(principal, root, now, rules, moved, versions);
		}
		List<Integer> restoredConnections = new ArrayList<>();
		List<NotRestored> notRestored = new ArrayList<>();
		for (Row connection : connections) {
			String reason = root.connection() ? null : connectionBlock(connection);
			if (reason == null) {
				restoredConnections.add(connection.id());
			}
			else {
				notRestored.add(new NotRestored(connection.id(), reason));
			}
		}
		this.deletions.restoreAll(restoredConnections);
		List<Integer> restored = new ArrayList<>();
		for (Row row : elements) {
			restored.add(row.id());
			audit(principal, row, versions.getOrDefault(row.id(), row.version()), root, batchId, rules);
		}
		Map<Integer, Row> byId = new HashMap<>();
		connections.forEach((row) -> byId.put(row.id(), row));
		for (int id : restoredConnections) {
			restored.add(id);
			audit(principal, byId.get(id), byId.get(id).version(), root, batchId, rules);
		}
		return new Result(List.copyOf(restored), List.copyOf(moved), List.copyOf(notRestored));
	}

	/**
	 * A connection restored directly needs two active endpoints, and an automatic composition
	 * connection a child that still has its source as parent.
	 */
	private void checkConnection(Row connection) {
		String reason = connectionBlock(connection);
		if (reason != null) {
			throw new ConnectionNotRestorableException(reason);
		}
	}

	/** Why the connection cannot be active now, or {@code null}. */
	private String connectionBlock(Row connection) {
		Map<Integer, Row> endpoints = new HashMap<>();
		this.deletions.findAll(List.of(connection.sourceId(), connection.targetId()))
			.forEach((row) -> endpoints.put(row.id(), row));
		Row source = endpoints.get(connection.sourceId());
		Row target = endpoints.get(connection.targetId());
		if (source == null || target == null || source.deleted() || target.deleted()) {
			return ENDPOINT_DELETED;
		}
		if (connection.autoCreated() && !Integer.valueOf(connection.sourceId()).equals(target.parentEntityId())) {
			return PARENT_CHANGED;
		}
		return null;
	}

	/**
	 * The elements and connections of the cascade that belong to the restore of the entity: the
	 * entity, its descendants of the same cascade, and the connections of that cascade with an
	 * endpoint among them.
	 */
	private void collect(Row root, List<Row> elements, List<Row> connections) {
		elements.add(root);
		int origin = (root.deletedWithEntityId() != null) ? root.deletedWithEntityId() : root.id();
		List<Row> cascade = this.deletions.cascadeOf(origin, root.id());
		Map<Integer, List<Row>> childrenOf = new LinkedHashMap<>();
		for (Row row : cascade) {
			if (!row.connection() && row.parentEntityId() != null) {
				childrenOf.computeIfAbsent(row.parentEntityId(), (parent) -> new ArrayList<>()).add(row);
			}
		}
		Set<Integer> inSet = new LinkedHashSet<>();
		inSet.add(root.id());
		List<Row> queue = new ArrayList<>(List.of(root));
		for (int i = 0; i < queue.size(); i++) {
			for (Row child : childrenOf.getOrDefault(queue.get(i).id(), List.of())) {
				if (inSet.add(child.id())) {
					queue.add(child);
					elements.add(child);
				}
			}
		}
		for (Row row : cascade) {
			if (row.connection() && (inSet.contains(row.sourceId()) || inSet.contains(row.targetId()))) {
				connections.add(row);
			}
		}
	}

	/** Places the entity at the top level as a new version when its parent is deleted or its subtree gets too deep. */
	private void resetParent(OeaPrincipal principal, Row root, Instant now, MetatypeRules rules,
			List<Integer> moved, Map<Integer, Integer> versions) {
		if (root.parentEntityId() == null) {
			return;
		}
		Row parent = this.deletions.find(root.parentEntityId()).orElse(null);
		String reason = null;
		if (parent == null || parent.deleted()) {
			reason = PARENT_DELETED_REASON;
		}
		else if (this.hierarchy.ancestry(parent.id(), null).level()
				+ this.hierarchy.subtreeLevels(root.id()) > MAX_LEVEL) {
			reason = TOO_DEEP_REASON;
		}
		if (reason == null) {
			return;
		}
		StoredEntity stored = this.entities.find(root.id()).orElseThrow();
		int version = this.entities.updateParent(stored, null, principal.personId(), now);
		versions.put(root.id(), version);
		moved.add(root.id());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("version", version);
		details.put("fromParentId", root.parentEntityId());
		details.put("cause", "undeleted");
		details.put("reason", reason);
		List<AuditEvent.Change> changes = List.of(
				new AuditEvent.Change(null, "parent_entity_id", true, root.parentEntityId().toString(), null));
		this.audit.append(new AuditEvent("entity.parent-changed", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(root.id()), details), new AuditEvent.EntityTarget(root.id(), name(root, rules)),
				AuditEvent.Agent.NONE, changes);
	}

	private void audit(OeaPrincipal principal, Row row, int version, Row root, UUID batchId,
			MetatypeRules rules) {
		Map<String, Object> details = new LinkedHashMap<>();
		if (batchId != null) {
			details.put("batchId", batchId.toString());
		}
		details.put("metatypeId", row.metatypeId().toString());
		details.put("version", version);
		if (row.id() != root.id()) {
			details.put("triggeredByEntityId", root.id());
		}
		this.audit.append(new AuditEvent("entity.undeleted", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(row.id()), details), new AuditEvent.EntityTarget(row.id(), name(row, rules)),
				AuditEvent.Agent.NONE, List.of());
	}

	/** ADR-033 §3: no names of modeled persons in the audit schema. */
	static String name(Row row, MetatypeRules rules) {
		MetatypeRules.Metatype metatype = rules.metatype(row.metatypeId());
		return (metatype != null && "person".equalsIgnoreCase(metatype.name())) ? null : row.name();
	}

}
