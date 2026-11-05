package io.oea.app.metamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The metamodel changes from the audit trail for metamodel administrators: the {@code metamodel.*}
 * events with the names of their actors and elements (design D1, D2 of us-034-metamodel-changes;
 * US-034, REQ-034). Other audit events are never read.
 */
@Service
public class MetamodelChanges {

	/** The predicate of the partial index {@code events_metamodel_occurred} (audit V4, design D3). */
	private static final String METAMODEL_EVENTS = "e.event_type LIKE 'metamodel.%' AND e.event_category = 'change'";

	private static final Pattern PERSON_REF = Pattern.compile("[1-9][0-9]{0,18}");

	/** The event types of each kind, as LIKE patterns, and the types a kind leaves out (design D2). */
	private static final Map<String, List<String>> KINDS = Map.of("metatype", List.of("metamodel.metatype-%"),
			"property", List.of("metamodel.property-%"), "category", List.of("metamodel.category-%"), "enum-value",
			List.of("metamodel.enum-value-%"), "mapping", List.of("metamodel.mapping-%"), "labels",
			List.of("metamodel.labels-changed"), "reimport", List.of("metamodel.reimport-policy-changed"), "package",
			List.of("metamodel.package-%", "metamodel.metatype-imported"), "configuration",
			List.of("metamodel.configuration-%", "metamodel.edit-mode-changed"));

	private static final String NAMES = """
			LEFT JOIN persons p ON p.id = e.actor_person_id
			LEFT JOIN system_admin_accounts a ON a.id = e.actor_admin_id""";

	private final JdbcTemplate jdbc;

