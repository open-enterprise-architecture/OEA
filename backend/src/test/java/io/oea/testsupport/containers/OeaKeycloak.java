package io.oea.testsupport.containers;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * JVM-wide Keycloak singleton with the development realm {@code dev/keycloak/realm-oea-dev.json}
 * (ADR-036 §4-§5). Tests obtain tokens only through real flows, see
 * {@link io.oea.testsupport.auth.OidcLogin}.
 */
public final class OeaKeycloak {

	/** quay.io/keycloak/keycloak:26.8.1, pinned by digest (REQ-191); same image as compose.dev.yaml. */
	public static final DockerImageName IMAGE = DockerImageName
		.parse("quay.io/keycloak/keycloak@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244");

	public static final String REALM = "oea-dev";

	public static final Path REALM_FILE = Path.of("..", "dev", "keycloak", "realm-oea-dev.json");

	private static final GenericContainer<?> CONTAINER = new GenericContainer<>(IMAGE)
		.withCommand("start-dev", "--import-realm")
		.withEnv("KC_HEALTH_ENABLED", "true")
		.withCopyFileToContainer(MountableFile.forHostPath(REALM_FILE, 0644),
				"/opt/keycloak/data/import/realm-oea-dev.json")
		.withExposedPorts(8080, 9000)
		.waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));

	private OeaKeycloak() {
	}

	public static synchronized GenericContainer<?> started() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return CONTAINER;
	}

	/** Issuer of the development realm as seen from the test JVM. */
	public static String issuer() {
		GenericContainer<?> container = started();
		return "http://" + container.getHost() + ":" + container.getMappedPort(8080) + "/realms/" + REALM;
	}

	/** Registers the realm as the first OIDC provider (design D4). */
	public static void registerIssuer(DynamicPropertyRegistry registry) {
		registry.add("oea.security.oidc.providers[0].issuer", OeaKeycloak::issuer);
	}

}
