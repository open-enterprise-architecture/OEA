package io.oea.app.metamodel;

import io.oea.core.metamodel.MetamodelChangeStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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

	private static final Pattern PERSON_REF = Pattern.compile("[1-9][0-9]{0,18}");

	/** The event types of each kind, as LIKE patterns, and the types a kind leaves out (design D2). */
	private static final Map<String, List<String>> KINDS = Map.of("metatype", List.of("metamodel.metatype-%"),
			"property", List.of("metamodel.property-%"), "category", List.of("metamodel.category-%"), "enum-value",
			List.of("metamodel.enum-value-%"), "mapping", List.of("metamodel.mapping-%"), "layer", List.of("metamodel.layer-%"), "labels",
			List.of("metamodel.labels-changed"), "reimport", List.of("metamodel.reimport-policy-changed"), "package",
			List.of("metamodel.package-%", "metamodel.metatype-imported"), "configuration",
			List.of("metamodel.configuration-%", "metamodel.edit-mode-changed"));

	private final MetamodelChangeStore store;

	public MetamodelChanges(MetamodelChangeStore store) {
		this.store = store;
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
		MetamodelChangeStore.Filter filter = filter(query);
		long total = this.store.count(filter);
		List<Change> items = this.store.page(filter, pageSize, (long) page * pageSize)
			.stream()
			.map(MetamodelChanges::change)
			.toList();
		List<Actor> actors = this.store.actors().stream().map(MetamodelChanges::actor).toList();
		return new Page(items, page, pageSize, total, actors);
	}

	private static MetamodelChangeStore.Filter filter(Query query) {
		List<String> patterns = null;
		boolean withoutImported = false;
		if (query.kind() != null) {
			patterns = KINDS.get(query.kind());
			if (patterns == null) {
				throw new InvalidQueryException("unknown kind " + query.kind());
			}
			withoutImported = "metatype".equals(query.kind());
		}
		Long person = null;
		UUID admin = null;
		if (query.actorRef() != null) {
			if (PERSON_REF.matcher(query.actorRef()).matches()) {
				person = Long.parseLong(query.actorRef());
			}
			else {
				try {
					admin = UUID.fromString(query.actorRef());
				}
				catch (IllegalArgumentException ex) {
					throw new InvalidQueryException("invalid actorRef");
				}
			}
		}
		if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
			throw new InvalidQueryException("from is after to");
		}
		Instant from = (query.from() != null) ? query.from().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
		Instant to = (query.to() != null) ? query.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
		return new MetamodelChangeStore.Filter(patterns, withoutImported, person, admin, from, to,
				(query.batchId() != null) ? query.batchId().toString() : null);
	}

	@SuppressWarnings("unchecked")
	private static Change change(MetamodelChangeStore.ChangeRow row) {
		String batch = row.batchId();
		String details = row.details();
		return new Change(row.id(), row.occurredAt(), row.eventType(), kind(row.eventType()), actor(row.actor()),
				row.targetType(), row.targetId(), row.elementName(), (batch != null) ? uuid(batch) : null,
				(details != null) ? JsonMapper.shared().readValue(details, Map.class) : null);
	}

	private static Actor actor(MetamodelChangeStore.ActorRow row) {
		String ref = (row.personId() != null) ? row.personId().toString()
				: (row.adminId() != null) ? row.adminId().toString() : null;
		return new Actor(row.type(), ref, row.name());
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
