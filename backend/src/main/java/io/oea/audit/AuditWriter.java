package io.oea.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
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

	private final JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.shared();

	public AuditWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Appends the event in the current transaction, or in a new one if none is active. */
	@Transactional(propagation = Propagation.REQUIRED)
	public AppendedLink append(AuditEvent event) {
		return this.jdbc.queryForObject(INSERT, (ResultSet rs, int rowNum) -> new AppendedLink(rs.getLong(1),
				rs.getLong(2), rs.getBytes(3)), event.eventType(), event.category().dbValue(),
				event.severity().dbValue(), event.outcome().dbValue(), event.source().dbValue(), sourceIp(),
				MDC.get(REQUEST_ID), event.actor().type(), event.actor().personId(), event.actor().adminId(),
				event.targetType(), event.targetId(),
				event.details().isEmpty() ? null : this.json.writeValueAsString(event.details()));
	}

	/** Appends the event in its own transaction, e.g. for rejections whose business transaction rolls back. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AppendedLink appendIndependently(AuditEvent event) {
		return append(event);
	}

	private static String sourceIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			return request.getRemoteAddr();
		}
		return null;
	}

}
