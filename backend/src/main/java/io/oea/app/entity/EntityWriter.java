package io.oea.app.entity;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.FieldChange;
import io.oea.core.entity.EntityStore.Fields;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Creates and changes validated entities in the caller's transaction (design D3, D5, D6):
 * version 1 on create, the next version with the previous values of the changed fields on
 * change, nothing for an unchanged write; one audit event per written entity, appended last.
 */
@Service
public class EntityWriter {

	private final EntityStore store;

	private final PropertyValueStore values;

	private final AuditWriter audit;

	private final Clock clock;

	public EntityWriter(EntityStore store, PropertyValueStore values, AuditWriter audit, Clock clock) {
		this.store = store;
		this.values = values;
		this.audit = audit;
		this.clock = clock;
	}

	/** Correlation of the writes of one request: batch ID and the informational agent fields. */
	public record WriteContext(UUID batchId, AuditEvent.Agent agent) {
	}

	public enum Status {

		CREATED, UPDATED, UNCHANGED

	}

	public record Written(Status status, int entityId) {
	}

	/**
	 * Writes a valid entity.
	 *
	 * @param existing the stored entity (locked by the caller), {@code null} to create one
	 * @param stored its stored property values
	 */
	public Written write(OeaPrincipal principal, EntityValidator.Checked checked, StoredEntity existing,
			Map<UUID, TypedValue> stored, WriteContext context) {
		if (!checked.valid()) {
			throw new IllegalArgumentException("entity is not valid");
		}
		Instant now = this.clock.instant();
		Fields fields = checked.fields();
		Map<UUID, String> names = new HashMap<>();
		checked.metatype().mappings().forEach((id, mapping) -> names.put(id, mapping.definition().name()));
		List<AuditEvent.Change> changes = new ArrayList<>();
		if (existing == null) {
			int id = this.store.create(fields, checked.values(), principal.personId(), now);
			changes.add(new AuditEvent.Change(null, "name", true, null, fields.name()));
			changes.add(new AuditEvent.Change(null, "description", true, null, fields.description()));
			changes.add(new AuditEvent.Change(null, "is_logical", true, null, Boolean.toString(fields.logical())));
			checked.values()
				.forEach((definition, value) -> changes
					.add(new AuditEvent.Change(definition, names.get(definition), false, null, value.asText())));
			audit("entity.created", principal, checked, id, 1, context, changes);
			return new Written(Status.CREATED, id);
		}
		List<FieldChange> fieldChanges = new ArrayList<>();
		compare(fieldChanges, "name", existing.name(), fields.name());
		compare(fieldChanges, "description", existing.description(), fields.description());
		compare(fieldChanges, "is_logical", Boolean.toString(existing.logical()), Boolean.toString(fields.logical()));
		Map<UUID, TypedValue> newValues = new LinkedHashMap<>();
		Set<UUID> definitions = new HashSet<>(stored.keySet());
		definitions.addAll(checked.values().keySet());
		for (UUID definition : definitions) {
			TypedValue after = checked.values().get(definition);
			if (!Objects.equals(stored.get(definition), after)) {
				newValues.put(definition, after);
			}
		}
		if (fieldChanges.isEmpty() && newValues.isEmpty()) {
			return new Written(Status.UNCHANGED, existing.id());
		}
		int version = this.store.update(existing, fields, fieldChanges, stored, newValues, principal.personId(), now);
		// a value clears the mark of a missing mandatory property (us-032-metatype-editing D7)
		this.values.clearMarks(existing.id(), checked.values().keySet());
		fieldChanges.forEach((change) -> changes
			.add(new AuditEvent.Change(null, change.field(), true, change.oldValue(), change.newValue())));
		newValues.forEach((definition, value) -> {
			TypedValue before = stored.get(definition);
			changes.add(new AuditEvent.Change(definition,
					names.getOrDefault(definition, definition.toString()), false,
					(before != null) ? before.asText() : null, (value != null) ? value.asText() : null));
		});
		audit("entity.updated", principal, checked, existing.id(), version, context, changes);
		return new Written(Status.UPDATED, existing.id());
	}

	private void audit(String eventType, OeaPrincipal principal, EntityValidator.Checked checked, int entityId,
			int version, WriteContext context, List<AuditEvent.Change> changes) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("batchId", context.batchId().toString());
		details.put("metatypeId", checked.metatype().id().toString());
		if (checked.fields().externalId() != null) {
			details.put("externalId", checked.fields().externalId());
		}
		details.put("version", version);
		// ADR-033 §3: no names of modeled persons in the audit schema
		String entityName = "person".equalsIgnoreCase(checked.metatype().name()) ? null : checked.fields().name();
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "entity",
				Integer.toString(entityId), details), new AuditEvent.EntityTarget(entityId, entityName),
				context.agent(), changes);
	}

	private static void compare(List<FieldChange> changes, String field, String before, String after) {
		if (!Objects.equals(before, after)) {
			changes.add(new FieldChange(field, before, after));
		}
	}

}
