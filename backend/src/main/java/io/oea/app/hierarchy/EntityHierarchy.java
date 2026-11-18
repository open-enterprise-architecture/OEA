package io.oea.app.hierarchy;

import io.oea.app.entity.EntityBlocks;
import io.oea.app.entity.EntityDeletion;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.EntityWriter;
import io.oea.app.entity.Issue;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.history.PreconditionRequiredException;
import io.oea.app.history.StaleEntityVersionException;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityHierarchyStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.metamodel.BuiltInMetatypes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The parent of an element entity, structuring aids and the automatic composition connection
 * (design D3 of us-135-component-hierarchy; US-135, US-136, REQ-141, REQ-142, ADR-021). Every
 * operation runs in one transaction under an advisory lock that serializes the changes of the
 * hierarchy, so that concurrent moves cannot create a cycle. A rejected change writes nothing and
 * no audit event. The permissions {@code entity:write} and, for the deletion of an aid,
 * {@code entity:delete} are enforced by the contract.
 */
@Service
public class EntityHierarchy {

	/** The deepest level of the hierarchy; the root level is level 1 (REQ-141). */
	public static final int MAX_LEVEL = 10;

	/** The reasons a response gives for the automatic connection. */
	public static final String CREATED = "created";

	public static final String STRUCTURING_AID = "structuring-aid";

	public static final String NO_COMPOSITION_METATYPE = "no-composition-metatype";

	public static final String NOT_ALLOWED = "not-allowed";

	public static final String UNCHANGED = "unchanged";

	public static final String CLEARED = "cleared";

	private static final int NAME_MAX_LENGTH = 255;

	private final EntityStore entities;

	private final EntityHierarchyStore hierarchy;

	private final EntityBlocks blocks;

	private final EntityWriter writer;

	private final EntityDeletion deletion;

	private final MetatypeRuleStore ruleStore;

	private final PropertyValueStore values;

	private final AuditWriter audit;

	private final Clock clock;

	EntityHierarchy(EntityStore entities, EntityHierarchyStore hierarchy, EntityBlocks blocks, EntityWriter writer,
			EntityDeletion deletion, MetatypeRuleStore ruleStore, PropertyValueStore values, AuditWriter audit,
			Clock clock) {
		this.entities = entities;
		this.hierarchy = hierarchy;
		this.blocks = blocks;
		this.writer = writer;
		this.deletion = deletion;
		this.ruleStore = ruleStore;
		this.values = values;
		this.audit = audit;
		this.clock = clock;
	}

	/** What the automatic connection of a parent change did. */
	public record Composition(boolean created, Integer connectionId, String reason) {
	}

	/** The outcome of a parent change. */
	public record ParentResult(int entityId, int version, Integer parentEntityId, Composition composition,
			Integer removedConnectionId) {
	}

	/** A created structuring aid. */
	public record StructuringAid(int id, String name, String description, Integer parentEntityId, int version) {
	}

	/**
	 * Sets or clears the parent of an element entity.
	 *
	 * @param parentId the new parent, {@code null} for the root level
	 * @param expectedVersion the version named by {@code If-Match}, {@code null} without the header
	 * @throws NoSuchElementException for an unknown or deleted entity (404)
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the entity is at another version (412)
	 * @throws io.oea.app.entity.EntityBlockedException while the entity has open issues of a solution (409)
	 * @throws InvalidHierarchyException naming {@code entityId} or {@code parentEntityId} (422)
	 */
	@Transactional
	public ParentResult setParent(OeaPrincipal principal, int entityId, Integer parentId, Long expectedVersion) {
		this.hierarchy.lock();
		StoredEntity entity = this.entities.lock(entityId).filter((stored) -> !stored.deleted())
			.orElseThrow(() -> new NoSuchElementException("no entity " + entityId));
		checkVersion(entity, expectedVersion);
		MetatypeRules rules = rules();
		if (isConnection(rules, entity)) {
			throw new InvalidHierarchyException("entityId", "A connection has no parent.");
		}
		this.blocks.check(principal, List.of(entityId));
		StoredEntity parent = null;
		if (parentId != null) {
			parent = checkParent(rules, entity, parentId);
		}
		if (Objects.equals(entity.parentEntityId(), parentId)) {
			return new ParentResult(entityId, entity.version(), parentId, new Composition(false, null, UNCHANGED),
					null);
		}
		Instant now = this.clock.instant();
		Integer removed = removeComposition(principal, entity, now);
		int version = this.entities.updateParent(entity, parentId, principal.personId(), now);
		Composition composition = (parent == null) ? new Composition(false, null, CLEARED)
				: composition(principal, rules, parent, entity);
		audit(principal, rules, entity, version, entity.parentEntityId(), parentId, composition.connectionId(),
				removed);
		return new ParentResult(entityId, version, parentId, composition, removed);
	}

