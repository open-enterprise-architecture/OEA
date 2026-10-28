package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditFloodGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Audit of permission denials as {@code authz.denied} (REQ-175 AC1, AC5; design D4 of
 * s3-throttling-and-denials), written at every authorization decision with flood
 * suppression per principal, source address and operation.
 */
@Service
public class PermissionDenials {

	/** Reason: a permission declared by the contract is missing (ADR-032 §8 L2). */
	public static final String MISSING_PERMISSION = "missing-permission";

	/** Reason: a permission required by a contract condition is missing. */
	public static final String CONDITION = "condition";

	/** Reason: the setup phase requires a confirmed second factor (ADR-042). */
	public static final String SECOND_FACTOR_REQUIRED = "second-factor-required";

	/** Reason: separation-of-duties rule SoD-1 (US-147 AC5). */
	public static final String SELF_ADMINISTRATION = "self-administration";

	private final AuditFloodGuard guard;

	public PermissionDenials(AuditFloodGuard guard) {
		this.guard = guard;
	}

	/**
	 * Records a denial.
	 * @param principal the refused principal
	 * @param operation operation ID of the request
	 * @param targetType type of the target, {@code null} if not known
	 * @param targetId ID of the target, {@code null} if the operation has none
	 * @param reason one of the reason constants
	 */
	public void record(OeaPrincipal principal, String operation, String targetType, String targetId, String reason) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("reason", reason);
		details.put("operation", operation);
		this.guard.append(new AuditEvent("authz.denied", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.DENIED, AuditEvent.Source.API, Sessions.actor(principal), targetType, targetId,
				details), operation);
	}

}
