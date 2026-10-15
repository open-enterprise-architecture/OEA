package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.core.domain.person.Person;
import io.oea.core.domain.person.PersonIdentity;
import io.oea.core.repository.AuthProviderRepository;
import io.oea.core.repository.PersonIdentityRepository;
import io.oea.core.repository.PersonRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persons and their identity bindings (design D10, ADR-032 DM-4). The first binding of an
 * instance needs the second factor of the system admin and ends the setup phase (ADR-042).
 */
@Service
public class Persons {

	private final PersonRepository persons;

	private final PersonIdentityRepository identities;

	private final AuthProviderRepository providers;

	private final SetupPhase setupPhase;

	private final AuditWriter audit;

	private final Clock clock;

	public Persons(PersonRepository persons, PersonIdentityRepository identities, AuthProviderRepository providers,
			SetupPhase setupPhase, AuditWriter audit, Clock clock) {
		this.persons = persons;
		this.identities = identities;
		this.providers = providers;
		this.setupPhase = setupPhase;
		this.audit = audit;
		this.clock = clock;
	}

	/** A person as shown to administrators. */
	public record PersonView(long id, String personType, String name, String email, String status, Instant createdAt) {

		static PersonView of(Person person) {
			return new PersonView(person.getId(), person.getPersonType(), person.getName(), person.getEmail(),
					person.getStatus().name().toLowerCase(Locale.ROOT), person.getCreatedAt());
		}

	}

	/** An identity binding as shown to administrators. */
	public record IdentityView(UUID id, UUID authProviderId, String issuer, String subject, String linkMethod,
			Instant linkedAt, Instant lastLoginAt) {

		static IdentityView of(PersonIdentity identity) {
			return new IdentityView(identity.getId(), identity.getAuthProviderId(), identity.getIssuer(),
					identity.getSubject(), identity.getLinkMethod(), identity.getLinkedAt(), identity.getLastLoginAt());
		}

	}

	@Transactional(readOnly = true)
	public List<PersonView> list() {
		return this.persons.findAllByOrderById().stream().map(PersonView::of).toList();
	}

	@Transactional(readOnly = true)
	public PersonView get(long personId) {
		return PersonView.of(find(personId));
	}

	private Person find(long personId) {
		return this.persons.findById(personId).orElseThrow(() -> new NoSuchElementException("no person " + personId));
	}

	@Transactional
	public PersonView create(OeaPrincipal principal, String name, String email) {
		String normalizedEmail = email.strip();
		if (this.persons.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new AccessConflictException("A person with this e-mail address exists.");
		}
		Person person = this.persons.save(Person.invited(name.strip(), normalizedEmail,
				principal.systemAdminAccountId(), this.clock.instant()));
		append(principal, "person.created", AuditEvent.Severity.INFO, person.getId(), Map.of("status", "invited"));
		return PersonView.of(person);
	}

	@Transactional(readOnly = true)
	public List<IdentityView> identities(long personId) {
		find(personId);
		return this.identities.findByPersonIdOrderByLinkedAt(personId).stream().map(IdentityView::of).toList();
	}

	@Transactional
	public IdentityView bind(OeaPrincipal principal, long personId, UUID authProviderId, String subject) {
		Person person = find(personId);
		AuthProvider provider = this.providers.findById(authProviderId)
			.orElseThrow(() -> new InvalidAccessRequestException("authProviderId", "The provider does not exist."));
		if (this.identities.existsByIssuerAndSubject(provider.getIssuer(), subject)) {
			throw new AccessConflictException("This identity is already bound to a person.");
		}
		this.setupPhase.requireSecondFactorFor("identity-binding", principal);
		Instant now = this.clock.instant();
		PersonIdentity identity = this.identities.save(PersonIdentity.adminEntered(person.getId(), provider.getId(),
				provider.getIssuer(), subject, principal.systemAdminAccountId(), principal.personId(), now));
		append(principal, "person.identity-bound", AuditEvent.Severity.HIGH, person.getId(),
				Map.of("identityId", identity.getId().toString(), "authProviderId", provider.getId().toString(),
						"linkMethod", identity.getLinkMethod()));
		return IdentityView.of(identity);
	}

	@Transactional
	public void unbind(OeaPrincipal principal, long personId, UUID identityId) {
		PersonIdentity identity = this.identities.findById(identityId)
			.filter((candidate) -> candidate.getPersonId() == personId)
			.orElseThrow(() -> new NoSuchElementException("no identity " + identityId));
		this.identities.delete(identity);
		append(principal, "person.identity-unbound", AuditEvent.Severity.HIGH, personId,
				Map.of("identityId", identityId.toString(), "authProviderId", identity.getAuthProviderId().toString()));
	}

	private void append(OeaPrincipal principal, String eventType, AuditEvent.Severity severity, long personId,
			Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.SECURITY, severity, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "person", Long.toString(personId), details));
	}

}
