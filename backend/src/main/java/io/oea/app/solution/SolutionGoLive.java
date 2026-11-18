package io.oea.app.solution;

import io.oea.app.access.LanguagePreferences;
import io.oea.app.entity.EntityBlockedException;
import io.oea.app.entity.EntityBlocks;
import io.oea.app.entity.EntityDeletion;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.EntityWriter;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.Fields;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.EntityVersionStore;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.solution.GoLiveStore;
import io.oea.core.solution.GoLiveStore.DeltaRow;
import io.oea.core.solution.GoLiveStore.FieldRow;
import io.oea.core.solution.OpenIssueStore;
import io.oea.core.solution.PlateauStore;
import io.oea.core.solution.SnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The go-live of a solution in project mode (US-152; design D2 to D4 of us-152-solution-go-live):
 * its preview, the field conflicts between a delta {@code modified} and later changes of its
 * entity and their resolution, and the transaction that turns the deltas into the current state.
 * A delta {@code new} creates its entity, a delta {@code modified} writes its after values, a
 * delta {@code retiring} soft-deletes its entity and the connections of that entity (ADR-019).
 * Metamodel violations do not block; they become open issues of the solution. The values of the
 * deltas are taken as captured, without a new check of write roles (owner decision 2026-11-16).
 * An entity with an open issue blocks a delta {@code modified} or {@code retiring} of it (design D3
 * of us-153-open-issues). A value whose property or enumeration value only a snapshot of the
 * solution keeps is a conflict {@code not-in-metamodel}: it does not stop the go-live and becomes
 * an open issue keeping the value; the go-live drops the snapshots (D11, US-154 AC4). SQL runs only
 * in the stores of core (ADR-051).
 */
@Service
public class SolutionGoLive {

	static final String MANDATORY_MISSING = EntityBlocks.MANDATORY_MISSING;

	static final String INVALID_VALUE = EntityBlocks.INVALID_VALUE;

	static final String NOT_IN_METAMODEL = EntityBlocks.NOT_IN_METAMODEL;

	static final String FIELD = "field";

	private static final Set<String> COMPLETED = Set.of("implemented", "archived");

	private static final Map<String, String> SYSTEM_LABELS = Map.of("name", "Name", "description", "Description");

	private final GoLiveStore store;

	private final EntityStore entities;

	private final EntityVersionStore versions;

	private final PropertyValueStore values;

	private final MetatypeRuleStore ruleStore;

	private final EntityWriter writer;

	private final PropertyAccess access;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final SolutionDeltas deltas;

	private final AuditWriter audit;

	private final Clock clock;

	private final OpenIssueStore issues;

	private final OpenIssues openIssues;

	private final SnapshotStore snapshots;

	private final EntityBlocks blocks;

	private final PlateauStore plateaus;

	private final EntityDeletion deletion;

	SolutionGoLive(GoLiveStore store, EntityStore entities, EntityVersionStore versions, PropertyValueStore values,
			MetatypeRuleStore ruleStore, EntityWriter writer, PropertyAccess access, MetamodelLabels labels,
			LanguagePreferences languages, SolutionDeltas deltas, AuditWriter audit, Clock clock, OpenIssueStore issues,
			OpenIssues openIssues, SnapshotStore snapshots, EntityBlocks blocks, PlateauStore plateaus,
			EntityDeletion deletion) {
		this.deletion = deletion;
		this.plateaus = plateaus;
		this.issues = issues;
		this.openIssues = openIssues;
		this.snapshots = snapshots;
		this.blocks = blocks;
		this.store = store;
		this.entities = entities;
		this.versions = versions;
		this.values = values;
		this.ruleStore = ruleStore;
		this.writer = writer;
		this.access = access;
		this.labels = labels;
		this.languages = languages;
		this.deltas = deltas;
		this.audit = audit;
		this.clock = clock;
	}

