package io.oea.app.history;

import io.oea.app.entity.EntityBlockedException;
import io.oea.app.entity.EntityBlocks;
import io.oea.app.access.LanguagePreferences;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.EntityWriter;
import io.oea.app.entity.Issue;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.entity.PropertyWriteAccess;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.EntityHistoryStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.EntityVersionStore;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The preview and the full restore of an entity to an earlier version (UC-15, US-101 to US-103,
 * REQ-101 to REQ-103; design D2 of us-101-entity-restore). Name, description, the logical flag
 * and every restorable property get the values of the target version; the metatype and the
 * endpoints never change. A property is not restored when the caller may not write it, when it
 * is no longer mapped to the metatype, or when its current definition no longer accepts the
 * target value. Empty mandatory properties are warnings and are marked afterwards.
 */
@Service
public class EntityRestore {

	/** The maximum length of the reason of a restore. */
	public static final int REASON_MAX_LENGTH = 1000;

	static final String WRITE_PROTECTED = "write-protected";

	static final String NOT_MAPPED = "not-mapped";

	static final String INVALID_VALUE = "invalid-value";

	static final String NOT_IN_SOURCE = "not-in-source";

	/** The system fields a restore sets, with their key and label. */
	private static final Map<String, String> SYSTEM_LABELS = systemLabels();

	private final EntityStore entities;

	private final EntityVersionStore versions;

	private final EntityVersions display;

	private final EntityHistoryStore history;

	private final PropertyAccess access;

	private final EntityWriter writer;

	private final PropertyValueStore values;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final MetatypeRuleStore ruleStore;

	private final Clock clock;

	private final EntityBlocks blocks;

	public EntityRestore(EntityStore entities, EntityVersionStore versions, EntityVersions display,
			EntityHistoryStore history,
			PropertyAccess access, EntityWriter writer, PropertyValueStore values, MetamodelLabels labels,
			LanguagePreferences languages, MetatypeRuleStore ruleStore, Clock clock, EntityBlocks blocks) {
		this.blocks = blocks;
		this.entities = entities;
		this.versions = versions;
		this.display = display;
		this.history = history;
		this.access = access;
		this.writer = writer;
		this.values = values;
		this.labels = labels;
		this.languages = languages;
		this.ruleStore = ruleStore;
		this.clock = clock;
	}

	/**
	 * A field whose current value differs from the target version; both values are {@code null}
	 * when redacted, {@code reason} is {@code null} when the field is restored.
	 */
	public record Field(String field, String label, boolean systemField, String dataType, String currentValue,
			String targetValue, boolean redacted, boolean restore, String reason) {
	}

	/** What a restore to the target version would do. */
	public record Preview(int entityId, int currentVersion, int targetVersion, List<Field> fields,
			List<Issue> warnings, List<Connection> connections) {
	}

	/** A connection of the entity changed or created after the target version (us-104 D3). */
	public record Connection(int entityId, String name, String otherEndpoint, boolean outgoing, boolean added) {
	}

	/** A field of the result of a restore; {@code reason} is {@code null} for a restored field. */
	public record FieldRef(String field, String label, String reason) {
	}

	/** The outcome of a restore. */
	public record Result(int entityId, int version, int restoredFromVersion, List<FieldRef> restored,
			List<FieldRef> notRestored, List<Issue> warnings) {
	}

	/**
	 * The classification of the differing fields by key ({@code name}, {@code description},
	 * {@code isLogical} or the ID of a property definition), the input with the restorable
	 * values and its validation.
	 */
	private record Plan(Map<String, String> reasons, Set<String> restorable, Map<UUID, TypedValue> current,
			EntityValidator.Checked checked, List<Issue> warnings, Set<UUID> missingMandatory, Instant targetTime) {
	}

	/**
	 * The preview of a restore of the entity to {@code targetVersion}; changes nothing.
	 *
	 * @throws NoSuchElementException for an unknown entity or a version after the current one (404)
	 * @throws InvalidHistoryQueryException naming {@code targetVersion} when it is below 1 or not
	 * before the current version
	 */
	@Transactional(readOnly = true)
	public Preview preview(OeaPrincipal principal, int entityId, int targetVersion) {
		StoredEntity entity = this.entities.find(entityId).orElseThrow(NoSuchElementException::new);
		checkTarget(entity, targetVersion);
		Plan plan = plan(principal, entity, targetVersion, null);
		List<Field> fields = new ArrayList<>();
		// the display rules of the diff: labels, text values, redaction, hiding, the override audit
		for (EntityVersions.Change change : this.display.compare(principal, entityId, targetVersion, entity.version())
			.changes()) {
			if (change.systemField() && !SYSTEM_LABELS.containsKey(change.field())) {
				continue;
			}
			String reason = plan.reasons().get(change.field());
			fields.add(new Field(change.field(), change.label(), change.systemField(), change.dataType(),
					change.newValue(), change.oldValue(), change.redacted(), reason == null, reason));
		}
		List<Connection> connections = this.history.connectionsChangedSince(entityId, plan.targetTime())
			.stream()
			.map((connection) -> new Connection(connection.entityId(), connection.name(), connection.otherEndpoint(),
					connection.outgoing(), connection.added()))
			.toList();
		return new Preview(entityId, entity.version(), targetVersion, List.copyOf(fields), plan.warnings(),
				connections);
	}

