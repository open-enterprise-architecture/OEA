package io.oea.audit;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit record of {@code audit.events} (ADR-033 §3, §8). Actors are pseudonymous: only
 * their type and key, never names or e-mail addresses. Request ID and source address are
 * taken from the current request when not given.
 *
 * @param eventType stable name from docs/audit-event-catalog.md
 * @param category event category
 * @param severity severity
 * @param outcome outcome
 * @param source origin of the action
 * @param actor pseudonymous actor
 * @param targetType optional target type
 * @param targetId optional target key
 * @param details event-type-specific supplement; never secrets
 */
public record AuditEvent(String eventType, Category category, Severity severity, Outcome outcome, Source source,
		Actor actor, String targetType, String targetId, Map<String, Object> details) {

	public AuditEvent {
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(actor, "actor");
		details = (details != null) ? Map.copyOf(details) : Map.of();
	}

	public enum Category {

		CHANGE, SECURITY, ACCESS, SYSTEM;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	public enum Severity {

		INFO, WARNING, HIGH, CRITICAL;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	public enum Outcome {

		SUCCESS, FAILURE, DENIED;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	public enum Source {

		UI, API, JOB("Job"), IMPORT("Import");

		private final String dbValue;

		Source() {
			this.dbValue = name();
		}

		Source(String dbValue) {
			this.dbValue = dbValue;
		}

		String dbValue() {
			return this.dbValue;
		}

	}

	/** Pseudonymous actor (ADR-033 §3). */
	public record Actor(String type, Long personId, UUID adminId) {

		public static Actor system() {
			return new Actor("system", null, null);
		}

		public static Actor unauthenticated() {
			return new Actor("unauthenticated", null, null);
		}

		public static Actor systemAdminAccount(UUID adminId) {
			return new Actor("system-admin-account", null, Objects.requireNonNull(adminId));
		}

	}

}