	/** The counts of a go-live; connections retired with an endpoint are counted separately. */
	public record Counts(int created, int modified, int retired, int retiredConnections, int notInMetamodel) {
	}

	/**
	 * A conflict {@code field} or {@code not-in-metamodel}; the three values are text and
	 * {@code null} when redacted; {@code entityId} and {@code current} are {@code null} for a delta new.
	 */
	public record Conflict(String kind, UUID deltaId, UUID solutionId, Integer entityId, String entityName,
			String field, String label, String dataType, String before, String current, String after,
			boolean redacted) {
	}

	/**
	 * The values that the solutions going live before a solution of a plateau set, per entity and
	 * field key, and the entities they retire (design D2 of us-086-plateau-go-live); a value
	 * {@code null} is one they remove. Empty in project mode, and in the go-live of the plateau,
	 * where the earlier solutions were already written.
	 */
	record Ahead(Map<Integer, Map<String, TypedValue>> values, Set<Integer> retired) {

		static final Ahead EMPTY = new Ahead(Map.of(), Set.of());

		boolean sets(int entityId, String key) {
			return this.values.getOrDefault(entityId, Map.of()).containsKey(key);
		}

		TypedValue value(int entityId, String key) {
			return this.values.get(entityId).get(key);
		}

	}

	/** A metamodel violation a go-live takes as an open issue; {@code entityId} is {@code null} for a delta new. */
	public record Violation(UUID deltaId, Integer entityId, String entityName, String field, String label,
			String kind) {
	}

	/** What a go-live would do. */
	public record Preview(UUID solutionId, Counts counts, List<Conflict> conflicts, List<Violation> violations) {
	}

	/** The outcome of a go-live. */
	public record Result(UUID solutionId, String status, Counts counts, List<OpenIssues.OpenIssue> openIssues) {
	}

	/**
	 * The resolution of one conflicting field: {@code current}, {@code delta} or {@code value} for a
	 * conflict {@code field}, {@code discard} for a conflict {@code not-in-metamodel}.
	 */
	public record Choice(String field, String choice, String value) {
	}

	/** The plan of a go-live: the deltas, the rules of today and what the deltas would do. */
	private record Plan(GoLiveStore.SolutionRow solution, List<DeltaRow> deltas, MetatypeRules rules,
			Map<Integer, StoredEntity> entities, Map<Integer, Map<UUID, TypedValue>> current, Counts counts,
			Set<Integer> retiredConnections, List<RawConflict> conflicts, List<RawViolation> violations,
			List<RawConflict> snapshotValues) {

		/** Whether the field of the delta holds a value only a snapshot keeps. */
		boolean snapshotOnly(DeltaRow delta, FieldRow field) {
			return this.snapshotValues.stream()
				.anyMatch((conflict) -> conflict.delta().id().equals(delta.id())
						&& conflict.field().key().equals(field.key()));
		}

	}

	/** A conflict before the display rules: raw values; {@code current} is {@code null} for a delta new. */
	private record RawConflict(String kind, DeltaRow delta, FieldRow field, TypedValue current) {
	}

	/** A violation before the display rules. */
	private record RawViolation(DeltaRow delta, String field, UUID propertyDefId, String kind) {
	}

	/**
	 * The preview of the go-live; changes nothing.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 */
	@Transactional(readOnly = true)
	public Preview preview(OeaPrincipal principal, UUID solutionId) {
		GoLiveStore.SolutionRow solution = this.store.find(solutionId)
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
		requireOpen(solution);
		Plan plan = plan(solution, false, ahead(solution.id()));
		PropertyAccess.Snapshot reader = this.access.of(principal);
		String language = this.languages.language(principal);
		List<RawConflict> all = new ArrayList<>(plan.conflicts());
		all.addAll(plan.snapshotValues());
		return new Preview(solutionId, plan.counts(), conflicts(plan, all, reader, language),
				violations(plan, language));
	}

