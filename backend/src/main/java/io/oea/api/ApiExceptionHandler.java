package io.oea.api;

import io.oea.api.generated.model.ValidationError;
import io.oea.api.identity.OAuthErrorResponse;
import io.oea.api.identity.OAuthRequestException;
import io.oea.app.login.AccessTokens;
import io.oea.app.access.AccessConflictException;
import io.oea.app.access.InvalidServiceAccountException;
import io.oea.app.access.InvalidStatusTransitionException;
import io.oea.app.access.LastAdminPathException;
import io.oea.app.access.LastSecondFactorException;
import io.oea.app.access.PasswordAlreadySetException;
import io.oea.app.access.PasswordPolicyUnsatisfiableException;
import io.oea.app.access.PasswordPolicyViolationException;
import io.oea.app.access.PasswordResets;
import io.oea.app.access.SelfAdministrationException;
import io.oea.app.access.SodConflictException;
import io.oea.app.catalog.CatalogQueryTimeoutException;
import io.oea.app.catalog.InvalidCatalogException;
import io.oea.app.catalog.InvalidCatalogQueryException;
import io.oea.app.dashboard.InvalidDashboardException;
import io.oea.app.deletion.InvalidDeletedEntityQueryException;
import io.oea.app.diagram.DiagramChangeException;
import io.oea.app.diagramview.DiagramViewException;
import io.oea.app.entity.AtomicBatchFailedException;
import io.oea.app.entity.BatchRejectedException;
import io.oea.app.entity.ConnectionRejectedException;
import io.oea.app.hierarchy.InvalidHierarchyException;
import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.InvalidPasskeyException;
import io.oea.app.login.InvalidTotpCodeException;
import io.oea.app.login.PasskeysUnavailableException;
import io.oea.app.login.PasswordChange;
import io.oea.app.login.SecondFactorRequiredException;
import io.oea.app.metamodel.InvalidPackageException;
import io.oea.app.metamodel.MetamodelChangeException;
import io.oea.app.metamodel.MetamodelChanges;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.PackageConflictException;
import io.oea.app.metamodel.SolutionDecisionRequiredException;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.navigation.InvalidBrowserQueryException;
import io.oea.app.navigation.InvalidTreeException;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.ReauthenticationRequiredException;
import io.oea.app.security.SetupPhase;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.app.solution.EntityDeletedException;
import io.oea.app.entity.ConnectionNotRestorableException;
import io.oea.app.entity.DeletedEntityException;
import io.oea.app.entity.EntityBlockedException;
import io.oea.app.entity.EntityNotDeletedException;
import io.oea.app.entity.InvalidDeletionException;
import io.oea.app.entity.EntityVersionConflictException;
import io.oea.app.solution.InvalidPlateauException;
import io.oea.app.solution.InvalidSolutionException;
import io.oea.app.solution.PlateauInUseException;
import io.oea.app.solution.PlateauNotNextException;
import io.oea.app.solution.PlateauSolutionException;
import io.oea.app.solution.InvalidSolutionQueryException;
import io.oea.app.history.InvalidHistoryQueryException;
import io.oea.app.history.InvalidRestoreSelectionException;
import io.oea.app.history.NothingToRestoreException;
import io.oea.app.history.PreconditionRequiredException;
import io.oea.app.history.StaleEntityVersionException;
import io.oea.app.solution.MergeRequiredException;
import io.oea.app.solution.SolutionCompletedException;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.web.RequestSizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

	/** RFC 6749 §5.2 errors of the token endpoint (design D3 of s12-client-credentials-token). */
	@ExceptionHandler(OAuthRequestException.class)
	ResponseEntity<Map<String, String>> oauthRequest(OAuthRequestException ex) {
		return oauthError(ex.getStatus(), ex.getError());
	}

	/** The uniform refusal of a client authentication at the token endpoint (ADR-050 rule 5). */
	@ExceptionHandler(AccessTokens.InvalidClientException.class)
	ResponseEntity<Map<String, String>> invalidClient(AccessTokens.InvalidClientException ex) {
		return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
	}

	@ExceptionHandler(AccessTokens.ThrottledException.class)
	ResponseEntity<ProblemDetail> tokenThrottled(AccessTokens.ThrottledException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(problem(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too many requests",
					"Too many failed token requests; try again later."));
	}

	/**
	 * A missing required request parameter: an RFC 6749 {@code invalid_request} at the token
	 * endpoint, a problem document everywhere else.
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<?> missingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
		if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler
				&& handler.hasMethodAnnotation(OAuthErrorResponse.class)) {
			return oauthError(HttpStatus.BAD_REQUEST, "invalid_request");
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request", ex.getMessage());
		problem.setProperty("invalidFields", List.of(ex.getParameterName()));
		return ResponseEntity.badRequest().body(problem);
	}

	private static ResponseEntity<Map<String, String>> oauthError(HttpStatus status, String error) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header(HttpHeaders.PRAGMA, "no-cache")
			.contentType(MediaType.APPLICATION_JSON);
		if (status == HttpStatus.UNAUTHORIZED) {
			response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"oea\"");
		}
		return response.body(Map.of("error", error));
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
				"Confirm the login with your authenticator app or your passkey.");
		problem.setProperty("loginTransaction", ex.getLoginTransaction());
		problem.setProperty("secondFactors", ex.getMethods());
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

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ResponseEntity<ProblemDetail> invalidStatusTransition(InvalidStatusTransitionException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "invalid-status-transition", "Status change not allowed", ex.getMessage()));
	}

	@ExceptionHandler(PasswordAlreadySetException.class)
	ResponseEntity<ProblemDetail> passwordAlreadySet(PasswordAlreadySetException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "password-already-set", "Password already set", ex.getMessage()));
	}

	@ExceptionHandler(PasswordPolicyViolationException.class)
	ResponseEntity<ProblemDetail> passwordPolicyViolation(PasswordPolicyViolationException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "password-policy-violation",
				"Password policy not met", ex.getMessage());
		problem.setProperty("invalidFields", ex.getRules());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(PasswordPolicyUnsatisfiableException.class)
	ResponseEntity<ProblemDetail> passwordPolicyUnsatisfiable(PasswordPolicyUnsatisfiableException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "password-policy-unsatisfiable", "Password policy cannot be satisfied",
					ex.getMessage()));
	}

	@ExceptionHandler(PasswordResets.AdminResetRestrictedException.class)
	ResponseEntity<ProblemDetail> adminResetRestricted(PasswordResets.AdminResetRestrictedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage()));
	}

	@ExceptionHandler(PasswordChange.NoLocalPasswordException.class)
	ResponseEntity<ProblemDetail> noLocalPassword(PasswordChange.NoLocalPasswordException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage()));
	}

	@ExceptionHandler(PasswordChange.WrongCurrentPasswordException.class)
	ResponseEntity<ProblemDetail> wrongCurrentPassword(PasswordChange.WrongCurrentPasswordException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(problem(HttpStatus.FORBIDDEN, "invalid-credentials", "Password not changed", ex.getMessage()));
	}

	@ExceptionHandler(SodConflictException.class)
	ResponseEntity<ProblemDetail> sodConflict(SodConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "sod-conflict", "Separation of duties conflict", ex.getMessage()));
	}

	@ExceptionHandler(LastAdminPathException.class)
	ResponseEntity<ProblemDetail> lastAdminPath(LastAdminPathException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "last-admin-path", "Last administration path", ex.getMessage()));
	}

	@ExceptionHandler(PasskeysUnavailableException.class)
	ResponseEntity<ProblemDetail> passkeysUnavailable(PasskeysUnavailableException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "passkeys-unavailable", "Passkeys unavailable", ex.getMessage()));
	}

	@ExceptionHandler(InvalidPasskeyException.class)
	ResponseEntity<ProblemDetail> invalidPasskey(InvalidPasskeyException ex) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "invalid-passkey", "Invalid passkey", ex.getMessage()));
	}

	@ExceptionHandler(LastSecondFactorException.class)
	ResponseEntity<ProblemDetail> lastSecondFactor(LastSecondFactorException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "last-second-factor", "Last second factor", ex.getMessage()));
	}

	@ExceptionHandler(SelfAdministrationException.class)
	ResponseEntity<ProblemDetail> selfAdministration(SelfAdministrationException ex) {
		ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
				"You cannot administer your own access, neither your own groups nor your own roles (separation of duties).");
		problem.setProperty("reason", "self-administration");
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
	}

	@ExceptionHandler(InvalidAccessRequestException.class)
	ResponseEntity<ProblemDetail> invalidAccessRequest(InvalidAccessRequestException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid request",
				ex.getMessage());
		problem.setProperty("invalidFields", ex.getFields());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(InvalidSolutionException.class)
	ResponseEntity<ProblemDetail> invalidSolution(InvalidSolutionException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid solution",
				ex.getMessage());
		problem.setProperty("invalidFields", ex.getFields());
		problem.setProperty("errors", ex.getErrors()
			.stream()
			.map((issue) -> Map.of("field", issue.field(), "code", issue.code(), "message", issue.message()))
			.toList());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	/** A plateau or plateau reference that violates a rule (design D2, D3 of us-084-plateaus). */
	@ExceptionHandler(InvalidPlateauException.class)
	ResponseEntity<ProblemDetail> invalidPlateau(InvalidPlateauException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid plateau",
				ex.getMessage());
		problem.setProperty("invalidFields", ex.getFields());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	/** A new predecessor of a plateau that solutions target (design D2 of us-084-plateaus). */
	@ExceptionHandler(PlateauInUseException.class)
	ResponseEntity<ProblemDetail> plateauInUse(PlateauInUseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "plateau-in-use", "Plateau in use", ex.getMessage()));
	}

	/** The go-live of a plateau that does not succeed the baseline (design D3 of us-086-plateau-go-live). */
	@ExceptionHandler(PlateauNotNextException.class)
	ResponseEntity<ProblemDetail> plateauNotNext(PlateauNotNextException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "plateau-not-next", "Plateau not next", ex.getMessage()));
	}

	/** The single go-live of a solution of a plateau (design D4 of us-086-plateau-go-live). */
	@ExceptionHandler(PlateauSolutionException.class)
	ResponseEntity<ProblemDetail> plateauSolution(PlateauSolutionException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "plateau-solution", "Plateau solution", ex.getMessage()));
	}

	/** A go-live with unresolved field conflicts (design D4 of us-152-solution-go-live). */
	@ExceptionHandler(MergeRequiredException.class)
	ResponseEntity<ProblemDetail> mergeRequired(MergeRequiredException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "merge-required", "Merge required", ex.getMessage());
		problem.setProperty("conflicts", ex.getConflicts().stream().map((conflict) -> {
			Map<String, Object> entry = new java.util.LinkedHashMap<>();
			entry.put("deltaId", conflict.deltaId().toString());
			entry.put("solutionId", (conflict.solutionId() != null) ? conflict.solutionId().toString() : null);
			entry.put("entityId", conflict.entityId());
			entry.put("entityName", conflict.entityName());
			entry.put("field", conflict.field());
			entry.put("label", conflict.label());
			entry.put("dataType", conflict.dataType());
			entry.put("before", conflict.before());
			entry.put("current", conflict.current());
			entry.put("after", conflict.after());
			entry.put("redacted", conflict.redacted());
			return entry;
		}).toList());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** A go-live with a modification of an entity deleted meanwhile (design D4 of us-152-solution-go-live). */
	@ExceptionHandler(EntityDeletedException.class)
	ResponseEntity<ProblemDetail> entityDeleted(EntityDeletedException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "entity-deleted", "Entity deleted", ex.getMessage());
		problem.setProperty("deltaIds", ex.getDeltaIds().stream().map(UUID::toString).toList());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** A write that needs an active entity addressed a deleted one (design D3 of s17-entity-delete). */
	@ExceptionHandler(DeletedEntityException.class)
	ResponseEntity<ProblemDetail> deletedEntity(DeletedEntityException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "entity-deleted", "Entity deleted", ex.getMessage()));
	}

	/** The undelete of an entity that is not deleted (design D3 of s17-entity-delete). */
	@ExceptionHandler(EntityNotDeletedException.class)
	ResponseEntity<ProblemDetail> entityNotDeleted(EntityNotDeletedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "entity-not-deleted", "Entity not deleted", ex.getMessage()));
	}

	/** A connection restored directly whose endpoint is deleted or whose parent changed (design D3 of s17-entity-delete). */
	@ExceptionHandler(ConnectionNotRestorableException.class)
	ResponseEntity<ProblemDetail> connectionNotRestorable(ConnectionNotRestorableException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "connection-not-restorable", "Connection not restorable",
				ex.getMessage());
		problem.setProperty("reason", ex.getReason());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** A deletion the operation does not offer (design D2 of s17-entity-delete). */
	@ExceptionHandler(InvalidDeletionException.class)
	ResponseEntity<ProblemDetail> invalidDeletion(InvalidDeletionException ex) {
		return catalogProblem("validation-failed", "Invalid deletion", ex.getMessage(), ex.getInvalidFields());
	}

	/** A metamodel change without a decision per affected solution (design D9 of us-153-open-issues). */
	@ExceptionHandler(SolutionDecisionRequiredException.class)
	ResponseEntity<ProblemDetail> solutionDecisionRequired(SolutionDecisionRequiredException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "solution-decision-required", "Solution decision required",
				ex.getMessage());
		problem.setProperty("solutions", ex.getSolutions().stream().map((solution) -> {
			Map<String, Object> shown = new LinkedHashMap<>();
			shown.put("id", solution.id().toString());
			shown.put("name", solution.name());
			shown.put("status", solution.status());
			Map<String, Object> plateau = null;
			if (solution.plateauId() != null) {
				plateau = new LinkedHashMap<>();
				plateau.put("id", solution.plateauId().toString());
				plateau.put("name", solution.plateauName());
			}
			shown.put("plateau", plateau);
			shown.put("deltaValues", solution.deltaValues());
			shown.put("extensionMetatypes", solution.extensionMetatypes());
			shown.put("properties", solution.lost().stream().map((lost) -> {
				Map<String, Object> property = new LinkedHashMap<>();
				property.put("metatypeId", lost.metatypeId().toString());
				property.put("propertyDefId", lost.propertyDefId().toString());
				property.put("label", solution.labels().getOrDefault(lost.propertyDefId(), lost.propertyDefId().toString()));
				property.put("enumValue", lost.enumValue());
				return property;
			}).toList());
			return shown;
		}).toList());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** A write of an entity with open issues of a solution (design D3 of us-153-open-issues). */
	@ExceptionHandler(EntityBlockedException.class)
	ResponseEntity<ProblemDetail> entityBlocked(EntityBlockedException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "entity-blocked", "Entity blocked", ex.getMessage());
		problem.setProperty("entities", ex.getBlocks().stream().map((block) -> {
			Map<String, Object> entity = new LinkedHashMap<>();
			entity.put("entityId", block.entityId());
			entity.put("entityName", block.entityName());
			entity.put("issues", block.issues().stream().map((issue) -> {
				Map<String, Object> shown = new LinkedHashMap<>();
				shown.put("id", issue.id().toString());
				shown.put("field", issue.field());
				shown.put("label", issue.label());
				shown.put("kind", issue.kind());
				shown.put("solutionId", issue.solutionId().toString());
				shown.put("solutionName", issue.solutionName());
				return shown;
			}).toList());
			return entity;
		}).toList());
		if (!ex.getDeltaIds().isEmpty()) {
			problem.setProperty("deltaIds", ex.getDeltaIds().stream().map(UUID::toString).toList());
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** An interactive entity write with a version that is no longer current (issue #69). */
	@ExceptionHandler(EntityVersionConflictException.class)
	ResponseEntity<ProblemDetail> entityVersionConflict(EntityVersionConflictException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "entity-version-conflict", "Entity changed meanwhile",
				ex.getMessage());
		problem.setProperty("invalidFields",
				ex.getConflicts().stream().map((conflict) -> "entityUpdates[" + conflict.index() + "]").toList());
		problem.setProperty("conflicts", ex.getConflicts().stream().map((conflict) -> {
			Map<String, Object> shown = new LinkedHashMap<>();
			shown.put("entityId", conflict.entityId());
			shown.put("currentVersion", conflict.currentVersion());
			return shown;
		}).toList());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	/** A change to a completed solution (design D4 of us-040-entity-deltas). */
	@ExceptionHandler(SolutionCompletedException.class)
	ResponseEntity<ProblemDetail> solutionCompleted(SolutionCompletedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "solution-completed", "Solution completed", ex.getMessage()));
	}

	/** A baseline query with a cursor of another query (design D3 of us-039-solution-baseline). */
	@ExceptionHandler(InvalidSolutionQueryException.class)
	ResponseEntity<ProblemDetail> invalidSolutionQuery(InvalidSolutionQueryException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid query",
				ex.getMessage());
		problem.setProperty("invalidFields", ex.getFields());
		return ResponseEntity.badRequest().body(problem);
	}

	/** A service account field outside its limits (design D3 of us-142-service-accounts). */
	@ExceptionHandler(InvalidServiceAccountException.class)
	ResponseEntity<ProblemDetail> invalidServiceAccount(InvalidServiceAccountException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid request",
				ex.getMessage());
		problem.setProperty("invalidFields", List.of(ex.getField()));
		return ResponseEntity.badRequest().body(problem);
	}

	/** A history read with a reversed period or a cursor of another query (design D2 of us-098-entity-history). */
	@ExceptionHandler(InvalidHistoryQueryException.class)
	ResponseEntity<ProblemDetail> invalidHistoryQuery(InvalidHistoryQueryException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid query",
				ex.getMessage());
		problem.setProperty("invalidFields", ex.getFields());
		return ResponseEntity.badRequest().body(problem);
	}

	/**
	 * A required {@code If-Match} that is missing (428, design D4 of us-101-entity-restore); any
	 * other missing header is an invalid request.
	 */
	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException ex) {
		if (HttpHeaders.IF_MATCH.equalsIgnoreCase(ex.getHeaderName())) {
			return preconditionRequired(
					new PreconditionRequiredException("The write requires If-Match with the version read."));
		}
		return invalidRequest();
	}

	@ExceptionHandler(PreconditionRequiredException.class)
	ResponseEntity<ProblemDetail> preconditionRequired(PreconditionRequiredException ex) {
		return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
			.body(problem(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match required",
					ex.getMessage()));
	}

	@ExceptionHandler(StaleEntityVersionException.class)
	ResponseEntity<ProblemDetail> staleEntityVersion(StaleEntityVersionException ex) {
		ProblemDetail problem = problem(HttpStatus.PRECONDITION_FAILED, "stale-version", "Changed meanwhile",
				ex.getMessage());
		problem.setProperty("currentVersion", ex.getCurrentVersion());
		return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(problem);
	}

	/** An empty or unknown selection of a partial restore (design D2 of us-104-partial-restore). */
	@ExceptionHandler(InvalidRestoreSelectionException.class)
	ResponseEntity<ProblemDetail> invalidRestoreSelection(InvalidRestoreSelectionException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid selection",
				ex.getMessage());
		problem.setProperty("invalidFields", List.of("fields"));
		return ResponseEntity.unprocessableContent().body(problem);
	}

	/**
	 * A package import rejected by conflicts (REQ-114; issue #262 API-3, design D3 of
	 * s13-issue-fixes): 409 {@code package-conflict} with the conflicts in {@code conflictList}.
	 */
	@ExceptionHandler(PackageConflictException.class)
	ResponseEntity<ProblemDetail> packageConflict(PackageConflictException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "package-conflict", "Package conflict",
				"The package conflicts with content of another package or of the organization; nothing was stored.");
		problem.setProperty("conflictList", ex.getConflicts()
			.stream()
			.map((conflict) -> Map.of("element", conflict.element(), "reason", conflict.reason()))
			.toList());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(NothingToRestoreException.class)
	ResponseEntity<ProblemDetail> nothingToRestore(NothingToRestoreException ex) {
		return ResponseEntity.unprocessableContent()
			.body(problem(HttpStatus.UNPROCESSABLE_CONTENT, "nothing-to-restore", "Nothing to restore",
					ex.getMessage()));
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
		problem.setProperty("errors", ex.getErrors().stream().map(BatchValidationErrors::validationError).toList());
		return ResponseEntity.unprocessableContent().body(problem);
	}

	@ExceptionHandler(ConnectionRejectedException.class)
	ResponseEntity<ProblemDetail> connectionRejected(ConnectionRejectedException ex) {
		HttpStatus status = ex.endpointMissing() ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_CONTENT;
		ProblemDetail problem = problem(status, ex.endpointMissing() ? "not-found" : "validation-failed",
				"Connection not written", ex.getMessage());
		problem.setProperty("errors", ex.getErrors().stream().map(BatchValidationErrors::validationError).toList());
		return ResponseEntity.status(status).body(problem);
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
			// the parameter's name; a violation of a list entry ends in a container element node
			String name = null;
			for (Path.Node node : violation.getPropertyPath()) {
				if (node.getKind() != ElementKind.CONTAINER_ELEMENT) {
					name = node.getName();
				}
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

	/** An invalid query of the list of deleted entities (design D1 of s17-deleted-entities). */
	@ExceptionHandler(InvalidDeletedEntityQueryException.class)
	ResponseEntity<ProblemDetail> invalidDeletedEntityQuery(InvalidDeletedEntityQueryException ex) {
		return catalogQueryProblem(ex.getInvalidFields());
	}

	/** A catalog query above its time limit (design D4 of s9-catalog-query-performance). */
	@ExceptionHandler(CatalogQueryTimeoutException.class)
	ResponseEntity<ProblemDetail> catalogQueryTimeout(CatalogQueryTimeoutException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(problem(HttpStatus.SERVICE_UNAVAILABLE, "query-timeout", "Query took too long",
					"The query took longer than its time limit. Narrow the filters or the joins and try again."));
	}

	@ExceptionHandler(InvalidCatalogException.class)
	ResponseEntity<ProblemDetail> invalidCatalog(InvalidCatalogException ex) {
		return ex.isNameTaken()
				? catalogProblem("catalog-name-taken", "Catalog name taken", ex.getMessage(), ex.getInvalidFields())
				: catalogProblem("validation-failed", "Invalid catalog", ex.getMessage(), ex.getInvalidFields());
	}

	/** A dashboard or widget that violates a rule, or a taken dashboard name (design D2 of us-055-dashboards). */
	@ExceptionHandler(InvalidDashboardException.class)
	ResponseEntity<ProblemDetail> invalidDashboard(InvalidDashboardException ex) {
		return ex.isNameTaken()
				? catalogProblem("dashboard-name-taken", "Dashboard name taken", ex.getMessage(), ex.getInvalidFields())
				: catalogProblem("validation-failed", "Invalid dashboard", ex.getMessage(), ex.getInvalidFields());
	}

	/** A diagram write or query that cannot be applied (design D2, D3 and D8 of us-074-diagrams). */
	@ExceptionHandler(DiagramChangeException.class)
	ResponseEntity<ProblemDetail> diagramChange(DiagramChangeException ex) {
		return switch (ex.getReason()) {
			case INVALID -> catalogProblem("validation-failed", "Invalid diagram", ex.getMessage(),
					ex.getInvalidFields());
			case NAME_TAKEN -> catalogProblem("diagram-name-taken", "Diagram name taken", ex.getMessage(),
					ex.getInvalidFields());
			case STALE_VERSION -> {
				ProblemDetail problem = problem(HttpStatus.PRECONDITION_FAILED, "stale-version", "Changed meanwhile",
						ex.getMessage());
				problem.setProperty("currentVersion", ex.getCurrentVersion());
				yield ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(problem);
			}
			case PRECONDITION_REQUIRED -> preconditionRequired(new PreconditionRequiredException(ex.getMessage()));
			case NOT_DELETED -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body(problem(HttpStatus.CONFLICT, "diagram-not-deleted", "Diagram not deleted", ex.getMessage()));
			case INVALID_QUERY -> {
				ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid query",
						ex.getMessage());
				problem.setProperty("invalidFields", ex.getInvalidFields());
				yield ResponseEntity.badRequest().body(problem);
			}
		};
	}

	/** Shapes or new entities of a diagram layout that violate a rule, or a value outside the contract (design D2, D4 of us-074-diagram-canvas). */
	@ExceptionHandler(DiagramViewException.class)
	ResponseEntity<ProblemDetail> diagramView(DiagramViewException ex) {
		boolean request = ex.getReason() == DiagramViewException.Reason.INVALID_REQUEST;
		HttpStatus status = request ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_CONTENT;
		ProblemDetail problem = problem(status, "validation-failed",
				request ? "Invalid request" : "Invalid diagram layout", ex.getMessage());
		problem.setProperty("invalidFields", ex.getInvalidFields());
		problem.setProperty("errors", ex.getIssues().stream().map(BatchValidationErrors::validationError).toList());
		return ResponseEntity.status(status).body(problem);
	}

	@ExceptionHandler(InvalidTreeException.class)
	ResponseEntity<ProblemDetail> invalidTree(InvalidTreeException ex) {
		return catalogProblem("validation-failed", "Invalid navigation tree change", ex.getMessage(),
				ex.getInvalidFields());
	}

	/** A parent change or a structuring aid that violates a rule of the hierarchy (US-135, US-136). */
	@ExceptionHandler(InvalidHierarchyException.class)
	ResponseEntity<ProblemDetail> invalidHierarchy(InvalidHierarchyException ex) {
		return catalogProblem("validation-failed", "Invalid hierarchy change", ex.getMessage(),
				ex.getInvalidFields());
	}

	@ExceptionHandler(InvalidBrowserQueryException.class)
	ResponseEntity<ProblemDetail> invalidBrowserQuery(InvalidBrowserQueryException ex) {
		return ex.getInvalidFields().isEmpty() ? invalidRequest()
				: catalogProblem("validation-failed", "Invalid browser query", ex.getMessage(),
						ex.getInvalidFields());
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

	@ExceptionHandler(MetamodelChanges.InvalidQueryException.class)
	ResponseEntity<ProblemDetail> invalidChangeQuery(MetamodelChanges.InvalidQueryException ex) {
		return invalidRequest();
	}

	@ExceptionHandler(MetamodelLabels.UnsupportedLocaleException.class)
	ResponseEntity<ProblemDetail> unsupportedLocale(MetamodelLabels.UnsupportedLocaleException ex) {
		return invalidRequest();
	}

	@ExceptionHandler(MetamodelChangeException.class)
	ResponseEntity<ProblemDetail> metamodelChange(MetamodelChangeException ex) {
		ProblemDetail problem = switch (ex.getReason()) {
			case READ_ONLY -> problem(HttpStatus.CONFLICT, "read-only-metatype", "Read-only metamodel content",
					ex.getMessage());
			case NAME_TAKEN -> problem(HttpStatus.CONFLICT, "name-taken", "Name taken", ex.getMessage());
			case IN_USE -> problem(HttpStatus.CONFLICT, "in-use", "Still in use", ex.getMessage());
			case CONFLICT -> problem(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage());
			case INVALID -> problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation-failed", "Invalid metamodel change",
					ex.getMessage());
			case STALE_VERSION -> problem(HttpStatus.PRECONDITION_FAILED, "stale-version", "Changed meanwhile",
					ex.getMessage());
			case LOCKED -> problem(HttpStatus.FORBIDDEN, "metamodel-locked", "Metamodel locked", ex.getMessage());
			case PRECONDITION_REQUIRED -> problem(HttpStatus.PRECONDITION_REQUIRED, "precondition-required",
					"If-Match required", ex.getMessage());
			case READ_ONLY_VIEWPOINT -> problem(HttpStatus.UNPROCESSABLE_CONTENT, "read-only-viewpoint",
					"Read-only viewpoint", ex.getMessage());
		};
		if (ex.getCurrentVersion() != null) {
			problem.setProperty("currentVersion", ex.getCurrentVersion());
		}
		if (!ex.getInvalidFields().isEmpty()) {
			problem.setProperty("invalidFields", ex.getInvalidFields());
		}
		if (ex.getCount() != null) {
			problem.setProperty("count", ex.getCount());
		}
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/**
	 * A method the path does not support, such as {@code PUT} or {@code DELETE} on an immutable
	 * version (US-100 AC2, AC3; design D3 of us-099-version-diff): 405 with {@code Allow}.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
		if (ex.getSupportedHttpMethods() != null) {
			response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
		}
		return response.body(problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
				"The resource does not support " + ex.getMethod() + "."));
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
