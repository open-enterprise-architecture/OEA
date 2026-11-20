package io.oea.app.deletion;

import io.oea.app.entity.EntityBlocks;
import io.oea.app.entity.EntityNotDeletedException;
import io.oea.app.entity.EntityUndeletion;
import io.oea.app.entity.InvalidDeletionException;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.history.PreconditionRequiredException;
import io.oea.app.history.StaleEntityVersionException;
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
import io.oea.core.solution.GoLiveStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an entity from the repository with its cascade and the preview of it (design D2 of
 * s17-entity-delete; REQ-146, US-074; ADR-019): the entity, its children on all levels and every
 * connection with one of them as endpoint are soft-deleted in one transaction with one time and
 * actor, each with the entity as cascade origin and an audit event. A connection is deleted alone.
 * The permissions {@code entity:delete} and {@code entity:restore} are enforced by the contract;
 * {@code visibility-scope:assign} for a connection of a scoping type and the open-issue block are
 * checked here, before anything is written.
 */
@Service
public class EntityDeletions {

	static final String CASCADE_REASON = "cascade: entity %d deleted";

	private final EntityStore entities;

	private final EntityDeletionStore deletions;

	private final EntityHierarchyStore hierarchy;

	private final EntityBlocks blocks;

	private final PropertyVisibility visibility;

	private final EntityUndeletion undeletion;

	private final GoLiveStore solutions;

	private final MetatypeRuleStore ruleStore;

	private final AuditWriter audit;

	private final Clock clock;

	EntityDeletions(EntityStore entities, EntityDeletionStore deletions, EntityHierarchyStore hierarchy,
			EntityBlocks blocks, PropertyVisibility visibility, EntityUndeletion undeletion, GoLiveStore solutions,
			MetatypeRuleStore ruleStore, AuditWriter audit, Clock clock) {
		this.entities = entities;
		this.deletions = deletions;
		this.hierarchy = hierarchy;
		this.blocks = blocks;
		this.visibility = visibility;
		this.undeletion = undeletion;
		this.solutions = solutions;
		this.ruleStore = ruleStore;
		this.audit = audit;
		this.clock = clock;
	}

	/** An active solution with deltas on the deleted set. */
	public record SolutionRef(UUID id, String name, int deltaCount) {
	}

	/** What a deletion would do. */
	public record Preview(int entityId, String name, int version, int childCount, int connectionCount,
			List<SolutionRef> solutions) {
	}

	/** The set a deletion takes: the entity, its children on all levels and the connections. */
	private record Cascade(Row root, List<Integer> children, List<Integer> connections) {

		List<Integer> all() {
			List<Integer> ids = new ArrayList<>();
			ids.add(this.root.id());
			ids.addAll(this.children);
			ids.addAll(this.connections);
			return ids;
		}

	}

	/**
	 * The preview of the deletion; changes and locks nothing.
	 *
	 * @throws NoSuchElementException for an unknown or deleted entity (404)
	 * @throws InvalidDeletionException for a structuring aid or an automatic composition connection (422)
	 */
	@Transactional(readOnly = true)
	public Preview preview(OeaPrincipal principal, int entityId) {
		Row row = deletable(entityId);
		Cascade cascade = cascade(row);
		List<SolutionRef> solutions = this.solutions.activeSolutionsWithDeltasOn(cascade.all())
			.stream()
			.map((solution) -> new SolutionRef(solution.id(), solution.name(), solution.deltaCount()))
			.toList();
		return new Preview(row.id(), row.name(), row.version(), cascade.children().size(),
				cascade.connections().size(), solutions);
	}