	/**
	 * Takes the solution live in one transaction.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 * @throws MergeRequiredException for unresolved field conflicts (409)
	 * @throws EntityDeletedException for a delta whose entity was deleted meanwhile (409)
	 */
	@Transactional
	public Result goLive(OeaPrincipal principal, UUID solutionId) {
		if (this.store.plateauOf(solutionId).isPresent()) {
			// a solution of a plateau goes live with its plateau (US-157 AC3)
			throw new PlateauSolutionException();
		}
		return goLiveOne(principal, solutionId);
	}

	/**
	 * Takes one solution live in the transaction of the caller; {@link PlateauGoLive} calls it for
	 * each solution of a plateau, in the order of the go-live.
	 */
	@Transactional
	Result goLiveOne(OeaPrincipal principal, UUID solutionId) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		GoLiveStore.SolutionRow solution = this.store.lock(solutionId)
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
		requireOpen(solution);
		Plan plan = plan(solution, true, ahead(solution.id()));
		Set<Integer> touched = new TreeSet<>();
		plan.deltas()
			.stream()
			.filter((delta) -> !"new".equals(delta.type()))
			.map(DeltaRow::entityId)
			.forEach(touched::add);
		Map<Integer, EntityBlocks.Block> blocked = this.blocks.open(principal, touched);
		if (!blocked.isEmpty()) {
			throw new EntityBlockedException(List.copyOf(blocked.values()),
					plan.deltas()
						.stream()
						.filter((delta) -> delta.entityId() != null && blocked.containsKey(delta.entityId())
								&& !"new".equals(delta.type()))
						.map(DeltaRow::id)
						.toList());
		}
		if (!plan.conflicts().isEmpty()) {
			throw new MergeRequiredException(
					conflicts(plan, plan.conflicts(), this.access.of(principal), this.languages.language(principal)));
		}
		Instant now = this.clock.instant();
		EntityWriter.WriteContext context = new EntityWriter.WriteContext(UUID.randomUUID(), AuditEvent.Agent.NONE);
		Map<UUID, Integer> createdEntities = new HashMap<>();
		for (DeltaRow delta : plan.deltas()) {
			switch (delta.type()) {
				case "new" -> createdEntities.put(delta.id(), create(principal, delta, plan, context));
				case "modified" -> modify(principal, delta, plan, context);
				default -> retire(principal, delta.entityId(), solution, now);
			}
		}
		for (int connection : plan.retiredConnections()) {
			retire(principal, connection, solution, now);
		}
		Map<Integer, Set<UUID>> missing = new HashMap<>();
		for (RawViolation violation : plan.violations()) {
			int entityId = (violation.delta().entityId() != null) ? violation.delta().entityId()
					: createdEntities.get(violation.delta().id());
			this.issues.insert(solutionId, entityId, violation.field(), violation.propertyDefId(), violation.kind(), null,
					now);
			if (MANDATORY_MISSING.equals(violation.kind())) {
				missing.computeIfAbsent(entityId, (k) -> new HashSet<>()).add(violation.propertyDefId());
			}
		}
		missing.forEach((entityId, properties) -> this.values.mark(entityId, properties, now));
		for (RawConflict kept : plan.snapshotValues()) {
			int entityId = (kept.delta().entityId() != null) ? kept.delta().entityId()
					: createdEntities.get(kept.delta().id());
			this.issues.insert(solutionId, entityId, kept.field().key(), kept.field().propertyDefId(), NOT_IN_METAMODEL,
					kept.field().after().asText(), now);
		}
		this.snapshots.deleteOf(solutionId);
		this.store.setStatus(solutionId, "implemented");
		List<OpenIssues.OpenIssue> issues = this.openIssues.list(principal, solutionId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", solution.name());
		details.put("created", plan.counts().created());
		details.put("modified", plan.counts().modified());
		details.put("retired", plan.counts().retired());
		details.put("retiredConnections", plan.counts().retiredConnections());
		details.put("notInMetamodel", plan.counts().notInMetamodel());
		details.put("openIssues", issues.size());
		this.audit.append(new AuditEvent("solution.went-live", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "solution",
				solutionId.toString(), details));
		return new Result(solutionId, "implemented", plan.counts(), issues);
	}

