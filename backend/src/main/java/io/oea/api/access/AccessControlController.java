package io.oea.api.access;

import io.oea.api.generated.AccessControlApi;
import io.oea.api.generated.model.AuthorizationRole;
import io.oea.api.generated.model.IdpGroupMapping;
import io.oea.api.generated.model.IdpGroupMappingWrite;
import io.oea.api.generated.model.InitialPassword;
import io.oea.api.generated.model.InitialPasswordResult;
import io.oea.api.generated.model.MappingWarning;
import io.oea.api.generated.model.Permission;
import io.oea.api.generated.model.Person;
import io.oea.api.generated.model.PersonIdentity;
import io.oea.api.generated.model.PersonIdentityWrite;
import io.oea.api.generated.model.PersonStatusChange;
import io.oea.api.generated.model.PersonWrite;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.RoleAssignment;
import io.oea.api.generated.model.RoleAssignmentRevoke;
import io.oea.api.generated.model.RoleAssignmentWrite;
import io.oea.api.generated.model.RoleRef;
import io.oea.app.access.AccessCatalog;
import io.oea.app.access.IdpGroupMappings;
import io.oea.app.access.PersonLifecycle;
import io.oea.app.access.PersonPasswords;
import io.oea.app.access.Persons;
import io.oea.app.access.RoleAssignments;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.ContractAuthorizationRules;
import io.oea.config.security.OeaAuthentication;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission catalog and authorization roles (US-143), persons with identity bindings, IdP
 * group mappings (design D10), local role assignments (US-144) and the status of persons
 * (US-148); further operations follow (UC-23).
 */
@RestController
public class AccessControlController implements AccessControlApi {

	private final AccessCatalog catalog;

	private final ContractAuthorizationRules rules;

	private final Persons persons;

	private final IdpGroupMappings mappings;

	private final RoleAssignments assignments;

	private final PersonLifecycle lifecycle;

	private final PersonPasswords passwords;

	public AccessControlController(AccessCatalog catalog, ContractAuthorizationRules rules, Persons persons,
			IdpGroupMappings mappings, RoleAssignments assignments, PersonLifecycle lifecycle,
			PersonPasswords passwords) {
		this.catalog = catalog;
		this.rules = rules;
		this.persons = persons;
		this.mappings = mappings;
		this.assignments = assignments;
		this.lifecycle = lifecycle;
		this.passwords = passwords;
	}

	@Override
	public ResponseEntity<InitialPasswordResult> setInitialPassword(Integer personId, InitialPassword request) {
		return ResponseEntity
			.ok(new InitialPasswordResult(time(this.passwords.setInitial(principal(), personId, request.getPassword()))));
	}

	@Override
	public ResponseEntity<Person> changePersonStatus(Integer personId, PersonStatusChange request) {
		this.lifecycle.change(principal(), personId, request.getStatus().getValue(), request.getReason());
		return ResponseEntity.ok(person(this.persons.get(personId)));
	}

	@Override
	public ResponseEntity<AuthorizationRole> getAuthorizationRole(String roleId) {
		return ResponseEntity.ok(role(this.catalog.role(roleId)));
	}

	@Override
	public ResponseEntity<List<RoleAssignment>> listRoleAssignments(Integer personId, List<String> status) {
		Set<String> statuses = (status == null || status.isEmpty()) ? Set.of("scheduled", "active") : Set.copyOf(status);
		return ResponseEntity.ok(this.assignments.list(personId, statuses)
			.stream()
			.map(AccessControlController::assignment)
			.toList());
	}

	@Override
	public ResponseEntity<RoleAssignment> createRoleAssignment(Integer personId, RoleAssignmentWrite request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(assignment(this.assignments.create(principal(), personId,
					new RoleAssignments.NewAssignment(request.getRoleId(), instant(request.getValidFrom()),
							instant(request.getValidTo()), request.getReason(),
							Boolean.TRUE.equals(request.getAcknowledgeSodConflict())))));
	}

	@Override
	public ResponseEntity<RoleAssignment> revokeRoleAssignment(Integer personId, UUID assignmentId,
			RoleAssignmentRevoke request) {
		return ResponseEntity.ok(assignment(this.assignments.revoke(principal(), personId, assignmentId,
				(request != null) ? request.getReason() : null)));
	}

	@Override
	public ResponseEntity<List<Permission>> listPermissions() {
		return ResponseEntity.ok(this.catalog.permissions()
			.stream()
			.map((p) -> new Permission(p.id(), p.category(), p.adminClass(),
					this.rules.permissionDescriptions().getOrDefault(p.id(), "")))
			.toList());
	}

	@Override
	public ResponseEntity<List<AuthorizationRole>> listAuthorizationRoles(Boolean assignableToServiceAccounts) {
		return ResponseEntity.ok(this.catalog.roles(assignableToServiceAccounts)
			.stream()
			.map(AccessControlController::role)
			.toList());
	}

	@Override
	public ResponseEntity<Person> createPerson(PersonWrite request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(person(this.persons.create(principal(), request.getName(), request.getEmail())));
	}

	@Override
	public ResponseEntity<List<Person>> listPersons() {
		return ResponseEntity.ok(this.persons.list().stream().map(AccessControlController::person).toList());
	}

	@Override
	public ResponseEntity<Person> getPerson(Integer personId) {
		return ResponseEntity.ok(person(this.persons.get(personId)));
	}