	/**
	 * Deletes the entity with its cascade.
	 *
	 * @param expectedVersion the version named by {@code If-Match}, {@code null} without the header
	 * @return the deleted IDs: the entity, its children, then the connections
	 * @throws NoSuchElementException for an unknown or deleted entity (404)
	 * @throws InvalidDeletionException for a structuring aid or an automatic composition connection (422)
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the entity is at another version (412)
	 * @throws io.oea.app.security.PermissionDeniedException for a connection of a scoping type
	 * without {@code visibility-scope:assign} (403)
	 * @throws io.oea.app.entity.EntityBlockedException while an entity of the set has open issues (409)
	 */
	@Transactional
	public List<Integer> delete(OeaPrincipal principal, int entityId, Long expectedVersion) {
		this.hierarchy.lock();
		StoredEntity entity = this.entities.lock(entityId)
			.filter((stored) -> !stored.deleted())
			.orElseThrow(() -> new NoSuchElementException("no entity " + entityId));
		Row row = deletable(entityId);
		if (expectedVersion == null) {
			throw new PreconditionRequiredException("The write requires If-Match with the current version of the entity.");
		}
		if (expectedVersion != entity.version()) {
			throw new StaleEntityVersionException(entity.version());
		}
		Cascade cascade = cascade(row);
		this.entities.lockAll(cascade.all());
		List<Row> rows = this.deletions.findAll(cascade.all());
		this.visibility.requireScopeAssignment(principal,
				rows.stream().filter(Row::connection).map(Row::metatypeId).distinct().toList());
		this.blocks.check(principal, cascade.all());
		Instant now = this.clock.instant();
		long person = principal.personId();
		this.deletions.softDeleteAll(List.of(entityId), person, now, null, null);
		List<Integer> cascaded = new ArrayList<>(cascade.children());
		cascaded.addAll(cascade.connections());
		this.deletions.softDeleteAll(cascaded, person, now, CASCADE_REASON.formatted(entityId), entityId);
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		Map<Integer, Row> byId = new LinkedHashMap<>();
		rows.forEach((candidate) -> byId.put(candidate.id(), candidate));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("metatypeId", row.metatypeId().toString());
		details.put("version", row.version());
		details.put("childCount", cascade.children().size());
		details.put("connectionCount", cascade.connections().size());
		audit("entity.deleted", principal, row, details, rules);
		for (int id : cascaded) {
			Row cascadedRow = byId.get(id);
			Map<String, Object> cascadeDetails = new LinkedHashMap<>();
			cascadeDetails.put("triggeredByEntityId", entityId);
			cascadeDetails.put("metatypeId", cascadedRow.metatypeId().toString());
			cascadeDetails.put("version", cascadedRow.version());
			audit("entity.cascade-deleted", principal, cascadedRow, cascadeDetails, rules);
		}
		return cascade.all();
	}

	/**
	 * Restores a deleted entity with its cascade ({@link EntityUndeletion}).
	 *
	 * @param expectedVersion the version of the deleted entity named by {@code If-Match}
	 * @throws NoSuchElementException for an unknown entity (404)
	 * @throws EntityNotDeletedException for an active entity (409)
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the entity is at another version (412)
	 */
	@Transactional
	public EntityUndeletion.Result undelete(OeaPrincipal principal, int entityId, Long expectedVersion) {
		this.hierarchy.lock();
		StoredEntity entity = this.entities.lock(entityId)
			.orElseThrow(() -> new NoSuchElementException("no entity " + entityId));
		if (!entity.deleted()) {
			throw new EntityNotDeletedException();
		}
		if (expectedVersion == null) {
			throw new PreconditionRequiredException("The write requires If-Match with the version of the deleted entity.");
		}
		if (expectedVersion != entity.version()) {
			throw new StaleEntityVersionException(entity.version());
		}
		return this.undeletion.restore(principal, entityId, null);
	}

	/** The row of an entity that can be deleted: it exists, is active, and is neither a structuring aid nor an automatic connection. */
	private Row deletable(int entityId) {
		Row row = this.deletions.find(entityId)
			.filter((found) -> !found.deleted())
			.orElseThrow(() -> new NoSuchElementException("no entity " + entityId));
		if (row.structuringAid()) {
			throw new InvalidDeletionException("entityId",
					"A structuring aid is deleted with its own operation; its children move to the top level.");
		}
		if (row.connection() && row.autoCreated()) {
			throw new InvalidDeletionException("entityId",
					"An automatic composition connection is removed by changing the parent of its child.");
		}
		return row;
	}

	/** The set of the deletion: a connection alone, an element with its children and connections. */
	private Cascade cascade(Row row) {
		if (row.connection()) {
			return new Cascade(row, List.of(), List.of());
		}
		List<Integer> children = this.deletions.activeDescendants(row.id());
		List<Integer> members = new ArrayList<>();
		members.add(row.id());
		members.addAll(children);
		return new Cascade(row, children, this.deletions.activeConnectionsOf(members));
	}

	private void audit(String type, OeaPrincipal principal, Row row, Map<String, Object> details,
			MetatypeRules rules) {
		MetatypeRules.Metatype metatype = rules.metatype(row.metatypeId());
		// ADR-033 §3: no names of modeled persons in the audit schema
		String name = (metatype != null && "person".equalsIgnoreCase(metatype.name())) ? null : row.name();
		this.audit.append(new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(row.id()), details), new AuditEvent.EntityTarget(row.id(), name),
				AuditEvent.Agent.NONE, List.of());
	}

}
