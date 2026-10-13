---
id: UC-23
title: Manage Roles and Role Assignments
status: draft
priority: must
target_release: walking-skeleton
complexity: large
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
primary_actor: SH-06
secondary_actors:
  - SH-03
  - SH-07
  - SH-05
references:
  business_objects:
    - person
    - role
    - system-admin-account
  concept:
    - concept/70-platform/21-api-architecture.md
    - concept/20-entities/08-organization-roles-persons.md
  related_use_cases:
    - UC-01
    - UC-02
    - UC-03
    - UC-21
    - UC-22
---

# UC-23: Manage Roles and Role Assignments

## Goal in Context

OEA holds the enterprise architecture of a regulated organization, including protection needs, security classifications and personal data categories (concept §20). The operator must be able to demonstrate to auditors that access is controlled: every person and every service account has exactly the access their task requires, privileged access is separated and protected, and access ends when the task ends. UC-01 (Login) presupposes that a person holds an active authorization role; UC-02 (Bootstrapping) creates the first privileged access. This use case closes the gap between them: it is how access is granted, adjusted, reviewed and withdrawn over the lifetime of an instance.

The authorization model is defined in [ADR-032](../../adrs/ADR-032-authorization-model.md): a fixed permission catalog, built-in roles (`portal-reader`, `viewer`, `analyst`, `enterprise-architect`, `integration-writer`, `auditor`, `metamodel-admin`, `security-admin`, `system-admin`), custom roles, local assignments and IdP group mappings. Authorization roles are separate from business roles modeled as EA content (e.g. "Process Owner"); assigning a business role in the model never grants access.

## Persona and Story

**Primary Actor**: [Max – Operator in a Regulated SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md), acting as security administrator (holds `security-admin` or `system-admin`, both with `role:assign`)
**Other Stakeholders**:

- [Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md) – states which access his architects need; is himself a holder of `enterprise-architect` and `metamodel-admin`
- [Sabine – Business Engineer](../../business-analysis/stakeholders/SH-07-sabine-business-engineer.md) – needs metamodel administration without access to security configuration
- [CIO](../../business-analysis/stakeholders/SH-05-cio-corporate-group.md) – consumes curated content in the web portal (`portal-reader`)
- Identity provider (Entra ID, Authentik) – external system delivering immutable group identifiers

**Story**: As the security administrator of our OEA instance, I want to give every person and every service account exactly the authorization roles their task requires – directly or through IdP groups – and to withdraw them reliably, so that I can prove in an audit that access to the architecture repository is controlled, separated and current.

## Trigger

- External occasion: a new employee or external consultant joins the architecture team; a person changes their task; a person leaves; a new integration (service account) goes live; a periodic access review (e.g. quarterly for FINMA/DORA audits); a request from Kurt for additional access
- Timing: ad hoc for joiners/movers/leavers; periodic for access reviews
- Predecessor use case: UC-02 (first privileged access exists); UC-03 (authentication method configured)

## Preconditions

- [ ] The security administrator is logged in with a second factor (REQ-171) and holds a role with `role:assign`
- [ ] The target person exists in OEA (status `invited` or `active`), or the target service account exists (REQ-155)
- [ ] The built-in roles and the permission catalog are present (shipped with the system)
- [ ] For IdP group mappings: an authentication provider is configured (UC-03) and the group's immutable identifier is known

## Postconditions

### On Success

- The target principal holds exactly the intended authorization roles; the effective permissions are the union of these roles (no hierarchy)
- Changes are effective at the target's next request, at the latest after 60 seconds (REQ-003 AC2, REQ-164)
- When access is reduced, all sessions of the target person are revoked (REQ-164)
- Every change is recorded in the audit log with actor, target, role, source, validity and flags (`sodConflict`, privileged) (REQ-175)
- The access review shows the current state, including the source of every assignment (local, IdP group, system admin account)

### On Failure

