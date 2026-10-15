package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.core.domain.auth.IdpGroupMapping;
import io.oea.core.repository.AuthProviderRepository;
import io.oea.core.repository.IdpGroupMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IdP group mappings (US-147 AC1, AC4, AC7; REQ-167). Group identifiers must be UUIDs, the
 * immutable group IDs of Entra ID, Authentik and Keycloak (design D10). The first mapping of
 * an instance needs the second factor of the system admin and ends the setup phase (ADR-042).
 * The self-administration rule SoD-1 follows with the OIDC login (change 3b).
 */
@Service
public class IdpGroupMappings {

	private static final Pattern UUID_FORMAT = Pattern
		.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private final IdpGroupMappingRepository mappings;

	private final AuthProviderRepository providers;

	private final SetupPhase setupPhase;

	private final AuditWriter audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	/** Who created a mapping, for {@code createdBy}. */
	public record Creator(boolean systemAdminAccount, Long personId, String displayName) {
	}

	/** A mapping as shown to administrators. */
	public record Mapping(UUID id, UUID authProviderId, String issuer, String groupIdentifier, String roleId,
			boolean active, Creator createdBy, Instant createdAt, Instant updatedAt) {
	}

	public IdpGroupMappings(IdpGroupMappingRepository mappings, AuthProviderRepository providers,
			SetupPhase setupPhase, AuditWriter audit, JdbcTemplate jdbc, Clock clock) {
		this.mappings = mappings;
		this.providers = providers;
		this.setupPhase = setupPhase;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Mapping> list(UUID authProviderId, String roleId) {
		return this.mappings.findLive(authProviderId, roleId).stream().map(this::view).toList();
	}

	private Mapping view(IdpGroupMapping mapping) {
		return new Mapping(mapping.getId(), mapping.getAuthProviderId(), mapping.getIssuer(),
				mapping.getGroupIdentifier(), mapping.getRoleId(), mapping.isActive(), creator(mapping),
				mapping.getCreatedAt(), mapping.getUpdatedAt());
	}

	private Creator creator(IdpGroupMapping mapping) {
		if (mapping.getCreatedByAdminId() != null) {
			return new Creator(true, null, this.jdbc.queryForObject(
					"SELECT login_name FROM system_admin_accounts WHERE id = ?", String.class,
					mapping.getCreatedByAdminId()));
		}
		return new Creator(false, mapping.getCreatedByPersonId(), this.jdbc
			.queryForObject("SELECT name FROM persons WHERE id = ?", String.class, mapping.getCreatedByPersonId()));
	}

	@Transactional
	public Mapping create(OeaPrincipal principal, UUID authProviderId, String groupIdentifier, String roleId,
			boolean active) {
		AuthProvider provider = validate(authProviderId, groupIdentifier, roleId);
		String identifier = groupIdentifier.strip();
		rejectSelfAdministration(principal, provider, identifier, "createIdpGroupMapping");
		if (this.mappings.existsLive(provider.getIssuer(), identifier, roleId, new UUID(0, 0))) {
			throw new AccessConflictException("This group is already mapped to the role.");
		}
		this.setupPhase.requireSecondFactorFor("idp-group-mapping", principal);
		IdpGroupMapping mapping = this.mappings.save(IdpGroupMapping.create(provider, identifier, roleId, active,
				principal.systemAdminAccountId(), principal.personId(), this.clock.instant()));
		append(principal, "idp-mapping.created", mapping);
		return view(mapping);
	}

	@Transactional
	public Mapping update(OeaPrincipal principal, UUID mappingId, UUID authProviderId, String groupIdentifier,
			String roleId, boolean active) {
		IdpGroupMapping mapping = live(mappingId);
		AuthProvider provider = validate(authProviderId, groupIdentifier, roleId);
		String identifier = groupIdentifier.strip();
		rejectSelfAdministration(principal, provider, mapping.getGroupIdentifier(), "updateIdpGroupMapping");
		rejectSelfAdministration(principal, provider, identifier, "updateIdpGroupMapping");
		if (this.mappings.existsLive(provider.getIssuer(), identifier, roleId, mappingId)) {
			throw new AccessConflictException("This group is already mapped to the role.");
		}
		mapping.change(provider, identifier, roleId, active, principal.systemAdminAccountId(), principal.personId(),
				this.clock.instant());
		append(principal, "idp-mapping.changed", mapping);
		return view(mapping);
	}

	@Transactional
	public void delete(OeaPrincipal principal, UUID mappingId) {
		IdpGroupMapping mapping = live(mappingId);
		mapping.delete(principal.systemAdminAccountId(), principal.personId(), this.clock.instant());
		append(principal, "idp-mapping.deleted", mapping);
	}

	/**
	 * SoD-1 (ADR-032, US-147 AC5): a person must not grant a role to a group of the own current
	 * groups claim. The system admin account has no IdP groups and is never affected.
	 */
	private void rejectSelfAdministration(OeaPrincipal principal, AuthProvider provider, String groupIdentifier,
			String operation) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.sessionId() == null) {
			return;
		}
		Boolean own = this.jdbc.query("""
				SELECT ? = ANY (s.idp_group_identifiers)
				FROM sessions s JOIN auth_providers p ON p.id = s.auth_provider_id
				WHERE s.id = ? AND p.issuer = ?""", (rs) -> rs.next() && rs.getBoolean(1), groupIdentifier,
				principal.sessionId(), provider.getIssuer());
		if (Boolean.TRUE.equals(own)) {
			this.audit.appendIndependently(new AuditEvent("authz.denied", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.WARNING, AuditEvent.Outcome.DENIED, AuditEvent.Source.API,
					Sessions.actor(principal), "idp-group-mapping", null,
					Map.of("reason", "self-administration", "operation", operation)));
			throw new SelfAdministrationException();
		}
	}

	private IdpGroupMapping live(UUID mappingId) {
		return this.mappings.findById(mappingId)
			.filter((mapping) -> !mapping.isDeleted())
			.orElseThrow(() -> new NoSuchElementException("no mapping " + mappingId));
	}

	private AuthProvider validate(UUID authProviderId, String groupIdentifier, String roleId) {
		AuthProvider provider = this.providers.findById(authProviderId)
			.orElseThrow(() -> new InvalidAccessRequestException("authProviderId", "The provider does not exist."));
		if (!UUID_FORMAT.matcher(groupIdentifier.strip()).matches()) {
			// REQ-167: immutable identifiers only, never display names
			throw new InvalidAccessRequestException("groupIdentifier",
					"The group identifier must be the immutable group ID (a UUID) of the provider, not a display name.");
		}
		Integer roles = this.jdbc.queryForObject(
				"SELECT count(*) FROM authorization_roles WHERE id = ? AND deleted_at IS NULL", Integer.class, roleId);
		if (roles == null || roles == 0) {
			throw new InvalidAccessRequestException("roleId", "The role does not exist.");
		}
		return provider;
	}

	private void append(OeaPrincipal principal, String eventType, IdpGroupMapping mapping) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "idp-group-mapping",
				mapping.getId().toString(),
				Map.of("authProviderId", mapping.getAuthProviderId().toString(), "groupIdentifier",
						mapping.getGroupIdentifier(), "roleId", mapping.getRoleId(), "active", mapping.isActive())));
	}

}
