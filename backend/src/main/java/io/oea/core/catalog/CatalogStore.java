package io.oea.core.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Catalogs (V7, ADR-030). The name is unique on {@code lower(btrim(name))}; an insert with a
 * taken name fails with a {@link org.springframework.dao.DuplicateKeyException} on the index
 * {@link #NAME_INDEX}.
 */
@Repository
public class CatalogStore {

	/** Unique index of the catalog names. */
	public static final String NAME_INDEX = "catalogs_name";

	private static final String SELECT = """
			SELECT c.id, c.name, c.description, c.primary_metatype_id, m.name, c.default_join_mode, c.scope,
			       c.created_by, p.name, c.created_at, c.last_modified_at, c.config -> 'columns',
			       c.config -> 'joinDefinitions', c.config -> 'savedViews', c.config -> 'savedFilters'
			FROM catalogs c
			JOIN metatypes m ON m.id = c.primary_metatype_id
			JOIN persons p ON p.id = c.created_by""";

	private static final String VISIBLE = "(c.scope = 'instance' OR c.created_by = ?)";

	private final JdbcTemplate jdbc;

	public CatalogStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * A stored catalog with the current name of its primary metatype and its creator; the
	 * configuration lists as JSON arrays.
	 */
	public record StoredCatalog(UUID id, String name, String description, UUID primaryMetatypeId,
			String primaryMetatypeName, String defaultJoinMode, String scope, long createdById, String createdByName,
			Instant createdAt, Instant lastModifiedAt, String columnsJson, String joinsJson, String savedViewsJson,
			String savedFiltersJson) {
	}

	/** Fields of a new catalog. */
	public record NewCatalog(String name, String description, UUID primaryMetatypeId, String defaultJoinMode,
			String scope) {
	}

	/** Stores the catalog with empty configuration lists and returns its ID. */
	public UUID create(NewCatalog catalog, long personId, Instant now) {
		Timestamp at = Timestamp.from(now);
		return this.jdbc.queryForObject("""
				INSERT INTO catalogs (name, description, primary_metatype_id, default_join_mode, scope, created_by,
				                      created_at, last_modified_by, last_modified_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, catalog.name(), catalog.description(),
				catalog.primaryMetatypeId(), catalog.defaultJoinMode(), catalog.scope(), personId, at, personId, at);
	}

	/** Instance catalogs and the person's own personal catalogs, ordered by name. */
	public List<StoredCatalog> visibleTo(long personId) {
		return this.jdbc.query(SELECT + " WHERE " + VISIBLE + " ORDER BY lower(c.name), c.id", CatalogStore::catalog,
				personId);
	}

	/** The catalog if it exists and is visible to the person. */
	public Optional<StoredCatalog> findVisible(UUID id, long personId) {
		return this.jdbc.query(SELECT + " WHERE c.id = ? AND " + VISIBLE, CatalogStore::catalog, id, personId)
			.stream()
			.findFirst();
	}

	/** The catalog whatever its visibility; the caller decides what the person may see. */
	public Optional<StoredCatalog> find(UUID id) {
		return this.jdbc.query(SELECT + " WHERE c.id = ?", CatalogStore::catalog, id).stream().findFirst();
	}

	/** The catalog as {@link #find}, locked until the transaction ends (row lock of the catalog). */
	public Optional<StoredCatalog> findForUpdate(UUID id) {
		return this.jdbc.query(SELECT + " WHERE c.id = ? FOR UPDATE OF c", CatalogStore::catalog, id)
			.stream()
			.findFirst();
	}

	/** Replaces the configured columns (a JSON array) and records the change (US-047). */
	public void updateColumns(UUID id, String columnsJson, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE catalogs SET config = jsonb_set(config, '{columns}', ?::jsonb), last_modified_at = ?,
				    last_modified_by = ? WHERE id = ?""", columnsJson, Timestamp.from(now), personId, id);
	}

	/**
	 * Replaces the join definitions, the saved views and the saved filters (JSON arrays) and
	 * records the change (US-048); the saved views and filters change when a removed join leaves
	 * overrides or expressions behind.
	 */
	public void updateJoins(UUID id, String joinsJson, String savedViewsJson, String savedFiltersJson, long personId,
			Instant now) {
		this.jdbc.update("""
				UPDATE catalogs SET config = jsonb_set(jsonb_set(jsonb_set(config, '{joinDefinitions}', ?::jsonb),
				    '{savedViews}', ?::jsonb), '{savedFilters}', ?::jsonb), last_modified_at = ?, last_modified_by = ?
				WHERE id = ?""", joinsJson, savedViewsJson, savedFiltersJson, Timestamp.from(now), personId, id);
	}

	/** Replaces the saved filters (a JSON array) and records the change (US-052). */
	public void updateSavedFilters(UUID id, String savedFiltersJson, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE catalogs SET config = jsonb_set(config, '{savedFilters}', ?::jsonb), last_modified_at = ?,
				    last_modified_by = ? WHERE id = ?""", savedFiltersJson, Timestamp.from(now), personId, id);
	}

	/**
	 * Whether the metatype is a connection, if it exists in the metamodel configuration;
	 * empty if it does not.
	 */
	public Optional<Boolean> isConnection(UUID metatypeId, UUID configId) {
		return this.jdbc
			.queryForList("SELECT is_connection FROM metatypes WHERE id = ? AND config_id = ?", Boolean.class,
					metatypeId, configId)
			.stream()
			.findFirst();
	}

	/** Whether a metatype with this ID exists in the metamodel configuration. */
	public boolean metatypeExists(UUID metatypeId, UUID configId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND config_id = ?)", Boolean.class, metatypeId,
				configId));
	}

	private static StoredCatalog catalog(ResultSet rs, int rowNum) throws SQLException {
		return new StoredCatalog(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
				rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
				rs.getString(9), rs.getTimestamp(10).toInstant(), rs.getTimestamp(11).toInstant(), rs.getString(12),
				rs.getString(13), rs.getString(14), rs.getString(15));
	}

}