- No assignment, role or mapping has changed; effective permissions of all principals are unchanged
- The security administrator receives a specific message (self-administration, service account restriction, last administration path, invalid role definition)
- Denied attempts are recorded as audit events (`authz.denied`)

## Basic Flow

Grant a new architect the access she needs.

1. **Max**: opens the access administration and searches for the person (e.g. a new solution architect who has logged in once and sees the notice "no role assigned", REQ-003)
2. **System**: shows the person with status, current authorization roles (none), their sources and validity
3. **Max**: selects the role `enterprise-architect` and, because the person is an external consultant, sets `validTo` to the end of the contract; optionally enters a reason (e.g. ticket number)
4. **System**: checks that Max holds `role:assign`, that the target is not Max himself (SoD-1), and that the role exists; shows the permissions the person will receive and indicates whether the role is privileged
5. **Max**: confirms the assignment
6. **System**: stores the assignment with `source = local`, `grantedBy`, `grantedAt`, `validFrom`, `validTo`; writes the audit event `role.assigned`; invalidates the authorization cache for the person
7. **System**: at the person's next request (at the latest after 60 s) the person has the permissions of `enterprise-architect`; the client shows the modeling functions
8. **Max**: opens the access review and sees the new assignment with source, validity and grantor

## Alternative Flows

**A1 – Grant access through an IdP group mapping**: At step 1, if access should follow group membership in the IdP (e.g. all members of the Entra ID group "EA-Viewers"), then:

1. Max opens the IdP group mappings and selects the authentication provider
2. Max enters the immutable group identifier (Entra group object ID, Authentik group UUID) and selects the role `viewer`
3. System validates the identifier format of the provider and rejects display names (REQ-167); checks that the group is not contained in Max's own current token (SoD-1)
4. System stores the mapping and writes `idp-mapping.created`
5. At the next login or session refresh of each group member, the role `viewer` is derived from the group claim of the configured issuer; a missing or truncated groups claim yields no derived role and a warning event. Group claims are re-evaluated at every token refresh and at the latest every 15 minutes, so removal from the group in the IdP withdraws the role within 15 minutes (owner decision Q16, REQ-165)

- Ends here

**A2 – Revoke access on offboarding**: At step 1, if a person leaves the organization, then:

1. Max sets the person's status to `offboarded` (requires `person:manage`)
2. System revokes all sessions and refresh tokens of the person within 60 s (REQ-164) and ends all local assignments with `revokedAt`/`revokedBy`
3. System writes `role.revoked` per assignment and `auth.session-revoked`
4. IdP-derived roles no longer apply because no session can be established; history and audit events of the person are retained

- Ends here

**A3 – Change of task (mover)**: At step 3, if the person changes from modeling to consumption, then:

1. Max revokes `enterprise-architect` and assigns `viewer`
2. System applies both changes; because access is reduced, all sessions of the person are revoked (REQ-164); the person logs in again with the new permissions

- Rejoins at step 8

**A4 – Assign roles to a service account**: At step 1, if a new integration (e.g. a CMDB importer, UC-22) needs write access, then:

1. Max (as `system-admin`) has created the service account with `service-account:manage` (REQ-155)
2. Max selects the service account; the system offers only roles flagged `assignableToServiceAccounts` (built-in: `viewer`, `integration-writer`, `auditor`)
3. Max assigns `integration-writer`
4. System stores the assignment and writes `role.assigned`; tokens issued for the service account from now on resolve to the permissions `entity:read` and `entity:write`

- Ends here

**A5 – Define a custom role for property visibility**: At step 3, if the organization needs a role that does not exist (e.g. `iso` for the information security officer), then:

1. Max creates the custom role `iso` with a name, a description and an explicit permission list (possibly empty – the role may serve only as `readRoles` entry for property visibility, UC-21)
2. System validates the permissions against the catalog, rejects a combination of `metamodel:write` with `visibility:configure` or `visibility-scope:assign` (SoD-2), and stores the role (`role.created`)
3. Max assigns `iso` to the information security officer