	public MetamodelChanges(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An actor of a metamodel change; {@code ref} and {@code name} are {@code null} for the system. */
	public record Actor(String type, String ref, String name) {
	}

	/** A metamodel change; {@code details} is the stored JSON of the event. */
	public record Change(long id, Instant occurredAt, String eventType, String kind, Actor actor, String targetType,
			String targetId, String elementName, UUID batchId, Map<String, Object> details) {
	}

	/** The filters; every one may be {@code null}. */
	public record Query(String kind, String actorRef, LocalDate from, LocalDate to, UUID batchId) {
	}

	public record Page(List<Change> items, int page, int pageSize, long total, List<Actor> actors) {
	}

	/** A filter that cannot be applied (400). */
	public static class InvalidQueryException extends RuntimeException {

		InvalidQueryException(String message) {
			super(message);
		}

	}

	@Transactional(readOnly = true)
	public Page list(Query query, int page, int pageSize) {
		List<Object> arguments = new ArrayList<>();
		String where = where(query, arguments);
		Long total = this.jdbc.queryForObject("SELECT count(*) FROM audit.events e WHERE " + where, Long.class,
				arguments.toArray());
		List<Object> paged = new ArrayList<>(arguments);
		paged.add(pageSize);
		paged.add((long) page * pageSize);
		List<Change> items = this.jdbc.query("""
				SELECT e.id, e.occurred_at, e.event_type, e.actor_type, e.actor_person_id, e.actor_admin_id,
				       coalesce(p.name, a.login_name), e.target_type, e.target_id,
				       coalesce(e.details ->> 'name', e.details ->> 'value', m.name, d.name, c.name, g.name,
				                CASE WHEN e.target_type = 'metamodel-package' THEN e.target_id END),
				       e.details ->> 'batchId', e.details::text
				FROM audit.events e
				""" + NAMES + """

				LEFT JOIN metatypes m ON e.target_type = 'metatype' AND m.id::text = e.target_id
				LEFT JOIN property_definitions d ON e.target_type = 'property-definition' AND d.id::text = e.target_id
				LEFT JOIN property_categories c ON e.target_type = 'property-category' AND c.id::text = e.target_id
				LEFT JOIN metamodel_configurations g ON e.target_type = 'metamodel-configuration'
				     AND g.id::text = e.target_id
				""" + " WHERE " + where + """

				ORDER BY e.occurred_at DESC, e.id DESC LIMIT ? OFFSET ?""", MetamodelChanges::change, paged.toArray());
		List<Actor> actors = this.jdbc.query("""
				SELECT DISTINCT e.actor_type, e.actor_person_id, e.actor_admin_id, coalesce(p.name, a.login_name)
				FROM audit.events e
				""" + NAMES + " WHERE " + METAMODEL_EVENTS + """

				ORDER BY 4 NULLS LAST, 1""", (rs, n) -> actor(rs, 1));
		return new Page(items, page, pageSize, (total != null) ? total : 0, actors);
	}

	private static String where(Query query, List<Object> arguments) {
		StringBuilder where = new StringBuilder(METAMODEL_EVENTS);
		if (query.kind() != null) {
			List<String> patterns = KINDS.get(query.kind());
			if (patterns == null) {
				throw new InvalidQueryException("unknown kind " + query.kind());
			}
			where.append(" AND (").append(String.join(" OR ", patterns.stream().map((p) -> "e.event_type LIKE ?").toList()))
				.append(')');
			arguments.addAll(patterns);
			if ("metatype".equals(query.kind())) {
				where.append(" AND e.event_type <> 'metamodel.metatype-imported'");
			}
		}
		if (query.actorRef() != null) {
			if (PERSON_REF.matcher(query.actorRef()).matches()) {
				where.append(" AND e.actor_person_id = ?");
				arguments.add(Long.parseLong(query.actorRef()));
			}
			else {
				try {
					arguments.add(UUID.fromString(query.actorRef()));
				}
				catch (IllegalArgumentException ex) {
					throw new InvalidQueryException("invalid actorRef");
				}
				where.append(" AND e.actor_admin_id = ?");
			}
		}
		if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
			throw new InvalidQueryException("from is after to");
		}
		if (query.from() != null) {
			where.append(" AND e.occurred_at >= ?");
			arguments.add(Timestamp.from(query.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
		}
		if (query.to() != null) {
			where.append(" AND e.occurred_at < ?");
			arguments.add(Timestamp.from(query.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
		}
		if (query.batchId() != null) {
			where.append(" AND e.details ->> 'batchId' = ?");
			arguments.add(query.batchId().toString());
		}
		return where.toString();
	}

	@SuppressWarnings("unchecked")
	private static Change change(ResultSet rs, int row) throws SQLException {
		String eventType = rs.getString(3);
		String batch = rs.getString(11);
		String details = rs.getString(12);
		return new Change(rs.getLong(1), rs.getTimestamp(2).toInstant(), eventType, kind(eventType), actor(rs, 4),
				rs.getString(8), rs.getString(9), rs.getString(10), (batch != null) ? uuid(batch) : null,
				(details != null) ? JsonMapper.shared().readValue(details, Map.class) : null);
	}

	/** The actor from the columns type, person ID, admin ID and name starting at {@code first}. */
	private static Actor actor(ResultSet rs, int first) throws SQLException {
		String type = rs.getString(first);
		Long person = rs.getObject(first + 1, Long.class);
		UUID admin = rs.getObject(first + 2, UUID.class);
		String ref = (person != null) ? person.toString() : (admin != null) ? admin.toString() : null;
		return new Actor(type, ref, rs.getString(first + 3));
	}

	/** The kind of an event type (design D2); every metamodel event type has one. */
	static String kind(String eventType) {
		if ("metamodel.metatype-imported".equals(eventType)) {
			return "package";
		}
		for (Map.Entry<String, List<String>> kind : KINDS.entrySet()) {
			for (String pattern : kind.getValue()) {
				if (pattern.endsWith("%") ? eventType.startsWith(pattern.substring(0, pattern.length() - 1))
						: eventType.equals(pattern)) {
					return kind.getKey();
				}
			}
		}
		return "metatype";
	}

	private static UUID uuid(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
