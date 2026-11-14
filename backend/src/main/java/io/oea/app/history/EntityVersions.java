package io.oea.app.history;

import io.oea.app.access.LanguagePreferences;
import io.oea.app.metamodel.HistoryVisibility;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.OverrideAccessAudit;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.core.entity.EntityHistoryStore;
import io.oea.core.entity.EntityHistoryStore.HistoryEntity;
import io.oea.core.entity.EntityVersionStore;
import io.oea.core.entity.EntityVersionStore.Definition;
import io.oea.core.entity.TypedValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
 * The values of an entity's versions (US-099, US-100, REQ-099, REQ-100; design D2 of
 * us-099-version-diff): the complete state of a version and the net diff of two versions. The
 * states are reconstructed by {@link EntityVersionStore}. A property the reader may not read
 * carries no value and is restricted or hidden by {@link HistoryAccess}; a value read only
 * through the override is audited as {@code property-override-access} (channel {@code history}).
 */
@Service
public class EntityVersions {

	/** The data type of system fields. */
	public static final String SYSTEM = "system";

	private static final List<String> SYSTEM_ORDER = List.of(EntityVersionStore.NAME, EntityVersionStore.DESCRIPTION,
			EntityVersionStore.LOGICAL, EntityVersionStore.METATYPE, EntityVersionStore.SOURCE,
			EntityVersionStore.TARGET);

	private final EntityVersionStore store;

	private final EntityHistoryStore entities;

	private final PropertyAccess access;

	private final PermissionResolver permissions;

	private final HistoryVisibility visibility;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final OverrideAccessAudit audit;

	public EntityVersions(EntityVersionStore store, EntityHistoryStore entities, PropertyAccess access,
			PermissionResolver permissions, HistoryVisibility visibility, MetamodelLabels labels,
			LanguagePreferences languages, OverrideAccessAudit audit) {
		this.store = store;
		this.entities = entities;
		this.access = access;
		this.permissions = permissions;
		this.visibility = visibility;
		this.labels = labels;
		this.languages = languages;
		this.audit = audit;
	}

	/** A field of a state; {@code value} is {@code null} without value or when restricted. */
	public record Field(String field, String label, boolean systemField, String dataType, String value,
			boolean restricted) {
	}

	/** The complete state of a version. */
	public record State(int entityId, String entityName, int version, Instant changedAt, EntityHistory.Actor changedBy,
			List<Field> fields) {
	}

	/** A field whose value differs between two versions; both values are {@code null} when redacted. */
	public record Change(String field, String label, boolean systemField, String dataType, String oldValue,
			String newValue, boolean redacted) {
	}

	/** The net diff of the states of {@code from} and {@code to}. */
	public record Diff(int entityId, int from, int to, List<Change> changes) {
	}

	/** A field of a reconstructed state before the visibility rule: raw value for comparing, text for showing. */
	private record Entry(String field, String label, boolean systemField, String dataType, UUID property,
			String raw, String text) {
	}

	/** The reader of a request. */
	private record Reader(OeaPrincipal principal, HistoryAccess rule, String language) {
	}

	/**
	 * The complete state of the version for the reader.
	 *
	 * @throws NoSuchElementException for an unknown entity or version (404)
	 */
	@Transactional(readOnly = true)
	public State state(OeaPrincipal principal, int entityId, int version) {
		HistoryEntity entity = this.entities.entity(entityId).orElseThrow(NoSuchElementException::new);
		Reader reader = reader(principal, entity);
		EntityVersionStore.State state = this.store.state(entityId, version).orElseThrow(NoSuchElementException::new);
		Map<String, Entry> entries = entries(List.of(state), reader.language()).get(0);
		Map<UUID, Set<Integer>> overridden = new LinkedHashMap<>();
		List<Field> fields = new ArrayList<>();
		for (Entry entry : entries.values()) {
			HistoryAccess.Visibility visibility = visibility(reader, entry, entityId);
			if (visibility == HistoryAccess.Visibility.HIDDEN) {
				continue;
			}
			boolean restricted = visibility == HistoryAccess.Visibility.RESTRICTED;
			if (!restricted && entry.raw() != null) {
				noteOverride(reader, entry, entityId, overridden);
			}
			fields.add(new Field(entry.field(), entry.label(), entry.systemField(), entry.dataType(),
					restricted ? null : entry.text(), restricted));
		}
		auditOverride(reader, entityId, overridden, entries);
		EntityVersionStore.Header header = state.header();
		return new State(entity.id(), entity.name(), header.version(), header.changedAt(),
				new EntityHistory.Actor(header.changedBy(), header.changedByName()), List.copyOf(fields));
	}

