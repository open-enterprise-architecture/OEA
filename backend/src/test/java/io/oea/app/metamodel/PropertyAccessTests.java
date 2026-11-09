package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Who may read and write a property (design D1 of us-126-role-restricted-enforcement). */
@SpringBootTest
class PropertyAccessTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final UUID PUBLIC = UUID.randomUUID();

	private static final UUID COST = UUID.randomUUID();

	private static final UUID SCOPED = UUID.randomUUID();

	private static final Map<UUID, PropertyVisibility.Visibility> VISIBILITIES = Map.of(
			PUBLIC, PropertyVisibility.Visibility.DEFAULT,
			COST, new PropertyVisibility.Visibility("role-restricted", List.of("auditor", "enterprise-architect"),
					List.of("enterprise-architect"), null),
			SCOPED, new PropertyVisibility.Visibility("connection-scoped", List.of(), List.of(), UUID.randomUUID()));

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> tempDirectory() + "/setup-token");
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private PropertyAccess access;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void publicIsReadableAndWritable() {
		PropertyAccess.Snapshot nobody = new PropertyAccess.Snapshot(VISIBILITIES, Set.of());
		assertThat(nobody.readable(PUBLIC)).isTrue();
		assertThat(nobody.writable(PUBLIC)).isTrue();
		assertThat(nobody.restricted(PUBLIC)).isFalse();
		// a property without stored visibility counts as public
		assertThat(nobody.readable(UUID.randomUUID())).isTrue();
	}

	@Test
	void roleRestrictedNeedsAMatchingRole() {
		PropertyAccess.Snapshot architect = new PropertyAccess.Snapshot(VISIBILITIES, Set.of("enterprise-architect"));
		PropertyAccess.Snapshot auditor = new PropertyAccess.Snapshot(VISIBILITIES, Set.of("auditor", "viewer"));
		PropertyAccess.Snapshot viewer = new PropertyAccess.Snapshot(VISIBILITIES, Set.of("viewer"));
		PropertyAccess.Snapshot none = new PropertyAccess.Snapshot(VISIBILITIES, Set.of());
		assertThat(architect.readable(COST)).isTrue();
		assertThat(architect.writable(COST)).isTrue();
		// a read role alone does not allow writing
		assertThat(auditor.readable(COST)).isTrue();
		assertThat(auditor.writable(COST)).isFalse();
		// one matching role suffices, none denies
		assertThat(viewer.readable(COST)).isFalse();
		assertThat(viewer.writable(COST)).isFalse();
		assertThat(none.readable(COST)).isFalse();
		assertThat(architect.restricted(COST)).isTrue();
	}

	@Test
	void connectionScopedIsNeither() {
		PropertyAccess.Snapshot architect = new PropertyAccess.Snapshot(VISIBILITIES, Set.of("enterprise-architect"));
		assertThat(architect.readable(SCOPED)).isFalse();
		assertThat(architect.writable(SCOPED)).isFalse();
		assertThat(architect.connectionScoped(SCOPED)).isTrue();
		assertThat(architect.connectionScoped(COST)).isFalse();
	}

	@Test
	void effectiveRolesAndTheCurrentVisibility() {
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'string', 'role-restricted') RETURNING id""", UUID.class, "Access " + UUID.randomUUID(),
				"p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		this.jdbc.update(
				"INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'metamodel-admin', 'read')",
				property);
		TestSession admin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("metamodel-admin");
		OeaPrincipal confirmed = this.sessions.authenticate(admin.token()).orElseThrow();
		assertThat(this.access.of(confirmed).readable(property)).isTrue();
		// a held privileged role without a second factor is not effective
		OeaPrincipal unconfirmed = new OeaPrincipal(confirmed.type(), confirmed.systemAdminAccountId(),
				confirmed.personId(), confirmed.displayName(), confirmed.sessionId(), false, confirmed.authenticatedAt());
		assertThat(this.access.of(unconfirmed).readable(property)).isFalse();
		// a changed visibility applies to the next snapshot
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'public' WHERE id = ?", property);
		assertThat(this.access.of(Set.of()).readable(property)).isTrue();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-property-access").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