- Rejoins at step 6

**A6 – Combined administrative roles in a small organization**: At step 4, if the target person would hold `metamodel-admin` and `security-admin` at the same time, then:

1. System warns that the combination joins metamodel and security administration (SoD-3)
2. Max confirms
3. System stores the assignment and flags the audit event with `sodConflict: true`; the access review lists the conflict

- Rejoins at step 7

**A7 – Assign a privileged role**: At step 4, if the selected role is privileged (`metamodel-admin`, `security-admin`, `system-admin` or a custom role with an admin-class permission), then:

1. System informs Max that the role requires a second factor (REQ-171)
2. After confirmation, the assignment is stored and audited with high severity; all administrators are notified
3. If the operator has enabled four-eyes approval (`privilegedAssignmentApproval`, owner decision Q15, REQ-194), the assignment stays `pending` without effect until a second administrator who is neither Max nor the target approves it; it can be rejected and expires if not decided in time
4. The role becomes effective in the target's sessions only after login with a second factor; without it the target is led to enrollment (UC-03)

- Rejoins at step 8

**A8 – Periodic access review**: At step 1, if Max performs the quarterly review, then:

1. Max opens the access review
2. System lists all principals with their roles, the source of every role (local, IdP group, system admin account), validity, grantor, last login, SoD conflicts, privileged holdings and service accounts with expiring secrets
3. Max revokes assignments that are no longer needed (as in A3) and exports the review as evidence

- Ends here

## Exception Flows

**E1 – Self-assignment attempt**:

- Condition: Max tries to assign a role to himself, revoke one of his own assignments, change the permissions of a role he holds, map a group contained in his own token, or link his own account to a Person entity; this includes the local system admin account when it is linked to Max's person
- Expected reaction: rejected with 403 and the message that own access cannot be changed by oneself; audit event `authz.denied` with reason `self-administration`; no change
- Resumption: another principal with `role:assign` performs the change

**E2 – Admin role for a service account**:

- Condition: Max tries to assign a role that is not flagged `assignableToServiceAccounts` or that contains an admin-class permission (e.g. `system-admin`) to a service account, or tries to flag such a custom role as assignable
- Expected reaction: rejected with 422 and a message naming the restriction (REQ-176); audit event `authz.denied`; no change
- Resumption: Max selects an assignable role

**E3 – Removing the last system admin**:

- Condition: a revocation, a `validTo`, a mapping deletion, a person status change or the deactivation of the local system admin account would leave no usable path to `system-admin` (no active local system admin account, no active IdP group mapping to `system-admin`, no active person holding `system-admin`) (SoD-7)
- Expected reaction: rejected with 409 and a message that at least one administration path must remain; no change
- Resumption: Max first establishes another path (e.g. assigns `system-admin` to a second person), then repeats the change

**E4 – Missing permission**:

- Condition: a principal without `role:assign` (e.g. Sabine with `metamodel-admin`) calls an assignment or mapping operation
- Expected reaction: 403 with a generic problem body; audit event `authz.denied`
- Resumption: the change is requested from a security or system administrator

**E5 – Invalid role definition or reference**:

- Condition: a custom role contains an unknown permission or violates SoD-2; or a role to be deleted is still referenced by an assignment, an IdP group mapping or property visibility configuration; or an IdP group identifier is not in the provider's identifier format
- Expected reaction: rejected with 422 and a message naming the problem; no change
- Resumption: Max corrects the definition or removes the references first

**E6 – Business role edited instead of authorization role**:

- Condition: an architect assigns the business role "Enterprise Architect" to a Person entity in the EA model, expecting it to grant access
- Expected reaction: the EA content changes as usual; no authorization role assignment and no effective permission changes (REQ-157 AC2)
- Resumption: the access request is directed to the security administrator (this use case)

## Data Flow

