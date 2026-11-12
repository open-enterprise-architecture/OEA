package io.oea.app.entity;

import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.core.entity.EntityStore;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Component;

/**
 * The write access to restricted properties (design D3 of us-126-role-restricted-enforcement, D6
 * of us-127-connection-scoped-visibility), shared by the batch write and the entity deltas of a
 * solution (design D2 of us-040-entity-deltas).
 */
@Component
public class PropertyWriteAccess {

	private final PropertyAccess access;

	private final EntityStore store;

	PropertyWriteAccess(PropertyAccess access, EntityStore store) {
		this.access = access;
		this.store = store;
	}

	/**
	 * The access of the writer; a value of a {@code role-restricted} property, set or removed,
	 * without a write role rejects the whole request before anything is written (403, audited). A
	 * {@code connection-scoped} value is writable only for an existing entity in the writer's
	 * scope, named by its external ID.
	 *
	 * @throws PermissionDeniedException if a value may not be written
	 */
	public PropertyAccess.Snapshot require(OeaPrincipal principal, List<EntityInput> inputs) {
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		for (EntityInput input : inputs) {
			for (EntityInput.PropertyInput property : input.properties()) {
				UUID id = property.propertyDefId();
				if (id == null || !snapshot.restricted(id)) {
					continue;
				}
				if (snapshot.connectionScoped(id)) {
					Integer entity = (input.externalId() != null) ? this.store.endpointByExternalId(input.externalId())
						.filter((found) -> !found.deleted())
						.map(EntityStore.Endpoint::id)
						.orElse(null) : null;
					if (entity == null || !snapshot.writableFor(id, entity)) {
						throw new PermissionDeniedException();
					}
				}
				else if (!snapshot.writable(id)) {
					throw new PermissionDeniedException();
				}
			}
		}
		return snapshot;
	}

	/**
	 * The access of the writer of values of the existing entity {@code entityId}: a
	 * {@code role-restricted} value needs a write role, a {@code connection-scoped} value the
	 * entity in the writer's scope (design D2 of us-041-modified-delta).
	 *
	 * @throws PermissionDeniedException if a value may not be written
	 */
	public PropertyAccess.Snapshot require(OeaPrincipal principal, List<EntityInput.PropertyInput> properties,
			int entityId) {
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		for (EntityInput.PropertyInput property : properties) {
			UUID id = property.propertyDefId();
			if (id == null || !snapshot.restricted(id)) {
				continue;
			}
			boolean writable = snapshot.connectionScoped(id) ? snapshot.writableFor(id, entityId) : snapshot.writable(id);
			if (!writable) {
				throw new PermissionDeniedException();
			}
		}
		return snapshot;
	}

	/** Who may write a restricted property of an entity, for {@link MetatypeRules#forWriter}. */
	public static BiPredicate<UUID, Integer> writer(PropertyAccess.Snapshot access) {
		return (property, entity) -> access.connectionScoped(property)
				? entity != null && access.writableFor(property, entity) : access.writable(property);
	}

}
