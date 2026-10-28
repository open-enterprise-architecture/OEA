package io.oea.api.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import io.oea.app.bootstrapping.BootstrapCommand;
import io.oea.app.bootstrapping.BootstrapRejectedException;
import io.oea.app.bootstrapping.BootstrapRejectedException.Reason;
import io.oea.app.bootstrapping.BootstrapResult;
import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.config.ApiPathConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import io.oea.config.security.SecurityConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Controller logic only; authentication and authorization are tested end to end
@WebMvcTest(controllers = SetupController.class,
		excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfiguration.class))
@Import(ApiPathConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SetupControllerTests {

	private static final String BODY = """
			{"setupToken": "tok-123", "loginName": "admin", "password": "secret-password-42", "instanceSlug": "acme-corp"}""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private Bootstrapping bootstrapping;

	@Test
	void statusReportsWhetherSetupIsRequired() {
		given(this.bootstrapping.setupRequired()).willReturn(true);
		assertThat(this.mvc.get().uri("/api/v1/setup/status")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("{\"setupRequired\": true}");
	}

	@Test
	void successfulSetupReturns201() {
		given(this.bootstrapping.bootstrap(any(), any())).willReturn(new BootstrapResult(UUID.randomUUID(), "admin", "acme-corp"));
		assertThat(post(BODY)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isStrictlyEqualTo("{\"loginName\": \"admin\", \"instanceSlug\": \"acme-corp\"}");
		ArgumentCaptor<BootstrapCommand> command = ArgumentCaptor.forClass(BootstrapCommand.class);
		then(this.bootstrapping).should().bootstrap(command.capture(), any());
		assertThat(command.getValue().setupToken()).isEqualTo("tok-123");
		assertThat(command.getValue().password()).isEqualTo("secret-password-42");
	}

	@Test
	void rejectionsAreRfc9457Problems() {
		willThrow(new BootstrapRejectedException(Reason.INVALID_SETUP_TOKEN, List.of()))
			.given(this.bootstrapping)
			.bootstrap(any(), any());
		assertThat(post(BODY)).hasStatus(HttpStatus.UNAUTHORIZED)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:invalid-setup-token");

		willThrow(new BootstrapRejectedException(Reason.ALREADY_BOOTSTRAPPED, List.of()))
			.given(this.bootstrapping)
			.bootstrap(any(), any());
		assertThat(post(BODY)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:already-bootstrapped");

		willThrow(new BootstrapRejectedException(Reason.VALIDATION_FAILED, List.of("password")))
			.given(this.bootstrapping)
			.bootstrap(any(), any());
		assertThat(post(BODY)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isLenientlyEqualTo("{\"type\": \"urn:oea:problem:validation-failed\", \"invalidFields\": [\"password\"]}");
	}

	@Test
	void throttledAddressGets429WithRetryAfter() {
		willThrow(BootstrapRejectedException.throttled(42)).given(this.bootstrapping).bootstrap(any(), any());
		assertThat(post(BODY)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.hasHeader("Retry-After", "42")
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:too-many-requests");
	}

	@Test
	void contractViolationsAreDecidedByTheService(CapturedOutput output) {
		willThrow(new BootstrapRejectedException(Reason.INVALID_SETUP_TOKEN, List.of()))
			.given(this.bootstrapping)
			.bootstrap(any(), any());
		String shortPassword = """
				{"setupToken": "", "loginName": "Admin!", "password": "short-pw", "instanceSlug": "x"}""";
		assertThat(post(shortPassword)).hasStatus(HttpStatus.UNAUTHORIZED);
		then(this.bootstrapping).should().bootstrap(any(), any());
		assertThat(output).doesNotContain("short-pw").doesNotContain("secret-password-42");
	}

	@Test
	void requestBodiesAreNotLogged(CapturedOutput output) {
		given(this.bootstrapping.bootstrap(any(), any())).willReturn(new BootstrapResult(UUID.randomUUID(), "admin", "acme-corp"));
		post(BODY);
		assertThat(output).doesNotContain("secret-password-42").doesNotContain("tok-123");
	}

	private MvcTestResult post(String body) {
		return this.mvc.post().uri("/api/v1/setup").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

}
