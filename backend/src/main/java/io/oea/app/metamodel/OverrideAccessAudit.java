package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the {@code property-override-access} event of a read that returned values the caller
 * can read only through a role on the override role list (REQ-128 AC2; design D3 of
 * us-128-override-role-list, design D2 of us-099-version-diff): one event per read naming, per
 * property, the entities whose value was returned through the override. The event is written in
 * its own transaction, as the reads are read-only.
 */
@Component
public class OverrideAccessAudit {

	private final AuditWriter audit;

	public OverrideAccessAudit(AuditWriter audit) {
		this.audit = audit;
	}

	/**
	 * Appends the event when {@code entities} is not empty.
	 * @param channel the read channel ({@code catalog}, {@code portal} or {@code history})
	 * @param names the property names by definition ID
	 * @param entities the entities per property whose value was read through the override
	 */
	public void append(OeaPrincipal principal, String channel, int page, String targetType, String targetId,
			Map<UUID, String> names, Map<UUID, ? extends Collection<Integer>> entities) {
		if (entities.isEmpty()) {
			return;
		}
		List<Map<String, Object>> properties = new ArrayList<>();
		entities.forEach((id, ids) -> {
			Map<String, Object> property = new LinkedHashMap<>();
			property.put("propertyDefId", id.toString());
			property.put("property", names.get(id));
			property.put("entityIds", List.copyOf(ids));
			properties.add(property);
		});
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("channel", channel);
		details.put("page", page);
		details.put("properties", properties);
		this.audit.appendIndependently(new AuditEvent("property-override-access", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				targetType, targetId, details));
	}

}
