package io.oea.app.metamodel;

import io.oea.app.security.ActingPrincipal;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.PersonLinkMappingStore;
import io.oea.core.access.PersonLinkMappingStore.Account;
import io.oea.core.access.PersonLinkMappingStore.AccountValue;
import io.oea.core.access.PersonLinkMappingStore.ElementValue;
import io.oea.core.access.PersonLinkMappingStore.Mapping;
import io.oea.core.access.PersonLinkTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The account mapping and the account link derived from it (REQ-127, ADR-032 DM-7 and SoD-1;
 * design D5 of us-075-lane-assignments): a string property of the person metatype whose value,
 * compared with an attribute of the account, links the account to the element. Security
 * administration ({@code visibility:configure}) sets the mapping; the links are recomputed in the
 * transaction of every change of an input, under one advisory lock.
 */
@Service
public class PersonLinkMappings implements PersonLinkTrigger.Recompute {

	/** The account attribute {@code email}. */
	public static final String EMAIL = "email";

	/** The account attribute {@code idp-subject}. */
	public static final String IDP_SUBJECT = "idp-subject";

	private static final String EVENT_MAPPING = "visibility.person-link-mapping-changed";

	private final PersonLinkMappingStore store;

	private final AuditWriter audit;

	private final ActingPrincipal acting;

	PersonLinkMappings(PersonLinkMappingStore store, AuditWriter audit, ActingPrincipal acting) {
		this.store = store;
		this.audit = audit;
		this.acting = acting;
	}

	/** The mapping with the name of its property; all {@code null} while unset. */
	public record View(UUID propertyDefId, String propertyName, String accountAttribute) {

		static View of(Mapping mapping) {
			return new View(mapping.propertyDefId(), mapping.propertyName(), mapping.accountAttribute());
		}

	}

	/** A value that matches several elements or several accounts, so that the account has no link. */
	public record Conflict(List<Integer> entityIds, int accountCount) {
	}

	/** The links through the mapping, and the conflicts of the accounts that have none. */
	record Result(Map<Long, Integer> links, Map<Long, Conflict> conflicts) {
	}

	/** The mapping. */
	@Transactional(readOnly = true)
	public View get() {
		return View.of(this.store.mapping());
	}

	/**
	 * Sets or, with a null property, clears the mapping; the links are recomputed in the same
	 * transaction.
	 *
	 * @throws InvalidAccessRequestException naming {@code propertyDefId} for a property that is
	 *     unknown, retired, not of data type string, not mapped to the person metatype or not
	 *     {@code role-restricted}, or {@code accountAttribute} for an unknown attribute (422)
	 * @throws SelfAdministrationException when the own account would get a link (403, SoD-1)
	 */
	@Transactional
	public View set(OeaPrincipal principal, UUID propertyDefId, String accountAttribute) {
		this.store.lock();
		if (propertyDefId != null) {
			if (!EMAIL.equals(accountAttribute) && !IDP_SUBJECT.equals(accountAttribute)) {
				throw new InvalidAccessRequestException("accountAttribute",
						"The account attribute must be email or idp-subject.");
			}
			String issue = this.store.propertyIssue(propertyDefId);
			if (!issue.isEmpty()) {
				throw new InvalidAccessRequestException("propertyDefId", switch (issue) {
					case "type" -> "The mapping property must be of data type string.";
					case "mode" -> "The mapping property must have the visibility mode role-restricted.";
					case "retired" -> "The mapping property is retired.";
					default -> "The mapping property does not exist.";
				});
			}
			if (!this.store.mappedToPersonMetatype(propertyDefId)) {
				throw new InvalidAccessRequestException("propertyDefId",
						"The mapping property must be mapped to the person metatype or one of its subtypes.");
			}
		}
		Mapping old = this.store.mapping();
		String attribute = (propertyDefId != null) ? accountAttribute : null;
		if (Objects.equals(old.propertyDefId(), propertyDefId) && Objects.equals(old.accountAttribute(), attribute)) {
			return View.of(old);
		}
		this.store.setMapping(propertyDefId, attribute);
		auditMapping(principal, old, this.store.mapping(), null);
		apply(computed(this.store.mapping()), principal);
		return get();
	}

	/**
	 * Whether the property is the mapping property, which keeps its visibility mode at
	 * {@code role-restricted} (design D5).
	 */
	@Transactional(readOnly = true)
	public boolean isMappingProperty(UUID propertyDefId) {
		return propertyDefId.equals(this.store.mapping().propertyDefId());
	}

	/**
	 * Clears the mapping because its property is deleted or retired, audited with the cause; the
	 * links are removed in the same transaction.
	 */
	@Transactional
	public void propertyRemoved(OeaPrincipal principal, UUID propertyDefId, String cause) {
		Mapping old = this.store.mapping();
		if (!propertyDefId.equals(old.propertyDefId())) {
			return;
		}
		this.store.lock();
		this.store.setMapping(null, null);
		auditMapping(principal, old, this.store.mapping(), cause);
		apply(computed(this.store.mapping()), principal);
	}

	/**
	 * The conflicts of the accounts that have no link through the mapping because the value
	 * matches several elements or several accounts, by account.
	 */
	@Transactional(readOnly = true)
	public Map<Long, Conflict> conflicts() {
		Mapping mapping = this.store.mapping();
		return (mapping.propertyDefId() == null) ? Map.of() : computed(mapping).conflicts();
	}