	/**
	 * Restores the entity to {@code targetVersion} in one transaction: in full, or with
	 * {@code fields} only the selected fields (us-104-partial-restore D2).
	 *
	 * @param expectedVersion the version named by {@code If-Match}, {@code null} without the header
	 * @param fields the keys of the fields of a partial restore, {@code null} for a full one
	 * @throws InvalidRestoreSelectionException for an empty selection or a key that is not a
	 * differing field (422)
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the entity is at another version (412)
	 * @throws NothingToRestoreException when every restorable field has its target value (422)
	 * @throws EntityBlockedException while the entity has open issues of a solution (409)
	 */
	@Transactional
	public Result restore(OeaPrincipal principal, int entityId, int targetVersion, Long expectedVersion,
			String reason, List<String> fields) {
		String given = (reason != null && !reason.isBlank()) ? reason : null;
		if (given != null && given.length() > REASON_MAX_LENGTH) {
			throw new InvalidHistoryQueryException("reason");
		}
		if (fields != null && fields.isEmpty()) {
			throw new InvalidRestoreSelectionException();
		}
		StoredEntity entity = this.entities.lock(entityId).orElseThrow(NoSuchElementException::new);
		if (expectedVersion == null) {
			throw new PreconditionRequiredException();
		}
		if (expectedVersion != entity.version()) {
			throw new StaleEntityVersionException(entity.version());
		}
		checkTarget(entity, targetVersion);
		// an entity with open issues of a solution is written only by resolving them (US-153 AC1)
		this.blocks.check(principal, List.of(entityId));
		Plan plan = plan(principal, entity, targetVersion, (fields != null) ? new LinkedHashSet<>(fields) : null);
		if (plan.restorable().isEmpty()) {
			throw new NothingToRestoreException();
		}
		EntityWriter.Written written = this.writer.write(principal, plan.checked(), entity, plan.current(),
				EntityWriter.WriteContext.restore(targetVersion, given,
						(fields != null) ? List.copyOf(plan.restorable()) : null));
		this.values.mark(entityId, plan.missingMandatory(), this.clock.instant());

		Set<UUID> properties = new HashSet<>();
		plan.reasons().keySet().forEach((key) -> property(key).ifPresent(properties::add));
		plan.restorable().forEach((key) -> property(key).ifPresent(properties::add));
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(this.languages.language(principal), properties);
		List<FieldRef> restored = plan.restorable()
			.stream()
			.map((key) -> new FieldRef(key, label(key, propertyLabels), null))
			.toList();
		List<FieldRef> notRestored = plan.reasons()
			.entrySet()
			.stream()
			.map((entry) -> new FieldRef(entry.getKey(), label(entry.getKey(), propertyLabels), entry.getValue()))
			.toList();
		return new Result(entityId, written.version(), targetVersion, restored, notRestored, plan.warnings());
	}

	private static void checkTarget(StoredEntity entity, int targetVersion) {
		if (targetVersion > entity.version()) {
			throw new NoSuchElementException();
		}
		if (targetVersion < 1 || targetVersion == entity.version()) {
			throw new InvalidHistoryQueryException("targetVersion");
		}
	}

