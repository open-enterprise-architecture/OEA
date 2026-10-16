package io.oea.api;

import io.oea.api.entity.EntityBatchController;
import io.oea.api.generated.model.ValidationError;
import io.oea.app.access.AccessConflictException;
import io.oea.app.access.InvalidAccessRequestException;
import io.oea.app.access.SelfAdministrationException;
import io.oea.app.entity.AtomicBatchFailedException;
import io.oea.app.entity.BatchRejectedException;
import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.InvalidTotpCodeException;
import io.oea.app.login.SecondFactorRequiredException;
import io.oea.app.metamodel.InvalidPackageException;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.ReauthenticationRequiredException;
import io.oea.app.security.SetupPhase;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.web.PackageImportConfiguration;
import io.oea.config.web.PackageSizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.JacksonException;

/** RFC 9457 problems for rejections shared by several controllers (problem types of the contract). */
@RestControllerAdvice
public class ApiExceptionHandler {

	// optional: web slice tests load this advice without the application services
	private final ObjectProvider<PackageImporter> packageImporter;

	public ApiExceptionHandler(ObjectProvider<PackageImporter> packageImporter) {
		this.packageImporter = packageImporter;
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ProblemDetail> invalidCredentials(InvalidCredentialsException ex) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.UNAUTHORIZED);
		if (ex.getRetryAfterSeconds() > 0) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()));
		}
		return response.body(problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Login failed",
				"The login name or the credentials are not valid."));
	}

	@ExceptionHandler(SecondFactorRequiredException.class)
	ResponseEntity<ProblemDetail> secondFactorForLogin(SecondFactorRequiredException ex) {
		ProblemDetail problem = problem(HttpStatus.UNAUTHORIZED, "second-factor-required", "Second factor required",
				"Enter the code of your authenticator app.");
		problem.setProperty("loginTransaction", ex.getLoginTransaction());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
	}

	@ExceptionHandler(SetupPhase.SecondFactorRequiredForAccessException.class)
	ResponseEntity<ProblemDetail> secondFactorForAccess(SetupPhase.SecondFactorRequiredForAccessException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "second-factor-required", "Second factor required",
					"Enroll and confirm a second factor before other identities get access (ADR-042)."));
	}

	@ExceptionHandler(ReauthenticationRequiredException.class)
	ResponseEntity<ProblemDetail> reauthentication(ReauthenticationRequiredException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(problem(HttpStatus.UNAUTHORIZED, "reauthentication-required", "Re-authentication required",
					"Log in again; this action needs an authentication within the last 5 minutes."));
	}

	@ExceptionHandler(InvalidTotpCodeException.class)
	ResponseEntity<ProblemDetail> invalidCode(InvalidTotpCodeException ex) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "invalid-totp-code", "Invalid code", "The code is not valid."));
	}

	@ExceptionHandler(AccessConflictException.class)
	ResponseEntity<ProblemDetail> conflict(AccessConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage()));
	}

	@ExceptionHandler(SelfAdministrationException.class)
	ResponseEntity<ProblemDetail> selfAdministration(SelfAdministrationException ex) {
		ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
				"A group of your own current sign-in cannot be mapped by you (separation of duties).");
		problem.setProperty("reason", "self-administration");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
	}

	@ExceptionHandler(InvalidAccessRequestException.class)
	ResponseEntity<ProblemDetail> invalidAccessRequest(InvalidAccessRequestException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid request",
				ex.getMessage());
		problem.setProperty("invalidFields", List.of(ex.getField()));
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(BatchRejectedException.class)
	ResponseEntity<ProblemDetail> batchRejected(BatchRejectedException ex) {
		return ResponseEntity.unprocessableContent()
			.body(problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getType(), "Batch rejected", ex.getMessage()));
	}

	@ExceptionHandler(AtomicBatchFailedException.class)
	ResponseEntity<ProblemDetail> atomicBatchFailed(AtomicBatchFailedException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Batch not written",
				ex.getMessage());
		problem.setProperty("errors", ex.getErrors().stream().map(EntityBatchController::validationError).toList());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> contractViolation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		if (EntityBatchController.BATCH_PATH.equals(request.getRequestURI())) {
			// REQ-154: a batch that violates the contract (e.g. above 500 entities) is rejected as a whole
			ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Batch rejected",
					"The batch does not meet the contract; no item was processed.");
			problem.setProperty("errors", ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map((error) -> new ValidationError(error.getField(),
						(error.getDefaultMessage() != null) ? error.getDefaultMessage() : "invalid")
					.code("CONTRACT_VIOLATION"))
				.toList());
			return ResponseEntity.unprocessableContent().body(problem);
		}
		if (isPackageImport(request)) {
			FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
			ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-package", "Invalid package",
					"The package does not match the package format.");
			if (first != null) {
				problem.setProperty("pointer", "/" + first.getField().replaceAll("\\[(\\d+)]", "/$1").replace('.', '/'));
			}
			this.packageImporter.ifAvailable((importer) -> importer.recordRejected(currentPrincipal(), "invalid-package"));
			return ResponseEntity.unprocessableContent().body(problem);
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request",
				"Some fields do not meet the contract.");
		problem.setProperty("invalidFields",
				ex.getBindingResult().getFieldErrors().stream().map(FieldError::getField).distinct().sorted().toList());
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HandlerMethodValidationException.class })
	ResponseEntity<ProblemDetail> malformedRequest(Exception ex, HttpServletRequest request) {
		if (isPackageImport(request)) {
			if (NestedExceptionUtils.getMostSpecificCause(ex) instanceof PackageSizeLimitFilter.PackageTooLargeException) {
				return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
					.body(problem(HttpStatus.CONTENT_TOO_LARGE, "package-too-large", "Package too large",
							"The package is larger than the configured maximum size."));
			}
			// REQ-033: a faulty package is answered with the location of the problem
			ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-package", "Invalid package",
					"The package is not valid JSON or does not match the package format.");
			Throwable cause = ex;
			while (cause != null && !(cause instanceof JacksonException)) {
				cause = cause.getCause();
			}
			if (cause instanceof JacksonException jackson && jackson.getLocation() != null) {
				problem.setProperty("location",
						Map.of("line", jackson.getLocation().getLineNr(), "column", jackson.getLocation().getColumnNr()));
				problem.setDetail(jackson.getOriginalMessage());
			}
			this.packageImporter.ifAvailable((importer) -> importer.recordRejected(currentPrincipal(), "invalid-package"));
			return ResponseEntity.unprocessableContent().body(problem);
		}
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request", "The request is invalid."));
	}

	@ExceptionHandler(InvalidPackageException.class)
	ResponseEntity<ProblemDetail> invalidPackage(InvalidPackageException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getType(), "Invalid package",
				ex.getMessage());
		problem.setProperty("pointer", ex.getPointer());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(problem(HttpStatus.NOT_FOUND, "not-found", "Not found", "The resource does not exist."));
	}

	private static boolean isPackageImport(HttpServletRequest request) {
		return PackageImportConfiguration.IMPORT_PATH.equals(request.getRequestURI());
	}

	private static OeaPrincipal currentPrincipal() {
		return (SecurityContextHolder.getContext().getAuthentication() instanceof OeaAuthentication authentication)
				? authentication.getPrincipal() : null;
	}

	public static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:oea:problem:" + type));
		problem.setTitle(title);
		return problem;
	}

}
