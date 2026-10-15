package io.oea.api;

import io.oea.app.access.AccessConflictException;
import io.oea.app.access.InvalidAccessRequestException;
import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.InvalidTotpCodeException;
import io.oea.app.login.SecondFactorRequiredException;
import io.oea.app.security.ReauthenticationRequiredException;
import io.oea.app.security.SetupPhase;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** RFC 9457 problems for rejections shared by several controllers (problem types of the contract). */
@RestControllerAdvice
public class ApiExceptionHandler {

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

	@ExceptionHandler(InvalidAccessRequestException.class)
	ResponseEntity<ProblemDetail> invalidAccessRequest(InvalidAccessRequestException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid request",
				ex.getMessage());
		problem.setProperty("invalidFields", List.of(ex.getField()));
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> contractViolation(MethodArgumentNotValidException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request",
				"Some fields do not meet the contract.");
		problem.setProperty("invalidFields",
				ex.getBindingResult().getFieldErrors().stream().map(FieldError::getField).distinct().sorted().toList());
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, HandlerMethodValidationException.class })
	ResponseEntity<ProblemDetail> malformedRequest(Exception ex) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request", "The request is invalid."));
	}

	@ExceptionHandler(NoSuchElementException.class)
	ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(problem(HttpStatus.NOT_FOUND, "not-found", "Not found", "The resource does not exist."));
	}

	public static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:oea:problem:" + type));
		problem.setTitle(title);
		return problem;
	}

}
