package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The imports of metamodel packages in {@code metamodel_package_imports}. */
@Repository
public class PackageImportStore {

	private final JdbcTemplate jdbc;

	public PackageImportStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The IDs of the packages that were imported at least once. */
	public List<String> importedPackageIds() {
		return this.jdbc.queryForList("SELECT DISTINCT package_id FROM metamodel_package_imports", String.class);
	}

	/** The version of the last import of each package that was imported (us-083-bpmn-data-objects D7). */
	public java.util.Map<String, String> importedVersions() {
		java.util.Map<String, String> versions = new java.util.HashMap<>();
		this.jdbc.query("""
				SELECT DISTINCT ON (package_id) package_id, package_version FROM metamodel_package_imports
				ORDER BY package_id, imported_at DESC""", (rs) -> {
			versions.put(rs.getString(1), rs.getString(2));
		});
		return versions;
	}

	public void record(String packageId, String packageVersion, String publisher, UUID batchId, Instant importedAt,
			Long personId, UUID adminId, int metatypesCreated, int metatypesUpdated) {
		this.jdbc.update("""
				INSERT INTO metamodel_package_imports (package_id, package_version, publisher, batch_id, imported_at,
				    imported_by_person_id, imported_by_admin_id, metatypes_created, metatypes_updated)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", packageId, packageVersion, publisher, batchId,
				Timestamp.from(importedAt), personId, adminId, metatypesCreated, metatypesUpdated);
	}

}
