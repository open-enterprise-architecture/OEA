package io.oea.app.history;

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
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.EntityVersionStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import java.time.Clock;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

	private static final Set<String> WARNINGS = Set.of("MANDATORY_MISSING", "VALUE_MISSING");

	/** The system fields a restore sets, with their key and label. */
	private static final Map<String, String> SYSTEM_LABELS = systemLabels();

	private final EntityStore entities;

	private final EntityVersionStore versions;

	private final EntityVersions display;

	private final PropertyAccess access;

	private final EntityWriter writer;

	private final PropertyValueStore values;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public EntityRestore(EntityStore entities, EntityVersionStore versions, EntityVersions display,
			PropertyAccess access, EntityWriter writer, PropertyValueStore values, MetamodelLabels labels,
			LanguagePreferences languages, JdbcTemplate jdbc, Clock clock) {
		this.entities = entities;
		this.versions = versions;
		this.display = display;
		this.access = access;
		this.writer = writer;
		this.values = values;
		this.labels = labels;
		this.languages = languages;
		this.jdbc = jdbc;
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
			List<Issue> warnings) {
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
			EntityValidator.Checked checked, Set<UUID> missingMandatory) {
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
		Plan plan = plan(principal, entity, targetVersion);
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
		return new Preview(entityId, entity.version(), targetVersion, List.copyOf(fields),
				warnings(plan.checked()));
	}

	/**
	 * Restores the entity to {@code targetVersion} in one transaction.
	 *
	 * @param expectedVersion the version named by {@code If-Match}, {@code null} without the header
	 * @throws PreconditionRequiredException without {@code If-Match} (428)
	 * @throws StaleEntityVersionException when the entity is at another version (412)
	 * @throws NothingToRestoreException when every restorable field has its target value (422)
	 */
	@Transactional
	public Result restore(OeaPrincipal principal, int entityId, int targetVersion, Long expectedVersion,
			String reason) {
		String given = (reason != null && !reason.isBlank()) ? reason : null;
		if (given != null && given.length() > REASON_MAX_LENGTH) {
			throw new InvalidHistoryQueryException("reason");
		}
		StoredEntity entity = this.entities.lock(entityId).orElseThrow(NoSuchElementException::new);
		if (expectedVersion == null) {
			throw new PreconditionRequiredException();
		}
		if (expectedVersion != entity.version()) {
			throw new StaleEntityVersionException(entity.version());
		}
		checkTarget(entity, targetVersion);
		Plan plan = plan(principal, entity, targetVersion);
		if (plan.restorable().isEmpty()) {
			throw new NothingToRestoreException();
		}
		EntityWriter.Written written = this.writer.write(principal, plan.checked(), entity, plan.current(),
				EntityWriter.WriteContext.restore(targetVersion, given));
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
		return new Result(entityId, written.version(), targetVersion, restored, notRestored,
				warnings(plan.checked()));
	}

	private static void checkTarget(StoredEntity entity, int targetVersion) {
		if (targetVersion > entity.version()) {
			throw new NoSuchElementException();
		}
		if (targetVersion < 1 || targetVersion == entity.version()) {
			throw new InvalidHistoryQueryException("targetVersion");
		}
	}

	private Plan plan(OeaPrincipal principal, StoredEntity entity, int targetVersion) {
		EntityVersionStore.State target = this.versions.state(entity.id(), targetVersion)
			.orElseThrow(NoSuchElementException::new);
		Map<UUID, TypedValue> current = this.entities.values(entity.id());
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock))
			.forWriter(PropertyWriteAccess.writer(snapshot));
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		if (metatype == null) {
			throw new NoSuchElementException();
		}

		Set<String> restorable = new LinkedHashSet<>();
		String name = systemValue(target, EntityVersionStore.NAME, entity.name());
		String description = systemValue(target, EntityVersionStore.DESCRIPTION, entity.description());
		boolean logical = Boolean.parseBoolean(
				systemValue(target, EntityVersionStore.LOGICAL, Boolean.toString(entity.logical())));
		if (!name.equals(entity.name())) {
			restorable.add("name");
		}
		if (!description.equals(Objects.requireNonNullElse(entity.description(), ""))) {
			restorable.add("description");
		}
		if (logical != entity.logical()) {
			restorable.add("isLogical");
		}

		Map<String, String> reasons = new LinkedHashMap<>();
		List<EntityInput.PropertyInput> inputs = new ArrayList<>();
		Set<UUID> properties = new LinkedHashSet<>(current.keySet());
		properties.addAll(target.values().keySet());
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
			else if (!rules.writable(mapping.definition(), entity.id())) {
				reasons.put(key, WRITE_PROTECTED);
			}
			else if (then != null && !EntityValidator.accepts(mapping.definition(), then.asText(),
					this.entities::isActive)) {
				reasons.put(key, INVALID_VALUE);
			}
			else {
				restorable.add(key);
				inputs.add(new EntityInput.PropertyInput(property, (then != null) ? then.asText() : ""));
			}
		}

		EntityInput input = new EntityInput(name, entity.metatypeId(), null, description, logical, null, null, inputs);
		EntityValidator.Checked checked = EntityValidator.check(input, rules, entity, current, this.entities::isActive,
				metatype.connection(), EntityValidator.Necessity.WARN);
		if (!checked.valid()) {
			throw new IllegalStateException("restore input is not valid: " + checked.errors());
		}
		Set<UUID> missingMandatory = new LinkedHashSet<>();
		metatype.mappings().forEach((property, mapping) -> {
			if ("mandatory".equals(mapping.necessity()) && !checked.values().containsKey(property)
					&& rules.writable(mapping.definition(), entity.id())) {
				missingMandatory.add(property);
			}
		});
		return new Plan(reasons, restorable, current, checked, missingMandatory);
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

	private static List<Issue> warnings(EntityValidator.Checked checked) {
		return checked.warnings().stream().filter((issue) -> WARNINGS.contains(issue.code())).toList();
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