	/**
	 * Creates a structuring aid at the root level or under a parent.
	 *
	 * @throws InvalidHierarchyException naming {@code name}, {@code description} or
	 * {@code parentEntityId} (422)
	 */
	@Transactional
	public StructuringAid createStructuringAid(OeaPrincipal principal, String name, String description,
			Integer parentId) {
		this.hierarchy.lock();
		MetatypeRules rules = rules();
		EntityInput input = new EntityInput((name != null) ? name.strip() : null, BuiltInMetatypes.STRUCTURING_AID,
				null, description, null, null, null, List.of());
		EntityValidator.Checked checked = EntityValidator.check(input, rules, null, Map.of(), this.entities::isActive);
		if (!checked.valid()) {
			Issue issue = checked.errors().get(0);
			throw new InvalidHierarchyException(issue.field(), issue.message());
		}
		if (parentId != null) {
			checkParent(rules, null, parentId);
		}
		EntityWriter.Written written = this.writer.write(principal, checked, null, Map.of(),
				new EntityWriter.WriteContext(UUID.randomUUID(), AuditEvent.Agent.NONE));
		this.entities.placeCreated(written.entityId(), parentId, true, false);
		return new StructuringAid(written.entityId(), checked.fields().name(), checked.fields().description(),
				parentId, written.version());
	}

	/**
	 * Deletes a structuring aid; its children move to the root level, each with a new version.
	 *
	 * @throws NoSuchElementException for an unknown or deleted entity (404)
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the aid is at another version (412)
	 * @throws io.oea.app.entity.EntityBlockedException while the aid or a child has open issues (409)
	 * @throws InvalidHierarchyException naming {@code entityId} for an entity that is no aid (422)
	 */
	@Transactional
	public void deleteStructuringAid(OeaPrincipal principal, int aidId, Long expectedVersion) {
		this.hierarchy.lock();
		StoredEntity aid = this.entities.lock(aidId).filter((stored) -> !stored.deleted())
			.orElseThrow(() -> new NoSuchElementException("no entity " + aidId));
		checkVersion(aid, expectedVersion);
		if (!BuiltInMetatypes.STRUCTURING_AID.equals(aid.metatypeId())) {
			throw new InvalidHierarchyException("entityId", "The entity is no structuring aid.");
		}
		List<StoredEntity> children = this.entities.lockChildren(aidId);
		Set<Integer> affected = new LinkedHashSet<>();
		affected.add(aidId);
		children.forEach((child) -> affected.add(child.id()));
		this.blocks.check(principal, affected);
		MetatypeRules rules = rules();
		Instant now = this.clock.instant();
		for (StoredEntity child : children) {
			int version = this.entities.updateParent(child, null, principal.personId(), now);
			audit(principal, rules, child, version, aidId, null, null, null);
		}
		this.deletion.delete(principal, aid, now, "Structuring aid deleted",
				Map.entry("cause", "structuring-aid-deleted"));
	}

	private MetatypeRules rules() {
		return MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
	}

	private static void checkVersion(StoredEntity entity, Long expectedVersion) {
		if (expectedVersion == null) {
			throw new PreconditionRequiredException("The write requires If-Match with the current version of the entity.");
		}
		if (expectedVersion != entity.version()) {
			throw new StaleEntityVersionException(entity.version());
		}
	}

	private static boolean isConnection(MetatypeRules rules, StoredEntity entity) {
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		return metatype == null || metatype.connection();
	}

	/**
	 * The parent of a change or of a new aid: an element entity that is not deleted, not the child
	 * or one of its descendants, and at most {@link #MAX_LEVEL} levels deep including the subtree
	 * of the child ({@code null} for a new aid). The parent stays locked against changes.
	 */
	private StoredEntity checkParent(MetatypeRules rules, StoredEntity child, int parentId) {
		StoredEntity parent = this.entities.lockForShare(parentId).filter((stored) -> !stored.deleted()).orElse(null);
		if (parent == null) {
			throw new InvalidHierarchyException("parentEntityId", "The parent does not exist or is deleted.");
		}
		if (isConnection(rules, parent)) {
			throw new InvalidHierarchyException("parentEntityId", "A connection cannot be a parent.");
		}
		EntityHierarchyStore.Ancestry ancestry = this.hierarchy.ancestry(parentId, (child != null) ? child.id() : null);
		if (ancestry.ancestor()) {
			throw new InvalidHierarchyException("parentEntityId",
					"The parent is the entity itself or one of its descendants.");
		}
		int levels = (child != null) ? this.hierarchy.subtreeLevels(child.id()) : 1;
		if (ancestry.level() + levels > MAX_LEVEL) {
			throw new InvalidHierarchyException("parentEntityId",
					"The hierarchy would be more than " + MAX_LEVEL + " levels deep.");
		}
		return parent;
	}