| Step | Data | Direction | Remark |
|---|---|---|---|
| 1–2 | Person, current assignments with source and validity | System → Max | Includes IdP-derived roles of the last login |
| 3 | Role ID, `validFrom`, `validTo`, reason | Max → System | |
| 4 | Permission check, SoD checks, preview of resulting permissions | System internal | SoD-1, SoD-2, SoD-4, SoD-7 |
| 6 | Role assignment | System → DB | Never hard-deleted; revocation sets `revokedAt` |
| 6 | Audit event `role.assigned` | System → audit store | REQ-175 |
| 6 | Cache invalidation | System internal | Effective ≤ 60 s |
| A1 | Issuer, immutable group identifier, role | Max → System | REQ-167 |
| A1.5 | Group claim from ID token | IdP → System | Fail closed on missing or truncated claim |

## Involved Business Objects

| Business Object | Operation | Note |
|---|---|---|
| [person](../../business-objects/person.md) | read, update | Target of assignments; lifecycle status on offboarding; account link to the Person entity |
| [authorization-role](../../business-objects/authorization-role.md) | read, create, update | Authorization roles, custom roles and the permission catalog; separate from the business role in [role](../../business-objects/role.md) (ADR-032 DM-1 to DM-3) |
| [role-assignment](../../business-objects/role-assignment.md) | read, create, update | Assignments with source, validity, four-eyes status and revocation; never hard-deleted (DM-4) |
| [idp-group-mapping](../../business-objects/idp-group-mapping.md) | read, create, update | Mapping of immutable IdP group identifiers to authorization roles (DM-5) |
| [system-admin-account](../../business-objects/system-admin-account.md) | read | Counts as an administration path (SoD-7) and as the linked person for SoD-1 |

## Acceptance Criteria

- [ ] Basic flow: after Max assigns `enterprise-architect` to a person, that person's next request after at most 60 s is authorized with `entity:write`, and an audit event `role.assigned` with actor, target, role, source `local`, `validFrom` and `validTo` exists
- [ ] A person whose assignment reached `validTo` receives 403 on `entity:write` operations at the latest 60 s after `validTo`
- [ ] A1: a member of the mapped IdP group logging in with a token from the configured issuer holds `viewer`; the same group claim from another issuer, a display name instead of the group ID, or an Entra overage claim yields no derived role
- [ ] A2: after a person is set to `offboarded`, every request with their previous session or refresh token is rejected with 401 within 60 s, and all their local assignments show `revokedAt`
- [ ] A3: after a role is revoked, all sessions of the person are revoked and the next login has only the remaining roles
- [ ] A4: a service account with `integration-writer` can call an entity write operation; it receives 403 on any operation requiring an admin-class permission
- [ ] A5: a custom role saved without permissions has no permissions; a custom role containing `metamodel:write` and `visibility:configure` is rejected with 422
- [ ] A6: an assignment that combines `metamodel-admin` and `security-admin` for one person is stored and its audit event carries `sodConflict: true`; the access review lists the conflict
- [ ] A7: a newly assigned privileged role is not effective in a session established without a second factor
- [ ] A7 (four-eyes enabled): an assignment of a privileged role stays `pending` without effect until a second administrator who is neither requester nor assignee approves it; requester and assignee receive 403 on approval
- [ ] A1: after removal of a person from a mapped IdP group, the derived role no longer applies at the latest 15 minutes later
- [ ] A8: the access review lists for every principal all roles with source, validity and grantor, and can be exported
- [ ] E1: a principal with `role:assign` attempting to assign a role to themselves receives 403, and an audit event `authz.denied` with reason `self-administration` exists; the same applies to the local system admin account when it is linked to the target person
- [ ] E2: assigning `system-admin` (or any role with an admin-class permission) to a service account is rejected with 422
- [ ] E3: revoking `system-admin` from the only person holding it while no active local system admin account and no IdP mapping to `system-admin` exist is rejected with 409, and the assignment remains active
- [ ] E4: a principal holding only `metamodel-admin` receives 403 on every role assignment, role definition and IdP mapping operation
- [ ] E6: creating a RoleAssignment connection to a business role "Enterprise Architect" in the EA model does not change the effective permissions of the linked account
- [ ] The permission matrix of all built-in and custom roles is retrievable via the admin API and matches ADR-032

