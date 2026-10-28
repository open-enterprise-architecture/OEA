package io.oea.api;

import io.oea.api.entity.EntityBatchController;
import io.oea.api.generated.model.ValidationError;
import io.oea.app.access.AccessConflictException;
import io.oea.app.access.InvalidAccessRequestException;
import io.oea.app.access.SelfAdministrationException;
import io.oea.app.catalog.InvalidCatalogException;
import io.oea.app.catalog.InvalidCatalogQueryException;
import io.oea.app.entity.AtomicBatchFailedException;
import io.oea.app.entity.BatchRejectedException;
import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.InvalidTotpCodeException;
import io.oea.app.login.SecondFactorRequiredException;
import io.oea.app.metamodel.InvalidPackageException;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.ReauthenticationRequiredException;
import io.oea.app.security.SetupPhase;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.web.RequestSizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

/** RFC 9457 problems for rejections shared by several controllers (problem types of the contract). */
@RestControllerAdvice
public class ApiExceptionHandler {

	// optional: web slice tests load this advice without the application services
	private final ObjectProvider<PackageImporter> packageImporter;

	// optional like the package importer: audits denials (REQ-175 AC1)
	private final ObjectProvider<PermissionDenials> denials;

	public ApiExceptionHandler(ObjectProvider<PackageImporter> packageImporter,
			ObjectProvider<PermissionDenials> denials) {
		this.packageImporter = packageImporter;
		this.denials = denials;
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
	ResponseEntity<ProblemDetail> secondFactorForAccess(SetupPhase.SecondFactorRequiredForAccessException ex,
			HandlerMethod handler) {
		recordDenial(handler, null, PermissionDenials.SECOND_FACTOR_REQUIRED);
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
		List<FieldError> errors = ex.getBindingResult().getFieldErrors();
		ContractViolationResponse response = declared(request);
		if (response == null) {
			ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request",
					"Some fields do not meet the contract.");
			problem.setProperty("invalidFields", fields(errors.stream().map(FieldError::getField)));
			return ResponseEntity.badRequest().body(problem);
		}
		return switch (response.shape()) {
			case INVALID_FIELDS -> invalidFields(response, fields(errors.stream().map(FieldError::getField)));
			case BATCH_ERRORS -> {
				// REQ-154: a batch that violates the contract (e.g. above 500 entities) is rejected as a whole
				ProblemDetail problem = declaredProblem(response, "validation-failed");
				problem.setProperty("errors", errors.stream()
					.map((error) -> new ValidationError(error.getField(),
							(error.getDefaultMessage() != null) ? error.getDefaultMessage() : "invalid")
						.code("CONTRACT_VIOLATION"))
					.toList());
				yield ResponseEntity.status(response.status()).body(problem);
			}
			case PACKAGE -> {
				ProblemDetail problem = declaredProblem(response, "invalid-package");
				if (!errors.isEmpty()) {
					problem.setProperty("pointer",
							"/" + errors.get(0).getField().replaceAll("\\[(\\d+)]", "/$1").replace('.', '/'));
				}
				yield packageRejected(response, problem);
			}
		};
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> malformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
		if (NestedExceptionUtils
			.getMostSpecificCause(ex) instanceof RequestSizeLimitFilter.RequestTooLargeException tooLarge) {
			// a body without declared length that exceeded the limit while it was read (REQ-186)
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(problem(HttpStatus.CONTENT_TOO_LARGE, tooLarge.getProblemType(), tooLarge.getTitle(),
						"The request body is larger than the configured limit."));
		}
		ContractViolationResponse response = declared(request);
		if (response != null && response.shape() == ContractViolationResponse.Shape.INVALID_FIELDS) {
			// e.g. an unknown enum value: the field is taken from the JSON path
			DatabindException databind = cause(ex, DatabindException.class);
			return invalidFields(response, (databind != null) ? databind.getPath()
				.stream()
				.map(DatabindException.Reference::getPropertyName)
				.filter((name) -> name != null)
				.limit(1)
				.toList() : List.of());
		}
		if (response != null && response.shape() == ContractViolationResponse.Shape.PACKAGE) {
			// REQ-033: a faulty package is answered with the location of the problem
			ProblemDetail problem = declaredProblem(response, "invalid-package");
			JacksonException jackson = cause(ex, JacksonException.class);
			if (jackson != null && jackson.getLocation() != null) {
				problem.setProperty("location",
						Map.of("line", jackson.getLocation().getLineNr(), "column", jackson.getLocation().getColumnNr()));
				problem.setDetail(jackson.getOriginalMessage());
			}
			return packageRejected(response, problem);
		}
		return invalidRequest();
	}

