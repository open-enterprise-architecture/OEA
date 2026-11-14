package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.ServiceAccountStore;
import io.oea.core.access.ServiceAccountStore.Account;
import io.oea.core.service.PasswordHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service accounts for external systems (US-142, US-149, REQ-155, REQ-176; design D1–D7 of
 * us-142-service-accounts): creation with a client secret shown once, roles through local role
 * assignments restricted to assignable roles (SoD-4), rotation, mandatory expiry and final
 * deactivation. No secret or hash is ever part of a return value other than the creating one, an
 * audit event or a log line.
 */
@Service
public class ServiceAccounts {

	/** Prefix of a client secret, recognized by secret scanners (design D2). */
	public static final String SECRET_PREFIX = "oea_cs_";

	/** Validity of a secret without a requested one (REQ-176). */
	public static final int DEFAULT_VALIDITY_DAYS = 90;

	/** A secret that expires within this period is marked as expiring soon (REQ-176 AC2). */
	public static final Duration EXPIRING_SOON = Duration.ofDays(14);

	public static final int NAME_LENGTH = 100;

	public static final int DESCRIPTION_LENGTH = 1000;

	public static final int MAXIMUM_ROLES = 20;

	/** The role of a service account created without roles (ADR-026). */
	public static final String DEFAULT_ROLE = "integration-writer";

	static final String SOD_4 = "sod-4";

	private static final String ROLE_ASSIGN = "role:assign";

	private static final int SECRET_BYTES = 32;

	private final ServiceAccountStore store;

	private final RoleAssignments assignments;

	private final AccessCatalog catalog;

	private final PermissionResolver permissions;

	private final PermissionDenials denials;

	private final RecentAuthentication recentAuthentication;

	private final SetupPhase setupPhase;

	private final PasswordHasher hasher;

	private final ServiceAccountProperties properties;

	private final AuditWriter audit;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	/**
	 * A service account as administrators read it, never with a secret: its client ID, the roles
	 * of its valid local assignments, and whether its active secret expires within 14 days;
	 * {@code secretExpiresAt} is null without an active secret.
	 */
	public record View(UUID clientId, long personId, String name, String description, String status,
			List<String> roles, Instant createdAt, Instant lastUsedAt, Instant rotatedAt, Instant secretExpiresAt,
			boolean secretExpiresSoon) {
	}

	/** A service account with its new plaintext secret, returned once on creation and rotation. */
	public record WithSecret(View view, String clientSecret) {
	}

	/** Input of a creation or change; {@code roles} null means the default on creation. */
	public record Request(String name, String description, List<String> roles, Integer secretValidityDays) {
	}

