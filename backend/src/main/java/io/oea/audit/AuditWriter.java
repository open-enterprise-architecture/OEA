package io.oea.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends audit events in the caller's transaction (READ COMMITTED, required by the chain
 * trigger). The database assigns all chain values; this class never sets them.
 */
@Component
public class AuditWriter {

	/** MDC key of the request correlation ID, set by the request ID filter. */
	public static final String REQUEST_ID = "requestId";

	private static final String INSERT = """
			INSERT INTO audit.events (event_type, event_category, severity, outcome, source, source_ip, request_id,
			                          actor_type, actor_person_id, actor_admin_id, target_type, target_id, details)
			VALUES (?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?::jsonb)
			RETURNING id, chain_seq, record_hash""";

	private static final String INSERT_FOR_ENTITY = """
			INSERT INTO audit.events (event_type, event_category, severity, outcome, source, source_ip, request_id,
			                          actor_type, actor_person_id, actor_admin_id, target_type, target_id, details,
			                          entity_id, entity_name, agent_label, agent_run)
			VALUES (?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
			RETURNING id, chain_seq, record_hash""";

	private static final String INSERT_CHANGE = """
			INSERT INTO audit.event_changes (event_id, property_def_id, property_name, system_field, old_value, new_value)
			VALUES (?, ?, ?, ?, ?, ?)""";

	private final JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.shared();

	public AuditWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Appends the event in the current transaction, or in a new one if none is active. */
	@Transactional(propagation = Propagation.REQUIRED)
	public AppendedLink append(AuditEvent event) {
		return insert(event, sourceIp(), MDC.get(REQUEST_ID));
	}

	/**
	 * Appends an event about an entity with its changed fields and values in the current
	 * transaction. Values are stored in plain text; only properties with visibility mode
	 * {@code public} may be passed (encrypted values follow with REQ-160 AC4).
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public AppendedLink append(AuditEvent event, AuditEvent.EntityTarget entity, AuditEvent.Agent agent,
			List<AuditEvent.Change> changes) {
		AppendedLink link = this.jdbc.queryForObject(INSERT_FOR_ENTITY,
				(ResultSet rs, int rowNum) -> new AppendedLink(rs.getLong(1), rs.getLong(2), rs.getBytes(3)),
				event.eventType(), event.category().dbValue(), event.severity().dbValue(), event.outcome().dbValue(),
				event.source().dbValue(), sourceIp(), MDC.get(REQUEST_ID), event.actor().type(),
				event.actor().personId(), event.actor().adminId(), event.targetType(), event.targetId(),
				event.details().isEmpty() ? null : this.json.writeValueAsString(event.details()), entity.entityId(),
				entity.entityName(), agent.label(), agent.run());
		if (!changes.isEmpty()) {
			this.jdbc.batchUpdate(INSERT_CHANGE, changes.stream()
				.map((change) -> new Object[] { link.id(), change.propertyDefId(), change.propertyName(),
						change.systemField(), change.oldValue(), change.newValue() })
				.toList());
		}
		return link;
	}

	/** Appends the event in its own transaction, e.g. for rejections whose business transaction rolls back. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AppendedLink appendIndependently(AuditEvent event) {
		return append(event);
	}

	/**
	 * Appends an event written outside the request it describes (e.g. a summary of suppressed
	 * events) in its own transaction, with the given source address and without request ID.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AppendedLink appendIndependently(AuditEvent event, String sourceAddress) {
		return insert(event, sourceAddress, null);
	}

	private AppendedLink insert(AuditEvent event, String sourceAddress, String requestId) {
		return this.jdbc.queryForObject(INSERT, (ResultSet rs, int rowNum) -> new AppendedLink(rs.getLong(1),
				rs.getLong(2), rs.getBytes(3)), event.eventType(), event.category().dbValue(),
				event.severity().dbValue(), event.outcome().dbValue(), event.source().dbValue(), sourceAddress,
				requestId, event.actor().type(), event.actor().personId(), event.actor().adminId(),
				event.targetType(), event.targetId(),
				event.details().isEmpty() ? null : this.json.writeValueAsString(event.details()));
	}

	/** Source address of the current request, {@code null} outside a request. */
	static String sourceIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			return request.getRemoteAddr();
		}
		return null;
	}

}