	/**
	 * Classifies every differing field and builds the input of the restore: all restorable
	 * fields, or with a {@code selection} only the selected ones (us-104-partial-restore D2).
	 *
	 * @throws InvalidRestoreSelectionException for a selected key that is not a differing field
	 */
	private Plan plan(OeaPrincipal principal, StoredEntity entity, int targetVersion, Set<String> selection) {
		EntityVersionStore.State target = this.versions.state(entity.id(), targetVersion)
			.orElseThrow(NoSuchElementException::new);
		Map<UUID, TypedValue> current = this.entities.values(entity.id());
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock))
			.forWriter(PropertyWriteAccess.writer(snapshot));
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		if (metatype == null) {
			throw new NoSuchElementException();
		}

		String currentDescription = Objects.requireNonNullElse(entity.description(), "");
		String name = systemValue(target, EntityVersionStore.NAME, entity.name());
		String description = systemValue(target, EntityVersionStore.DESCRIPTION, currentDescription);
		boolean logical = Boolean.parseBoolean(
				systemValue(target, EntityVersionStore.LOGICAL, Boolean.toString(entity.logical())));
		Set<String> restorable = new LinkedHashSet<>();
		if (!name.equals(entity.name())) {
			restorable.add("name");
		}
		if (!description.equals(currentDescription)) {
			restorable.add("description");
		}
		if (logical != entity.logical()) {
			restorable.add("isLogical");
		}

		Map<String, String> reasons = new LinkedHashMap<>();
		Map<UUID, String> values = new LinkedHashMap<>();
		// mapped properties in mapping order, then the others (the order of restoredFields)
		Set<UUID> properties = new LinkedHashSet<>();
		metatype.mappings().keySet().forEach((property) -> {
			if (current.containsKey(property) || target.values().containsKey(property)) {
				properties.add(property);
			}
		});
		current.keySet().stream().sorted().forEach(properties::add);
		target.values().keySet().stream().sorted().forEach(properties::add);
		for (UUID property : properties) {
			TypedValue now = current.get(property);
			TypedValue then = target.values().get(property);
			if (Objects.equals(text(now), text(then))) {
				continue;
			}
			String key = property.toString();
			MetatypeRules.Mapping mapping = metatype.mappings().get(property);
			if (mapping == null) {
				reasons.put(key, NOT_MAPPED);
			}
			else if (then == null && !target.mapped().containsKey(property)) {
				// the target version did not know the property (REQ-105 AC2)
				reasons.put(key, NOT_IN_SOURCE);
			}
			else if (!rules.writable(mapping.definition(), entity.id())) {
				reasons.put(key, WRITE_PROTECTED);
			}
			else if (then != null && !EntityValidator.accepts(mapping.definition(), then.asText(),
					this.entities::isActive)) {
				reasons.put(key, INVALID_VALUE);
			}
			else {
				restorable.add(key);
				values.put(property, (then != null) ? then.asText() : "");
			}
		}

		if (selection != null) {
			for (String key : selection) {
				if (!restorable.contains(key) && !reasons.containsKey(key)) {
					throw new InvalidRestoreSelectionException();
				}
			}
			restorable.retainAll(selection);
			reasons.keySet().retainAll(selection);
		}
		List<EntityInput.PropertyInput> inputs = new ArrayList<>();
		values.forEach((property, value) -> {
			if (restorable.contains(property.toString())) {
				inputs.add(new EntityInput.PropertyInput(property, value));
			}
		});
		EntityInput input = new EntityInput(restorable.contains("name") ? name : entity.name(), entity.metatypeId(),
				null, restorable.contains("description") ? description : currentDescription,
				restorable.contains("isLogical") ? logical : entity.logical(), null, null, inputs);
		EntityValidator.Checked checked = EntityValidator.check(input, rules, entity, current, this.entities::isActive,
				metatype.connection(), EntityValidator.Necessity.WARN);
		if (!checked.valid()) {
			throw new IllegalStateException("restore input is not valid: " + checked.errors());
		}

		// warnings by field key; a partial restore reports only the fields it restores
		List<Issue> warnings = new ArrayList<>();
		Set<UUID> missingMandatory = new LinkedHashSet<>();
		metatype.mappings().forEach((property, mapping) -> {
			if (checked.values().containsKey(property) || !rules.writable(mapping.definition(), entity.id())
					|| (selection != null && !restorable.contains(property.toString()))) {
				return;
			}
			String definition = mapping.definition().name();
			if ("mandatory".equals(mapping.necessity())) {
				warnings.add(new Issue(property.toString(), "MANDATORY_MISSING",
						"The mandatory property " + definition + " has no value."));
				if (restorable.contains(property.toString())) {
					missingMandatory.add(property);
				}
			}
			else if ("warning".equals(mapping.necessity())) {
				warnings.add(new Issue(property.toString(), "VALUE_MISSING",
						"The property " + definition + " should have a value."));
			}
		});
		return new Plan(reasons, restorable, current, checked, List.copyOf(warnings), missingMandatory,
				target.header().changedAt());
	}

	/** A system field of the target state; an empty description has no history value. */
	private static String systemValue(EntityVersionStore.State state, String key, String fallback) {
		String value = state.systemFields().get(key);
		if (value != null) {
			return value;
		}
		return EntityVersionStore.DESCRIPTION.equals(key) ? "" : Objects.requireNonNullElse(fallback, "");
	}

	private static String text(TypedValue value) {
		return (value != null) ? value.asText() : null;
	}

	private static Optional<UUID> property(String key) {
		return SYSTEM_LABELS.containsKey(key) ? Optional.empty()
				: Optional.of(UUID.fromString(key));
	}

	private static String label(String key, Map<UUID, String> propertyLabels) {
		String system = SYSTEM_LABELS.get(key);
		return (system != null) ? system : propertyLabels.getOrDefault(UUID.fromString(key), key);
	}

	private static Map<String, String> systemLabels() {
		Map<String, String> labels = new LinkedHashMap<>();
		for (String key : List.of(EntityVersionStore.NAME, EntityVersionStore.DESCRIPTION, EntityVersionStore.LOGICAL)) {
			EntityHistory.SystemField field = EntityHistory.SYSTEM_FIELDS.get(key);
			labels.put(field.key(), field.label());
		}
		return Map.copyOf(labels);
	}

}
