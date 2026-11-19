package io.oea.core.metamodel;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The mappings of property definitions to metatypes in {@code metatype_property_mappings}, their
 * entries by effective date (design D7 of us-032-metatype-editing; design D2, D3 of
 * us-137-mapping-effective-date; ADR-022, ADR-049).
 */
@Repository
public class PropertyMappingStore {

	private final JdbcTemplate jdbc;

	public PropertyMappingStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A mapping entry. */
	public record EntryRow(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, LocalDate validFrom,
			int sortOrder, String scope) {
	}

	/** An entry that takes effect. */
	public record DueRow(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, LocalDate validFrom) {
	}

	/** A mapping that was removed. */
	public record RemovedRow(UUID id, UUID propertyDefId) {
	}

	/** The metatype and the scope of a mapping. */
	public record MappingRef(UUID propertyDefId, String scope) {
	}

	/** The typed default value of a new entry; at most one field is set. */
	public record DefaultValue(String text, Long integer, Boolean bool, LocalDate date, String enumValue) {
	}

	/** Every entry ordered for display (sort order, property, date). */
	public List<EntryRow> entries() {
		return this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from, sort_order, scope
				FROM metatype_property_mappings ORDER BY sort_order, property_def_id, valid_from""",
				(rs, n) -> new EntryRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, LocalDate.class), rs.getInt(6),
						rs.getString(7)));
	}

	/** The active own mappings (latest {@code valid_from} not after the date): metatype to property to necessity. */
	public Map<UUID, Map<UUID, String>> active(LocalDate date) {
		Map<UUID, Map<UUID, String>> own = new HashMap<>();
		this.jdbc.query("""
				SELECT DISTINCT ON (metatype_id, property_def_id) metatype_id, property_def_id, necessity
				FROM metatype_property_mappings WHERE valid_from <= ?
				ORDER BY metatype_id, property_def_id, valid_from DESC""", (rs) -> {
			own.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new LinkedHashMap<>())
				.put(rs.getObject(2, UUID.class), rs.getString(3));
		}, Date.valueOf(date));
		return own;
	}

	/** The names of the metatypes that map each property today, in the order of the metatype. */
	public Map<UUID, List<String>> mappedMetatypeNames(LocalDate date) {
		Map<UUID, List<String>> metatypes = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT DISTINCT ON (m.metatype_id, m.property_def_id) m.property_def_id, t.name
				FROM metatype_property_mappings m JOIN metatypes t ON t.id = m.metatype_id
				WHERE m.valid_from <= ?
				ORDER BY m.metatype_id, m.property_def_id, m.valid_from DESC""", (rs) -> {
			metatypes.computeIfAbsent(rs.getObject(1, UUID.class), (key) -> new ArrayList<>()).add(rs.getString(2));
		}, Date.valueOf(date));
		return metatypes;
	}

	/** Whether an entry is due and not applied yet. */
	public boolean dueExists(LocalDate today) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM metatype_property_mappings
				               WHERE effective_applied_at IS NULL AND valid_from <= ?)""", Boolean.class,
				Date.valueOf(today)));
	}

	/** The entries that are due and not applied yet. */
	public List<DueRow> due(LocalDate today) {
		return this.jdbc.query("""
				SELECT id, metatype_id, property_def_id, necessity, valid_from FROM metatype_property_mappings
				WHERE effective_applied_at IS NULL AND valid_from <= ? ORDER BY valid_from, id""",
				(rs, n) -> new DueRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, LocalDate.class)),
				Date.valueOf(today));
	}

	public void markApplied(UUID id, Instant now) {
		this.jdbc.update("UPDATE metatype_property_mappings SET effective_applied_at = ? WHERE id = ?",
				Timestamp.from(now), id);
	}

	/** The sort orders of the entries of the property on the metatype, the latest date first. */
	public List<Integer> sortOrders(UUID metatypeId, UUID propertyDefId) {
		return this.jdbc.queryForList("""
				SELECT sort_order FROM metatype_property_mappings WHERE metatype_id = ? AND property_def_id = ?
				ORDER BY valid_from DESC""", Integer.class, metatypeId, propertyDefId);
	}

	/** Whether the property is mapped on one of the metatypes of the two sets. */
	public boolean mappedOnAny(UUID propertyDefId, Collection<UUID> chain, Collection<UUID> subtree) {
		Integer duplicates = this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings
				WHERE property_def_id = ? AND (metatype_id = ANY (?::uuid[]) OR metatype_id = ANY (?::uuid[]))""",
				Integer.class, propertyDefId, ids(chain), ids(subtree));
		return duplicates != null && duplicates > 0;
	}

	/** Whether an entry of the property on the metatype has the date. */
	public boolean entryExists(UUID metatypeId, UUID propertyDefId, LocalDate validFrom) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM metatype_property_mappings
				               WHERE metatype_id = ? AND property_def_id = ? AND valid_from = ?)""", Boolean.class,
				metatypeId, propertyDefId, Date.valueOf(validFrom)));
	}

	/** Inserts an entry of an organization; {@code appliedAt} is {@code null} for a future entry. */
	public void insert(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, LocalDate validFrom,
			int sortOrder, DefaultValue value, Long personId, Instant appliedAt) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (id, metatype_id, property_def_id, necessity, valid_from, sort_order,
				    default_value_text, default_value_int, default_value_bool, default_value_date, default_value_enum,
				    created_by, scope, effective_applied_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'organization', ?)""", id, metatypeId, propertyDefId,
				necessity, Date.valueOf(validFrom), sortOrder, (value != null) ? value.text() : null,
				(value != null) ? value.integer() : null, (value != null) ? value.bool() : null,
				(value != null && value.date() != null) ? Date.valueOf(value.date()) : null,
				(value != null) ? value.enumValue() : null, personId,
				(appliedAt != null) ? Timestamp.from(appliedAt) : null);
	}

	/** The property and scope of the mapping of the metatype. */
	public Optional<MappingRef> find(UUID mappingId, UUID metatypeId) {
		return this.jdbc.query("""
				SELECT property_def_id, scope FROM metatype_property_mappings WHERE id = ? AND metatype_id = ?""",
				(rs, n) -> new MappingRef(rs.getObject(1, UUID.class), rs.getString(2)), mappingId, metatypeId)
			.stream()
			.findFirst();
	}

	public void delete(UUID mappingId) {
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE id = ?", mappingId);
	}

	/** Removes all mappings of the metatype. */
	public void deleteOf(UUID metatypeId) {
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE metatype_id = ?", metatypeId);
	}

	/** Removes the organization mappings of the metatype and returns them. */
	public List<RemovedRow> deleteOrganization(UUID metatypeId) {
		return this.jdbc.query("""
				DELETE FROM metatype_property_mappings WHERE metatype_id = ? AND scope = 'organization'
				RETURNING id, property_def_id""",
				(rs, n) -> new RemovedRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), metatypeId);
	}

	/** Whether any mapping of the property exists. */
	public boolean mapped(UUID propertyDefId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM metatype_property_mappings WHERE property_def_id = ?)", Boolean.class,
				propertyDefId));
	}

	/** Whether a property would be mapped twice if the base type chain {@code above} met the subtree {@code below}. */
	public boolean mappedTwice(Collection<UUID> above, Collection<UUID> below) {
		Integer duplicates = this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings a JOIN metatype_property_mappings b
				    ON a.property_def_id = b.property_def_id
				WHERE a.metatype_id = ANY (?::uuid[]) AND b.metatype_id = ANY (?::uuid[])""", Integer.class, ids(above),
				ids(below));
		return duplicates != null && duplicates > 0;
	}

	/** Whether the metatype shares a property with one of the related metatypes. */
	public boolean sharesPropertyWith(UUID metatypeId, Collection<UUID> related) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM metatype_property_mappings a JOIN metatype_property_mappings b
				    ON a.property_def_id = b.property_def_id AND a.metatype_id <> b.metatype_id
				WHERE a.metatype_id = ? AND b.metatype_id = ANY (?::uuid[]))""", Boolean.class, metatypeId,
				ids(related)));
	}

	/** Existing mappings with the identifier of an imported one that belong to another metatype. */
	public List<ContentOwner> owners(UUID mappingId, UUID metatypeId) {
		return this.jdbc.query("""
				SELECT t.scope, t.source_package_id FROM metatype_property_mappings m
				JOIN metatypes t ON t.id = m.metatype_id
				WHERE m.id = ? AND m.metatype_id <> ?""",
				(rs, n) -> new ContentOwner(rs.getString(1), rs.getString(2)), mappingId, metatypeId);
	}

	/** The package mappings of the metatype as comparable strings. */
	public List<String> signature(UUID metatypeId) {
		return this.jdbc.query("""
				SELECT m.id || '|' || m.property_def_id || '|' || m.necessity || '|' || m.valid_from || '|' || m.sort_order
				FROM metatype_property_mappings m WHERE m.metatype_id = ? AND m.scope <> 'organization' ORDER BY m.id""",
				(rs, n) -> rs.getString(1), metatypeId);
	}

	/**
	 * Removes the mappings of a package from the metatype and returns when each was applied
	 * ({@code null} if not yet), so that a re-import keeps that state.
	 */
	public Map<UUID, Instant> deleteImported(UUID metatypeId) {
		Map<UUID, Instant> applied = new HashMap<>();
		this.jdbc.query("""
				DELETE FROM metatype_property_mappings WHERE metatype_id = ? AND scope <> 'organization'
				RETURNING id, effective_applied_at""", (rs) -> {
			Timestamp at = rs.getTimestamp(2);
			applied.put(rs.getObject(1, UUID.class), (at != null) ? at.toInstant() : null);
		}, metatypeId);
		return applied;
	}

	/** Inserts an imported entry; on a date an organization entry has, that entry wins. Returns the rows inserted. */
	public int insertImported(UUID id, UUID metatypeId, UUID propertyDefId, String necessity, LocalDate validFrom,
			int sortOrder, Instant appliedAt) {
		return this.jdbc.update("""
				INSERT INTO metatype_property_mappings (id, metatype_id, property_def_id, necessity, valid_from,
				    sort_order, scope, effective_applied_at) VALUES (?, ?, ?, ?, ?, ?, 'imported', ?)
				ON CONFLICT (metatype_id, property_def_id, valid_from) DO NOTHING""", id, metatypeId, propertyDefId,
				necessity, Date.valueOf(validFrom), sortOrder, (appliedAt != null) ? Timestamp.from(appliedAt) : null);
	}

	/** IDs as an SQL parameter for {@code ANY (?::uuid[])}. */
	private static String[] ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

}
