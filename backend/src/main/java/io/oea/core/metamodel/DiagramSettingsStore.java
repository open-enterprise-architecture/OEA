package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The global settings of diagrams (V51): one row with the setting "Ask for a new parent" and a version
 * for optimistic locking, like {@code visibility_settings} (V24).
 */
@Repository
public class DiagramSettingsStore {

	private final JdbcTemplate jdbc;

	public DiagramSettingsStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The settings with their version. */
	public record Settings(boolean askNewParent, int version) {
	}

	/** The settings. */
	public Settings get() {
		return this.jdbc.queryForObject("SELECT ask_new_parent, version FROM diagram_settings WHERE id = 1",
				(rs, n) -> new Settings(rs.getBoolean(1), rs.getInt(2)));
	}

	/** The settings, locked for a write (FOR UPDATE). */
	public Settings lock() {
		return this.jdbc.queryForObject("SELECT ask_new_parent, version FROM diagram_settings WHERE id = 1 FOR UPDATE",
				(rs, n) -> new Settings(rs.getBoolean(1), rs.getInt(2)));
	}

	/** Sets the value and raises the version by one; the caller locked the row and checked the version. */
	public Settings update(boolean askNewParent, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE diagram_settings SET ask_new_parent = ?, version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = 1""", askNewParent, Timestamp.from(now), personId);
		return get();
	}

}
