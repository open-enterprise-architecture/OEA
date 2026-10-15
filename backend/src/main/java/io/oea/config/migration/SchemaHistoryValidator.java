package io.oea.config.migration;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.api.output.ValidateResult;

/**
 * Refuses to start when the schema history of a migration set is not current. Runs with
 * the runtime data source and never changes the schema (spec: Application does not migrate).
 */
public class SchemaHistoryValidator {

	private final DataSource dataSource;

	public SchemaHistoryValidator(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void validate() {
		List<String> problems = new ArrayList<>();
		for (MigrationSet set : MigrationSet.values()) {
			ValidateResult result = set.flyway().dataSource(this.dataSource).load().validateWithResult();
			if (!result.validationSuccessful) {
				problems.add(set.location() + ": " + result.getAllErrorMessages());
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Database schema is not up to date. Run the migration step "
					+ "('oea migrate') before starting the application. " + String.join("; ", problems));
		}
	}

}
