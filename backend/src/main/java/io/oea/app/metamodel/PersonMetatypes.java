package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.PersonLinkTrigger;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.VisibilityStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person metatype of connection-scoped visibility (REQ-127; design D2 of
 * us-127-connection-scoped-visibility): the entity type whose elements represent persons. A
 * person account is linked to such an element, and connection-scoped visibility starts from it.
 * Only security administration ({@code visibility:configure}) sets it.
 */
@Service
public class PersonMetatypes {

	private final VisibilityStore store;

	private final MetatypeStore metatypes;

	private final AuditWriter audit;

	private final PersonLinkTrigger links;

	public PersonMetatypes(VisibilityStore store, MetatypeStore metatypes, AuditWriter audit, PersonLinkTrigger links) {
		this.links = links;
		this.store = store;
		this.metatypes = metatypes;
		this.audit = audit;
	}

	/** The person metatype with its name; both {@code null} while none is set. */
	public record View(UUID metatypeId, String metatypeName) {
	}

	@Transactional(readOnly = true)
	public View get() {
		VisibilityStore.PersonMetatypeRow row = this.store.personMetatype();
		return new View(row.metatypeId(), row.metatypeName());
	}

	/**
	 * Sets or unsets the person metatype; only an element metatype.
	 *
	 * @throws InvalidAccessRequestException naming {@code metatypeId} for a connection or unknown metatype
	 */
	@Transactional
	public View set(OeaPrincipal principal, UUID metatypeId) {
		View old = get();
		if (metatypeId != null) {
			Optional<Boolean> connection = this.metatypes.isConnection(metatypeId);
			if (connection.isEmpty() || connection.get()) {
				throw new InvalidAccessRequestException("metatypeId",
						"The person metatype must be an existing element metatype.");
			}
		}
		if (Objects.equals(old.metatypeId(), metatypeId)) {
			return old;
		}
		this.store.setPersonMetatype(metatypeId);
		this.links.changed();
		View changed = get();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("oldMetatypeId", (old.metatypeId() != null) ? old.metatypeId().toString() : "");
		details.put("newMetatypeId", (metatypeId != null) ? metatypeId.toString() : "");
		details.put("newMetatypeName", (changed.metatypeName() != null) ? changed.metatypeName() : "");
		this.audit.append(new AuditEvent("visibility.person-metatype-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"settings", "person-metatype", details));
		return changed;
	}

	/** Whether the metatype is the person metatype, which keeps it from being deleted. */
	public boolean isPersonMetatype(UUID metatypeId) {
		return metatypeId.equals(get().metatypeId());
	}

}
