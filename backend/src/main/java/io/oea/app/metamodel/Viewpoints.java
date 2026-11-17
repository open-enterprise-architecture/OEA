package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.ViewpointStore;
import io.oea.core.metamodel.ViewpointStore.ViewpointRow;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, reading, editing and deleting the generic viewpoints of the instance (US-090,
 * US-093, UC-12; design D2 of us-090-viewpoints): a name unique case-insensitively, at least one
 * element metatype and any number of connection metatypes of the instance configuration, and a
 * version for optimistic locking. The permissions are checked from the contract; the metamodel
 * lock mode does not apply.
 */
@Service
public class Viewpoints {

	private static final String NAME = "name";

	private static final String METATYPES = "metatypeIds";

	private static final String CONNECTIONS = "connectionTypeIds";

	private final ViewpointStore store;

	private final AuditWriter audit;

	private final Clock clock;

	Viewpoints(ViewpointStore store, AuditWriter audit, Clock clock) {
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	/** A metatype a viewpoint allows. */
	public record Type(UUID id, String name, String abbreviation) {
	}

	/** A viewpoint with its allowed element and connection metatypes, each ordered by name. */
	public record Viewpoint(UUID id, String name, String description, int version, java.time.Instant createdAt,
			long createdById, String createdByName, List<Type> metatypes, List<Type> connectionTypes) {

		static Viewpoint of(ViewpointRow row) {
			return new Viewpoint(row.id(), row.name(), row.description(), row.version(), row.createdAt(),
					row.createdById(), row.createdByName(), types(row.metatypes()), types(row.connectionTypes()));
		}

		private static List<Type> types(List<ViewpointStore.TypeRow> rows) {
			return rows.stream().map((row) -> new Type(row.id(), row.name(), row.abbreviation())).toList();
		}

	}

	/** The input of a write; the contract checks the lengths. */
	public record ViewpointInput(String name, String description, List<UUID> metatypeIds,
			List<UUID> connectionTypeIds) {
	}

	/** All viewpoints, ordered by name. */
	@Transactional(readOnly = true)
	public List<Viewpoint> list() {
		return this.store.list().stream().map(Viewpoint::of).toList();
	}

	/**
	 * One viewpoint.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 */
	@Transactional(readOnly = true)
	public Viewpoint get(UUID id) {
		return Viewpoint.of(row(id));
	}

	/**
	 * Creates the viewpoint and audits {@code viewpoint.created}.
	 *
	 * @throws MetamodelChangeException naming the field for a rule the input violates (422)
	 */
	@Transactional
	public Viewpoint create(OeaPrincipal principal, ViewpointInput input) {
		long person = person(principal);
		ViewpointStore.Fields fields = checked(input, null);
		UUID id = UUID.randomUUID();
		if (!this.store.insert(id, fields, person, this.clock.instant())) {
			throw MetamodelChangeException.invalid(NAME, "Name '" + fields.name() + "' is already taken");
		}
		audit(principal, "viewpoint.created", id, Map.of(NAME, fields.name()));
		return get(id);
	}

	/**
	 * Replaces the fields with the expected version and audits {@code viewpoint.updated}.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 * @throws MetamodelChangeException without an expected version (428)
	 * @throws MetamodelChangeException for a stale version (412) or an invalid field (422)
	 */
	@Transactional
	public Viewpoint update(OeaPrincipal principal, UUID id, ViewpointInput input, Long expectedVersion) {
		long person = person(principal);
		ViewpointRow current = locked(id, expectedVersion);
		ViewpointStore.Fields fields = checked(input, id);
		List<String> changed = new ArrayList<>();
		if (!current.name().equals(fields.name())) {
			changed.add(NAME);
		}
		if (!Objects.equals(current.description(), fields.description())) {
			changed.add("description");
		}
		if (!new LinkedHashSet<>(ids(current.metatypes())).equals(new LinkedHashSet<>(fields.metatypeIds()))) {
			changed.add(METATYPES);
		}
		if (!new LinkedHashSet<>(ids(current.connectionTypes()))
			.equals(new LinkedHashSet<>(fields.connectionTypeIds()))) {
			changed.add(CONNECTIONS);
		}
		this.store.update(id, fields, person, this.clock.instant());
		audit(principal, "viewpoint.updated", id, Map.of(NAME, fields.name(), "fields", changed));
		return get(id);
	}

	/**
	 * Deletes the viewpoint with the expected version and audits {@code viewpoint.deleted}.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 * @throws MetamodelChangeException without an expected version (428)
	 * @throws MetamodelChangeException for a stale version (412)
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		person(principal);
		ViewpointRow current = locked(id, expectedVersion);
		this.store.delete(id);
		audit(principal, "viewpoint.deleted", id, Map.of(NAME, current.name()));
	}

	private ViewpointRow locked(UUID id, Long expectedVersion) {
		int version = this.store.lockVersion(id).orElseThrow(() -> new NoSuchElementException("viewpoint " + id));
		if (expectedVersion == null) {
			throw MetamodelChangeException.preconditionRequired("viewpoint");
		}
		if (expectedVersion != version) {
			throw MetamodelChangeException.staleVersion("viewpoint", version);
		}
		return row(id);
	}

	/** The checked fields: name, then metatypes, then connection types (design D2). */
	private ViewpointStore.Fields checked(ViewpointInput input, UUID self) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty()) {
			throw MetamodelChangeException.invalid(NAME, "The name must not be blank.");
		}
		if (this.store.nameTaken(name, self)) {
			throw MetamodelChangeException.invalid(NAME, "Name '" + name + "' is already taken");
		}
		List<UUID> metatypes = distinct(input.metatypeIds());
		List<UUID> connections = distinct(input.connectionTypeIds());
		if (metatypes.isEmpty()) {
			throw MetamodelChangeException.invalid(METATYPES, "A viewpoint needs at least one metatype.");
		}
		List<UUID> all = new ArrayList<>(metatypes);
		all.addAll(connections);
		Map<UUID, ViewpointStore.Kind> kinds = this.store.kinds(all, PackageImporter.INSTANCE_CONFIGURATION);
		for (UUID id : metatypes) {
			ViewpointStore.Kind kind = kinds.get(id);
			if (kind == null || kind.connection() || !kind.instance()) {
				throw MetamodelChangeException.invalid(METATYPES,
						"The metatype " + id + " is not an element metatype of the instance.");
			}
		}
		for (UUID id : connections) {
			ViewpointStore.Kind kind = kinds.get(id);
			if (kind == null || !kind.connection() || !kind.instance()) {
				throw MetamodelChangeException.invalid(CONNECTIONS,
						"The metatype " + id + " is not a connection metatype of the instance.");
			}
		}
		String description = (input.description() != null && !input.description().isBlank()) ? input.description()
				: null;
		return new ViewpointStore.Fields(name, description, metatypes, connections);
	}

	private ViewpointRow row(UUID id) {
		return this.store.find(id).orElseThrow(() -> new NoSuchElementException("viewpoint " + id));
	}

	private static List<UUID> distinct(List<UUID> ids) {
		return (ids == null) ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
	}

	private static List<UUID> ids(List<ViewpointStore.TypeRow> types) {
		return types.stream().map(ViewpointStore.TypeRow::id).toList();
	}

	private static long person(OeaPrincipal principal) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		return person;
	}

	private void audit(OeaPrincipal principal, String type, UUID id, Map<String, Object> details) {
		this.audit.append(new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "viewpoint",
				id.toString(), new LinkedHashMap<>(details)));
	}

}
