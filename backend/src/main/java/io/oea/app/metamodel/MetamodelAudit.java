package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Audit events of metamodel changes, appended in the transaction of the change (ADR-033, US-032 AC3). */
@Component
class MetamodelAudit {

	private final AuditWriter audit;

	MetamodelAudit(AuditWriter audit) {
		this.audit = audit;
	}

	void changed(String eventType, OeaPrincipal principal, String targetType, Object targetId,
			Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), targetType,
				targetId.toString(), details));
	}

}
