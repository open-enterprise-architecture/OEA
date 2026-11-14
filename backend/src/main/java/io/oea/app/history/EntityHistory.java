package io.oea.app.history;

import io.oea.app.access.LanguagePreferences;
import io.oea.app.catalog.CatalogCursors;
import io.oea.app.metamodel.HistoryVisibility;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.core.catalog.CatalogQueryStore.KeyType;
import io.oea.core.entity.EntityHistoryStore;
import io.oea.core.entity.EntityHistoryStore.ChangedField;
import io.oea.core.entity.EntityHistoryStore.Filter;
import io.oea.core.entity.EntityHistoryStore.HistoryEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
 * The timeline of an entity's versions (US-098, REQ-098; design D2 of us-098-entity-history):
 * newest first, each with the fields it changed and without values. A changed property the
 * reader may not read under the current visibility (REQ-160) is marked restricted, or left out
 * when every role of the reader hides it for the entity's metatype ({@link HistoryVisibility}).
 * A version whose fields are all left out stays in the list, so the numbering stays complete.
 */
@Service
public class EntityHistory {

	private static final List<KeyType> KEY_TYPES = List.of(KeyType.INTEGER);

	/** The system fields of the history rows with their contract keys and English labels. */
	private static final Map<String, SystemField> SYSTEM_FIELDS = Map.of("name", new SystemField("name", "Name", 0),
			"description", new SystemField("description", "Description", 1), "is_logical",
			new SystemField("isLogical", "Logical", 2), "metatype_id", new SystemField("metatypeId", "Metatype", 3),
			"source_entity_id", new SystemField("sourceId", "Source", 4), "target_entity_id",
			new SystemField("targetId", "Target", 5));

	private final EntityHistoryStore store;

	private final PropertyAccess access;

	private final PermissionResolver permissions;

	private final HistoryVisibility visibility;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final CatalogCursors cursors;

	public EntityHistory(EntityHistoryStore store, PropertyAccess access, PermissionResolver permissions,
			HistoryVisibility visibility, MetamodelLabels labels, LanguagePreferences languages,
			CatalogCursors cursors) {
		this.store = store;
		this.access = access;
		this.permissions = permissions;
		this.visibility = visibility;
		this.labels = labels;
		this.languages = languages;
		this.cursors = cursors;
	}

	private record SystemField(String key, String label, int order) {
	}

	/** A person who made a version. */
	public record Actor(long id, String name) {

		static Actor of(EntityHistoryStore.Actor actor) {
			return new Actor(actor.id(), actor.name());
		}

	}

	/** A history read: the optional filters and the page; the contract checks the page limits. */
	public record Query(Instant from, Instant to, Long actorId, int page, int pageSize, String cursor) {
	}

	/** A changed field: a system field or a property definition (its ID as {@code field}). */
	public record Change(String field, String label, boolean systemField, boolean restricted) {
	}

	/** A version with the person who made it and its listed fields. */
	public record Version(int version, Instant changedAt, Actor changedBy, List<Change> changes) {
	}

	/** The creation of the entity: version 1. */
	public record Creation(Instant changedAt, Actor changedBy) {
	}

	/** One page of the timeline; {@code nextCursor} is {@code null} on the last page. */
	public record Page(int entityId, String entityName, Creation created, List<Actor> actors, List<Version> versions,
			int page, int pageSize, long totalElements, int totalPages, String nextCursor) {
	}

	/**
	 * One page of the history of the entity for the reader.
	 *
	 * @throws NoSuchElementException for an unknown entity (404)
	 * @throws InvalidHistoryQueryException naming {@code from} when it is after {@code to}, or
	 * {@code cursor} for a cursor of another query
	 */
	@Transactional(readOnly = true)
	public Page read(OeaPrincipal principal, int entityId, Query query) {
		return read(this.access.of(principal), this.permissions.roles(principal), this.languages.language(principal),
				entityId, query);
	}

