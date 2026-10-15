package io.oea.core.domain.auth;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps an immutable group identifier of one issuer to an authorization role (ADR-032 §7,
 * REQ-167); deleting is a soft delete.
 */
@Entity
@Table(name = "idp_group_mappings")
public class IdpGroupMapping extends NewAwareEntity<UUID> {

	@Id
	private UUID id;

	@Column(name = "auth_provider_id", nullable = false)
	private UUID authProviderId;

	@Column(nullable = false)
	private String issuer;

	@Column(name = "group_identifier", nullable = false)
	private String groupIdentifier;

	@Column(name = "role_id", nullable = false)
	private String roleId;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "created_by_admin_id", updatable = false)
	private UUID createdByAdminId;

	@Column(name = "created_by_person_id", updatable = false)
	private Long createdByPersonId;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Column(name = "updated_by_admin_id")
	private UUID updatedByAdminId;

	@Column(name = "updated_by_person_id")
	private Long updatedByPersonId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by_admin_id")
	private UUID deletedByAdminId;

	@Column(name = "deleted_by_person_id")
	private Long deletedByPersonId;

	protected IdpGroupMapping() {
	}

	public static IdpGroupMapping create(AuthProvider provider, String groupIdentifier, String roleId, boolean active,
			UUID byAdminId, Long byPersonId, Instant now) {
		IdpGroupMapping mapping = new IdpGroupMapping();
		mapping.id = UUID.randomUUID();
		mapping.authProviderId = provider.getId();
		mapping.issuer = provider.getIssuer();
		mapping.groupIdentifier = groupIdentifier;
		mapping.roleId = roleId;
		mapping.active = active;
		mapping.createdAt = now;
		mapping.createdByAdminId = byAdminId;
		mapping.createdByPersonId = byPersonId;
		return mapping;
	}

	public void change(AuthProvider provider, String groupIdentifier, String roleId, boolean active, UUID byAdminId,
			Long byPersonId, Instant now) {
		this.authProviderId = provider.getId();
		this.issuer = provider.getIssuer();
		this.groupIdentifier = groupIdentifier;
		this.roleId = roleId;
		this.active = active;
		this.updatedAt = now;
		this.updatedByAdminId = byAdminId;
		this.updatedByPersonId = byPersonId;
	}

	public void delete(UUID byAdminId, Long byPersonId, Instant now) {
		this.deletedAt = now;
		this.deletedByAdminId = byAdminId;
		this.deletedByPersonId = byPersonId;
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public UUID getAuthProviderId() {
		return this.authProviderId;
	}

	public String getIssuer() {
		return this.issuer;
	}

	public String getGroupIdentifier() {
		return this.groupIdentifier;
	}

	public String getRoleId() {
		return this.roleId;
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public UUID getCreatedByAdminId() {
		return this.createdByAdminId;
	}

	public Long getCreatedByPersonId() {
		return this.createdByPersonId;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
