package io.oea.app.solution;

import io.oea.app.catalog.CatalogCursors;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore.KeyType;
import io.oea.core.entity.TypedValue;
import io.oea.core.solution.PlateauContentStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The baseline of a solution (US-039, REQ-039; design of us-039-solution-baseline). In project
 * mode, and for a solution whose target plateau has the basis {@code catalog}, it is the current
 * catalog: the entities of element metatypes that are not deleted (D1). For a solution whose
 * target plateau has the basis {@code predecessor} it is the content of the predecessor, with the
 * changes of its active solutions applied (US-155; design D4 of us-084-plateaus). Ordered by
 * name case-insensitively, then by a sort key that keeps the entity order by ID, and paged by
 * offset or by a cursor over these two keys (D2, D3).
 */
@Service
public class SolutionLandscape {

	private static final List<KeyType> KEY_TYPES = List.of(KeyType.TEXT, KeyType.TEXT);

	private final JdbcTemplate jdbc;

	private final Solutions solutions;

	private final CatalogCursors cursors;

	private final PropertyAccess access;

	private final SolutionConflicts conflicts;

	private final PlateauContentStore content;

	SolutionLandscape(JdbcTemplate jdbc, Solutions solutions, CatalogCursors cursors, PropertyAccess access,
			SolutionConflicts conflicts, PlateauContentStore content) {
		this.jdbc = jdbc;
		this.conflicts = conflicts;
		this.solutions = solutions;
		this.cursors = cursors;
		this.access = access;
		this.content = content;
	}

	/** An entity of the baseline with its description and the values it has (us-041-modified-delta D4). */
	public record EntityEntry(Entry entry, String description, List<SolutionDeltas.Value> properties) {
	}

	/** The solution of a predecessor plateau that retires, proposes or last changed an entry. */
	public record ChangedBy(UUID solutionId, String name) {
	}