	/**
	 * The net diff of the states of {@code from} and {@code to} for the reader.
	 *
	 * @throws NoSuchElementException for an unknown entity or version (404)
	 * @throws InvalidHistoryQueryException naming {@code from} when it is below 1 or not before
	 * {@code to}
	 */
	@Transactional(readOnly = true)
	public Diff compare(OeaPrincipal principal, int entityId, int from, int to) {
		HistoryEntity entity = this.entities.entity(entityId).orElseThrow(NoSuchElementException::new);
		if (from < 1 || from >= to) {
			throw new InvalidHistoryQueryException("from");
		}
		Reader reader = reader(principal, entity);
		EntityVersionStore.State before = this.store.state(entityId, from).orElseThrow(NoSuchElementException::new);
		EntityVersionStore.State after = this.store.state(entityId, to).orElseThrow(NoSuchElementException::new);
		List<Map<String, Entry>> both = entries(List.of(before, after), reader.language());
		Map<String, Entry> old = both.get(0);
		Map<String, Entry> now = both.get(1);
		// the order of the later state, then the fields only the earlier state has
		Map<String, Entry> all = new LinkedHashMap<>(now);
		old.forEach(all::putIfAbsent);
		List<Entry> ordered = List.copyOf(all.values());

		Map<UUID, Set<Integer>> overridden = new LinkedHashMap<>();
		List<Change> changes = new ArrayList<>();
		for (Entry entry : ordered) {
			Entry was = old.get(entry.field());
			Entry is = now.get(entry.field());
			String oldRaw = (was != null) ? was.raw() : null;
			String newRaw = (is != null) ? is.raw() : null;
			if (Objects.equals(oldRaw, newRaw)) {
				continue;
			}
			Entry shown = (is != null) ? is : was;
			HistoryAccess.Visibility visibility = visibility(reader, shown, entityId);
			if (visibility == HistoryAccess.Visibility.HIDDEN) {
				continue;
			}
			boolean redacted = visibility == HistoryAccess.Visibility.RESTRICTED;
			if (!redacted) {
				noteOverride(reader, shown, entityId, overridden);
			}
			changes.add(new Change(shown.field(), shown.label(), shown.systemField(), shown.dataType(),
					(redacted || was == null) ? null : was.text(), (redacted || is == null) ? null : is.text(),
					redacted));
		}
		auditOverride(reader, entityId, overridden, all);
		return new Diff(entity.id(), from, to, List.copyOf(changes));
	}

	private Reader reader(OeaPrincipal principal, HistoryEntity entity) {
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		Collection<String> roles = this.permissions.roles(principal);
		boolean hide = this.visibility.hidden(new HashSet<>(roles), entity.metatypeId());
		return new Reader(principal, new HistoryAccess(snapshot, hide), this.languages.language(principal));
	}

	private static HistoryAccess.Visibility visibility(Reader reader, Entry entry, int entityId) {
		return entry.systemField() ? HistoryAccess.Visibility.READABLE : reader.rule().of(entry.property(), entityId);
	}

	private static void noteOverride(Reader reader, Entry entry, int entityId, Map<UUID, Set<Integer>> overridden) {
		if (!entry.systemField() && reader.rule().access().throughOverrideFor(entry.property(), entityId)) {
			overridden.computeIfAbsent(entry.property(), (key) -> new LinkedHashSet<>()).add(entityId);
		}
	}

