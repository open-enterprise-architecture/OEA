package io.oea.testsupport.containers;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL test containers provisioned like a real deployment: the init script of
 * {@code deploy/postgres/initdb} creates the OEA roles and schemas (ADR-037).
 */
public final class OeaPostgres {

	/** Minimum supported PostgreSQL major version (REQ-156): postgres:15-alpine, pinned by digest (REQ-191). */
	public static final DockerImageName MINIMUM_SUPPORTED_IMAGE = DockerImageName
		.parse("postgres@sha256:f7d23353e1b15400d22ebe31189f4d314b87a4c129cc400c8c2d8d4ca127bf81")
		.asCompatibleSubstituteFor("postgres");

	/**
	 * Role passwords, generated per test JVM: they satisfy the secret guards (G4) and never
	 * appear as literals in the repository.
	 */
	public static final Map<String, String> ROLE_PASSWORDS = Stream
		.of("oea_owner", "oea_reporting_owner", "oea_app", "oea_audit_cleanup", "oea_auditor", "oea_analytics")
		.collect(Collectors.toUnmodifiableMap(Function.identity(), (role) -> UUID.randomUUID().toString()));

	private static final Path PROVISIONING_SCRIPT = Path.of("..", "deploy", "postgres", "initdb", "10-oea-roles.sh");

	private OeaPostgres() {
	}

	/** Creates a not yet started container that runs the provisioning script on first start. */
	public static PostgreSQLContainer provisioned(DockerImageName image) {
		PostgreSQLContainer container = new PostgreSQLContainer(image).withDatabaseName("oea")
			.withCopyFileToContainer(MountableFile.forHostPath(PROVISIONING_SCRIPT, 0755),
					"/docker-entrypoint-initdb.d/10-oea-roles.sh");
		ROLE_PASSWORDS.forEach((role, password) -> container.withEnv(environmentVariable(role), password));
		return container;
	}

	public static PostgreSQLContainer provisioned() {
		return provisioned(MINIMUM_SUPPORTED_IMAGE);
	}

	/** Name of the password variable of the provisioning script, e.g. {@code OEA_APP_PASSWORD}. */
	public static String environmentVariable(String role) {
		return role.toUpperCase() + "_PASSWORD";
	}

}
