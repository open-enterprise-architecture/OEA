package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Which properties a principal may read and write under the visibility configuration (US-126,
 * REQ-126, REQ-160; design D1 of us-126-role-restricted-enforcement): {@code public} properties
 * are readable and writable; {@code role-restricted} ones are readable with an effective role
 * among the read roles and writable with one among the write roles; {@code connection-scoped}
 * ones are neither until their enforcement follows.
 */
@Service
public class PropertyAccess {

	private final PropertyVisibility visibility;

	private final PermissionResolver permissions;

	public PropertyAccess(PropertyVisibility visibility, PermissionResolver permissions) {
		this.visibility = visibility;
		this.permissions = permissions;
	}

	/** The access of one principal at one point in time, used for a whole request. */
	public static final class Snapshot {

		private final Map<UUID, PropertyVisibility.Visibility> visibilities;

		private final Set<String> roles;

		Snapshot(Map<UUID, PropertyVisibility.Visibility> visibilities, Set<String> roles) {
			this.visibilities = Map.copyOf(visibilities);
			this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
		}

		/** Whether the principal may read the values of the property; unknown properties are public. */
		public boolean readable(UUID propertyDefId) {
			return allowed(propertyDefId, false);
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
		return new Snapshot(this.visibility.all(), new HashSet<>(this.permissions.roles(principal)));
	}

	/** An access for given roles, e.g. for tests and system writes. */
	public Snapshot of(Set<String> roles) {
		return new Snapshot(this.visibility.all(), roles);
	}

}