	/** {@link #read(OeaPrincipal, int, Query)} with the reader's access, roles and language. */
	Page read(PropertyAccess.Snapshot access, Collection<String> roles, String language, int entityId, Query query) {
		HistoryEntity entity = this.store.entity(entityId).orElseThrow(NoSuchElementException::new);
		if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
			throw new InvalidHistoryQueryException("from");
		}
		Filter filter = new Filter(query.from(), query.to(), query.actorId());
		String fingerprint = CatalogCursors.fingerprint(List.of("entity-history", Integer.toString(entityId),
				Integer.toString(query.pageSize()), Objects.toString(query.from(), ""), Objects.toString(query.to(), ""),
				Objects.toString(query.actorId(), "")));
		Integer before = null;
		if (query.cursor() != null && !query.cursor().isEmpty()) {
			before = (Integer) this.cursors.decode(query.cursor(), fingerprint, query.page(), KEY_TYPES)
				.orElseThrow(() -> new InvalidHistoryQueryException("cursor"))
				.get(0);
		}
		long total = this.store.count(entityId, filter);
		List<EntityHistoryStore.Version> headers = this.store.versions(entityId, filter, before,
				(long) query.page() * query.pageSize(), query.pageSize());
		Map<Integer, List<ChangedField>> changed = this.store.changes(entityId,
				headers.stream().map(EntityHistoryStore.Version::version).toList());

		Set<UUID> properties = new LinkedHashSet<>();
		changed.values()
			.forEach((fields) -> fields.stream()
				.map(ChangedField::propertyDefId)
				.filter(Objects::nonNull)
				.forEach(properties::add));
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(language, properties);
		boolean hide = !properties.isEmpty() && this.visibility.hidden(new HashSet<>(roles), entity.metatypeId());

		List<Version> versions = new ArrayList<>(headers.size());
		for (EntityHistoryStore.Version header : headers) {
			List<Change> changes = new ArrayList<>();
			List<Change> propertyChanges = new ArrayList<>();
			for (ChangedField field : changed.getOrDefault(header.version(), List.of())) {
				if (field.systemField() != null) {
					SystemField system = SYSTEM_FIELDS.get(field.systemField());
					changes.add((system != null) ? new Change(system.key(), system.label(), true, false)
							: new Change(field.systemField(), field.systemField(), true, false));
					continue;
				}
				UUID property = field.propertyDefId();
				boolean readable = access.readableFor(property, entityId);
				if (!readable && hide) {
					continue;
				}
				propertyChanges.add(new Change(property.toString(),
						propertyLabels.getOrDefault(property, property.toString()), false, !readable));
			}
			changes.sort(Comparator.comparingInt((Change change) -> systemOrder(change.field())));
			propertyChanges.sort(Comparator.comparing(Change::label, String.CASE_INSENSITIVE_ORDER));
			changes.addAll(propertyChanges);
			versions.add(new Version(header.version(), header.changedAt(), Actor.of(header.changedBy()),
					List.copyOf(changes)));
		}

		String nextCursor = null;
		if (!versions.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			nextCursor = this.cursors.encode(fingerprint, query.page() + 1,
					List.of(versions.get(versions.size() - 1).version()));
		}
		Creation created = this.store.creation(entityId)
			.map((version) -> new Creation(version.changedAt(), Actor.of(version.changedBy())))
			.orElse(null);
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(entity.id(), entity.name(), created,
				this.store.actors(entityId).stream().map(Actor::of).toList(), List.copyOf(versions),
				query.page(), query.pageSize(), total, totalPages, nextCursor);
	}

	private static int systemOrder(String key) {
		return SYSTEM_FIELDS.values()
			.stream()
			.filter((field) -> field.key().equals(key))
			.mapToInt(SystemField::order)
			.findFirst()
			.orElse(SYSTEM_FIELDS.size());
	}

}