	/** Deletes the connection auto-created for the previous parent, if it is not deleted yet. */
	private Integer removeComposition(OeaPrincipal principal, StoredEntity child, Instant now) {
		if (child.parentEntityId() == null) {
			return null;
		}
		Integer removed = null;
		for (int connectionId : this.hierarchy.autoCreatedConnections(child.parentEntityId(), child.id())) {
			StoredEntity connection = this.entities.lock(connectionId).filter((stored) -> !stored.deleted())
				.orElse(null);
			if (connection != null) {
				this.deletion.delete(principal, connection, now, "The parent of a component changed",
						Map.entry("cause", "parent-changed"));
				removed = (removed == null) ? connectionId : removed;
			}
		}
		return removed;
	}

	/** Creates the composition connection from parent to child when the metamodel allows it. */
	private Composition composition(OeaPrincipal principal, MetatypeRules rules, StoredEntity parent,
			StoredEntity child) {
		if (BuiltInMetatypes.STRUCTURING_AID.equals(parent.metatypeId())
				|| BuiltInMetatypes.STRUCTURING_AID.equals(child.metatypeId())) {
			return new Composition(false, null, STRUCTURING_AID);
		}
		MetatypeRules.Metatype composition = rules.composition();
		if (composition == null) {
			return new Composition(false, null, NO_COMPOSITION_METATYPE);
		}
		if (!rules.allows(composition, true, parent.metatypeId()) || !rules.allows(composition, false, child.metatypeId())) {
			return new Composition(false, null, NOT_ALLOWED);
		}
		String name = parent.name() + " → " + child.name();
		EntityInput input = new EntityInput((name.length() > NAME_MAX_LENGTH) ? name.substring(0, NAME_MAX_LENGTH) : name,
				composition.id(), null, null, null, null, null, List.of());
		// an empty mandatory property is marked as missing instead of blocking, as after a restore
		EntityValidator.Checked checked = EntityValidator.check(input, rules, null, Map.of(), this.entities::isActive,
				true, EntityValidator.Necessity.WARN);
		if (!checked.valid()) {
			return new Composition(false, null, NOT_ALLOWED);
		}
		EntityWriter.Written written = this.writer.write(principal, checked, null, Map.<UUID, TypedValue>of(),
				new EntityWriter.WriteContext(UUID.randomUUID(), AuditEvent.Agent.NONE),
				new EntityWriter.Endpoints(parent.id(), child.id()));
		this.entities.placeCreated(written.entityId(), null, false, true);
		Set<UUID> missing = new LinkedHashSet<>();
		composition.mappings().forEach((property, mapping) -> {
			if ("mandatory".equals(mapping.necessity()) && !checked.values().containsKey(property)
					&& rules.writable(mapping.definition(), null)) {
				missing.add(property);
			}
		});
		this.values.mark(written.entityId(), missing, this.clock.instant());
		return new Composition(true, written.entityId(), CREATED);
	}

	private static void putIfPresent(Map<String, Object> details, String key, Integer value) {
		if (value != null) {
			details.put(key, value);
		}
	}

	private void audit(OeaPrincipal principal, MetatypeRules rules, StoredEntity entity, int version,
			Integer fromParentId, Integer toParentId, Integer createdConnectionId, Integer removedConnectionId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("version", version);
		// a detail without value is left out
		putIfPresent(details, "fromParentId", fromParentId);
		putIfPresent(details, "toParentId", toParentId);
		putIfPresent(details, "createdConnectionId", createdConnectionId);
		putIfPresent(details, "removedConnectionId", removedConnectionId);
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		// ADR-033 §3: no names of modeled persons in the audit schema
		String name = (metatype != null && "person".equalsIgnoreCase(metatype.name())) ? null : entity.name();
		List<AuditEvent.Change> changes = new ArrayList<>();
		changes.add(new AuditEvent.Change(null, "parent_entity_id", true,
				(fromParentId != null) ? fromParentId.toString() : null,
				(toParentId != null) ? toParentId.toString() : null));
		this.audit.append(new AuditEvent("entity.parent-changed", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(entity.id()), details), new AuditEvent.EntityTarget(entity.id(), name),
				AuditEvent.Agent.NONE, changes);
	}

}