	/** A parameter outside its contract range, validated before the handler runs. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ProblemDetail> invalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
		ContractViolationResponse response = declared(request);
		if (response == null || response.shape() == ContractViolationResponse.Shape.BATCH_ERRORS) {
			return invalidRequest();
		}
		if (response.shape() == ContractViolationResponse.Shape.PACKAGE) {
			return packageRejected(response, declaredProblem(response, "invalid-package"));
		}
		// the rejected parameters by their contract names
		return invalidFields(response, fields(ex.getParameterValidationResults().stream().map((result) -> {
			RequestParam param = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
			return (param != null) ? param.value() : result.getMethodParameter().getParameterName();
		})));
	}

	/** A parameter outside its contract range, e.g. {@code pageSize=201} (method validation). */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
		ContractViolationResponse response = declared(request);
		if (response == null || response.shape() != ContractViolationResponse.Shape.INVALID_FIELDS) {
			return invalidRequest();
		}
		return invalidFields(response, fields(ex.getConstraintViolations().stream().map((violation) -> {
			String name = null;
			for (Path.Node node : violation.getPropertyPath()) {
				name = node.getName();
			}
			return name;
		})));
	}

	/** A parameter that is not a number, e.g. {@code page=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		ContractViolationResponse response = declared(request);
		if (response == null || response.shape() != ContractViolationResponse.Shape.INVALID_FIELDS) {
			return invalidRequest();
		}
		return invalidFields(response, List.of(ex.getName()));
	}

	@ExceptionHandler(InvalidCatalogQueryException.class)
	ResponseEntity<ProblemDetail> invalidCatalogQuery(InvalidCatalogQueryException ex) {
		return catalogQueryProblem(ex.getInvalidFields());
	}

	@ExceptionHandler(InvalidCatalogException.class)
	ResponseEntity<ProblemDetail> invalidCatalog(InvalidCatalogException ex) {
		return ex.isNameTaken()
				? catalogProblem("catalog-name-taken", "Catalog name taken", ex.getMessage(), ex.getInvalidFields())
				: catalogProblem("validation-failed", "Invalid catalog", ex.getMessage(), ex.getInvalidFields());
	}

	/** A permission required by a contract condition is missing: the filter's generic 403 (REQ-158). */
	@ExceptionHandler(PermissionDeniedException.class)
	ResponseEntity<ProblemDetail> permissionDenied(PermissionDeniedException ex, HandlerMethod handler,
			HttpServletRequest request) {
		recordDenial(handler, targetId(request), PermissionDenials.CONDITION);
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		problem.setType(URI.create("urn:oea:problem:forbidden"));
		problem.setTitle("Forbidden");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
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

	private static ResponseEntity<ProblemDetail> catalogProblem(String type, String title, String detail,
			List<String> invalidFields) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, type, title, detail);
		problem.setProperty("invalidFields", invalidFields);
		return ResponseEntity.unprocessableContent().body(problem);
	}

	private static ResponseEntity<ProblemDetail> catalogQueryProblem(List<String> invalidFields) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid query",
				"The paging or sort parameters are invalid.");
		problem.setProperty("invalidFields", invalidFields);
		return ResponseEntity.badRequest().body(problem);
	}

	/** The response declared by the handler method that the request was mapped to, or {@code null}. */
	private static ContractViolationResponse declared(HttpServletRequest request) {
		return (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)
				? handler.getMethodAnnotation(ContractViolationResponse.class) : null;
	}

	private static ProblemDetail declaredProblem(ContractViolationResponse response, String type) {
		return problem(HttpStatus.valueOf(response.status()), type, response.title(), response.detail());
	}

	private static ResponseEntity<ProblemDetail> invalidFields(ContractViolationResponse response, List<String> fields) {
		ProblemDetail problem = declaredProblem(response, "validation-failed");
		problem.setProperty("invalidFields", fields);
		return ResponseEntity.status(response.status()).body(problem);
	}

	private ResponseEntity<ProblemDetail> packageRejected(ContractViolationResponse response, ProblemDetail problem) {
		this.packageImporter.ifAvailable((importer) -> importer.recordRejected(currentPrincipal(), "invalid-package"));
		return ResponseEntity.status(response.status()).body(problem);
	}

	private static ResponseEntity<ProblemDetail> invalidRequest() {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request", "The request is invalid."));
	}

	private static List<String> fields(Stream<String> names) {
		return names.filter((name) -> name != null).distinct().sorted().toList();
	}

	private static <T extends Throwable> T cause(Throwable ex, Class<T> type) {
		Throwable cause = ex;
		while (cause != null && !type.isInstance(cause)) {
			cause = cause.getCause();
		}
		return type.cast(cause);
	}

	private void recordDenial(HandlerMethod handler, String targetId, String reason) {
		OeaPrincipal principal = currentPrincipal();
		PermissionDenials recorder = this.denials.getIfAvailable();
		if (principal != null && recorder != null) {
			recorder.record(principal, handler.getMethod().getName(), null, targetId, reason);
		}
	}

	/** The target of the request: the path variable {@code id}, or the only path variable. */
	private static String targetId(HttpServletRequest request) {
		if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)) {
			return null;
		}
		Object id = variables.containsKey("id") ? variables.get("id")
				: (variables.size() == 1) ? variables.values().iterator().next() : null;
		return (id instanceof String value) ? value : null;
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
