package io.oea.config.migration;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies all migration sets, each with the credentials of its owner role. */
public class Migrator {

	private static final Logger logger = LoggerFactory.getLogger(Migrator.class);

	private final MigrationProperties properties;

	public Migrator(MigrationProperties properties) {
		this.properties = properties;
	}

	public void migrateAll() {
		checkComplete();
		for (MigrationSet set : MigrationSet.values()) {
			MigrationProperties.Credentials credentials = this.properties.credentialsOf(set.ownerRole());
			MigrateResult result = set.flyway()
				.dataSource(this.properties.url(), credentials.username(), credentials.password())
				.load()
				.migrate();
			logger.info("Migration set {} ({}): {} migration(s) applied as {}, schema version {}", set,
					set.location(), result.migrationsExecuted, credentials.username(), result.targetSchemaVersion);
		}
	}

	private void checkComplete() {
		List<String> missing = new ArrayList<>();
		if (isBlank(this.properties.url())) {
			missing.add("oea.migration.url");
		}
		for (MigrationSet.OwnerRole role : MigrationSet.OwnerRole.values()) {
			String prefix = "oea.migration." + ((role == MigrationSet.OwnerRole.OWNER) ? "owner" : "reporting-owner");
			MigrationProperties.Credentials credentials = this.properties.credentialsOf(role);
			if (credentials == null || isBlank(credentials.username())) {
				missing.add(prefix + ".username");
			}
			if (credentials == null || isBlank(credentials.password())) {
				missing.add(prefix + ".password");
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Migration step is not configured, missing: " + String.join(", ", missing));
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
