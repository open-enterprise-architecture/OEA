package io.oea.app.deletion;

import io.oea.core.entity.DeletedEntityStore;
import io.oea.core.entity.DeletedEntityStore.Row;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The list of deleted entities (design D1 of s17-deleted-entities; REQ-159, ADR-019): newest deletion
 * first, then by descending ID, filtered by a name part and a metatype, in pages with a cursor. The
 * permission {@code entity:restore} is enforced by the contract. The entries carry no property
 * values, so property visibility does not apply.
 */
@Service
public class DeletedEntities {

	private final DeletedEntityStore store;

	DeletedEntities(DeletedEntityStore store) {
		this.store = store;
	}

	/** The parameters of a list: name part, metatype, paging and the cursor of the previous page. */
	public record Query(String q, UUID metatypeId, int page, int pageSize, String cursor) {
	}

	/** An entity named by a deleted entity, active or deleted. */
	public record Ref(int id, String name) {
	}

	/** The actor of a deletion. */
	public record Actor(long id, String name, boolean serviceAccount) {
	}

	/**
	 * A deleted entity; {@code source} and {@code target} are set for a connection, {@code deletedBy}
	 * when an actor was recorded, {@code deletedWith} for an entity taken along by a cascade.
	 */
	public record Entry(int id, String name, UUID metatypeId, String metatypeName, String metatypeAbbreviation,
			boolean connection, int version, Instant deletedAt, Actor deletedBy, Ref deletedWith, Ref source,
			Ref target) {
	}

	/** One page; {@code nextCursor} is {@code null} on the last page. */
	public record Page(List<Entry> items, int page, int pageSize, long totalElements, int totalPages,
			String nextCursor) {
	}

	/**
	 * One page of the deleted entities.
	 *
	 * @throws InvalidDeletedEntityQueryException naming {@code cursor} for a cursor of another query (400)
	 */
	@Transactional(readOnly = true)
	public Page list(Query query) {
		String search = (query.q() != null && !query.q().isBlank()) ? query.q() : null;
		String fingerprint = DeletedEntityCursors.fingerprint(List.of("deleted-entities", (search != null) ? search : "",
				(query.metatypeId() != null) ? query.metatypeId().toString() : "", Integer.toString(query.pageSize())));
		DeletedEntityCursors.Position after = null;
		if (query.cursor() != null && !query.cursor().isEmpty()) {
			after = DeletedEntityCursors.decode(query.cursor(), fingerprint, query.page())
				.orElseThrow(() -> new InvalidDeletedEntityQueryException(List.of("cursor")));
		}
		String like = (search != null) ? "%" + escape(search) + "%" : null;
		List<Row> rows = this.store.page(new DeletedEntityStore.Query(like, query.metatypeId(),
				(after != null) ? after.deletedAt() : null, (after != null) ? after.id() : null, query.pageSize(),
				(long) query.page() * query.pageSize()));
		long total = this.store.count(like, query.metatypeId());
		String next = null;
		if (!rows.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			Row last = rows.get(rows.size() - 1);
			next = DeletedEntityCursors.encode(fingerprint, query.page() + 1,
					new DeletedEntityCursors.Position(last.deletedAt(), last.id()));
		}
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(rows.stream().map(DeletedEntities::entry).toList(), query.page(), query.pageSize(), total,
				totalPages, next);
	}

	/** The pattern with {@code \}, {@code %} and {@code _} escaped, so that they match literally. */
	static String escape(String search) {
		return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Entry entry(Row row) {
		return new Entry(row.id(), row.name(), row.metatypeId(), row.metatypeName(), row.metatypeAbbreviation(),
				row.connection(), row.version(), row.deletedAt(),
				(row.deletedBy() != null) ? new Actor(row.deletedBy().id(), row.deletedBy().name(),
						row.deletedBy().serviceAccount()) : null,
				ref(row.deletedWith()), ref(row.source()), ref(row.target()));
	}

	private static Ref ref(DeletedEntityStore.Ref ref) {
		return (ref != null) ? new Ref(ref.id(), ref.name()) : null;
	}

}