	/**
	 * Resolves the field conflicts of a delta {@code modified}: per field the current value (the
	 * field leaves the delta), the delta value or an own value; the current value becomes the
	 * before value.
	 *
	 * @throws InvalidSolutionException naming a field without a conflict or an invalid own value (422)
	 */
	@Transactional
	public SolutionDeltas.Delta resolve(OeaPrincipal principal, UUID solutionId, UUID deltaId, List<Choice> choices) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		GoLiveStore.SolutionRow solution = this.store.lock(solutionId)
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
		requireOpen(solution);
		Plan plan = plan(solution, true, ahead(solution.id()));
		DeltaRow delta = plan.deltas()
			.stream()
			.filter((candidate) -> candidate.id().equals(deltaId))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("delta " + deltaId));
		Map<String, RawConflict> conflicts = new LinkedHashMap<>();
		java.util.stream.Stream.concat(plan.conflicts().stream(), plan.snapshotValues().stream())
			.filter((conflict) -> conflict.delta().id().equals(deltaId))
			.forEach((conflict) -> conflicts.put(conflict.field().key(), conflict));
		List<FieldRow> replacements = new ArrayList<>();
		List<FieldRow> removals = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Choice choice : choices) {
			RawConflict conflict = conflicts.get(choice.field());
			if (conflict == null || !seen.add(choice.field())) {
				throw new InvalidSolutionException(choice.field(), "The field has no conflict to resolve.");
			}
			FieldRow field = conflict.field();
			String chosen = Objects.requireNonNullElse(choice.choice(), "");
			if (NOT_IN_METAMODEL.equals(conflict.kind()) != "discard".equals(chosen)) {
				throw new InvalidSolutionException(choice.field(), "The choice does not fit the conflict.");
			}
			switch (chosen) {
				case "discard" -> removals.add(field);
				case "current" -> removals.add(field);
				case "delta" -> replacements
					.add(new FieldRow(field.systemField(), field.propertyDefId(), conflict.current(), field.after()));
				case "value" -> replacements.add(new FieldRow(field.systemField(), field.propertyDefId(),
						conflict.current(), own(field, choice.value(), plan, delta)));
				default -> throw new InvalidSolutionException(choice.field(), "The choice is not supported.");
			}
		}
		removals.forEach((field) -> this.store.removeField(deltaId, field.systemField(), field.propertyDefId()));
		replacements.forEach((field) -> this.store.replaceField(deltaId, field));
		this.store.touch(deltaId, person, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("deltaId", deltaId.toString());
		details.put("deltaType", delta.type());
		if (delta.entityId() != null) {
			details.put("entityId", delta.entityId());
		}
		details.put("resolvedFields", choices.stream().map(Choice::field).toList());
		this.audit.append(new AuditEvent("solution.delta-updated", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"solution", solutionId.toString(), details));
		return this.deltas.get(principal, solutionId, deltaId);
	}

	/**
	 * The values the solutions before this one set in the go-live of its plateau (design D2 of
	 * us-086-plateau-go-live): the active solutions of the plateau in the order of the go-live, up
	 * to this one. Solutions already implemented are in the catalog and not part of it.
	 */
	Ahead ahead(UUID solutionId) {
		java.util.Optional<UUID> plateau = this.store.plateauOf(solutionId);
		if (plateau.isEmpty()) {
			return Ahead.EMPTY;
		}
		Map<Integer, Map<String, TypedValue>> values = new HashMap<>();
		Set<Integer> retired = new HashSet<>();
		for (UUID earlier : this.plateaus.goLiveOrder(plateau.get())) {
			if (earlier.equals(solutionId)) {
				break;
			}
			for (DeltaRow delta : this.store.deltas(earlier)) {
				if ("retiring".equals(delta.type())) {
					retired.add(delta.entityId());
				}
				else if ("modified".equals(delta.type())) {
					Map<String, TypedValue> fields = values.computeIfAbsent(delta.entityId(), (k) -> new HashMap<>());
					delta.fields().forEach((field) -> fields.put(field.key(), field.after()));
				}
			}
		}
		return new Ahead(values, retired);
	}

	private static void requireOpen(GoLiveStore.SolutionRow solution) {
		if (COMPLETED.contains(solution.status())) {
			throw new SolutionCompletedException();
		}
	}

	/**
	 * What the deltas would do with the rules of today: counts, conflicts and violations. With
	 * {@code lock}, the entities of the deltas are locked in ID order (design D4).
	 */
	private Plan plan(GoLiveStore.SolutionRow solution, boolean lock, Ahead ahead) {
		List<DeltaRow> rows = this.store.deltas(solution.id());
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		List<SnapshotStore.SnapshotRow> kept = this.snapshots.of(solution.id());
		List<RawConflict> snapshotValues = new ArrayList<>();
		Set<Integer> ids = new TreeSet<>();
		rows.stream().map(DeltaRow::entityId).filter(Objects::nonNull).forEach(ids::add);
		Map<Integer, StoredEntity> stored = new HashMap<>();
		List<UUID> deleted = new ArrayList<>();
		for (int id : ids) {
			StoredEntity entity = (lock ? this.entities.lock(id) : this.entities.find(id)).orElse(null);
			if (entity != null) {
				stored.put(id, entity);
			}
		}
		Map<Integer, Map<UUID, TypedValue>> current = new HashMap<>();
		List<RawConflict> conflicts = new ArrayList<>();
		List<RawViolation> violations = new ArrayList<>();
		Set<Integer> retiring = new HashSet<>();
		int created = 0;
		int modified = 0;
		for (DeltaRow delta : rows) {
			if ("new".equals(delta.type())) {
				created++;
				List<FieldRow> onlyKept = snapshotOnly(delta.metatypeId(), delta, rules, kept);
				onlyKept.forEach((field) -> snapshotValues.add(new RawConflict(NOT_IN_METAMODEL, delta, field, null)));
				violations.addAll(violationsOfNew(delta, rules, onlyKept));
				continue;
			}
			StoredEntity entity = stored.get(delta.entityId());
			if (entity == null || entity.deleted() || ahead.retired().contains(delta.entityId())) {
				if ("modified".equals(delta.type())) {
					deleted.add(delta.id());
				}
				continue;
			}
			if ("retiring".equals(delta.type())) {
				retiring.add(entity.id());
				continue;
			}
			modified++;
			Map<UUID, TypedValue> values = current.computeIfAbsent(entity.id(), this.entities::values);
			List<FieldRow> onlyKept = snapshotOnly(entity.metatypeId(), delta, rules, kept);
			for (FieldRow field : delta.fields()) {
				TypedValue now = ahead.sets(entity.id(), field.key()) ? ahead.value(entity.id(), field.key())
						: currentValue(entity, values, field);
				if (onlyKept.contains(field)) {
					snapshotValues.add(new RawConflict(NOT_IN_METAMODEL, delta, field, now));
				}
				else if (!Objects.equals(text(now), text(field.before()))) {
					conflicts.add(new RawConflict(FIELD, delta, field, now));
				}
			}
			violations.addAll(violationsOfModified(delta, entity, rules, onlyKept));
		}
		if (lock && !deleted.isEmpty()) {
			throw new EntityDeletedException(deleted);
		}
		Set<Integer> retiredConnections = new TreeSet<>();
		for (int entityId : retiring) {
			for (int connection : this.entities.activeConnectionsOf(entityId)) {
				if (!retiring.contains(connection)) {
					retiredConnections.add(connection);
				}
			}
		}
		Counts counts = new Counts(created, modified, retiring.size(), retiredConnections.size(),
				snapshotValues.size());
		return new Plan(solution, rows, rules, stored, current, counts, retiredConnections, conflicts, violations,
				snapshotValues);
	}

	/**
	 * The property fields of the delta whose value only a snapshot of the solution keeps: the
	 * metatype no longer has the property, or the enumeration no longer has the value (US-154 AC4).
	 */
	private static List<FieldRow> snapshotOnly(UUID metatypeId, DeltaRow delta, MetatypeRules rules,
			List<SnapshotStore.SnapshotRow> kept) {
		if (kept.isEmpty()) {
			return List.of();
		}
		MetatypeRules.Metatype metatype = rules.metatype(metatypeId);
		List<FieldRow> fields = new ArrayList<>();
		for (FieldRow field : delta.fields()) {
			if (field.propertyDefId() == null || field.after() == null) {
				continue;
			}
			MetatypeRules.Mapping mapping = (metatype != null) ? metatype.mappings().get(field.propertyDefId()) : null;
			boolean match = kept.stream()
				.filter((snapshot) -> snapshot.metatypeId().equals(metatypeId)
						&& snapshot.propertyDefId().equals(field.propertyDefId()))
				.anyMatch((snapshot) -> (snapshot.enumValue() == null) ? mapping == null
						: snapshot.enumValue().equals(field.after().enumValue()) && (mapping == null
								|| !mapping.definition().enumValues().contains(snapshot.enumValue())));
			if (match) {
				fields.add(field);
			}
		}
		return fields;
	}

	private static TypedValue currentValue(StoredEntity entity, Map<UUID, TypedValue> values, FieldRow field) {
		if ("name".equals(field.systemField())) {
			return TypedValue.ofText(entity.name());
		}
		if ("description".equals(field.systemField())) {
			String description = entity.description();
			return (description != null && !description.isEmpty()) ? TypedValue.ofText(description) : null;
		}
		return values.get(field.propertyDefId());
	}

	/** Violations of a delta new: empty mandatory properties and values the definition rejects; snapshot values aside. */
	private List<RawViolation> violationsOfNew(DeltaRow delta, MetatypeRules rules, List<FieldRow> onlyKept) {
		List<RawViolation> violations = new ArrayList<>();
		MetatypeRules.Metatype metatype = rules.metatype(delta.metatypeId());
		if (metatype == null) {
			return violations;
		}
		Set<UUID> set = new HashSet<>();
		for (FieldRow field : delta.fields()) {
			if (field.propertyDefId() == null || field.after() == null || onlyKept.contains(field)) {
				continue;
			}
			set.add(field.propertyDefId());
			if (!accepted(metatype, field)) {
				violations.add(new RawViolation(delta, field.key(), field.propertyDefId(), INVALID_VALUE));
			}
		}
		metatype.mappings().forEach((property, mapping) -> {
			if ("mandatory".equals(mapping.necessity()) && !set.contains(property)) {
				violations.add(new RawViolation(delta, property.toString(), property, MANDATORY_MISSING));
			}
		});
		return violations;
	}

	/** Violations of a delta modified: values it writes that the definition rejects, and mandatory values it removes. */
	private List<RawViolation> violationsOfModified(DeltaRow delta, StoredEntity entity, MetatypeRules rules,
			List<FieldRow> onlyKept) {
		List<RawViolation> violations = new ArrayList<>();
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		if (metatype == null) {
			return violations;
		}
		for (FieldRow field : delta.fields()) {
			if (field.propertyDefId() == null || onlyKept.contains(field)) {
				continue;
			}
			MetatypeRules.Mapping mapping = metatype.mappings().get(field.propertyDefId());
			if (field.after() == null) {
				if (mapping != null && "mandatory".equals(mapping.necessity())) {
					violations.add(new RawViolation(delta, field.key(), field.propertyDefId(), MANDATORY_MISSING));
				}
			}
			else if (!accepted(metatype, field)) {
				violations.add(new RawViolation(delta, field.key(), field.propertyDefId(), INVALID_VALUE));
			}
		}
		return violations;
	}

	private boolean accepted(MetatypeRules.Metatype metatype, FieldRow field) {
		MetatypeRules.Mapping mapping = metatype.mappings().get(field.propertyDefId());
		return mapping != null
				&& EntityValidator.accepts(mapping.definition(), field.after().asText(), this.entities::isActive);
	}

	/** Creates the entity of a delta new with its values as captured. */
	private int create(OeaPrincipal principal, DeltaRow delta, Plan plan, EntityWriter.WriteContext context) {
		MetatypeRules.Metatype metatype = plan.rules().metatype(delta.metatypeId());
		if (metatype == null) {
			throw new IllegalStateException("metatype " + delta.metatypeId() + " of delta " + delta.id());
		}
		Map<UUID, TypedValue> captured = new LinkedHashMap<>();
		delta.fields()
			.stream()
			.filter((field) -> field.propertyDefId() != null && field.after() != null
					&& !plan.snapshotOnly(delta, field))
			.forEach((field) -> captured.put(field.propertyDefId(), field.after()));
		Fields fields = new Fields(metatype.id(), delta.name(), Objects.requireNonNullElse(delta.description(), ""),
				true, null);
		EntityWriter.Written written = this.writer.write(principal,
				new EntityValidator.Checked(List.of(), List.of(), metatype, fields, captured), null, Map.of(), context);
		this.store.setDeltaEntity(delta.id(), written.entityId());
		return written.entityId();
	}

	/** Writes the after values of a delta modified into its entity. */
	private void modify(OeaPrincipal principal, DeltaRow delta, Plan plan, EntityWriter.WriteContext context) {
		StoredEntity entity = plan.entities().get(delta.entityId());
		MetatypeRules.Metatype metatype = plan.rules().metatype(entity.metatypeId());
		Map<UUID, TypedValue> stored = plan.current().get(entity.id());
		Map<UUID, TypedValue> next = new LinkedHashMap<>(stored);
		String name = entity.name();
		String description = Objects.requireNonNullElse(entity.description(), "");
		for (FieldRow field : delta.fields()) {
			if (plan.snapshotOnly(delta, field)) {
				continue;
			}
			if ("name".equals(field.systemField())) {
				name = field.after().asText();
			}
			else if ("description".equals(field.systemField())) {
				description = (field.after() != null) ? field.after().asText() : "";
			}
			else if (field.after() == null) {
				next.remove(field.propertyDefId());
			}
			else {
				next.put(field.propertyDefId(), field.after());
			}
		}
		Fields fields = new Fields(entity.metatypeId(), name, description, entity.logical(), entity.externalId());
		this.writer.write(principal, new EntityValidator.Checked(List.of(), List.of(), metatype, fields, next), entity,
				stored, context);
	}

	/** Soft-deletes an entity and audits {@code entity.deleted}. */
	private void retire(OeaPrincipal principal, int entityId, GoLiveStore.SolutionRow solution, Instant now) {
		StoredEntity entity = this.entities.find(entityId).orElseThrow();
		this.deletion.delete(principal, entity, now, "Retired by the solution " + solution.name(),
				Map.entry("solutionId", solution.id().toString()));
	}

	/** The typed own value of a resolution; an empty value removes a property value. */
	private TypedValue own(FieldRow field, String value, Plan plan, DeltaRow delta) {
		String text = (value != null) ? value : "";
		if ("name".equals(field.systemField())) {
			String name = text.strip();
			if (name.isEmpty() || name.length() > 255) {
				throw new InvalidSolutionException("name", "The name must have 1 to 255 characters.");
			}
			return TypedValue.ofText(name);
		}
		if ("description".equals(field.systemField())) {
			if (text.length() > 2000) {
				throw new InvalidSolutionException("description", "The description must not be longer than 2000 characters.");
			}
			return text.isEmpty() ? null : TypedValue.ofText(text);
		}
		if (text.isEmpty()) {
			return null;
		}
		StoredEntity entity = plan.entities().get(delta.entityId());
		MetatypeRules.Metatype metatype = plan.rules().metatype(entity.metatypeId());
		MetatypeRules.Mapping mapping = (metatype != null) ? metatype.mappings().get(field.propertyDefId()) : null;
		TypedValue typed = (mapping != null) ? EntityValidator.typed(mapping.definition(), text, this.entities::isActive)
				: null;
		if (typed == null) {
			throw new InvalidSolutionException(field.key(), "The value is not valid for the property.");
		}
		return typed;
	}

	private List<Conflict> conflicts(Plan plan, List<RawConflict> raw, PropertyAccess.Snapshot reader,
			String language) {
		Set<UUID> properties = new HashSet<>();
		Set<Integer> references = new HashSet<>();
		for (RawConflict conflict : raw) {
			if (conflict.field().propertyDefId() != null) {
				properties.add(conflict.field().propertyDefId());
			}
			for (TypedValue value : java.util.Arrays.asList(conflict.field().before(), conflict.current(),
					conflict.field().after())) {
				if (value != null && value.reference() != null) {
					references.add(value.reference());
				}
			}
		}
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(language, properties);
		Map<UUID, EntityVersionStore.Definition> definitions = this.versions.definitions(properties);
		Map<Integer, String> names = this.versions.entityNames(references);
		List<Conflict> conflicts = new ArrayList<>();
		for (RawConflict conflict : raw) {
			FieldRow field = conflict.field();
			Integer entityId = conflict.delta().entityId();
			boolean redacted = field.propertyDefId() != null && ((entityId != null)
					? !reader.readableFor(field.propertyDefId(), entityId) : reader.restricted(field.propertyDefId()));
			String entityName = (entityId != null) ? plan.entities().get(entityId).name() : conflict.delta().name();
			String dataType = (field.propertyDefId() == null) ? "system"
					: (definitions.get(field.propertyDefId()) != null) ? definitions.get(field.propertyDefId()).dataType()
							: "string";
			conflicts.add(new Conflict(conflict.kind(), conflict.delta().id(), plan.solution().id(), entityId, entityName,
					field.key(),
					label(field.key(), field.propertyDefId(), propertyLabels), dataType,
					redacted ? null : shown(field.before(), names), redacted ? null : shown(conflict.current(), names),
					redacted ? null : shown(field.after(), names), redacted));
		}
		return List.copyOf(conflicts);
	}

	private List<Violation> violations(Plan plan, String language) {
		Set<UUID> properties = new LinkedHashSet<>();
		plan.violations().stream().map(RawViolation::propertyDefId).filter(Objects::nonNull).forEach(properties::add);
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(language, properties);
		return plan.violations()
			.stream()
			.map((violation) -> {
				Integer entityId = violation.delta().entityId();
				String entityName = (entityId != null && plan.entities().get(entityId) != null)
						? plan.entities().get(entityId).name() : violation.delta().name();
				return new Violation(violation.delta().id(), entityId, entityName, violation.field(),
						label(violation.field(), violation.propertyDefId(), propertyLabels), violation.kind());
			})
			.toList();
	}

	private static String label(String field, UUID propertyDefId, Map<UUID, String> propertyLabels) {
		String system = SYSTEM_LABELS.get(field);
		if (system != null) {
			return system;
		}
		return (propertyDefId != null) ? propertyLabels.getOrDefault(propertyDefId, field) : field;
	}

	private static String shown(TypedValue value, Map<Integer, String> names) {
		if (value == null) {
			return null;
		}
		if (value.reference() != null) {
			return names.getOrDefault(value.reference(), value.asText());
		}
		return value.asText();
	}

	private static String text(TypedValue value) {
		return (value != null) ? value.asText() : null;
	}

}
