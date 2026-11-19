package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyMappingStore;
import io.oea.core.metamodel.VisibilityStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The visibility configuration of property definitions (US-124, REQ-124, REQ-161; design D1–D4 of
 * us-124-property-visibility): the mode, the read and write roles of {@code role-restricted} and
 * the scoping connection metatype of {@code connection-scoped}. Only security administration
 * ({@code visibility:configure}) changes it; every change is versioned and audited. Connections of
 * a scoping type need {@code visibility-scope:assign}.
 */
@Service
public class PropertyVisibility {

	public static final String PUBLIC = "public";

	public static final String ROLE_RESTRICTED = "role-restricted";

	public static final String CONNECTION_SCOPED = "connection-scoped";

	static final String SCOPE_PERMISSION = "visibility-scope:assign";

	private static final Set<String> MODES = Set.of(PUBLIC, ROLE_RESTRICTED, CONNECTION_SCOPED);

	private final VisibilityStore store;

	private final MetatypeStore metatypes;

	private final PropertyMappingStore mappings;

	private final MetamodelVersions versions;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	public PropertyVisibility(VisibilityStore store, MetatypeStore metatypes, PropertyMappingStore mappings,
			MetamodelVersions versions, PermissionResolver permissions,
			AuditWriter audit, Clock clock) {
		this.store = store;
		this.metatypes = metatypes;
		this.mappings = mappings;
		this.versions = versions;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** The visibility of a property definition; the role lists are sorted. */
	public record Visibility(String mode, List<String> readRoles, List<String> writeRoles, UUID scopingType) {

		public static final Visibility DEFAULT = new Visibility(PUBLIC, List.of(), List.of(), null);

		public Visibility {
			readRoles = List.copyOf(readRoles);
			writeRoles = List.copyOf(writeRoles);
		}

	}

	/** The visibility of one property with its name and the metatypes that map it today. */
	public record Entry(UUID propertyDefId, String name, List<String> metatypes, Visibility visibility) {
	}

	/** A requested change: the property and its new visibility as sent. */
	public record Change(UUID propertyDefId, String mode, List<String> readRoles, List<String> writeRoles,
			UUID scopingType) {
	}

	/** The visibility of every property definition that is not retired, ordered by name. */
	@Transactional(readOnly = true)
	public List<Entry> list() {
		Map<UUID, Visibility> visibilities = all();
		Map<UUID, List<String>> metatypes = this.mappings.mappedMetatypeNames(LocalDate.now(this.clock));
		metatypes.values().forEach((names) -> names.sort(String.CASE_INSENSITIVE_ORDER));
		return this.store.activeProperties()
			.stream()
			.map((property) -> new Entry(property.id(), property.name(),
					List.copyOf(metatypes.getOrDefault(property.id(), List.of())),
					visibilities.getOrDefault(property.id(), Visibility.DEFAULT)))
			.toList();
	}

	/** The visibility of every property definition, retired ones included, by ID. */
	public Map<UUID, Visibility> all() {
		Map<UUID, List<String>> read = new LinkedHashMap<>();
		Map<UUID, List<String>> write = new LinkedHashMap<>();
		for (VisibilityStore.RoleAccessRow row : this.store.roleAccess()) {
			Map<UUID, List<String>> target = "write".equals(row.access()) ? write : read;
			target.computeIfAbsent(row.propertyDefId(), (key) -> new ArrayList<>()).add(row.roleId());
		}
		Map<UUID, Visibility> visibilities = new LinkedHashMap<>();
		for (VisibilityStore.ModeRow row : this.store.modes()) {
			UUID id = row.propertyDefId();
			visibilities.put(id, new Visibility(row.mode(), read.getOrDefault(id, List.of()),
					write.getOrDefault(id, List.of()), row.scopingTypeId()));
		}
		return visibilities;
	}

	/**
	 * Changes the visibility of the listed properties in one transaction (D2). Every invalid entry
	 * is named in the 422; nothing changes then. A changed property gets a new version and the
	 * event {@code visibility.changed} (D3); an unchanged entry neither.
	 *
	 * @return the changed entries in their new state, in the order of the request
	 * @throws InvalidAccessRequestException naming the invalid fields, e.g. {@code [1].readRoles}
	 */
	@Transactional
	public List<Entry> update(OeaPrincipal principal, List<Change> changes) {
		Map<UUID, Visibility> current = all();
		Set<UUID> retired = new HashSet<>(this.store.retiredPropertyIds());
		Set<String> roles = new HashSet<>(this.store.activeRoleIds());
		Set<UUID> connectionTypes = new HashSet<>(this.metatypes.connectionIds());
		List<String> invalid = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		List<Visibility> requested = new ArrayList<>();
		for (int i = 0; i < changes.size(); i++) {
			Change change = changes.get(i);
			String prefix = "[" + i + "].";
			Visibility visibility = normalized(change);
			requested.add(visibility);
			if (change.propertyDefId() == null || !current.containsKey(change.propertyDefId())
					|| retired.contains(change.propertyDefId()) || !seen.add(change.propertyDefId())) {
				invalid.add(prefix + "propertyDefId");
			}
			if (!MODES.contains(visibility.mode())) {
				invalid.add(prefix + "visibilityMode");
				continue;
			}
			boolean restricted = ROLE_RESTRICTED.equals(visibility.mode());
			if (restricted ? visibility.readRoles().isEmpty() || !roles.containsAll(visibility.readRoles())
					: !visibility.readRoles().isEmpty()) {
				invalid.add(prefix + "readRoles");
			}
			if (restricted ? !roles.containsAll(visibility.writeRoles())
					|| !visibility.readRoles().containsAll(visibility.writeRoles()) : !visibility.writeRoles().isEmpty()) {
				invalid.add(prefix + "writeRoles");
			}
			if (CONNECTION_SCOPED.equals(visibility.mode())
					? visibility.scopingType() == null || !connectionTypes.contains(visibility.scopingType())
					: visibility.scopingType() != null) {
				invalid.add(prefix + "scopingConnectionType");
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidAccessRequestException(invalid, "The visibility configuration is invalid ("
					+ String.join(", ", invalid) + ").");
		}
		Set<String> held = new HashSet<>(this.permissions.roles(principal));
		List<UUID> changed = new ArrayList<>();
		for (int i = 0; i < changes.size(); i++) {
			UUID id = changes.get(i).propertyDefId();
			Visibility old = current.get(id);
			Visibility next = requested.get(i);
			if (old.equals(next)) {
				continue;
			}
			store(id, next);
			this.versions.increment(MetamodelVersions.Element.PROPERTY_DEFINITION, id);
			audit(principal, id, old, next, held);
			changed.add(id);
		}
		Map<UUID, Entry> entries = new LinkedHashMap<>();
		list().forEach((entry) -> entries.put(entry.propertyDefId(), entry));
		return changed.stream().map(entries::get).toList();
	}

	/**
	 * Requires {@code visibility-scope:assign} if one of the connection metatypes is the scoping
	 * type of a property definition (D4, REQ-161).
	 *
	 * @throws PermissionDeniedException without the permission (403, audited as a denial)
	 */
	public void requireScopeAssignment(OeaPrincipal principal, Collection<UUID> connectionMetatypes) {
		List<UUID> ids = connectionMetatypes.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return;
		}
		if (this.store.isScopingType(ids) && !this.permissions.permissions(principal).contains(SCOPE_PERMISSION)) {
			throw new PermissionDeniedException();
		}
	}

	private static Visibility normalized(Change change) {
		String mode = (change.mode() != null) ? change.mode() : PUBLIC;
		return new Visibility(mode, sorted(change.readRoles()), sorted(change.writeRoles()), change.scopingType());
	}

	private static List<String> sorted(List<String> roles) {
		List<String> unique = new ArrayList<>(new LinkedHashSet<>((roles != null) ? roles : List.of()));
		unique.sort(null);
		return unique;
	}

	private void store(UUID id, Visibility visibility) {
		this.store.save(id, visibility.mode(), visibility.scopingType(), visibility.readRoles(),
				visibility.writeRoles());
	}

	/** D3: old and new state; self-benefiting if a role the actor holds is added. */
	private void audit(OeaPrincipal principal, UUID id, Visibility old, Visibility next, Set<String> held) {
		String name = this.store.propertyName(id);
		Set<String> addedWrite = new HashSet<>(next.writeRoles());
		addedWrite.removeAll(old.writeRoles());
		Set<String> addedRead = new HashSet<>(next.readRoles());
		addedRead.removeAll(old.readRoles());
		boolean selfBenefiting = addedRead.stream().anyMatch(held::contains) || addedWrite.stream().anyMatch(held::contains);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("property", name);
		details.put("oldMode", old.mode());
		details.put("newMode", next.mode());
		details.put("oldReadRoles", old.readRoles());
		details.put("newReadRoles", next.readRoles());
		details.put("oldWriteRoles", old.writeRoles());
		details.put("newWriteRoles", next.writeRoles());
		details.put("oldScopingType", (old.scopingType() != null) ? old.scopingType().toString() : "");
		details.put("newScopingType", (next.scopingType() != null) ? next.scopingType().toString() : "");
		details.put("selfBenefiting", selfBenefiting);
		this.audit.append(new AuditEvent("visibility.changed", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "property-definition",
				id.toString(), details));
	}

}
