package io.oea.core.metamodel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The composition mark of connection metatypes (design D4 of us-135-component-hierarchy): at most
 * one metatype of the instance is marked ({@code is_composition}, V42); setting the parent of an
 * entity creates its automatic connection with it.
 */
@Repository
public class CompositionStore {

	private final JdbcTemplate jdbc;

	public CompositionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The metatype marked as composition and the package it came from ({@code null} for an organization metatype). */
	public record Marked(UUID id, String packageId) {
	}

	/** The metatype marked as composition, if any. */
	public Optional<UUID> marked() {
		return markedMetatype().map(Marked::id);
	}

	/** The marked metatype with its package. */
	public Optional<Marked> markedMetatype() {
		return this.jdbc
			.query("SELECT id, source_package_id FROM metatypes WHERE is_composition",
					(rs, n) -> new Marked(rs.getObject(1, UUID.class), rs.getString(2)))
			.stream()
			.findFirst();
	}

	/** Removes the mark from the metatypes of a package; returns how many had it. */
	public int unmarkPackage(String packageId) {
		return this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE is_composition AND source_package_id = ?",
				packageId);
	}

}
