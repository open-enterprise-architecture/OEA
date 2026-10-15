package io.oea.core.domain.person;

import io.oea.core.domain.NewAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Binding of a person to the immutable pair of issuer and subject of an identity provider
 * (ADR-032 DM-4, REQ-166); never derived from an e-mail address in this change.
 */
@Entity
@Table(name = "person_identities")
public class PersonIdentity extends NewAwareEntity<UUID> {

	@Id
	private UUID id;

	@Column(name = "person_id", nullable = false, updatable = false)
	private Long personId;

	@Column(name = "auth_provider_id", nullable = false, updatable = false)
	private UUID authProviderId;

	@Column(nullable = false, updatable = false)
	private String issuer;

	@Column(nullable = false, updatable = false)
	private String subject;

	@Column(name = "link_method", nullable = false, updatable = false)
	private String linkMethod;

	@Column(name = "linked_at", nullable = false, updatable = false)
	private Instant linkedAt;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "confirmed_by_admin_id")
	private UUID confirmedByAdminId;

	@Column(name = "confirmed_by_person_id")
	private Long confirmedByPersonId;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected PersonIdentity() {
	}

	/** A binding entered by an administrator, confirmed by that administrator. */
	public static PersonIdentity adminEntered(Long personId, UUID authProviderId, String issuer, String subject,
			UUID byAdminId, Long byPersonId, Instant now) {
		PersonIdentity identity = new PersonIdentity();
		identity.id = UUID.randomUUID();
		identity.personId = personId;
		identity.authProviderId = authProviderId;
		identity.issuer = issuer;
		identity.subject = subject;
		identity.linkMethod = "admin-entered";
		identity.linkedAt = now;
		identity.confirmedAt = now;
		identity.confirmedByAdminId = byAdminId;
		identity.confirmedByPersonId = byPersonId;
		return identity;
	}

	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}

	@Override
	public UUID getId() {
		return this.id;
	}

	public Long getPersonId() {
		return this.personId;
	}

	public UUID getAuthProviderId() {
		return this.authProviderId;
	}

	public String getIssuer() {
		return this.issuer;
	}

	public String getSubject() {
		return this.subject;
	}

	public String getLinkMethod() {
		return this.linkMethod;
	}

	public Instant getLinkedAt() {
		return this.linkedAt;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

}