## Out of Scope

- Authentication itself (UC-01) and setup of authentication methods (UC-03)
- Initial bootstrapping of the first administrator (UC-02)
- Configuration of property visibility and override lists (UC-21); this use case only provides the roles referenced there
- Creation, rotation and expiry of service account secrets (REQ-155, REQ-176); only role assignment to service accounts is covered here
- Modeling of business roles, RACI and organizational structure as EA content (concept §8)
- Strict SoD mode, declarative (GitOps) provisioning and SCIM (follow-up decisions in ADR-032); four-eyes approval of privileged assignments is in scope as an optional operator setting (A7, REQ-194)

## Concept References

- [§21.8 Security and Authorization](../../concept/70-platform/21-api-architecture.md)
- [§8.2 Role, §8.3 Person](../../concept/20-entities/08-organization-roles-persons.md)

## Implementation Notes

- Authorization model, permission catalog, built-in roles and SoD rules: [ADR-032](../../adrs/ADR-032-authorization-model.md)
- Walking skeleton subset (S1–S2): seeded permission catalog and built-in roles, deny-by-default enforcement, and the basic flow (local assignment of a built-in role by the bootstrap system admin account) with E1 and E3; the alternative flows A1–A8 and the remaining exception flows can follow in S3–S8
- Constraints: effective permissions are resolved server-side per request (cache ≤ 60 s); permissions are never embedded in tokens
- Performance notes: the access review covers up to a few thousand principals; the effective-permission resolution must not add measurable latency to catalog queries (REQ-071)
- Data model and API contract changes are listed in ADR-032 (DM-1 to DM-10, API-1 to API-9)

## Realizing Components

- Requirements: REQ-003, REQ-128, REQ-157, REQ-158, REQ-159, REQ-160, REQ-161, REQ-162, REQ-163, REQ-164, REQ-165, REQ-167, REQ-171, REQ-175, REQ-176, REQ-194
- User Stories: [US-143](../user-stories/US-143-seed-permission-catalog-built-in-roles.md), [US-144](../user-stories/US-144-assign-and-revoke-local-role.md), [US-145](../user-stories/US-145-reject-self-administration.md), [US-146](../user-stories/US-146-protect-last-system-admin-path.md) (walking skeleton); [US-147](../user-stories/US-147-map-idp-group-to-role.md), [US-148](../user-stories/US-148-revoke-access-on-offboarding.md), [US-149](../user-stories/US-149-assign-roles-to-service-account.md), [US-150](../user-stories/US-150-four-eyes-approval-privileged-assignment.md); custom roles (A5), SoD-3 flag (A6) and access review (A8) pending
- ADRs: ADR-032, ADR-006, ADR-026
- Test Cases: pending
- Implementation: –

## Open Questions

- [x] Should a strict mode reject combined holdings of `metamodel-admin` and `security-admin` (SoD-3), or is flagging sufficient for v1? → owner decision Q13: combination allowed and flagged; strict mode is a follow-up
- [x] Should assignments of privileged roles require approval by a second administrator (four-eyes)? → owner decision Q15: optional operator setting, default off (REQ-194)
- [ ] Should roles and IdP group mappings be provisionable declaratively from the instance configuration file (SH-06 concern "configuration versionable in Git")?

## Notes

Created as a follow-up of security review SEC-01 (recommendation 5) and UC-01 "Out of Scope" ("Management of roles and role assignments themselves (separate use case)").

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | solution-architect | Initial draft based on ADR-032 and security review SEC-01 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Owner decisions Q13, Q15, Q16 applied (A1 15-minute re-evaluation, A7 optional four-eyes approval, open questions closed); REQ-128, REQ-165, REQ-194 and user stories US-143 to US-150 linked |
