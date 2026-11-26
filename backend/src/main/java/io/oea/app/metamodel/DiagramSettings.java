package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.DiagramSettingsStore;
import io.oea.core.metamodel.DiagramSettingsStore.Settings;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The global settings of diagrams (design D1 and D2 of us-074-diagram-nesting): the setting "Ask for a
 * new parent" (default true) with a version for optimistic locking. A viewpoint may override the
 * value; {@link #effective} gives the value that applies. The permissions are checked from the
 * contract; the metamodel lock mode does not apply.
 */
@Service
public class DiagramSettings {

	private final DiagramSettingsStore store;

	private final AuditWriter audit;

	private final Clock clock;

	DiagramSettings(DiagramSettingsStore store, AuditWriter audit, Clock clock) {
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	/** The settings with their version. */
	public record Values(boolean askNewParent, int version) {
	}

	/** The settings. */
	@Transactional(readOnly = true)
	public Values get() {
		Settings settings = this.store.get();
		return new Values(settings.askNewParent(), settings.version());
	}

	/** The value that applies to a viewpoint: its own, or the global one when it stores none. */
	@Transactional(readOnly = true)
	public boolean effective(Boolean viewpointValue) {
		return (viewpointValue != null) ? viewpointValue : this.store.get().askNewParent();
	}

	/**
	 * Sets the global value with the expected version and audits {@code diagram.settings-updated}
	 * with the old and the new value. A write of the value the setting has changes nothing: the
	 * version stays and nothing is audited (the version is still checked).
	 *
	 * @throws PermissionDeniedException for a principal without a person (403)
	 * @throws MetamodelChangeException without an expected version (428) or with a stale one (412)
	 */
	@Transactional
	public Values update(OeaPrincipal principal, boolean askNewParent, Long expectedVersion) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		Settings current = this.store.lock();
		if (expectedVersion == null) {
			throw MetamodelChangeException.preconditionRequired("diagram settings");
		}
		if (expectedVersion != current.version()) {
			throw MetamodelChangeException.staleVersion("diagram settings", current.version());
		}
		if (current.askNewParent() == askNewParent) {
			return new Values(current.askNewParent(), current.version());
		}
		Settings updated = this.store.update(askNewParent, person, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("setting", "askNewParent");
		details.put("before", current.askNewParent());
		details.put("after", updated.askNewParent());
		details.put("version", updated.version());
		this.audit.append(new AuditEvent("diagram.settings-updated", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"diagram-settings", "1", details));
		return new Values(updated.askNewParent(), updated.version());
	}

}
