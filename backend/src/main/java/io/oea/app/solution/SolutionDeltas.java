package io.oea.app.solution;

import io.oea.app.entity.EntityBlocks;
import io.oea.core.solution.DeltaStore;
import io.oea.core.solution.DeltaValueStore;
import io.oea.core.solution.SnapshotStore;
import io.oea.core.solution.SolutionStore;
import io.oea.app.catalog.CatalogCursors;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.Issue;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.entity.PropertyWriteAccess;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogQueryStore.KeyType;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.LabeledValue;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.metamodel.BuiltInMetatypes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entity deltas of a solution in project mode (US-040, US-041, US-042, REQ-040; design D2–D8 of
 * us-040-entity-deltas, D2–D5 of us-041-modified-delta). A delta {@code new} is checked like the
 * write of a new entity and writes no entity before the go-live (S14); {@code modified} changes an
 * entity of the baseline, checked like its update, with the before values recorded from the
 * catalog; {@code retiring} names an entity of the baseline. The permissions are checked from the
 * contract.
 */
@Service
public class SolutionDeltas {

	private static final List<KeyType> KEY_TYPES = List.of(KeyType.INTEGER, KeyType.TEXT, KeyType.TEXT);

	static final String DUPLICATE = "A delta already exists for this entity in this solution";

	static final String NOT_IN_LANDSCAPE = "Entity not found in the current landscape";

	static final String NOTHING_CHANGES = "The delta changes nothing.";

	private static final Set<String> COMPLETED = Set.of("implemented", "archived");

	private static final String NAME = "name";

	private static final String DESCRIPTION = "description";

	private final DeltaStore deltaStore;

	private final DeltaValueStore deltaValues;

	private final SolutionStore solutionStore;

	private final MetatypeRuleStore ruleStore;

	private final EntityStore store;

	private final PropertyWriteAccess writeAccess;

	private final PropertyAccess readAccess;

	private final AuditWriter audit;

	private final Clock clock;

	private final CatalogCursors cursors;

	private final SolutionConflicts conflicts;

	private final EntityBlocks blocks;

	private final SnapshotStore snapshots;

	private final SolutionLandscape landscape;

	SolutionDeltas(DeltaStore deltaStore, DeltaValueStore deltaValues, SolutionStore solutionStore,
			MetatypeRuleStore ruleStore, EntityStore store, PropertyWriteAccess writeAccess, PropertyAccess readAccess,
			AuditWriter audit, Clock clock, CatalogCursors cursors, SolutionConflicts conflicts, EntityBlocks blocks,
			SnapshotStore snapshots, SolutionLandscape landscape) {
		this.landscape = landscape;
		this.blocks = blocks;
		this.snapshots = snapshots;
		this.deltaStore = deltaStore;
		this.deltaValues = deltaValues;
		this.solutionStore = solutionStore;
		this.ruleStore = ruleStore;
		this.cursors = cursors;
		this.conflicts = conflicts;
		this.store = store;
		this.writeAccess = writeAccess;
		this.readAccess = readAccess;
		this.audit = audit;
		this.clock = clock;
	}

	/** The type of a delta. */
	public enum Type {

		NEW("new"), MODIFIED("modified"), RETIRING("retiring");

		private final String value;

		Type(String value) {
			this.value = value;
		}

		public String value() {
			return this.value;
		}

		static Type of(String value) {
			for (Type type : values()) {
				if (type.value.equals(value)) {
					return type;
				}
			}
			throw new IllegalArgumentException(value);
		}

	}