	@Override
	public ResponseEntity<List<PersonIdentity>> listPersonIdentities(Integer personId) {
		return ResponseEntity
			.ok(this.persons.identities(personId).stream().map(AccessControlController::identity).toList());
	}

	@Override
	public ResponseEntity<PersonIdentity> createPersonIdentity(Integer personId, PersonIdentityWrite request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(identity(this.persons.bind(principal(), personId, request.getAuthProviderId(), request.getSubject())));
	}

	@Override
	public ResponseEntity<Void> deletePersonIdentity(Integer personId, UUID identityId) {
		this.persons.unbind(principal(), personId, identityId);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<IdpGroupMapping> createIdpGroupMapping(IdpGroupMappingWrite request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(mapping(this.mappings.create(principal(), request.getAuthProviderId(), request.getGroupIdentifier(),
					request.getRoleId(), !Boolean.FALSE.equals(request.getActive()))));
	}

	@Override
	public ResponseEntity<List<IdpGroupMapping>> listIdpGroupMappings(UUID authProviderId, String roleId) {
		return ResponseEntity.ok(this.mappings.list(authProviderId, roleId).stream().map(AccessControlController::mapping).toList());
	}

	@Override
	public ResponseEntity<IdpGroupMapping> updateIdpGroupMapping(UUID mappingId, IdpGroupMappingWrite request) {
		return ResponseEntity.ok(mapping(this.mappings.update(principal(), mappingId, request.getAuthProviderId(),
				request.getGroupIdentifier(), request.getRoleId(), !Boolean.FALSE.equals(request.getActive()))));
	}

	@Override
	public ResponseEntity<Void> deleteIdpGroupMapping(UUID mappingId) {
		this.mappings.delete(principal(), mappingId);
		return ResponseEntity.noContent().build();
	}

	private static Person person(Persons.PersonView person) {
		return new Person(Math.toIntExact(person.id()), Person.PersonTypeEnum.fromValue(person.personType()),
				person.name(), Person.StatusEnum.fromValue(person.status()), time(person.createdAt()))
			.email(person.email());
	}

	private static PersonIdentity identity(Persons.IdentityView identity) {
		return new PersonIdentity(identity.id(), identity.authProviderId(), identity.issuer(), identity.subject(),
				PersonIdentity.LinkMethodEnum.fromValue(identity.linkMethod()), time(identity.linkedAt()))
			.lastLoginAt(time(identity.lastLoginAt()));
	}

	private static IdpGroupMapping mapping(IdpGroupMappings.Mapping mapping) {
		IdpGroupMappings.Creator creator = mapping.createdBy();
		PrincipalRef createdBy = new PrincipalRef(creator.systemAdminAccount()
				? PrincipalRef.PrincipalTypeEnum.SYSTEM_ADMIN_ACCOUNT : PrincipalRef.PrincipalTypeEnum.PERSON,
				creator.displayName())
			.id((creator.personId() != null) ? Math.toIntExact(creator.personId()) : null);
		return new IdpGroupMapping(mapping.id(), mapping.authProviderId(), mapping.issuer(), mapping.groupIdentifier(),
				mapping.roleId(), mapping.active(), createdBy, time(mapping.createdAt()))
			.updatedAt(time(mapping.updatedAt()))
			.lastMatchedAt(time(mapping.lastMatchedAt()))
			.warnings(mapping.warnings().stream().map(AccessControlController::warning).toList());
	}

	private static MappingWarning warning(String code) {
		// IdpGroupMappings.GROUP_NOT_SEEN is the only code (US-018)
		return new MappingWarning(MappingWarning.CodeEnum.fromValue(code),
				"No login at this instance has presented this group yet: the mapping grants system admin access "
						+ "to nobody known until a member of the group logs in.");
	}

	private static AuthorizationRole role(AccessCatalog.RoleEntry r) {
		return new AuthorizationRole(r.id(), r.name(), r.builtin(), r.privileged(), r.assignableToServiceAccounts(),
				r.overridesPropertyVisibility(), new LinkedHashSet<>(r.permissions()))
			.description(r.description());
	}

	private static RoleAssignment assignment(RoleAssignments.Assignment a) {
		return new RoleAssignment(a.id(), Math.toIntExact(a.personId()), new RoleRef(a.roleId(), a.roleName(), a.privileged()),
				RoleAssignment.SourceEnum.LOCAL, RoleAssignment.StatusEnum.fromValue(a.status()), time(a.validFrom()),
				principalRef(a.grantedBy()), time(a.grantedAt()), a.sodConflict())
			.validTo(time(a.validTo()))
			.reason(a.reason())
			.revokedBy((a.revokedBy() != null) ? principalRef(a.revokedBy()) : null)
			.revokedAt(time(a.revokedAt()));
	}

	private static PrincipalRef principalRef(RoleAssignments.Actor actor) {
		return new PrincipalRef(actor.systemAdminAccount() ? PrincipalRef.PrincipalTypeEnum.SYSTEM_ADMIN_ACCOUNT
				: PrincipalRef.PrincipalTypeEnum.PERSON, actor.displayName())
			.id((actor.personId() != null) ? Math.toIntExact(actor.personId()) : null);
	}

	private static Instant instant(OffsetDateTime time) {
		return (time != null) ? time.toInstant() : null;
	}

	private static OffsetDateTime time(Instant instant) {
		return (instant != null) ? instant.atOffset(ZoneOffset.UTC) : null;
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