	private void auditOverride(Reader reader, int entityId, Map<UUID, Set<Integer>> overridden,
			Map<String, Entry> entries) {
		if (overridden.isEmpty()) {
			return;
		}
		Map<UUID, String> names = new LinkedHashMap<>();
		Map<UUID, Definition> definitions = this.store.definitions(overridden.keySet());
		overridden.keySet()
			.forEach((id) -> names.put(id,
					(definitions.get(id) != null) ? definitions.get(id).name() : entries.get(id.toString()).label()));
		this.audit.append(reader.principal(), "history", 0, "entity", Integer.toString(entityId), names, overridden);
	}

	/**
	 * The fields of each state in order: system fields, then the mapped properties in mapping
	 * order, then the other properties with a value by label. Names of references, endpoints and
	 * metatypes and the labels are read once for all states.
	 */
	private List<Map<String, Entry>> entries(List<EntityVersionStore.State> states, String language) {
		Set<UUID> properties = new LinkedHashSet<>();
		Set<Integer> referenced = new HashSet<>();
		Set<UUID> metatypes = new HashSet<>();
		for (EntityVersionStore.State state : states) {
			properties.addAll(state.mapped().keySet());
			properties.addAll(state.values().keySet());
			state.values().values().stream().map(TypedValue::reference).filter(Objects::nonNull).forEach(referenced::add);
			metatypes.add(state.metatypeId());
			for (String endpoint : List.of(EntityVersionStore.SOURCE, EntityVersionStore.TARGET)) {
				String id = state.systemFields().get(endpoint);
				if (id != null) {
					referenced.add(Integer.valueOf(id));
				}
			}
		}
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(language, properties);
		Map<UUID, Definition> definitions = this.store.definitions(properties);
		Map<Integer, String> entityNames = this.store.entityNames(referenced);
		Map<UUID, String> metatypeNames = this.store.metatypeNames(metatypes);

		List<Map<String, Entry>> result = new ArrayList<>();
		for (EntityVersionStore.State state : states) {
			Map<String, Entry> entries = new LinkedHashMap<>();
			for (String key : SYSTEM_ORDER) {
				if (!state.systemFields().containsKey(key)) {
					continue;
				}
				String stored = state.systemFields().get(key);
				// an empty description is no value
				String raw = (stored != null && !stored.isEmpty()) ? stored : null;
				String text = raw;
				if (raw != null && key.equals(EntityVersionStore.METATYPE)) {
					text = metatypeNames.getOrDefault(UUID.fromString(raw), raw);
				}
				else if (raw != null && (key.equals(EntityVersionStore.SOURCE) || key.equals(EntityVersionStore.TARGET))) {
					text = entityNames.getOrDefault(Integer.valueOf(raw), raw);
				}
				EntityHistory.SystemField system = EntityHistory.SYSTEM_FIELDS.get(key);
				entries.put(system.key(), new Entry(system.key(), system.label(), true, SYSTEM, null, raw, text));
			}
			List<UUID> others = new ArrayList<>();
			for (UUID property : state.values().keySet()) {
				if (!state.mapped().containsKey(property)) {
					others.add(property);
				}
			}
			others.sort(Comparator.comparing((UUID id) -> propertyLabels.getOrDefault(id, id.toString()),
					String.CASE_INSENSITIVE_ORDER));
			List<UUID> ordered = new ArrayList<>(state.mapped().keySet());
			ordered.addAll(others);
			for (UUID property : ordered) {
				TypedValue value = state.values().get(property);
				String raw = (value != null) ? value.asText() : null;
				String text = raw;
				if (value != null && value.reference() != null) {
					text = entityNames.getOrDefault(value.reference(), raw);
				}
				Definition definition = definitions.get(property);
				entries.put(property.toString(),
						new Entry(property.toString(), propertyLabels.getOrDefault(property, property.toString()), false,
								(definition != null) ? definition.dataType() : "string", property, raw, text));
			}
			result.add(entries);
		}
		return result;
	}

}
