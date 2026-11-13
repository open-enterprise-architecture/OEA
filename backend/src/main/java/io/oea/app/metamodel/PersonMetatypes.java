package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	public PersonMetatypes(JdbcTemplate jdbc, AuditWriter audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	/** The person metatype with its name; both {@code null} while none is set. */
	public record View(UUID metatypeId, String metatypeName) {
	}

	@Transactional(readOnly = true)
	public View get() {
		return this.jdbc.queryForObject("""
				SELECT s.person_metatype_id, m.name FROM visibility_settings s
				LEFT JOIN metatypes m ON m.id = s.person_metatype_id WHERE s.id = 1""",
				(rs, n) -> new View(rs.getObject(1, UUID.class), rs.getString(2)));
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
			List<Boolean> connection = this.jdbc.queryForList("SELECT is_connection FROM metatypes WHERE id = ?",
					Boolean.class, metatypeId);
			if (connection.isEmpty() || connection.getFirst()) {
				throw new InvalidAccessRequestException("metatypeId",
						"The person metatype must be an existing element metatype.");
			}
		}
		if (Objects.equals(old.metatypeId(), metatypeId)) {
			return old;
		}
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ? WHERE id = 1", metatypeId);
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
