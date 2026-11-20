package io.oea.app.entity;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Soft-deletes an entity and audits {@code entity.deleted} (ADR-019; design D3 of
 * us-135-component-hierarchy). Shared by the go-live of a solution, which retires entities, and by
 * the hierarchy, which removes automatic connections and structuring aids. The caller runs in a
 * transaction and names the cause in {@code detail}.
 */
@Component
public class EntityDeletion {

	private final EntityStore entities;

	private final AuditWriter audit;

	EntityDeletion(EntityStore entities, AuditWriter audit) {
		this.entities = entities;
		this.audit = audit;
	}

	/**
	 * Deletes the entity.
	 *
	 * @param reason the deletion reason kept with the entity
	 * @param cause the detail naming why, e.g. {@code solutionId} or {@code cause}, with its value
	 */
	public void delete(OeaPrincipal principal, StoredEntity entity, Instant now, String reason,
			Map.Entry<String, String> cause) {
		delete(principal, entity, now, reason, cause, null);
	}

	/**
	 * Deletes the entity as part of the deletion of another one: {@code originId} is the entity
	 * whose deletion takes it along, so that restoring that entity restores this one with the
	 * same deletion time (V49). The go-live passes the retired entity for the connections it
	 * retires with it.
	 */
	public void delete(OeaPrincipal principal, StoredEntity entity, Instant now, String reason,
			Map.Entry<String, String> cause, Integer originId) {
		this.entities.softDelete(entity.id(), principal.personId(), now, reason, originId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put(cause.getKey(), cause.getValue());
		details.put("metatypeId", entity.metatypeId().toString());
		details.put("version", entity.version());
		this.audit.append(new AuditEvent("entity.deleted", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(entity.id()), details), new AuditEvent.EntityTarget(entity.id(), entity.name()),
				AuditEvent.Agent.NONE, List.of());
	}

}
