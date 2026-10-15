package io.oea.core.domain.person;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A human account or a service account (business object person). */
@Entity
@Table(name = "persons")
public class Person {

	public enum Status {

		INVITED, ACTIVE, INACTIVE, OFFBOARDED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "person_type", nullable = false, updatable = false)
	private String personType;

	@Column(nullable = false)
	private String name;

	private String email;

	@Column(nullable = false)
	private String status;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "status_changed_by_admin_id")
	private UUID statusChangedByAdminId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Person() {
	}

	/** A human account in status {@code invited} (pre-provisioning). */
	public static Person invited(String name, String email, UUID byAdminId, Instant now) {
		Person person = new Person();
		person.personType = "human";
		person.name = name;
		person.email = email;
		person.status = "invited";
		person.statusChangedAt = now;
		person.statusChangedByAdminId = byAdminId;
		person.createdAt = now;
		return person;
	}

	/** First successful login of an invited person (REQ-007); returns true if the status changed. */
	public boolean activateOnFirstLogin(Instant now) {
		if (!"invited".equals(this.status)) {
			return false;
		}
		this.status = "active";
		this.statusChangedAt = now;
		this.statusChangedByAdminId = null;
		return true;
	}

	/** Persons in status {@code invited} or {@code active} may log in (REQ-004). */
	public boolean mayLogIn() {
		return "invited".equals(this.status) || "active".equals(this.status);
	}

	public Long getId() {
		return this.id;
	}

	public String getPersonType() {
		return this.personType;
	}

	public String getName() {
		return this.name;
	}

	public String getEmail() {
		return this.email;
	}

	public Status getStatus() {
		return Status.valueOf(this.status.toUpperCase(Locale.ROOT));
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
