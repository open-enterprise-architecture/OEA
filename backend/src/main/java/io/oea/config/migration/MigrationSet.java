package io.oea.config.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * The three Flyway migration sets of ADR-037 §3, each with its own schema history table
 * and owner role.
 */
public enum MigrationSet {

	CORE("db/migration", "public", OwnerRole.OWNER),

	AUDIT("db/migration-audit", "audit", OwnerRole.OWNER),

	REPORTING("db/migration-reporting", "reporting", OwnerRole.REPORTING_OWNER);

	/** Role that owns the schema and runs the migrations of a set. */
	public enum OwnerRole {

		OWNER, REPORTING_OWNER

	}

	private final String location;

	private final String schema;

	private final OwnerRole ownerRole;

	MigrationSet(String location, String schema, OwnerRole ownerRole) {
		this.location = location;
		this.schema = schema;
		this.ownerRole = ownerRole;
	}

	public String location() {
		return this.location;
	}

	public String schema() {
		return this.schema;
	}

	public OwnerRole ownerRole() {
		return this.ownerRole;
	}

	/**
	 * Flyway configuration of this set without a data source. Schemas are created by
	 * provisioning, never by Flyway; the history table lives in the set's schema.
	 */
	public FluentConfiguration flyway() {
		return Flyway.configure()
			.locations("classpath:" + this.location)
			.schemas(this.schema)
			.defaultSchema(this.schema)
			.table("flyway_schema_history")
			.createSchemas(false)
			.failOnMissingLocations(true);
	}

}
