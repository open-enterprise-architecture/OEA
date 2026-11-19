package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The read model of the metamodel changes over the audit trail (design D1 of
 * us-034-metamodel-changes): the {@code metamodel.*} events with the names of their actors and
 * elements. Other audit events are never read.
 */
@Repository
public class MetamodelChangeStore {

	/** The predicate of the partial index {@code events_metamodel_occurred} (audit V4, design D3). */
	private static final String METAMODEL_EVENTS = "e.event_type LIKE 'metamodel.%' AND e.event_category = 'change'";

	private static final String NAMES = """
			LEFT JOIN persons p ON p.id = e.actor_person_id
			LEFT JOIN system_admin_accounts a ON a.id = e.actor_admin_id""";

	private final JdbcTemplate jdbc;

	public MetamodelChangeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An actor row; at most one of {@code personId} and {@code adminId} is set. */
	public record ActorRow(String type, Long personId, UUID adminId, String name) {
	}

	/** A change row; {@code details} is the stored JSON of the event. */
	public record ChangeRow(long id, Instant occurredAt, String eventType, ActorRow actor, String targetType,
			String targetId, String elementName, String batchId, String details) {
	}

	/**
	 * The filters; every one may be {@code null}. {@code eventTypePatterns} are LIKE patterns,
	 * {@code to} is exclusive.
	 */
	public record Filter(List<String> eventTypePatterns, boolean withoutImported, Long actorPersonId, UUID actorAdminId,
			Instant from, Instant to, String batchId) {
	}

	/** The number of changes matching the filter. */
	public long count(Filter filter) {
		List<Object> arguments = new ArrayList<>();
		String where = where(filter, arguments);
		Long total = this.jdbc.queryForObject("SELECT count(*) FROM audit.events e WHERE " + where, Long.class,
				arguments.toArray());
		return (total != null) ? total : 0;
	}

	/** One page of the changes matching the filter, newest first. */
	public List<ChangeRow> page(Filter filter, int limit, long offset) {
		List<Object> arguments = new ArrayList<>();
		String where = where(filter, arguments);
		arguments.add(limit);
		arguments.add(offset);
		return this.jdbc.query("""
				SELECT e.id, e.occurred_at, e.event_type, e.actor_type, e.actor_person_id, e.actor_admin_id,
				       coalesce(p.name, a.login_name), e.target_type, e.target_id,
				       coalesce(e.details ->> 'name', e.details ->> 'value', m.name, d.name, c.name, g.name, y.name,
				                CASE WHEN e.target_type = 'metamodel-package' THEN e.target_id END),
				       e.details ->> 'batchId', e.details::text
				FROM audit.events e
				""" + NAMES + """

				LEFT JOIN metatypes m ON e.target_type = 'metatype' AND m.id::text = e.target_id
				LEFT JOIN property_definitions d ON e.target_type = 'property-definition' AND d.id::text = e.target_id
				LEFT JOIN property_categories c ON e.target_type = 'property-category' AND c.id::text = e.target_id
				LEFT JOIN metamodel_configurations g ON e.target_type = 'metamodel-configuration'
				     AND g.id::text = e.target_id
				LEFT JOIN architecture_layers y ON e.target_type = 'architecture-layer' AND y.id::text = e.target_id
				""" + " WHERE " + where + """

				ORDER BY e.occurred_at DESC, e.id DESC LIMIT ? OFFSET ?""",
				(rs, row) -> new ChangeRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3),
						new ActorRow(rs.getString(4), rs.getObject(5, Long.class), rs.getObject(6, UUID.class),
								rs.getString(7)),
						rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12)),
				arguments.toArray());
	}

	/** The actors of all metamodel changes, by name (the system last). */
	public List<ActorRow> actors() {
		return this.jdbc.query("""
				SELECT DISTINCT e.actor_type, e.actor_person_id, e.actor_admin_id, coalesce(p.name, a.login_name)
				FROM audit.events e
				""" + NAMES + " WHERE " + METAMODEL_EVENTS + """

				ORDER BY 4 NULLS LAST, 1""",
				(rs, n) -> new ActorRow(rs.getString(1), rs.getObject(2, Long.class), rs.getObject(3, UUID.class),
						rs.getString(4)));
	}

	private static String where(Filter filter, List<Object> arguments) {
		StringBuilder where = new StringBuilder(METAMODEL_EVENTS);
		if (filter.eventTypePatterns() != null) {
			where.append(" AND (")
				.append(String.join(" OR ", filter.eventTypePatterns().stream().map((p) -> "e.event_type LIKE ?").toList()))
				.append(')');
			arguments.addAll(filter.eventTypePatterns());
			if (filter.withoutImported()) {
				where.append(" AND e.event_type <> 'metamodel.metatype-imported'");
			}
		}
		if (filter.actorPersonId() != null) {
			where.append(" AND e.actor_person_id = ?");
			arguments.add(filter.actorPersonId());
		}
		if (filter.actorAdminId() != null) {
			arguments.add(filter.actorAdminId());
			where.append(" AND e.actor_admin_id = ?");
		}
		if (filter.from() != null) {
			where.append(" AND e.occurred_at >= ?");
			arguments.add(Timestamp.from(filter.from()));
		}
		if (filter.to() != null) {
			where.append(" AND e.occurred_at < ?");
			arguments.add(Timestamp.from(filter.to()));
		}
		if (filter.batchId() != null) {
			where.append(" AND e.details ->> 'batchId' = ?");
			arguments.add(filter.batchId());
		}
		return where.toString();
	}

}
