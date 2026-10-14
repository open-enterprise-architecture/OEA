package io.oea.config.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings of the migration step. The owner credentials are used only by
 * {@code oea migrate}, never by the running application (ADR-037).
 *
 * @param url JDBC URL of the OEA database
 * @param owner credentials of {@code oea_owner} ({@code db/migration}, {@code db/migration-audit})
 * @param reportingOwner credentials of {@code oea_reporting_owner} ({@code db/migration-reporting})
 */
@ConfigurationProperties("oea.migration")
public record MigrationProperties(String url, Credentials owner, Credentials reportingOwner) {

	public record Credentials(String username, String password) {
	}

	Credentials credentialsOf(MigrationSet.OwnerRole role) {
		return (role == MigrationSet.OwnerRole.OWNER) ? this.owner : this.reportingOwner;
	}

}
