package io.oea.core.dashboard;

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
 * Dashboards and their widgets (V43, ADR-051: all SQL of the feature lives here). The names are
 * unique per scope on {@code lower(btrim(name))}; an insert or update with a taken name fails with
 * a {@link org.springframework.dao.DuplicateKeyException} on {@link #INSTANCE_NAME_INDEX} or
 * {@link #PERSONAL_NAME_INDEX}.
 */
@Repository
public class DashboardStore {

	/** Unique index of the names of shared dashboards. */
	public static final String INSTANCE_NAME_INDEX = "dashboards_instance_name";

	/** Unique index of the names of the personal dashboards of one owner. */
	public static final String PERSONAL_NAME_INDEX = "dashboards_personal_name";

	private static final String SELECT = """
			SELECT d.id, d.name, d.description, d.scope, d.owner_id, p.name, d.version, d.created_at, d.updated_at,
			       (SELECT count(*) FROM dashboard_widgets w WHERE w.dashboard_id = d.id)
			FROM dashboards d
			JOIN persons p ON p.id = d.owner_id""";

	private static final String WIDGET_SELECT = """
			SELECT id, dashboard_id, type, title, col, grid_row, width, height, config::text
			FROM dashboard_widgets""";

	private final JdbcTemplate jdbc;

	public DashboardStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A dashboard with its owner's name and its number of widgets. */
	public record StoredDashboard(UUID id, String name, String description, String scope, long ownerId,
			String ownerName, int version, Instant createdAt, Instant updatedAt, int widgetCount) {
	}

	/** A widget; {@code configJson} holds the type-specific configuration. */
	public record StoredWidget(UUID id, UUID dashboardId, String type, String title, int col, int row, int width,
			int height, String configJson) {
	}

	/** The position of a widget. */
	public record Position(UUID widgetId, int col, int row, int width, int height) {
	}

	/** Stores a dashboard at version 1 and returns its ID. */
	public UUID create(String name, String description, String scope, long personId, Instant now) {
		Timestamp at = Timestamp.from(now);
		return this.jdbc.queryForObject("""
				INSERT INTO dashboards (name, description, scope, owner_id, created_at, created_by, updated_at, updated_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, name, description, scope, personId, at,
				personId, at, personId);
	}

	/**
	 * The dashboards the person sees: the shared ones, the person's own personal ones and, with
	 * {@code all}, the personal ones of everybody; ordered by name regardless of case, then by ID.
	 */
	public List<StoredDashboard> visibleTo(long personId, boolean all) {
		return this.jdbc.query(SELECT + " WHERE (d.scope = 'instance' OR d.owner_id = ? OR ?) ORDER BY lower(d.name), d.id",
				DashboardStore::dashboard, personId, all);
	}

	/** The dashboard whatever its visibility; the caller decides what the person may see. */
	public Optional<StoredDashboard> find(UUID id) {
		return this.jdbc.query(SELECT + " WHERE d.id = ?", DashboardStore::dashboard, id).stream().findFirst();
	}

	/** The dashboard as {@link #find}, locked until the transaction ends. */
	public Optional<StoredDashboard> findForUpdate(UUID id) {
		return this.jdbc.query(SELECT + " WHERE d.id = ? FOR UPDATE OF d", DashboardStore::dashboard, id)
			.stream()
			.findFirst();
	}

	/** Changes name, description and scope and raises the version. */
	public void update(UUID id, String name, String description, String scope, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE dashboards SET name = ?, description = ?, scope = ?, version = version + 1, updated_at = ?,
				    updated_by = ? WHERE id = ?""", name, description, scope, Timestamp.from(now), personId, id);
	}

	/** Raises the version after a change of the widgets or their layout. */
	public void touch(UUID id, long personId, Instant now) {
		this.jdbc.update("UPDATE dashboards SET version = version + 1, updated_at = ?, updated_by = ? WHERE id = ?",
				Timestamp.from(now), personId, id);
	}

	/** Deletes the dashboard; its widgets are deleted with it. */
	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM dashboards WHERE id = ?", id);
	}

	/** The widgets of the dashboard by row, column and creation. */
	public List<StoredWidget> widgets(UUID dashboardId) {
		return this.jdbc.query(WIDGET_SELECT + " WHERE dashboard_id = ? ORDER BY grid_row, col, created_at, id",
				DashboardStore::widget, dashboardId);
	}

	/** Adds a widget and returns its ID. */
	public UUID addWidget(UUID dashboardId, String type, String title, int col, int row, int width, int height,
			String configJson, long personId, Instant now) {
		return this.jdbc.queryForObject("""
				INSERT INTO dashboard_widgets (dashboard_id, type, title, col, grid_row, width, height, config,
				                               created_at, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id""", UUID.class, dashboardId, type, title, col,
				row, width, height, configJson, Timestamp.from(now), personId);
	}

	/** Replaces title, position and configuration of a widget of the dashboard. */
	public void updateWidget(UUID dashboardId, UUID widgetId, String title, int col, int row, int width, int height,
			String configJson) {
		this.jdbc.update("""
				UPDATE dashboard_widgets SET title = ?, col = ?, grid_row = ?, width = ?, height = ?, config = ?::jsonb
				WHERE id = ? AND dashboard_id = ?""", title, col, row, width, height, configJson, widgetId, dashboardId);
	}

	/** Removes a widget of the dashboard. */
	public void removeWidget(UUID dashboardId, UUID widgetId) {
		this.jdbc.update("DELETE FROM dashboard_widgets WHERE id = ? AND dashboard_id = ?", widgetId, dashboardId);
	}

	/** Sets the positions of widgets of the dashboard. */
	public void updatePositions(UUID dashboardId, List<Position> positions) {
		this.jdbc.batchUpdate("""
				UPDATE dashboard_widgets SET col = ?, grid_row = ?, width = ?, height = ?
				WHERE id = ? AND dashboard_id = ?""", positions, positions.size(), (statement, position) -> {
			statement.setInt(1, position.col());
			statement.setInt(2, position.row());
			statement.setInt(3, position.width());
			statement.setInt(4, position.height());
			statement.setObject(5, position.widgetId());
			statement.setObject(6, dashboardId);
		});
	}

	private static StoredDashboard dashboard(ResultSet rs, int rowNum) throws SQLException {
		return new StoredDashboard(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getLong(5), rs.getString(6), rs.getInt(7), rs.getTimestamp(8).toInstant(),
				rs.getTimestamp(9).toInstant(), rs.getInt(10));
	}

	private static StoredWidget widget(ResultSet rs, int rowNum) throws SQLException {
		return new StoredWidget(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
				rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9));
	}

}
