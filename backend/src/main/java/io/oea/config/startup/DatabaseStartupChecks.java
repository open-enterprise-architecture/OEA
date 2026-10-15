package io.oea.config.startup;

import io.oea.config.migration.SchemaHistoryValidator;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database checks while the context starts, before the web server accepts connections
 * (design D3): PostgreSQL version (REQ-156 AC3), current schema history (ADR-037 §3) and
 * the fail-closed audit privilege check (REQ-151 AC5, ADR-033).
 */
@Component
public class DatabaseStartupChecks implements InitializingBean {

	/** Minimum supported PostgreSQL major version (REQ-156 AC3). */
	public static final int MINIMUM_POSTGRESQL_MAJOR = 15;

	private static final Logger logger = LoggerFactory.getLogger(DatabaseStartupChecks.class);

	/** Everything beyond INSERT and SELECT; MAINTAIN exists since PostgreSQL 17. */
	private static final String EXCESS_AUDIT_PRIVILEGES = """
			SELECT c.relname || ': ' || p.privilege
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			CROSS JOIN unnest(ARRAY['UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']
			                  || CASE WHEN current_setting('server_version_num')::int >= 170000
			                          THEN ARRAY['MAINTAIN'] ELSE ARRAY[]::text[] END) AS p(privilege)
			WHERE n.nspname = 'audit' AND c.relkind IN ('r', 'p', 'v', 'm')
			  AND has_table_privilege(current_user, c.oid, p.privilege)
			ORDER BY 1""";

	private static final String OWNED_AUDIT_OBJECTS = """
			SELECT 'schema audit' FROM pg_namespace
			WHERE nspname = 'audit' AND pg_has_role(current_user, nspowner, 'USAGE')
			UNION ALL
			SELECT 'audit.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE n.nspname = 'audit' AND pg_has_role(current_user, c.relowner, 'USAGE')
			UNION ALL
			SELECT 'audit.' || p.proname || '()' FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
			WHERE n.nspname = 'audit' AND pg_has_role(current_user, p.proowner, 'USAGE')
			ORDER BY 1""";

	private final DataSource dataSource;

	private final boolean development;

	public DatabaseStartupChecks(DataSource dataSource, Environment environment) {
		this.dataSource = dataSource;
		this.development = environment.acceptsProfiles(Profiles.of("dev"));
	}

	@Override
	public void afterPropertiesSet() {
		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);
		checkVersion(jdbc);
		new SchemaHistoryValidator(this.dataSource).validate();
		checkAuditPrivileges(jdbc);
	}

	private void checkVersion(JdbcTemplate jdbc) {
		Integer versionNumber = jdbc.queryForObject("SELECT current_setting('server_version_num')::int", Integer.class);
		int major = (versionNumber != null) ? versionNumber / 10000 : 0;
		if (major < MINIMUM_POSTGRESQL_MAJOR) {
			throw new StartupGuardException(List.of("PostgreSQL " + MINIMUM_POSTGRESQL_MAJOR
					+ " or later is required (REQ-156), the server runs major version " + major));
		}
	}

	private void checkAuditPrivileges(JdbcTemplate jdbc) {
		List<String> violations = new ArrayList<>();
		String role = jdbc.queryForObject("SELECT current_user", String.class);
		jdbc.queryForList(EXCESS_AUDIT_PRIVILEGES, String.class)
			.forEach((entry) -> violations.add("runtime role " + role + " holds privilege " + entry.split(": ")[1]
					+ " on audit." + entry.split(": ")[0] + "; only INSERT and SELECT are allowed (REQ-151 AC5)"));
		jdbc.queryForList(OWNED_AUDIT_OBJECTS, String.class)
			.forEach((object) -> violations.add("runtime role " + role + " owns " + object
					+ "; audit objects must be owned by the owner role (REQ-151 AC5)"));
		if (violations.isEmpty()) {
			return;
		}
		if (this.development) {
			violations.forEach((violation) -> logger.warn("Audit privilege check (relaxed in profile 'dev'): {}", violation));
			return;
		}
		throw new StartupGuardException(violations);
	}

}