	/** A delta to capture; the fields that do not belong to its type must be absent. */
	public record NewDelta(Type type, Integer entityId, UUID metatypeId, String name, String description,
			List<EntityInput.PropertyInput> properties) {

		public NewDelta {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** The intended changes of a delta {@code modified} (replace semantics on an edit). */
	public record Modification(String name, String description, List<EntityInput.PropertyInput> properties) {

		public Modification {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** A typed value in the app layer: exactly one component is set. */
	public record Typed(String text, Long integer, Boolean bool, LocalDate date, String enumValue,
			Reference reference) {

		/** The value with the label of its reference target, or {@code null} for no value. */
		static Typed of(LabeledValue labeled) {
			return (labeled != null) ? of(labeled.value(), labeled.referenceName(), labeled.referenceAbbreviation())
					: null;
		}

		static Typed of(TypedValue value, String referenceName, String referenceAbbreviation) {
			if (value == null) {
				return null;
			}
			Reference reference = (value.reference() != null)
					? new Reference(value.reference(), referenceName, referenceAbbreviation) : null;
			return new Typed(value.text(), value.integer(), value.bool(), value.date(), value.enumValue(), reference);
		}

	}

	/**
	 * A value of an entity or of a delta {@code new}; {@code typed} is {@code null} when
	 * {@code redacted}, because the reader may not read it, and for no value.
	 */
	public record Value(UUID propertyDefId, String propertyName, String dataType, boolean redacted, Typed typed) {

		/** The same value without its content, for a reader who may not read it. */
		Value redact() {
			return new Value(this.propertyDefId, this.propertyName, this.dataType, true, null);
		}

		static Value of(PropertyValueStore.NamedValue named) {
			return new Value(named.propertyDefId(), named.propertyName(), named.dataType(), false,
					Typed.of(named.value()));
		}

	}

	/**
	 * A change of a delta {@code modified}: {@code field} is {@code name}, {@code description} or
	 * {@code property}; {@code before} and {@code after} are {@code null} for no value and when
	 * {@code redacted}.
	 */
	public record Change(String field, UUID propertyDefId, String propertyName, String dataType, boolean redacted,
			Typed before, Typed after) {
	}

	/** The target of a reference value. */
	public record Reference(int id, String name, String metatypeAbbreviation) {
	}

	/**
	 * A captured delta; for {@code modified} and {@code retiring} the current name and metatype, and
	 * the conflicts with other active solutions (US-044). {@code entityDeleted} marks a deletion
	 * conflict: the entity of a delta of an active solution is deleted in the repository.
	 */
	public record Delta(UUID id, Type type, Integer entityId, String name, UUID metatypeId, String metatypeName,
			String description, List<Value> properties, List<Change> changes,
			List<SolutionConflicts.Conflict> conflicts, Instant createdAt, Instant updatedAt, boolean entityDeleted) {
	}

	/** A computed change before it is stored: a system field or a property, with typed values. */
	private record Computed(String systemField, UUID propertyDefId, String label, TypedValue before,
			TypedValue after) {

		String key() {
			return (this.systemField != null) ? this.systemField : this.propertyDefId.toString();
		}

	}

	/**
	 * Captures a delta and audits {@code solution.delta-added}.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 * @throws PermissionDeniedException for a restricted value without write access, or an account
	 * that is not a person (403)
	 * @throws InvalidSolutionException for a delta that violates a rule (422)
	 */
	@Transactional
	public Delta create(OeaPrincipal principal, UUID solutionId, NewDelta input) {
		requireOpen(solutionId);
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		if (input.type() != Type.NEW && input.entityId() != null) {
			// an entity with open issues of a solution gets no new modification or retirement (US-153 AC1)
			this.blocks.check(principal, List.of(input.entityId()));
		}
		Map<UUID, TypedValue> values = Map.of();
		List<Computed> changes = List.of();
		switch (input.type()) {
			case NEW -> values = checkNew(principal, solutionId, input);
			case RETIRING -> checkRetiring(input);
			case MODIFIED -> {
				if (input.metatypeId() != null) {
					throw new InvalidSolutionException(List.of(new Issue("metatypeId", "NOT_SUPPORTED",
							"A modified delta keeps the metatype of its entity.")));
				}
				changes = computeChanges(principal, solutionId, input.entityId(),
						new Modification(input.name(), input.description(), input.properties()), Map.of(), false);
			}
		}
		UUID id = UUID.randomUUID();
		boolean isNew = input.type() == Type.NEW;
		try {
			this.deltaStore.insert(id, solutionId, input.type().value(), input.entityId(),
					isNew ? input.metatypeId() : null, isNew ? input.name() : null,
					isNew ? ((input.description() != null) ? input.description() : "") : null, person);
		}
		catch (DuplicateKeyException ex) {
			throw new InvalidSolutionException(isNew ? NAME : "entityId", DUPLICATE);
		}
		values.forEach((definition, value) -> this.deltaValues.insertValue(id, null, definition, null, value));
		changes.forEach((change) -> this.deltaValues.insertValue(id, change.systemField(), change.propertyDefId(), change.before(),
				change.after()));
		Delta delta = read(solutionId, id, this.readAccess.of(principal)).get(0);
		this.audit.append(event("solution.delta-added", principal, solutionId, delta, changes));
		return delta;
	}

	/**
	 * Replaces the changes of a delta {@code modified} and audits {@code solution.delta-updated}
	 * (design D3 of us-041-modified-delta).
	 *
	 * @throws NoSuchElementException for an unknown solution or a delta of another solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 * @throws PermissionDeniedException for a restricted value without write access (403)
	 * @throws InvalidSolutionException for a delta of another type or a change that violates a rule
	 * (422)
	 */
	@Transactional
	public Delta update(OeaPrincipal principal, UUID solutionId, UUID deltaId, Modification input) {
		Optional<DeltaStore.Locked> locked = this.deltaStore.lock(deltaId, solutionId);
		if (locked.isEmpty()) {
			status(solutionId);
			throw new NoSuchElementException("delta " + deltaId);
		}
		requireOpen(solutionId);
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		if (!Type.MODIFIED.value().equals(locked.get().type())) {
			throw new InvalidSolutionException(List.of(new Issue("deltaType", "NOT_SUPPORTED",
					"Only a modified delta can be edited.")));
		}
		int entityId = locked.get().entityId();
		Map<String, TypedValue> recorded = new HashMap<>();
		List<Computed> hidden = new ArrayList<>();
		PropertyAccess.Snapshot reader = this.readAccess.of(principal);
		Set<UUID> sent = new HashSet<>();
		input.properties().forEach((property) -> sent.add(property.propertyDefId()));
		for (DeltaValueStore.RecordedValue value : this.deltaValues.recorded(deltaId)) {
			String field = value.systemField();
			UUID property = value.propertyDefId();
			recorded.put((field != null) ? field : property.toString(), value.before());
			// a change the editor may not read cannot be resent, so it stays as recorded
			if (property != null && !sent.contains(property) && !reader.readableFor(property, entityId)) {
				hidden.add(new Computed(null, property, value.propertyName(), value.before(), value.after()));
			}
		}
		List<Computed> changes = new ArrayList<>(
				computeChanges(principal, solutionId, entityId, input, recorded, !hidden.isEmpty()));
		changes.addAll(hidden);
		this.deltaValues.deleteValues(deltaId);
		changes.forEach((change) -> this.deltaValues.insertValue(deltaId, change.systemField(), change.propertyDefId(),
				change.before(), change.after()));
		this.deltaStore.touch(deltaId, person);
		Delta delta = read(solutionId, deltaId, this.readAccess.of(principal)).get(0);
		this.audit.append(event("solution.delta-updated", principal, solutionId, delta, changes));
		return delta;
	}

	/** A page of the deltas; the contract checks the limits. */
	public record PageQuery(int page, int pageSize, String cursor) {
	}

	/** The number of deltas per type over all pages. */
	public record Counts(long newCount, long modified, long retiring) {

		public long total() {
			return this.newCount + this.modified + this.retiring;
		}

	}

	/**
	 * One page of the deltas; {@code conflicted} counts the deltas with a conflict over all pages,
	 * {@code nextCursor} is {@code null} on the last page.
	 */
	public record Page(List<Delta> items, int page, int pageSize, Counts counts, long conflicted, int totalPages,
			String nextCursor) {
	}

	/**
	 * One page of the deltas of a solution, {@code new}, then {@code modified}, then
	 * {@code retiring}, each by name, then by ID (design D0 of us-043-diff-view), with the counts
	 * per type.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws InvalidSolutionQueryException naming {@code cursor} for a cursor of another query
	 */
	@Transactional(readOnly = true)
	public Page list(OeaPrincipal principal, UUID solutionId, PageQuery query) {
		return list(this.readAccess.of(principal), solutionId, query);
	}

	/** {@link #list(OeaPrincipal, UUID, PageQuery)} with the reader's snapshot. */
	Page list(PropertyAccess.Snapshot access, UUID solutionId, PageQuery query) {
		status(solutionId);
		Map<String, Long> byType = this.deltaStore.countsByType(solutionId);
		Counts counts = new Counts(byType.getOrDefault("new", 0L), byType.getOrDefault("modified", 0L),
				byType.getOrDefault("retiring", 0L));
		long total = counts.total();

		String fingerprint = CatalogCursors.fingerprint(
				List.of("solution-deltas", solutionId.toString(), Integer.toString(query.pageSize())));
		List<Object> after = null;
		if (query.cursor() != null && !query.cursor().isEmpty()) {
			after = this.cursors.decode(query.cursor(), fingerprint, query.page(), KEY_TYPES)
				.orElseThrow(() -> new InvalidSolutionQueryException("cursor"));
		}
		List<Delta> items = read(solutionId, null, after, query.pageSize(), (long) query.page() * query.pageSize(),
				access);

		String nextCursor = null;
		if (!items.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			Delta last = items.get(items.size() - 1);
			nextCursor = this.cursors.encode(fingerprint, query.page() + 1,
					List.of(last.type().ordinal(), last.name(), last.id().toString()));
		}
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(items, query.page(), query.pageSize(), counts, this.conflicts.conflicted(solutionId),
				totalPages, nextCursor);
	}

	/**
	 * Removes a delta with its values and audits {@code solution.delta-removed}.
	 *
	 * @throws NoSuchElementException for an unknown solution or a delta of another solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID solutionId, UUID deltaId) {
		requireOpen(solutionId);
		List<Delta> found = read(solutionId, deltaId, this.readAccess.of(principal));
		if (found.isEmpty()) {
			throw new NoSuchElementException("delta " + deltaId);
		}
		this.deltaStore.delete(deltaId, solutionId);
		this.audit.append(event("solution.delta-removed", principal, solutionId, found.get(0), List.of()));
	}

	/**
	 * One delta of the solution for the reader (us-152-solution-go-live D3).
	 *
	 * @throws NoSuchElementException for an unknown solution or a delta of another solution (404)
	 */
	@Transactional(readOnly = true)
	public Delta get(OeaPrincipal principal, UUID solutionId, UUID deltaId) {
		return read(solutionId, deltaId, this.readAccess.of(principal)).stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("delta " + deltaId));
	}

	private void requireOpen(UUID solutionId) {
		if (COMPLETED.contains(status(solutionId))) {
			throw new SolutionCompletedException();
		}
	}

	private String status(UUID solutionId) {
		return this.solutionStore.status(solutionId)
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
	}

	/**
	 * The rules of today with the snapshots of the solution (design D10 of us-153-open-issues): its
	 * deltas may keep a property or enumeration value a metamodel change removed (US-154 AC3).
	 */
	private MetatypeRules rules(UUID solutionId) {
		return MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock)).withSnapshots(this.snapshots.of(solutionId));
	}

	/** The checks of an entity write (design D2 of us-040-entity-deltas); the values after defaults. */
	private Map<UUID, TypedValue> checkNew(OeaPrincipal principal, UUID solutionId, NewDelta input) {
		EntityInput entity = new EntityInput(input.name(), input.metatypeId(), null, input.description(), null, null,
				null, input.properties());
		PropertyAccess.Snapshot access = this.writeAccess.require(principal, List.of(entity));
		List<Issue> errors = new ArrayList<>();
		if (input.entityId() != null) {
			errors.add(new Issue("entityId", "NOT_SUPPORTED", "A new entity has no entity ID."));
		}
		if (input.metatypeId() == null) {
			errors.add(new Issue("metatypeId", "REQUIRED", "The metatype is required."));
			EntityValidator.Checked named = EntityValidator.check(
					new EntityInput(input.name(), null, null, input.description(), null, null, null, List.of()),
					MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock)), null, Map.of(), this.store::isActive);
			named.errors().stream().filter((issue) -> !"metatypeId".equals(issue.field())).forEach(errors::add);
			throw new InvalidSolutionException(errors);
		}
		if (BuiltInMetatypes.STRUCTURING_AID.equals(input.metatypeId())) {
			errors.add(new Issue("metatypeId", "NOT_SUPPORTED", "A structuring aid is no part of a solution."));
			throw new InvalidSolutionException(errors);
		}
		MetatypeRules rules = rules(solutionId).forWriter(PropertyWriteAccess.writer(access));
		EntityValidator.Checked checked = EntityValidator.check(entity, rules, null, Map.of(), this.store::isActive);
		errors.addAll(checked.errors());
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
		return checked.values();
	}

	/** An entity of the baseline and nothing else (design D3 of us-040-entity-deltas). */
	private void checkRetiring(NewDelta input) {
		List<Issue> errors = new ArrayList<>();
		if (input.metatypeId() != null) {
			errors.add(new Issue("metatypeId", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.name() != null) {
			errors.add(new Issue(NAME, "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.description() != null) {
			errors.add(new Issue(DESCRIPTION, "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (!input.properties().isEmpty()) {
			errors.add(new Issue("properties", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.entityId() == null || !inBaseline(input.entityId())) {
			errors.add(new Issue("entityId", "NOT_IN_LANDSCAPE", NOT_IN_LANDSCAPE));
		}
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
	}

	private boolean inBaseline(int entityId) {
		return this.store.isBaselineElement(entityId);
	}

	/**
	 * The changes of a modification against the entity, checked like its update (design D2, D3 of
	 * us-041-modified-delta). The before value of a field is its {@code recorded} one if the delta
	 * already holds it, else the current value; a field whose new value equals that before value
	 * is no change.
	 */
	private List<Computed> computeChanges(OeaPrincipal principal, UUID solutionId, Integer entityId, Modification input,
			Map<String, TypedValue> recorded, boolean othersRemain) {
		StoredEntity stored = (entityId != null) ? this.store.lockForShare(entityId)
			.filter((entity) -> !entity.deleted() && inBaseline(entity.id()))
			.orElse(null) : null;
		if (stored == null) {
			throw new InvalidSolutionException(List.of(new Issue("entityId", "NOT_IN_LANDSCAPE", NOT_IN_LANDSCAPE)));
		}
		PropertyAccess.Snapshot access = this.writeAccess.require(principal, input.properties(), stored.id());
		Map<UUID, TypedValue> current = this.store.values(stored.id());
		EntityInput entity = new EntityInput((input.name() != null) ? input.name() : stored.name(), stored.metatypeId(),
				null, input.description(), null, null, null, input.properties());
		MetatypeRules rules = rules(solutionId).forWriter(PropertyWriteAccess.writer(access));
		EntityValidator.Checked checked = EntityValidator.check(entity, rules, stored, current, this.store::isActive);
		if (!checked.valid()) {
			throw new InvalidSolutionException(checked.errors());
		}
		List<Computed> changes = new ArrayList<>();
		// the before values come from the baseline of the solution, the basis of its plateau (us-084 D5)
		Map<String, Object> basis = this.landscape.overlay(solutionId, stored.id());
		if (input.name() != null) {
			add(changes, recorded, new Computed(NAME, null, NAME,
					TypedValue.ofText((String) basis.getOrDefault(NAME, stored.name())),
					TypedValue.ofText(input.name())));
		}
		if (input.description() != null) {
			add(changes, recorded, new Computed(DESCRIPTION, null, DESCRIPTION,
					basis.containsKey(DESCRIPTION) ? (TypedValue) basis.get(DESCRIPTION)
							: (stored.description() != null) ? TypedValue.ofText(stored.description()) : null,
					TypedValue.ofText(input.description())));
		}
		Set<UUID> seen = new HashSet<>();
		for (EntityInput.PropertyInput property : input.properties()) {
			UUID definition = property.propertyDefId();
			MetatypeRules.Mapping mapping = checked.metatype().mappings().get(definition);
			if (mapping == null || !seen.add(definition)) {
				// an unmapped property is ignored, as in the batch write (REQ-149)
				continue;
			}
			TypedValue before = basis.containsKey(definition.toString()) ? (TypedValue) basis.get(definition.toString())
					: current.get(definition);
			add(changes, recorded, new Computed(null, definition, mapping.definition().name(), before,
					checked.values().get(definition)));
		}
		if (changes.isEmpty() && !othersRemain) {
			throw new InvalidSolutionException("properties", NOTHING_CHANGES);
		}
		return changes;
	}

	private static void add(List<Computed> changes, Map<String, TypedValue> recorded, Computed change) {
		TypedValue before = recorded.containsKey(change.key()) ? recorded.get(change.key()) : change.before();
		if (!Objects.equals(before, change.after())) {
			changes.add(new Computed(change.systemField(), change.propertyDefId(), change.label(), before,
					change.after()));
		}
	}

	/** The delta with {@code deltaId}, if it belongs to the solution. */
	private List<Delta> read(UUID solutionId, UUID deltaId, PropertyAccess.Snapshot access) {
		return read(solutionId, deltaId, null, 0, 0, access);
	}

	/**
	 * The deltas of the solution in the order of the list (design D5, D4 of us-041, D0 of us-043):
	 * one delta if {@code deltaId} is set, else a page after the keyset {@code after} or at the
	 * offset; with their values, changes and conflicts (D1 of us-044).
	 */
	private List<Delta> read(UUID solutionId, UUID deltaId, List<Object> after, int limit, long offset,
			PropertyAccess.Snapshot access) {
		List<Delta> deltas = this.deltaStore.page(solutionId, deltaId, after, limit, offset)
			.stream()
			.map(SolutionDeltas::delta)
			.toList();
		if (deltas.isEmpty()) {
			return deltas;
		}
		Map<Integer, List<SolutionConflicts.Conflict>> conflicts = this.conflicts.of(solutionId,
				deltas.stream().map(Delta::entityId).filter(Objects::nonNull).toList());
		boolean onlyRetiring = deltas.stream().allMatch((delta) -> delta.type() == Type.RETIRING);
		List<UUID> ids = deltas.stream().map(Delta::id).toList();
		Map<UUID, List<Value>> values = onlyRetiring ? Map.of() : values(ids, access);
		Map<UUID, List<Change>> changes = onlyRetiring ? Map.of() : changes(ids, access);
		return deltas.stream()
			.map((delta) -> new Delta(delta.id(), delta.type(), delta.entityId(), delta.name(), delta.metatypeId(),
					delta.metatypeName(), delta.description(), values.getOrDefault(delta.id(), List.of()),
					changes.getOrDefault(delta.id(), List.of()),
					(delta.entityId() != null) ? conflicts.getOrDefault(delta.entityId(), List.of()) : List.of(),
					delta.createdAt(), delta.updatedAt(), delta.entityDeleted()))
			.toList();
	}

	private Map<UUID, List<Value>> values(List<UUID> ids, PropertyAccess.Snapshot access) {
		Map<UUID, List<Value>> values = new HashMap<>();
		this.deltaValues.newValues(ids).forEach((deltaId, stored) -> {
			List<Value> shown = new ArrayList<>();
			for (PropertyValueStore.NamedValue named : stored) {
				Value value = Value.of(named);
				// a delta new has no entity yet: connection-scoped only through the override (#256)
				shown.add(access.readableWithoutEntity(named.propertyDefId()) ? value : value.redact());
			}
			values.put(deltaId, shown);
		});
		return values;
	}

	/** The changes of the deltas {@code modified}: name, description, then properties by name. */
	private Map<UUID, List<Change>> changes(List<UUID> ids, PropertyAccess.Snapshot access) {
		Map<UUID, List<Change>> changes = new HashMap<>();
		this.deltaValues.changes(ids).forEach((deltaId, stored) -> {
			List<Change> shown = new ArrayList<>();
			for (DeltaValueStore.ChangeRow row : stored) {
				if (row.systemField() != null) {
					shown.add(new Change(row.systemField(), null, null, null, false, Typed.of(row.before()),
							Typed.of(row.after())));
				}
				else {
					boolean readable = access.readableFor(row.propertyDefId(), row.entityId());
					shown.add(new Change("property", row.propertyDefId(), row.propertyName(), row.dataType(), !readable,
							readable ? Typed.of(row.before()) : null, readable ? Typed.of(row.after()) : null));
				}
			}
			changes.put(deltaId, shown);
		});
		return changes;
	}

	private static Delta delta(DeltaStore.DeltaRow row) {
		return new Delta(row.id(), Type.of(row.type()), row.entityId(), row.name(), row.metatypeId(),
				row.metatypeName(), row.description(), List.of(), List.of(), List.of(), row.createdAt(),
				row.updatedAt(), row.entityDeleted());
	}

	/**
	 * The audit event of a delta, without values (design D8 of us-040-entity-deltas, D5 of
	 * us-041-modified-delta): a delta {@code modified} names its changed fields.
	 */
	private static AuditEvent event(String type, OeaPrincipal principal, UUID solutionId, Delta delta,
			List<Computed> changes) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("solutionId", solutionId.toString());
		details.put("deltaId", delta.id().toString());
		details.put("deltaType", delta.type().value());
		if (delta.type() == Type.NEW) {
			details.put("metatypeId", delta.metatypeId().toString());
			details.put("name", delta.name());
		}
		else {
			details.put("entityId", delta.entityId());
		}
		if (delta.type() == Type.MODIFIED && !changes.isEmpty()) {
			details.put("fields", changes.stream().map(Computed::label).toList());
		}
		return new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "solution", solutionId.toString(), details);
	}

}
