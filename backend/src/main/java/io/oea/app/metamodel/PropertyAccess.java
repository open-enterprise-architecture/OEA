package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.core.catalog.ScopeStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Which properties a principal may read and write under the visibility configuration (US-126,
 * REQ-126, REQ-160; design D1 of us-126-role-restricted-enforcement): {@code public} properties
 * are readable and writable; {@code role-restricted} ones are readable with an effective role
 * among the read roles and writable with one among the write roles; {@code connection-scoped}
 * ones are readable per entity in the scope of the caller's linked element (us-127). A holder
 * of a role on the override role list reads every property (design D2 of
 * us-128-override-role-list); writing is unchanged.
 */
@Service
public class PropertyAccess {

	private final PropertyVisibility visibility;

	private final PermissionResolver permissions;

	private final PersonEntityLinksView links;

	private final ScopeStore scopes;

	private final VisibilityOverride override;

	public PropertyAccess(PropertyVisibility visibility, PermissionResolver permissions, PersonEntityLinksView links,
			ScopeStore scopes, VisibilityOverride override) {
		this.override = override;
		this.visibility = visibility;
		this.permissions = permissions;
		this.links = links;
		this.scopes = scopes;
	}

	/** The effective linked element of a person (design D2, D4 of us-127-connection-scoped-visibility). */
	public interface PersonEntityLinksView {

		Integer anchor(long personId);

	}

	/** The scope of a connection-scoped property for one caller: its scoping type and the caller's element. */
	public record Scope(UUID scopingType, int anchor) {
	}

	/** The access of one principal at one point in time, used for a whole request. */
	public static final class Snapshot {

		private final Map<UUID, PropertyVisibility.Visibility> visibilities;

		private final Set<String> roles;

		private final Integer anchor;

		private final ScopeStore scopes;

		private final boolean override;

		Snapshot(Map<UUID, PropertyVisibility.Visibility> visibilities, Set<String> roles) {
			this(visibilities, roles, null, null, false);
		}

		Snapshot(Map<UUID, PropertyVisibility.Visibility> visibilities, Set<String> roles, Integer anchor,
				ScopeStore scopes, boolean override) {
			this.visibilities = Map.copyOf(visibilities);
			this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
			this.anchor = anchor;
			this.scopes = scopes;
			this.override = override;
		}

		/** Whether the principal holds a role on the override role list (REQ-128). */
		public boolean override() {
			return this.override;
		}

		/**
		 * Whether the property is readable only through the override for every entity: the
		 * principal holds an override role, the property is restricted, no read role grants it,
		 * and for a {@code connection-scoped} property the principal has no account link. A
		 * connection-scoped property with a link is decided per entity by its scope.
		 */
		public boolean throughOverride(UUID propertyDefId) {
			return this.override && restricted(propertyDefId) && !allowed(propertyDefId, false)
					&& scope(propertyDefId).isEmpty();
		}

		/**
		 * The scope of a {@code connection-scoped} property for this caller, empty for other
		 * properties and for a caller without an effective account link.
		 */
		public Optional<Scope> scope(UUID propertyDefId) {
			PropertyVisibility.Visibility visibility = this.visibilities.get(propertyDefId);
			if (this.anchor == null || visibility == null
					|| !PropertyVisibility.CONNECTION_SCOPED.equals(visibility.mode())) {
				return Optional.empty();
			}
			return Optional.of(new Scope(visibility.scopingType(), this.anchor));
		}

		/** Whether the caller may write the connection-scoped property of this existing entity. */
		public boolean writableFor(UUID propertyDefId, int entityId) {
			return scope(propertyDefId)
				.map((scope) -> this.scopes.inScope(scope.anchor(), scope.scopingType(), entityId))
				.orElse(false);
		}

		/**
		 * Whether the principal may read the values of the property; unknown properties are
		 * public. A connection-scoped property is readable per entity with an account link
		 * ({@link #scope}). A holder of an override role reads every property.
		 */
		public boolean readable(UUID propertyDefId) {
			return this.override || allowed(propertyDefId, false) || scope(propertyDefId).isPresent();
		}

		/**
		 * Whether the principal may read the values of the property of this entity: like
		 * {@link #readable}, but a {@code connection-scoped} property only for an entity in the
		 * caller's scope (design D4 of us-041-modified-delta).
		 */
		public boolean readableFor(UUID propertyDefId, int entityId) {
			if (this.override || allowed(propertyDefId, false)) {
				return true;
			}
			return connectionScoped(propertyDefId) && writableFor(propertyDefId, entityId);
		}

		/** Whether the principal may write the values of the property. */
		public boolean writable(UUID propertyDefId) {
			return allowed(propertyDefId, true);
		}

		/** Whether the property's visibility is anything but {@code public}. */
		public boolean restricted(UUID propertyDefId) {
			PropertyVisibility.Visibility visibility = this.visibilities.get(propertyDefId);
			return visibility != null && !PropertyVisibility.PUBLIC.equals(visibility.mode());
		}

		/** Whether the property is {@code connection-scoped}. */
		public boolean connectionScoped(UUID propertyDefId) {
			PropertyVisibility.Visibility visibility = this.visibilities.get(propertyDefId);
			return visibility != null && PropertyVisibility.CONNECTION_SCOPED.equals(visibility.mode());
		}

		private boolean allowed(UUID propertyDefId, boolean write) {
			PropertyVisibility.Visibility visibility = this.visibilities.get(propertyDefId);
			if (visibility == null || PropertyVisibility.PUBLIC.equals(visibility.mode())) {
				return true;
			}
			if (!PropertyVisibility.ROLE_RESTRICTED.equals(visibility.mode())) {
				return false;
			}
			List<String> granted = write ? visibility.writeRoles() : visibility.readRoles();
			return granted.stream().anyMatch(this.roles::contains);
		}

	}

	/** The access of the principal under the current visibility and its effective roles. */
	public Snapshot of(OeaPrincipal principal) {
		Integer anchor = (principal.personId() != null) ? this.links.anchor(principal.personId()) : null;
		Set<String> roles = new HashSet<>(this.permissions.roles(principal));
		boolean override = this.override.roles().stream().anyMatch(roles::contains);
		return new Snapshot(this.visibility.all(), roles, anchor, this.scopes, override);
	}

	/** An access for given roles, e.g. for tests and system writes. */
	public Snapshot of(Set<String> roles) {
		return new Snapshot(this.visibility.all(), roles);
	}

}