	/**
	 * The name, description and values of an entity in the baseline of a solution (design D5 of
	 * us-084-plateaus): the catalog with the values the layers of the solution set; a value
	 * {@code null} in {@code values} is one a layer removed.
	 */
	public record Basis(String name, TypedValue description, Map<UUID, TypedValue> values) {
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
		List<UUID> layers = this.content.layers(solutionId);
		Entry entry = parallelChanges(solutionId, List.of(entry(this.content.entity(layers, solutionId, entityId)
			.orElseThrow(() -> new NoSuchElementException("entity " + entityId))))).get(0);
		String description = this.content.description(entityId);
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		Map<UUID, SolutionDeltas.Value> values = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT p.id, p.name, p.data_type, v.value_text, v.value_int, v.value_bool, v.value_date, v.value_ref,
				       v.value_enum, r.name, rm.abbreviation
				FROM entity_property_values v
				JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities r ON r.id = v.value_ref
				LEFT JOIN metatypes rm ON rm.id = r.metatype_id
				WHERE v.entity_id = ? ORDER BY lower(p.name), p.id""", (rs, row) -> {
			java.sql.Date date = rs.getDate(7);
			Integer reference = rs.getObject(8, Integer.class);
			UUID property = rs.getObject(1, UUID.class);
			values.put(property, new SolutionDeltas.Value(property, rs.getString(2), rs.getString(3), false,
					rs.getString(4), rs.getObject(5, Long.class), rs.getObject(6, Boolean.class),
					(date != null) ? date.toLocalDate() : null, rs.getString(9), (reference != null)
							? new SolutionDeltas.Reference(reference, rs.getString(10), rs.getString(11)) : null));
			return null;
		}, entityId);
		for (PlateauContentStore.Overlay overlay : this.content.overlay(layers, entityId)) {
			if (overlay.systemField() != null) {
				description = (overlay.value() != null) ? overlay.value().text() : null;
			}
			else if (overlay.value() == null) {
				values.remove(overlay.propertyDefId());
			}
			else {
				TypedValue value = overlay.value();
				values.put(overlay.propertyDefId(), new SolutionDeltas.Value(overlay.propertyDefId(),
						overlay.propertyName(), overlay.dataType(), false, value.text(), value.integer(), value.bool(),
						value.date(), value.enumValue(), (value.reference() != null) ? new SolutionDeltas.Reference(
								value.reference(), overlay.referenceName(), overlay.referenceAbbreviation()) : null));
			}
		}
		List<SolutionDeltas.Value> shown = values.values()
			.stream()
			.sorted(java.util.Comparator.comparing((SolutionDeltas.Value value) -> value.propertyName().toLowerCase())
				.thenComparing((value) -> value.propertyDefId().toString()))
			.map((value) -> snapshot.readableFor(value.propertyDefId(), entityId) ? value
					: new SolutionDeltas.Value(value.propertyDefId(), value.propertyName(), value.dataType(), true, null, null,
							null, null, null, null))
			.toList();
		return new EntityEntry(entry, description, shown);
	}

	/**
	 * The name, description and values of an entity in the baseline of the solution, unredacted,
	 * for the before values of a modification (design D5 of us-084-plateaus); empty without layers.
	 */
	Map<String, Object> overlay(UUID solutionId, int entityId) {
		List<UUID> layers = this.content.layers(solutionId);
		Map<String, Object> overlay = new LinkedHashMap<>();
		if (layers.isEmpty()) {
			return overlay;
		}
		this.content.entity(layers, solutionId, entityId).ifPresent((row) -> overlay.put("name", row.name()));
		for (PlateauContentStore.Overlay value : this.content.overlay(layers, entityId)) {
			overlay.put((value.systemField() != null) ? value.systemField() : value.propertyDefId().toString(),
					value.value());
		}
		return overlay;
	}

	/**
	 * The entries with the other active solutions that change their entities (D1 of
	 * us-044-conflict-warning). The queries read an entry whose metatype shows parallel changes
	 * with {@code null} in their place, which this fills; any other entry has an empty list already.
	 */
	private List<Entry> parallelChanges(UUID solutionId, List<Entry> entries) {
		List<Integer> shown = entries.stream()
			.filter((entry) -> entry.parallelChanges() == null)
			.map(Entry::id)
			.toList();
		Map<Integer, List<SolutionConflicts.Conflict>> found = this.conflicts.of(solutionId, shown);
		return entries.stream()
			.map((entry) -> (entry.parallelChanges() != null) ? entry
					: new Entry(entry.id(), entry.name(), entry.metatypeId(), entry.metatypeName(), entry.version(),
							entry.deltaType(), found.getOrDefault(entry.id(), List.of()), entry.lifecycle(),
							entry.changedBy(), entry.deltaId()))
			.toList();
	}

	private static Entry entry(PlateauContentStore.Row row) {
		boolean proposed = row.entityId() == null;
		return new Entry(row.entityId(), row.name(), row.metatypeId(), row.metatypeName(), row.version(),
				(row.deltaType() != null) ? SolutionDeltas.Type.of(row.deltaType()) : null,
				(row.showParallelChanges() && !proposed) ? null : List.of(), row.lifecycle(),
				(row.changedBy() != null) ? new ChangedBy(row.changedBy(), row.changedByName()) : null, row.deltaId());
	}

	/** A query of the baseline; the contract checks the limits. */
	public record Query(String q, UUID metatypeId, int page, int pageSize, String cursor) {
	}

	/**
	 * An entry of the baseline: an entity with the type of its delta in the solution, if any (us-043
	 * D0), and the other active solutions that change it, when its metatype shows them (us-044 D1);
	 * its lifecycle in the basis and the solution of a predecessor plateau behind it, and for a
	 * proposed entry, which has no entity ID, its delta (us-084-plateaus D4).
	 */
	public record Entry(Integer id, String name, UUID metatypeId, String metatypeName, int version,
			SolutionDeltas.Type deltaType, List<SolutionConflicts.Conflict> parallelChanges, String lifecycle,
			ChangedBy changedBy, UUID deltaId) {
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
		List<UUID> layers = this.content.layers(solutionId);
		PlateauContentStore.Filter filter = new PlateauContentStore.Filter(query.metatypeId(), search);
		long total = this.content.count(layers, solutionId, filter);

		String fingerprint = CatalogCursors.fingerprint(List.of("solution-landscape", solutionId.toString(),
				(search != null) ? search : "", (query.metatypeId() != null) ? query.metatypeId().toString() : "",
				Integer.toString(query.pageSize())));
		List<Object> after = null;
		if (query.cursor() != null && !query.cursor().isEmpty()) {
			after = this.cursors.decode(query.cursor(), fingerprint, query.page(), KEY_TYPES)
				.orElseThrow(() -> new InvalidSolutionQueryException("cursor"));
		}
		List<PlateauContentStore.Row> rows = this.content.page(layers, solutionId, filter, after, query.pageSize(),
				(long) query.page() * query.pageSize());
		List<Entry> items = parallelChanges(solutionId, rows.stream().map(SolutionLandscape::entry).toList());

		String nextCursor = null;
		if (!rows.isEmpty() && (long) (query.page() + 1) * query.pageSize() < total) {
			PlateauContentStore.Row last = rows.get(rows.size() - 1);
			nextCursor = this.cursors.encode(fingerprint, query.page() + 1, List.of(last.sortName(), last.sortKey()));
		}
		int totalPages = Math.toIntExact((total + query.pageSize() - 1) / query.pageSize());
		return new Page(items, query.page(), query.pageSize(), total, totalPages, nextCursor);
	}

}