	public ServiceAccounts(ServiceAccountStore store, RoleAssignments assignments, AccessCatalog catalog,
			PermissionResolver permissions, PermissionDenials denials, RecentAuthentication recentAuthentication,
			SetupPhase setupPhase, PasswordHasher hasher, ServiceAccountProperties properties, AuditWriter audit,
			Clock clock) {
		this.store = store;
		this.assignments = assignments;
		this.catalog = catalog;
		this.permissions = permissions;
		this.denials = denials;
		this.recentAuthentication = recentAuthentication;
		this.setupPhase = setupPhase;
		this.hasher = hasher;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	/** All service accounts; {@code active} null for all. */
	@Transactional(readOnly = true)
	public List<View> list(Boolean active) {
		Instant now = this.clock.instant();
		return this.store.list(active, now).stream().map((account) -> view(account, now)).toList();
	}

	/**
	 * One service account.
	 * @throws NoSuchElementException for an unknown client ID
	 */
	@Transactional(readOnly = true)
	public View get(UUID clientId) {
		Instant now = this.clock.instant();
		return view(find(clientId, now), now);
	}

	/**
	 * Creates a service account with an active secret and its roles.
	 * @throws io.oea.app.security.ReauthenticationRequiredException without an authentication
	 * within the last 5 minutes
	 * @throws InvalidServiceAccountException for a field outside its limits (400)
	 * @throws InvalidAccessRequestException for an unknown or non-assignable role, or a taken
	 * name (422)
	 * @throws PermissionDeniedException for roles without {@code role:assign}
	 */
	@Transactional
	public WithSecret create(OeaPrincipal principal, Request request) {
		this.recentAuthentication.require(principal);
		String name = name(request.name());
		String description = description(request.description());
		int validity = validity(request.secretValidityDays());
		Set<String> roles = roles((request.roles() != null) ? request.roles() : List.of(DEFAULT_ROLE));
		requireRoleAssign(principal, !roles.isEmpty());
		checkAssignable(principal, roles, "createServiceAccount", null);
		// ADR-042: a service account gives another identity access
		this.setupPhase.requireSecondFactorFor("service-account", principal);
		Instant now = now();
		long personId;
		try {
			personId = this.store.insertAccount(name, description, now);
		}
		catch (DuplicateKeyException ex) {
			throw nameTaken();
		}
		UUID clientId = UUID.randomUUID();
		String secret = secret();
		Instant expiresAt = now.plus(Duration.ofDays(validity));
		this.store.insertSecret(clientId, personId, this.hasher.hash(secret), now, expiresAt, actor(principal));
		for (String role : roles) {
			this.assignments.create(principal, personId,
					new RoleAssignments.NewAssignment(role, null, null, "service account created", false));
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		details.put("name", name);
		details.put("roles", List.copyOf(roles));
		details.put("secretExpiresAt", expiresAt.toString());
		append(principal, "service-account.created", AuditEvent.Severity.HIGH, clientId, details);
		return new WithSecret(view(find(clientId, now), now), secret);
	}

	/**
	 * Changes name, description and roles of an active service account.
	 * @throws NoSuchElementException for an unknown client ID
	 * @throws AccessConflictException for an inactive service account
	 * @throws InvalidServiceAccountException for a field outside its limits (400)
	 * @throws InvalidAccessRequestException for an unknown or non-assignable role, or a taken
	 * name (422)
	 * @throws PermissionDeniedException for changed roles without {@code role:assign}
	 */
	@Transactional
	public View update(OeaPrincipal principal, UUID clientId, Request request) {
		long personId = lockActive(clientId);
		String name = name(request.name());
		String description = description(request.description());
		Set<String> requested = roles((request.roles() != null) ? request.roles() : List.of(DEFAULT_ROLE));
		List<RoleAssignments.Assignment> granted = this.assignments.list(personId, Set.of("active", "scheduled"));
		Set<String> held = new LinkedHashSet<>();
		granted.forEach((assignment) -> held.add(assignment.roleId()));
		List<String> added = requested.stream().filter((role) -> !held.contains(role)).toList();
		List<RoleAssignments.Assignment> removed = granted.stream()
			.filter((assignment) -> !requested.contains(assignment.roleId()))
			.toList();
		requireRoleAssign(principal, !added.isEmpty() || !removed.isEmpty());
		checkAssignable(principal, new LinkedHashSet<>(added), "updateServiceAccount", clientId);
		Account before = find(clientId, this.clock.instant());
		try {
			this.store.update(personId, name, description);
		}
		catch (DuplicateKeyException ex) {
			throw nameTaken();
		}
		for (RoleAssignments.Assignment assignment : removed) {
			this.assignments.revoke(principal, personId, assignment.id(), "service account roles changed");
		}
		for (String role : added) {
			this.assignments.create(principal, personId,
					new RoleAssignments.NewAssignment(role, null, null, "service account roles changed", false));
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		if (!before.name().equals(name)) {
			details.put("name", Map.of("before", before.name(), "after", name));
		}
		details.put("descriptionChanged", !Objects.equals(before.description(), description));
		details.put("rolesAdded", added);
		details.put("rolesRemoved", removed.stream().map(RoleAssignments.Assignment::roleId).toList());
		append(principal, "service-account.updated", AuditEvent.Severity.INFO, clientId, details);
		Instant now = this.clock.instant();
		return view(find(clientId, now), now);
	}

	/**
	 * Replaces the active secret by a new one with the same client ID; the previous one is
	 * invalid at once.
	 * @throws NoSuchElementException for an unknown client ID
	 * @throws AccessConflictException for an inactive service account
	 * @throws InvalidServiceAccountException for a validity outside its limits (400)
	 */
	@Transactional
	public WithSecret rotate(OeaPrincipal principal, UUID clientId, Integer secretValidityDays) {
		this.recentAuthentication.require(principal);
		long personId = lockActive(clientId);
		int validity = validity(secretValidityDays);
		Instant now = now();
		this.store.rotateActive(clientId, now, actor(principal));
		String secret = secret();
		Instant expiresAt = now.plus(Duration.ofDays(validity));
		this.store.insertSecret(clientId, personId, this.hasher.hash(secret), now, expiresAt, actor(principal));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		details.put("secretExpiresAt", expiresAt.toString());
		append(principal, "service-account.secret-rotated", AuditEvent.Severity.HIGH, clientId, details);
		return new WithSecret(view(find(clientId, now), now), secret);
	}

	/**
	 * Deactivates a service account for good: status {@code inactive}, the active secret
	 * revoked, every granted role assignment revoked.
	 * @throws NoSuchElementException for an unknown client ID
	 * @throws AccessConflictException for an inactive service account
	 */
	@Transactional
	public void deactivate(OeaPrincipal principal, UUID clientId) {
		this.recentAuthentication.require(principal);
		long personId = lockActive(clientId);
		List<String> revokedRoles = new ArrayList<>();
		for (RoleAssignments.Assignment assignment : this.assignments.list(personId, Set.of("active", "scheduled"))) {
			this.assignments.revoke(principal, personId, assignment.id(), "service account deactivated");
			revokedRoles.add(assignment.roleId());
		}
		this.store.deactivate(clientId, personId, now(), actor(principal));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		details.put("rolesRevoked", revokedRoles);
		append(principal, "service-account.deactivated", AuditEvent.Severity.HIGH, clientId, details);
	}

	private long lockActive(UUID clientId) {
		long personId = this.store.lock(clientId)
			.orElseThrow(() -> new NoSuchElementException("no service account " + clientId));
		if (!find(clientId, this.clock.instant()).active()) {
			throw new AccessConflictException("The service account is inactive; a deactivation is final.");
		}
		return personId;
	}

	private Account find(UUID clientId, Instant now) {
		return this.store.find(clientId, now)
			.orElseThrow(() -> new NoSuchElementException("no service account " + clientId));
	}

	private View view(Account account, Instant now) {
		boolean soon = account.secretExpiresAt() != null
				&& !account.secretExpiresAt().isAfter(now.plus(EXPIRING_SOON));
		return new View(account.clientId(), account.personId(), account.name(), account.description(),
				account.status(), account.roles(), account.createdAt(), account.lastUsedAt(), account.rotatedAt(),
				account.secretExpiresAt(), soon);
	}

	private static String name(String value) {
		String name = (value != null) ? value.strip() : "";
		if (name.isEmpty() || name.length() > NAME_LENGTH) {
			throw new InvalidServiceAccountException("name",
					"The name must have 1 to " + NAME_LENGTH + " characters.");
		}
		return name;
	}

	private static String description(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.length() > DESCRIPTION_LENGTH) {
			throw new InvalidServiceAccountException("description",
					"The description must have at most " + DESCRIPTION_LENGTH + " characters.");
		}
		return value;
	}

	private int validity(Integer days) {
		int maximum = this.properties.secretMaxValidityDays();
		if (days == null) {
			return Math.min(DEFAULT_VALIDITY_DAYS, maximum);
		}
		if (days < 1 || days > maximum) {
			throw new InvalidServiceAccountException("secretValidityDays",
					"The secret validity must be between 1 and " + maximum + " days.");
		}
		return days;
	}

	private static Set<String> roles(List<String> roles) {
		Set<String> unique = new LinkedHashSet<>(roles);
		if (unique.size() > MAXIMUM_ROLES) {
			throw new InvalidServiceAccountException("roles", "At most " + MAXIMUM_ROLES + " roles are allowed.");
		}
		return unique;
	}

	/**
	 * The contract condition: setting or changing roles also needs {@code role:assign}; the
	 * denial is audited by the exception handler.
	 */
	private void requireRoleAssign(OeaPrincipal principal, boolean changesRoles) {
		if (changesRoles && !this.permissions.permissions(principal).contains(ROLE_ASSIGN)) {
			throw new PermissionDeniedException();
		}
	}

	/** SoD-4 (ADR-032, REQ-176): only existing roles flagged assignable and without admin-class permissions. */
	private void checkAssignable(OeaPrincipal principal, Set<String> roles, String operation, UUID clientId) {
		for (String roleId : roles) {
			AccessCatalog.RoleEntry role;
			try {
				role = this.catalog.role(roleId);
			}
			catch (NoSuchElementException ex) {
				throw new InvalidAccessRequestException("roles", "The role " + roleId + " does not exist.");
			}
			if (!role.assignableToServiceAccounts() || role.privileged()) {
				this.denials.record(principal, operation, "service-account",
						(clientId != null) ? clientId.toString() : null, SOD_4);
				throw new InvalidAccessRequestException("roles",
						"The role " + roleId + " cannot be assigned to a service account (SoD-4).");
			}
		}
	}

	private static InvalidAccessRequestException nameTaken() {
		return new InvalidAccessRequestException("name", "A service account with this name already exists.");
	}

	private String secret() {
		byte[] bytes = new byte[SECRET_BYTES];
		this.random.nextBytes(bytes);
		return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private Instant now() {
		// PostgreSQL keeps microseconds
		return this.clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static ServiceAccountStore.Actor actor(OeaPrincipal principal) {
		return new ServiceAccountStore.Actor(principal.personId(), principal.systemAdminAccountId());
	}

	private void append(OeaPrincipal principal, String eventType, AuditEvent.Severity severity, UUID clientId,
			Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.SECURITY, severity, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "service-account", clientId.toString(), details));
	}

}
