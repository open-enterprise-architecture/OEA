package io.oea.app.solution;

import io.oea.app.catalog.CatalogCursors;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore.KeyType;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The baseline of a solution (US-039, REQ-039; design of us-039-solution-baseline). In project
 * mode it is the current catalog: the entities of element metatypes that are not deleted (D1),
 * ordered by name case-insensitively, then by ID, and paged by offset or by a cursor over these
 * two keys (D2, D3).
 */
@Service
public class SolutionLandscape {

	private static final List<KeyType> KEY_TYPES = List.of(KeyType.TEXT, KeyType.INTEGER);

	private static final String FROM = " FROM entities e JOIN metatypes m ON m.id = e.metatype_id"
			+ " WHERE e.deleted_at IS NULL AND NOT m.is_connection";

	private final JdbcTemplate jdbc;

	private final Solutions solutions;

	private final CatalogCursors cursors;

	private final PropertyAccess access;

	SolutionLandscape(JdbcTemplate jdbc, Solutions solutions, CatalogCursors cursors, PropertyAccess access) {
		this.jdbc = jdbc;
		this.solutions = solutions;
		this.cursors = cursors;
		this.access = access;
	}

	/** An entity of the baseline with its description and the values it has (us-041-modified-delta D4). */
	public record EntityEntry(Entry entry, String description, List<SolutionDeltas.Value> properties) {
	}

	/**
	 * One entity of the baseline with its values; a value the principal may not read for this entity
	 * is redacted.
	 *
	 * @throws NoSuchElementException for an unknown solution or an entity not in the baseline (404)
	 */
	@Transactional(readOnly = true)
	public EntityEntry entry(OeaPrincipal principal, UUID solutionId, int entityId) {
		this.solutions.get(solutionId);
		List<EntityEntry> found = this.jdbc.query(
				"SELECT e.id, e.name, m.id, m.name, e.version, e.description" + FROM + " AND e.id = ?",
				(rs, row) -> new EntityEntry(new Entry(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class),
						rs.getString(4), rs.getInt(5)), rs.getString(6), List.of()),
				entityId);
		if (found.isEmpty()) {
			throw new NoSuchElementException("entity " + entityId);
		}
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		List<SolutionDeltas.Value> values = this.jdbc.query("""
				SELECT p.id, p.name, p.data_type, v.value_text, v.value_int, v.value_bool, v.value_date, v.value_ref,
				       v.value_enum, r.name, rm.abbreviation
				FROM entity_property_values v
				JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities r ON r.id = v.value_ref
				LEFT JOIN metatypes rm ON rm.id = r.metatype_id
				WHERE v.entity_id = ? ORDER BY lower(p.name), p.id""", (rs, row) -> {
			UUID property = rs.getObject(1, UUID.class);
			if (!snapshot.readableFor(property, entityId)) {
				return new SolutionDeltas.Value(property, rs.getString(2), rs.getString(3), true, null, null, null, null,
						null, null);
			}
			java.sql.Date date = rs.getDate(7);
			Integer reference = rs.getObject(8, Integer.class);
			return new SolutionDeltas.Value(property, rs.getString(2), rs.getString(3), false, rs.getString(4),
					rs.getObject(5, Long.class), rs.getObject(6, Boolean.class), (date != null) ? date.toLocalDate() : null,
					rs.getString(9), (reference != null)
							? new SolutionDeltas.Reference(reference, rs.getString(10), rs.getString(11)) : null);
		}, entityId);
		return new EntityEntry(found.get(0).entry(), found.get(0).description(), values);
	}

	/** A query of the baseline; the contract checks the limits. */
	public record Query(String q, UUID metatypeId, int page, int pageSize, String cursor) {
	}

	/** An entity of the baseline. */
	public record Entry(int id, String name, UUID metatypeId, String metatypeName, int version) {
	}

	/** One page of the baseline; {@code nextCursor} is {@code null} on the last page. */
	public record Page(List<Entry> items, int page, int pageSize, long totalElements, int totalPages,
			String nextCursor) {
	}

	/**
	 * One page of the baseline of the solution.
	 *
	 * @throws java.util.NoSuchElementException for an unknown solution (404)
	 * @throws InvalidSolutionQueryException naming {@code cursor} for a cursor of another query
	 */
	@Transactional(readOnly = true)
	public Page read(UUID solutionId, Query query) {
		this.solutions.get(solutionId);
		String search = (query.q() != null && !query.q().isBlank()) ? query.q() : null;
		StringBuilder where = new StringBuilder(FROM);
		List<Object> args = new ArrayList<>();
		if (query.metatypeId() != null) {
			where.append(" AND e.metatype_id = ?");
			args.add(query.metatypeId());
		}
		if (search != null) {
			where.append(" AND strpos(lower(e.name), lower(?)) > 0");
			args.add(search);
		}
		long total = this.jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());

		String fingerprint = CatalogCursors.fingerprint(List.of("solution-landscape", solutionId.toString(),
				(search != null) ? search : "", (query.metatypeId() != null) ? query.metatypeId().toString() : "",
				Integer.toString(query.pageSize())));
		StringBuilder sql = new StringBuilder(
				"SELECT e.id, e.name, m.id, m.name, e.version, lower(e.name)").append(where);
		List<Object> pageArgs = new ArrayList<>(args);
		boolean keyset = query.cursor() != null && !query.cursor().isEmpty();
		if (keyset) {
			List<Object> after = this.cursors.decode(query.cursor(), fingerprint, query.page(), KEY_TYPES)
				.orElseThrow(() -> new InvalidSolutionQueryException("cursor"));
			sql.append(" AND (lower(e.name), e.id) > (?, ?)");
			pageArgs.addAll(after);
		}
		sql.append(" ORDER BY lower(e.name), e.id LIMIT ?");
		pageArgs.add(query.pageSize());
		if (!keyset) {
			sql.append(" OFFSET ?");
			pageArgs.add((long) query.page() * query.pageSize());
		}
		List<String> lastKeys = new ArrayList<>(1);
		List<Entry> items = this.jdbc.query(sql.toString(), (rs, row) -> {
			lastKeys.clear();
			lastKeys.add(rs.getString(6));
			return new Entry(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
					rs.getInt(5));
		}, pageArgs.toArray());

		String nextCursor = null;
		if (!items.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			Entry last = items.get(items.size() - 1);
			nextCursor = this.cursors.encode(fingerprint, query.page() + 1, List.of(lastKeys.get(0), last.id()));
		}
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(items, query.page(), query.pageSize(), total, totalPages, nextCursor);
	}

}
