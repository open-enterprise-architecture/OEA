package io.oea.api.setup;

import io.oea.api.generated.SetupApi;
import io.oea.api.generated.model.SetupRequest;
import io.oea.api.generated.model.SetupResult;
import io.oea.api.generated.model.SetupStatus;
import io.oea.app.bootstrapping.BootstrapCommand;
import io.oea.app.bootstrapping.BootstrapRejectedException;
import io.oea.app.bootstrapping.BootstrapResult;
import io.oea.app.bootstrapping.Bootstrapping;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local bootstrapping endpoints (UC-02, US-013). The bootstrapping service is authoritative
 * for the order of checks (token before input) and for auditing every rejection; request
 * bodies are never logged because they contain the setup token and the password.
 */
@RestController
public class SetupController implements SetupApi {

	private static final String PROBLEM_PREFIX = "urn:oea:problem:";

	private final Bootstrapping bootstrapping;

	public SetupController(Bootstrapping bootstrapping) {
		this.bootstrapping = bootstrapping;
	}

	@Override
	public ResponseEntity<SetupStatus> getSetupStatus() {
		return ResponseEntity.ok(new SetupStatus(this.bootstrapping.setupRequired()));
	}

	@Override
	public ResponseEntity<SetupResult> completeSetup(SetupRequest request) {
		BootstrapResult result = this.bootstrapping.bootstrap(toCommand(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(new SetupResult(result.loginName(), result.instanceSlug()));
	}

	/**
	 * Contract validation failed before the controller ran. The service decides instead, so
	 * that a missing or wrong token is reported (401) before input errors and every attempt
	 * is audited.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<?> contractViolation(MethodArgumentNotValidException ex) {
		if (ex.getBindingResult().getTarget() instanceof SetupRequest request) {
			try {
				return completeSetup(request);
			}
			catch (BootstrapRejectedException rejected) {
				return rejected(rejected);
			}
		}
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request", "The request is invalid."));
	}

	@ExceptionHandler(BootstrapRejectedException.class)
	ResponseEntity<ProblemDetail> rejected(BootstrapRejectedException ex) {
		return switch (ex.getReason()) {
			case INVALID_SETUP_TOKEN -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(problem(HttpStatus.UNAUTHORIZED, "invalid-setup-token", "Invalid setup token",
						"The setup token is missing, wrong or expired. Read the current token from the file inside "
								+ "the backend container."));
			case ALREADY_BOOTSTRAPPED -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body(problem(HttpStatus.CONFLICT, "already-bootstrapped", "Instance already bootstrapped",
						"A system admin account exists; repeated bootstrapping is not possible."));
			case VALIDATION_FAILED -> {
				ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid setup data",
						"Some fields do not meet the requirements.");
				problem.setProperty("invalidFields", ex.getInvalidFields());
				yield ResponseEntity.badRequest().body(problem);
			}
		};
	}

	private static BootstrapCommand toCommand(SetupRequest request) {
		return new BootstrapCommand(request.getSetupToken(), request.getLoginName(), request.getPassword(),
				request.getInstanceSlug());
	}

	private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(PROBLEM_PREFIX + type));
		problem.setTitle(title);
		return problem;
	}

}
