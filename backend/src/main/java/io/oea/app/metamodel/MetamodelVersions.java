package io.oea.app.metamodel;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Versions of metatypes, property definitions and property categories for optimistic locking
 * (design D1, D2 of scr-030-metamodel-configuration; UC-04 A5). Callers hold the metamodel lock,
 * so a check and the following write cannot interleave with another write.
 */
@Component
public class MetamodelVersions {

	/** The versioned elements with their tables. */
	public enum Element {

		METATYPE("metatypes", "metatype", ""),
		PROPERTY_DEFINITION("property_definitions", "property definition", " AND retired_at IS NULL"),
		CATEGORY("property_categories", "property category", "");

		private final String table;

		private final String label;

		private final String condition;

		Element(String table, String label, String condition) {
			this.table = table;
			this.label = label;
			this.condition = condition;
		}

	}

	private final JdbcTemplate jdbc;

	public MetamodelVersions(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Checks that the element exists and, if a version is expected, that it is the current one.
	 *
	 * @param expected the version of {@code If-Match}, or {@code null} without the header
	 * @throws NoSuchElementException if the element does not exist (404)
	 * @throws MetamodelChangeException with {@code STALE_VERSION} if the version differs (412)
	 */
	public void check(Element element, UUID id, Long expected) {
		List<Integer> current = this.jdbc.queryForList(
				"SELECT version FROM " + element.table + " WHERE id = ?" + element.condition, Integer.class, id);
		if (current.isEmpty()) {
			throw new NoSuchElementException("no " + element.label + " " + id);
		}
		if (expected != null && expected != current.get(0).longValue()) {
			throw MetamodelChangeException.staleVersion(element.label, current.get(0));
		}
	}

	/** Increments the version of the element after a change. */
	public void increment(Element element, UUID id) {
		this.jdbc.update("UPDATE " + element.table + " SET version = version + 1 WHERE id = ?", id);
	}

}
