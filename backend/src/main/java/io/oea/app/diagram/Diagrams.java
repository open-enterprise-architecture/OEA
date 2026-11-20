package io.oea.app.diagram;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.diagram.DiagramStore;
import io.oea.core.diagram.DiagramStore.Row;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diagrams as objects (US-074, UC-10; design D3 and D8 of us-074-diagrams): create, read, list,
 * rename, delete and restore. Persons and service accounts with {@code entity:write} write; a
 * principal without a person gets 403. Every change locks the row, checks {@code If-Match} and
 * raises the version. A deleted diagram is kept restorable and answers as unknown everywhere
 * except to the restore. The permissions are checked from the contract.
 */
@Service
public class Diagrams {

	/** The longest name. */
	public static final int NAME_MAX_LENGTH = 200;

	/** The longest description. */
	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** The cause named on a diagram that is removed with its viewpoint. */
	public static final String VIEWPOINT_DELETED = "viewpoint-deleted";

	private static final String NAME = "name";

	private static final String DESCRIPTION = "description";

	private final DiagramStore store;

	private final AuditWriter audit;

	private final Clock clock;

	Diagrams(DiagramStore store, AuditWriter audit, Clock clock) {
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	/** A person or service account. */
	public record Actor(long id, String name, boolean serviceAccount) {

		static Actor of(DiagramStore.Actor actor) {
			return new Actor(actor.id(), actor.name(), actor.serviceAccount());
		}

	}

	/** The viewpoint of a diagram. */
	public record ViewpointRef(UUID id, String name, String notation) {
	}

	/** A diagram; {@code deletedAt} and {@code deletedBy} are {@code null} unless it is deleted. */
	public record Diagram(UUID id, String name, String description, ViewpointRef viewpoint, int version,
			Actor createdBy, Instant createdAt, Instant updatedAt, Instant deletedAt, Actor deletedBy,
			String sortName) {

		static Diagram of(Row row) {
			return new Diagram(row.id(), row.name(), row.description(),
					new ViewpointRef(row.viewpointId(), row.viewpointName(), row.viewpointNotation()), row.version(),
					Actor.of(row.createdBy()), row.createdAt(), row.updatedAt(), row.deletedAt(),
					(row.deletedBy() != null) ? Actor.of(row.deletedBy()) : null, row.sortName());
		}

	}

	/** A query of the list; the contract checks the limits. */
	public record Query(String q, int page, int pageSize, String cursor) {
	}

	/** One page; {@code nextCursor} is {@code null} on the last page. */
	public record Page(List<Diagram> items, int page, int pageSize, long totalElements, int totalPages,
			String nextCursor) {
	}

	/**
	 * Creates an empty diagram and audits {@code diagram.created}.
	 *
	 * @throws PermissionDeniedException for a principal without a person (403)
	 * @throws DiagramChangeException naming the field for a rule the input violates or a taken name (422)
	 */
	@Transactional
	public Diagram create(OeaPrincipal principal, String name, String description, UUID viewpointId) {
		long person = person(principal);
		String checkedName = name(name);
		String checkedDescription = description(description);
		if (viewpointId == null || !this.store.viewpointExists(viewpointId)) {
			throw DiagramChangeException.invalid("viewpointId", "The viewpoint does not exist.");
		}
		UUID id;
		try {
			id = this.store.insert(checkedName, checkedDescription, viewpointId, person, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			throw DiagramChangeException.nameTaken();
		}
		catch (DataIntegrityViolationException ex) {
			// the viewpoint was deleted meanwhile
			throw DiagramChangeException.invalid("viewpointId", "The viewpoint does not exist.");
		}
		audit(principal, "diagram.created", id, details(checkedName, viewpointId));
		return Diagram.of(row(id));
	}

	/**
	 * A diagram that is not deleted.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 */
	@Transactional(readOnly = true)
	public Diagram get(UUID id) {
		return Diagram.of(active(row(id)));
	}

	/**
	 * One page of the diagrams that are not deleted, or of the deleted ones, ordered by name
	 * ignoring case, then by ID.
	 *
	 * @throws DiagramChangeException naming {@code cursor} for a cursor of another query (400)
	 */
	@Transactional(readOnly = true)
	public Page list(Query query, boolean deleted) {
		String search = (query.q() != null && !query.q().isBlank()) ? query.q() : null;
		String fingerprint = DiagramCursors.fingerprint(List.of("diagrams", Boolean.toString(deleted),
				(search != null) ? search : "", Integer.toString(query.pageSize())));
		DiagramCursors.Position after = null;
		if (query.cursor() != null && !query.cursor().isEmpty()) {
			after = DiagramCursors.decode(query.cursor(), fingerprint, query.page())
				.orElseThrow(() -> DiagramChangeException.invalidQuery("cursor"));
		}
		String like = (search != null) ? "%" + escape(search) + "%" : null;
		long offset = (long) query.page() * query.pageSize();
		List<Row> rows = this.store.page(new DiagramStore.Query(deleted, like,
				(after != null) ? after.sortName() : null, (after != null) ? after.id() : null, query.pageSize(),
				offset));
		long total = this.store.count(new DiagramStore.Query(deleted, like, null, null, 0, 0));
		String next = null;
		if (!rows.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			Row last = rows.get(rows.size() - 1);
			next = DiagramCursors.encode(fingerprint, query.page() + 1,
					new DiagramCursors.Position(last.sortName(), last.id()));
		}
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(rows.stream().map(Diagram::of).toList(), query.page(), query.pageSize(), total, totalPages,
				next);
	}

	/**
	 * Replaces name and description with the expected version and audits {@code diagram.updated}.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws DiagramChangeException for a missing (428) or stale (412) version, an invalid field or a taken name (422)
	 */
	@Transactional
	public Diagram update(OeaPrincipal principal, UUID id, String name, String description, Long expectedVersion) {
		long person = person(principal);
		Row current = locked(id, expectedVersion);
		String checkedName = name(name);
		String checkedDescription = description(description);
		List<String> changed = new ArrayList<>();
		if (!current.name().equals(checkedName)) {
			changed.add(NAME);
		}
		if (!Objects.equals(current.description(), checkedDescription)) {
			changed.add(DESCRIPTION);
		}
		try {
			this.store.update(id, checkedName, checkedDescription, person, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			throw DiagramChangeException.nameTaken();
		}
		Map<String, Object> details = details(checkedName, current.viewpointId());
		details.put("fields", changed);
		audit(principal, "diagram.updated", id, details);
		return Diagram.of(row(id));
	}

	/**
	 * Marks the diagram as deleted, with the expected version, and audits {@code diagram.deleted}.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws DiagramChangeException for a missing (428) or stale (412) version
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		long person = person(principal);
		Row current = locked(id, expectedVersion);
		this.store.markDeleted(id, person, this.clock.instant());
		audit(principal, "diagram.deleted", id, details(current.name(), current.viewpointId()));
	}

	/**
	 * Restores a deleted diagram with the expected version and audits {@code diagram.restored}.
	 *
	 * @throws NoSuchElementException for an unknown diagram (404)
	 * @throws DiagramChangeException for a missing (428) or stale (412) version, a diagram that is not
	 *         deleted (409) or a name that a diagram has meanwhile (422)
	 */
	@Transactional
	public Diagram restore(OeaPrincipal principal, UUID id, Long expectedVersion) {
		long person = person(principal);
		Row current = this.store.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("diagram " + id));
		if (expectedVersion == null) {
			throw DiagramChangeException.preconditionRequired();
		}
		if (current.deletedAt() == null) {
			throw DiagramChangeException.notDeleted();
		}
		if (expectedVersion != current.version()) {
			throw DiagramChangeException.staleVersion(current.version());
		}
		try {
			this.store.restore(id, person, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			throw DiagramChangeException.nameTaken();
		}
		audit(principal, "diagram.restored", id, details(current.name(), current.viewpointId()));
		return Diagram.of(row(id));
	}

	/**
	 * Removes the diagrams of a viewpoint for good, the deleted ones included, and audits each as
	 * {@code diagram.deleted} with the cause {@code viewpoint-deleted}. The caller has locked the
	 * viewpoint and deletes it afterwards in the same transaction. Returns the number of diagrams
	 * that were not deleted before.
	 */
	@Transactional
	public int removeOfViewpoint(OeaPrincipal principal, UUID viewpointId) {
		int removed = 0;
		for (DiagramStore.Removed diagram : this.store.ofViewpointForUpdate(viewpointId)) {
			Map<String, Object> details = details(diagram.name(), viewpointId);
			details.put("cause", VIEWPOINT_DELETED);
			if (diagram.deleted()) {
				details.put("wasDeleted", true);
			}
			else {
				removed++;
			}
			audit(principal, "diagram.deleted", diagram.id(), details);
		}
		this.store.deleteOfViewpoint(viewpointId);
		return removed;
	}

	/** The number of diagrams that are not deleted, per viewpoint with at least one. */
	@Transactional(readOnly = true)
	public Map<UUID, Integer> countsPerViewpoint() {
		return this.store.activeCounts();
	}

	/**
	 * Locks the diagram for the save of its layout (design D4 of us-074-diagram-canvas) and checks
	 * the expected version; the caller runs in a transaction.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws DiagramChangeException for a missing (428) or stale (412) version
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Diagram lockForLayout(UUID id, Long expectedVersion) {
		return Diagram.of(locked(id, expectedVersion));
	}

	/**
	 * Raises the version of the diagram for a saved layout and returns the new version; the caller
	 * has locked it and audits the save.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int layoutSaved(OeaPrincipal principal, UUID id) {
		return this.store.touch(id, person(principal), this.clock.instant());
	}

	private Row locked(UUID id, Long expectedVersion) {
		Row current = active(this.store.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("diagram " + id)));
		if (expectedVersion == null) {
			throw DiagramChangeException.preconditionRequired();
		}
		if (expectedVersion != current.version()) {
			throw DiagramChangeException.staleVersion(current.version());
		}
		return current;
	}

	private Row row(UUID id) {
		return this.store.find(id).orElseThrow(() -> new NoSuchElementException("diagram " + id));
	}

	private static Row active(Row row) {
		if (row.deletedAt() != null) {
			throw new NoSuchElementException("diagram " + row.id());
		}
		return row;
	}

	private static String name(String input) {
		String name = (input != null) ? input.strip() : "";
		if (name.isEmpty()) {
			throw DiagramChangeException.invalid(NAME, "The name must not be blank.");
		}
		if (name.length() > NAME_MAX_LENGTH) {
			throw DiagramChangeException.invalid(NAME, "The name has at most " + NAME_MAX_LENGTH + " characters.");
		}
		return name;
	}

	private static String description(String input) {
		if (input != null && input.length() > DESCRIPTION_MAX_LENGTH) {
			throw DiagramChangeException.invalid(DESCRIPTION,
					"The description has at most " + DESCRIPTION_MAX_LENGTH + " characters.");
		}
		return (input != null && !input.isBlank()) ? input : null;
	}

	/** The pattern with {@code \}, {@code %} and {@code _} escaped, so that they match literally. */
	static String escape(String search) {
		return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static long person(OeaPrincipal principal) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		return person;
	}

	private static Map<String, Object> details(String name, UUID viewpointId) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put(NAME, name);
		details.put("viewpointId", viewpointId.toString());
		return details;
	}

	private void audit(OeaPrincipal principal, String type, UUID id, Map<String, Object> details) {
		this.audit.append(new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "diagram",
				id.toString(), details));
	}

}
