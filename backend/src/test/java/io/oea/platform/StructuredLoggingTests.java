package io.oea.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.logging.StructuredLogging;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec platform/logging (design D1, D2 of s3-structured-logging-and-icons) on own databases:
 * the JSON format, the request ID field, no secrets in any field, and plain text for an empty
 * format setting.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTests {

	private static final String PASSWORD = "structured-logging-password";

	private final HttpClient http = HttpClient.newHttpClient();

	@AfterEach
	void plainTextAgain() {
		StructuredLogging.reset();
	}

	@Test
	void jsonLogWithRequestIdAndWithoutSecrets(CapturedOutput output) throws Exception {
		OeaDatabase postgres = OeaDatabases.freshMigrated();
		Path tokenFile = Files.createTempDirectory("oea-logging").resolve("setup-token");
		int start = output.getOut().length();
		StructuredLogging.prepare();
		ConfigurableApplicationContext application = OeaApplications.start(postgres, tokenFile,
				WebApplicationType.SERVLET, "--logging.structured.format.console=ecs",
				"--oea.security.kek.current=" + OeaTestKeys.newKeyFile(), "--oea.web.public-url=https://oea.example.org");
		String token;
		String sessionCookie;
		try {
			token = Files.readString(tokenFile).strip();
			HttpResponse<String> setup = post(application, "/api/v1/setup", JsonMapper.shared()
				.createObjectNode()
				.put("setupToken", token)
				.put("loginName", "admin")
				.put("password", PASSWORD)
				.put("instanceSlug", "acme-corp")
				.toString());
			assertThat(setup.statusCode()).isEqualTo(201);
			HttpResponse<String> login = post(application, "/api/v1/auth/local/login",
					"{\"loginName\":\"admin\",\"password\":\"" + PASSWORD + "\"}");
			assertThat(login.statusCode()).isEqualTo(201);
			sessionCookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0].split("=", 2)[1];
		}
		finally {
			application.close();
		}
		String requestId = single(postgres,
				"SELECT request_id FROM audit.events WHERE event_type = 'system.bootstrapped'");
		postgres.stop();

		List<String> lines = output.getOut().substring(start).lines().filter((line) -> !line.isBlank()).toList();
		assertThat(lines).isNotEmpty().allSatisfy((line) -> {
			JsonNode json = JsonMapper.shared().readTree(line);
			assertThat(json.path("@timestamp").isString()).as(line).isTrue();
			assertThat(json.path("log").path("level").isString()).as(line).isTrue();
			assertThat(json.path("log").path("logger").isString()).as(line).isTrue();
			assertThat(json.path("message").isString()).as(line).isTrue();
		});
		assertThat(lines).anySatisfy((line) -> {
			JsonNode json = JsonMapper.shared().readTree(line);
			assertThat(json.path("message").asString()).startsWith("Bootstrapping completed");
			assertThat(json.path("requestId").asString()).isEqualTo(requestId);
		});
		String all = String.join("\n", lines);
		for (String secret : List.of(token, PASSWORD, sessionCookie)) {
			assertThat(all).doesNotContain(secret);
		}
	}

	@Test
	void emptyFormatKeepsPlainText(CapturedOutput output) throws Exception {
		OeaDatabase postgres = OeaDatabases.freshMigrated();
		int start = output.getOut().length();
		// what Compose passes for an unset OEA_LOG_FORMAT (design D2)
		StructuredLogging.prepare();
		ConfigurableApplicationContext application = OeaApplications.start(postgres,
				Files.createTempDirectory("oea-logging").resolve("setup-token"), WebApplicationType.SERVLET,
				"--logging.structured.format.console=", "--oea.security.kek.current=" + OeaTestKeys.newKeyFile(),
				"--oea.web.public-url=https://oea.example.org");
		application.close();
		postgres.stop();
		List<String> lines = output.getOut().substring(start).lines().filter((line) -> !line.isBlank()).toList();
		assertThat(lines).isNotEmpty().noneSatisfy((line) -> assertThat(line).startsWith("{"));
	}

	private HttpResponse<String> post(ConfigurableApplicationContext application, String path, String body)
			throws Exception {
		return this.http.send(HttpRequest
			.newBuilder(URI.create(
					"http://localhost:" + application.getEnvironment().getProperty("local.server.port") + path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String single(OeaDatabase postgres, String sql) throws Exception {
		try (Connection connection = OeaDatabases.asSuperuser(postgres); Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

}