	@Override
	@Transactional
	public void recompute(Set<UUID> properties, boolean structural) {
		Mapping mapping = this.store.mapping();
		if (mapping.propertyDefId() == null) {
			if (structural && !this.store.mappedLinks().isEmpty()) {
				this.store.lock();
				apply(new Result(Map.of(), Map.of()), this.acting.current().orElse(null));
			}
			return;
		}
		if (!structural && !properties.contains(mapping.propertyDefId())) {
			return;
		}
		this.store.lock();
		OeaPrincipal actor = this.acting.current().orElse(null);
		if (structural && !valid(mapping)) {
			// the property was unmapped, retired or lost its restriction by another path
			this.store.setMapping(null, null);
			auditMapping(actor, mapping, this.store.mapping(), "mapping-invalid");
			apply(new Result(Map.of(), Map.of()), actor);
			return;
		}
		apply(computed(mapping), actor);
	}

	private boolean valid(Mapping mapping) {
		return this.store.propertyIssue(mapping.propertyDefId()).isEmpty()
				&& this.store.mappedToPersonMetatype(mapping.propertyDefId());
	}

	private Result computed(Mapping mapping) {
		if (mapping.propertyDefId() == null) {
			return new Result(Map.of(), Map.of());
		}
		boolean email = EMAIL.equals(mapping.accountAttribute());
		List<Account> accounts = this.store.accounts();
		Map<Long, Set<String>> values = new LinkedHashMap<>();
		if (email) {
			for (Account account : accounts) {
				String value = normalize(account.email(), true);
				values.put(account.id(), (value != null) ? Set.of(value) : Set.of());
			}
		}
		else {
			for (Account account : accounts) {
				values.put(account.id(), new LinkedHashSet<>());
			}
			for (AccountValue subject : this.store.subjects()) {
				String value = normalize(subject.value(), false);
				if (value != null && values.containsKey(subject.personId())) {
					values.get(subject.personId()).add(value);
				}
			}
		}
		Map<String, List<Integer>> elements = new HashMap<>();
		for (ElementValue element : this.store.elementValues(mapping.propertyDefId())) {
			String value = normalize(element.value(), email);
			if (value != null && !element.linkedManually()) {
				elements.computeIfAbsent(value, (key) -> new ArrayList<>()).add(element.entityId());
			}
		}
		Map<Long, Integer> links = new LinkedHashMap<>();
		Map<Long, Conflict> conflicts = new LinkedHashMap<>();
		for (Account account : accounts) {
			if (account.manualEntityId() != null) {
				continue;
			}
			Set<Integer> matched = new LinkedHashSet<>();
			for (String value : values.get(account.id())) {
				matched.addAll(elements.getOrDefault(value, List.of()));
			}
			if (matched.isEmpty()) {
				continue;
			}
			Set<String> matchedValues = new HashSet<>();
			elements.forEach((value, ids) -> {
				if (ids.stream().anyMatch(matched::contains)) {
					matchedValues.add(value);
				}
			});
			Set<Long> sharing = new HashSet<>();
			for (Account other : accounts) {
				if (values.get(other.id()).stream().anyMatch(matchedValues::contains)) {
					sharing.add(other.id());
				}
			}
			if (matched.size() == 1 && sharing.size() == 1) {
				links.put(account.id(), matched.iterator().next());
			}
			else {
				conflicts.put(account.id(), new Conflict(matched.stream().sorted().toList(), sharing.size()));
			}
		}
		return new Result(links, conflicts);
	}

	private static String normalize(String value, boolean ignoreCase) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.isEmpty()) {
			return null;
		}
		return ignoreCase ? stripped.toLowerCase(Locale.ROOT) : stripped;
	}

	private void apply(Result result, OeaPrincipal actor) {
		Map<Long, Integer> current = this.store.mappedLinks();
		Map<Long, Integer> changes = new LinkedHashMap<>();
		Set<Long> ids = new LinkedHashSet<>(current.keySet());
		ids.addAll(result.links().keySet());
		for (long id : ids) {
			Integer old = current.get(id);
			Integer now = result.links().get(id);
			if (!Objects.equals(old, now)) {
				changes.put(id, now);
			}
		}
		if (changes.isEmpty()) {
			return;
		}
		if (actor != null && actor.personId() != null) {
			Integer own = changes.get(actor.personId());
			if (own != null) {
				throw new SelfLinkException(actor.personId());
			}
		}
		changes.keySet().forEach((id) -> this.store.setMappedEntity(id, null));
		changes.forEach((id, entityId) -> {
			if (entityId != null) {
				this.store.setMappedEntity(id, entityId);
			}
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("oldEntityId", (current.get(id) != null) ? current.get(id) : "");
			details.put("newEntityId", (entityId != null) ? entityId : "");
			details.put("source", "mapping");
			this.audit.append(new AuditEvent("person.account-linked", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS,
					(actor != null) ? AuditEvent.Source.API : AuditEvent.Source.JOB,
					(actor != null) ? Sessions.actor(actor) : AuditEvent.Actor.system(), "person", Long.toString(id),
					details));
		});
	}

	private void auditMapping(OeaPrincipal actor, Mapping old, Mapping now, String cause) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("oldPropertyDefId", (old.propertyDefId() != null) ? old.propertyDefId().toString() : "");
		details.put("newPropertyDefId", (now.propertyDefId() != null) ? now.propertyDefId().toString() : "");
		details.put("oldAccountAttribute", (old.accountAttribute() != null) ? old.accountAttribute() : "");
		details.put("newAccountAttribute", (now.accountAttribute() != null) ? now.accountAttribute() : "");
		if (cause != null) {
			details.put("cause", cause);
		}
		this.audit.append(new AuditEvent(EVENT_MAPPING, AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.SUCCESS, (actor != null) ? AuditEvent.Source.API : AuditEvent.Source.JOB,
				(actor != null) ? Sessions.actor(actor) : AuditEvent.Actor.system(), "settings",
				"person-link-mapping", details));
	}

}
