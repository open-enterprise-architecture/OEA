# ADR-032: Authorization Model – RBAC with a Fixed Permission Catalog and Configurable Roles

**Status**: accepted
**Date**: 2026-10-13
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer (security review 2026-10, SEC-01/SEC-03/SEC-05/SEC-15), Requirements Engineer (REQ-157 to REQ-163, REQ-171, REQ-176)
**Informed**: Business Engineer (data model follow-up), Backend Engineer, Frontend Engineer
**Partially supersedes**: [ADR-006](./ADR-006-auth-stack-choice.md) (follow-up "claim mapping conventions to OEA roles"), [ADR-008](./ADR-008-gui-architecture-dual-track.md) (portal access "without login effort"), [ADR-026](./ADR-026-external-system-integration.md) (Security section: service account roles, "Entities – Write" role requirement) – see [Relation to Existing ADRs](#relation-to-existing-adrs)
**Partially superseded**: 2026-10-15 by [ADR-042](./ADR-042-setup-phase-without-second-factor.md) — SoD-8 does not apply to the local system admin account during the setup phase

## Context and Problem

The security review of the requirements baseline (`security-reviews/requirements-baseline-2026-10.md`) rated **SEC-01 "No authorization model"** as the only critical finding:

- No document defines which role may perform which operation. `api/openapi.yaml` names roles that do not exist ("Architect or higher", "Admin", `portal-reader`) and carries no per-operation authorization.
- `business-objects/role.md` and `docs/data-model.puml` model a single `roles` table that mixes **business roles** of the EA model (type `functional|process|project|governance|architectural`, `isStandardRole`) with an authorization attribute `permissionLevel: read|write|admin` "per resource" without a resource list.
- Concept §21.8 states that roles of the metamodel "can be used directly as authorization roles". Implemented literally, an architect with write access to EA content grants themselves administrative rights by editing Role entities or role assignments in the model.
- No use case covers the management of roles and role assignments (UC-01 "Out of Scope").

Since then, the requirements engineer has created REQ-157 (permission model), REQ-158 (deny by default), REQ-159 (object-level authorization), REQ-160 (property visibility on every read channel), REQ-161 (governance of visibility configuration), REQ-162 (property write protection), REQ-163 (no anonymous access), REQ-171 (MFA for privileged accounts), REQ-176 (machine credentials) and REQ-179 (secured raw analytics). They require a permission catalog, built-in roles and enforcement rules, but leave the model itself, its enforcement architecture and its data and contract consequences open. The walking skeleton (S1–S2) implements bootstrapping, login, entity writes and the catalog; the review requires SEC-01 to be resolved before that implementation starts.

The owner decisions of 2026-10-13 that shape the model:

| # | Decision | Effect on this ADR |
|---|---|---|
| Q1 | No anonymous access in v1 | Every principal is authenticated; `portal-reader` is an authenticated role |
| Q4 | Separate roles for metamodel administration and security administration; nobody may grant themselves access to restricted data | Separation-of-duties rules SoD-1 to SoD-3 |
| Q5 | Machine access only via service accounts with OAuth2 client credentials; service accounts never hold admin roles | Rule SoD-4 |
| Q8 | Raw SQL/Cypher analytics stay, but behind a dedicated permission | Permission `analytics:query` |
| Q11 | MFA mandatory for privileged roles | Privileged = role with an admin-class permission |
| Q13 | A person may hold `metamodel-admin` and `security-admin` together; the combination is flagged | SoD-3 (flagged, allowed); operator may enable a strict mode |
| Q14 | Nobody reads restricted properties by default; access only via `readRoles` of the property | Override role list (REQ-128) is empty by default |
| Q15 | Four-eyes approval for privileged role assignments is optional and chosen by the operator | Setting `privilegedAssignmentApproval` (default off); assignments are always audited and all admins are notified |
| Q16 | Removing a person from an IdP group takes effect in OEA within 15 minutes | Group claims are re-evaluated at every token refresh, at the latest every 15 minutes |
| Q17 | If the IdP cannot be reached or rejects the refresh, IdP-derived roles no longer apply (fail closed); locally assigned roles stay | REQ-165 AC6 |
| Q18 | When four-eyes approval is enabled, it also covers IdP group mappings to privileged roles and adding admin-class permissions to custom roles | REQ-194 AC10/AC11; approval requests of three kinds |
| Q19 | Reactivation of offboarded persons is configurable (`allowReactivationOfOffboarded`, default off); a reactivated person starts without roles and factors | REQ-004 AC5 |
| Q20 | Ended sessions: tokens deleted immediately, session record deleted after 30 days (configurable) | REQ-189 AC4 |

## Decision Drivers

- **Deny by default and testability from the contract**: every API operation needs a machine-readable authorization declaration that CI can check and from which tests can be generated (ADR-013 spec-first, REQ-158).
- **Separation of duties (Q4)**: metamodel administration and security administration are separate; nobody can widen their own access, neither through role assignments nor through visibility configuration nor through EA content.
- **Separation of authorization from EA content**: business roles such as "Process Owner" are architecture content maintained by architects; they must not have any authorization effect.
- **Property visibility needs named, organization-specific roles**: REQ-124 `readRoles`/`writeRoles` (e.g. "ISO", "DPO") require roles that operators can define, not only a fixed set.
- **Operator fit (SH-06 Max)**: identity federation instead of local user maintenance, mapping of IdP groups to roles, no additional runtime component to operate, declarative configuration where possible.
- **Performance**: authorization must not dominate the latency of catalog queries over 10,000 entities (REQ-071); property visibility is evaluated per row and property.
- **Revocation within 60 seconds** (REQ-003 AC2, REQ-164): permission changes must not be frozen into long-lived tokens.
- **Auditability**: "who may do what, and since when" must be answerable for regulated operators (REQ-175, SH-06 concern "demonstrable access controls").
- **OSS simplicity**: a model that contributors understand and that does not require a policy language.

## Considered Options

### Option (a): Fixed hard-coded roles

A small set of roles (e.g. viewer, architect, admin, auditor) is hard-coded; each controller checks role names.

- **Pro**:
  - Simplest implementation and documentation
  - No configuration that can be misconfigured
- **Con**:
  - Property visibility (REQ-124) needs organization-specific roles such as "ISO" or "DPO" – impossible with a fixed set
  - Checks on role names spread across controllers; adding a role means touching every check (the failure mode SEC-01 describes)
  - Separation of duties requires many fine-grained roles, which a fixed list cannot provide without explosion
  - No adaptation to organizations with different splits of duties (an SME with one admin vs. a corporate group with separate teams)

### Option (b): RBAC with a fixed permission catalog and configurable roles

The system ships a fixed, versioned catalog of permissions with stable identifiers (`entity:read`, `metamodel:write`, …). Every API operation declares the permission(s) it requires. Roles are named sets of permissions: built-in roles are shipped and immutable; operators can create custom roles from the catalog. Persons and service accounts receive roles through local assignments or IdP group mappings. Effective permissions are the union of all active roles. Object-level and property-level checks are explicit rules in the application layer.

- **Pro**:
  - Code checks permissions, never role names – roles can change without code changes
  - The permission declaration fits into the OpenAPI contract and can be linted and tested per operation (REQ-158)
  - Custom roles cover organization-specific needs (ISO, DPO, external auditors) and serve as `readRoles`/`writeRoles` for property visibility
  - Separation-of-duties rules can be stated over permissions and enforced as invariants
  - Well understood by operators and auditors; "who has which permission" is a simple query
  - No additional runtime component
- **Con**:
  - Object-level and attribute-dependent rules (ownership, publication, deletion state, connection scope) are not expressed by RBAC and must be coded explicitly
  - Adding a permission requires a release (deliberate: the catalog is part of the API contract)
  - Custom roles can be misconfigured by operators (mitigated by validation rules and the matrix view)

### Option (c): Attribute-/policy-based authorization with an external policy engine (OPA, Cedar)

Authorization decisions are delegated to a policy engine evaluating policies over attributes of principal, resource and context (OPA as sidecar or embedded Wasm; Cedar as embedded library).

- **Pro**:
  - Very expressive: ownership, scope, time and connection-based rules in one language
  - Policies are declarative, versionable and testable in isolation
  - Decision logs of OPA support auditing
- **Con**:
  - Additional runtime component or native library (OPA sidecar, Cedar via Rust/JNI) that SH-06 has to operate, patch and monitor
  - Property-level filtering over 10,000 entities × n properties per request needs partial evaluation or data-filter compilation; per-decision calls are too slow, and the integration with JPA queries is non-trivial
  - Policy authoring by operators is a new attack surface (a misconfigured policy is a privilege escalation) and needs its own governance
  - Contributors and operators must learn a policy language; reviewability of the OSS code base decreases
  - The OpenAPI contract cannot carry policies; deny-by-default per operation would still need option (b)'s declaration
  - The required rules (REQ-157 to REQ-163) are a known, bounded set; the flexibility is not needed in v1

### Option (d): Per-object access control lists (ACLs)

Every entity, catalog, diagram and dashboard carries an ACL of principals/roles with read/write/admin rights.

- **Pro**:
  - Maximum granularity per object
  - Natural model for sharing personal objects
- **Con**:
  - Does not solve function-level authorization (who may change the metamodel or settings) – an RBAC layer is needed anyway
  - ACL explosion: 10,000+ entities with individual ACLs are unmanageable and unauditable ("who can see the HR system?" requires scanning all ACLs)
  - Catalog queries must join ACLs per row; performance and correctness risks
  - Contradicts the fully open model (ADR-017) and the goal of a shared architecture repository; restriction is needed for **properties** (REQ-124), not for whole objects
  - Copying, importing and restoring entities must carry ACLs consistently – many new bypass paths (cf. SEC-04)

## Decision

We choose **Option (b): RBAC with a fixed permission catalog and configurable roles**, supplemented by explicit object-level rules and a separate property-visibility layer in the application layer. It is the only option that makes deny-by-default checkable in the spec-first contract, supports the organization-specific roles that property visibility needs, enforces the owner's separation-of-duties decisions as simple invariants over permissions, and adds no runtime component for operators. Policy engines (c) are not needed for the bounded rule set of v1; per-object ACLs (d) do not solve function-level authorization and do not scale to the repository size. The object-level rules that RBAC cannot express are a small, enumerated set (see [Enforcement](#8-enforcement)) and are implemented and tested as code.

### 1. Principals

| Principal | Description | Source of roles |
|---|---|---|
| Person (human account) | Row in `persons` with `person_type = human`, bound to (`iss`, `sub`) of an IdP (REQ-166) or to local credentials | Local role assignments and IdP group mappings |
| Service account | Row in `persons` with `person_type = service-account`, authenticated only via OAuth2 client credentials (REQ-176) | Local role assignments only; only roles flagged `assignableToServiceAccounts` |
| Local system admin account | Bootstrap account (UC-02, `system-admin-account.md`), not a person | Holds exactly the permissions of the built-in role `system-admin`; no other role |

There is no anonymous principal (Q1, REQ-163).

### 2. Authorization roles are separate from business roles

OEA distinguishes two concepts that the current artifacts both call "role":

| | Business role (EA content) | Authorization role (access control) |
|---|---|---|
| Purpose | Describes responsibility in the modeled enterprise, e.g. "Process Owner", "Data Owner", "Enterprise Architect" (concept §8.2) | Grants permissions in OEA, e.g. `viewer`, `metamodel-admin` |
| Stored as | Entity of a metatype `role` in the repository; RoleAssignment as connection between a Person entity and a Role entity (§8.3) | System-managed table of authorization roles with their permission sets |
| Maintained by | Architects with `entity:write` | Principals with `role:assign` |
| Effect on access | **None** | Determines effective permissions |
| Versioned/restored as EA content | Yes (entity history, solutions, plateaus) | No; changes are audited (REQ-175), not part of the EA model |

Rules:

- No business role, no RoleAssignment connection and no other EA content grants or removes a permission. The sentence in concept §21.8 "Roles in the metamodel … can be used directly as authorization roles" is superseded by this ADR.
- The same name may exist in both worlds (e.g. a business role "Enterprise Architect" and an authorization role `enterprise-architect`); they are not linked.
- The **only** place where EA content influences access is connection-scoped property visibility (REQ-127): a connection type that the security administrator designates as `scopingConnectionType` grants read access to scoped properties. Creating, changing or deleting such connections requires `visibility-scope:assign` (REQ-161), so the rule above is not undermined by content editing.

How a person relates to both:

```text
persons (account, principal) --< role_assignments >-- authorization roles --< role_permissions >-- permissions
      |
      | 0..1  account link (set by person:manage, never by the person themselves)
      v
Person entity (EA content) --< RoleAssignment connection >-- Role entity (business role)
      |
      +-- connections of a scoping type --> entities   (connection-scoped visibility, REQ-127)
```

The account link from a `persons` row to a Person entity is needed for connection-scoped visibility (REQ-127 traversal starts at "the user's Person entity"). Because it affects access, it is set only by principals with `person:manage`, never for the acting principal's own account, and audited.

### 3. Permission catalog

Permissions are stable identifiers of the form `<resource>:<action>`. The catalog is fixed per release, versioned with the API contract, and returned by the admin API. A permission includes reading the configuration objects it manages (e.g. `role:assign` includes reading roles and assignments), but never repository content. Permissions marked **admin-class** make a role **privileged** (REQ-157, REQ-171).

| Permission | Allows | Admin-class |
|---|---|---|
| `portal:read` | Read shared (instance-scope) dashboards, catalogs and diagrams through the web portal endpoints, including the entity data projected by them; own personal dashboards. No raw entity API, history, search, audit | no |
| `entity:read` | Read entities, connections, catalogs, diagrams, viewpoints, lineage, roadmap, search, entity history, metamodel and schema introspection | no |
| `entity:write` | Create and change entities and connections (UI, upsert, batch, import of content); excludes connections of scoping types | no |
| `entity:delete` | Soft-delete entities and connections (ADR-019) | no |
| `entity:restore` | See soft-deleted entities, restore entities and versions (full and partial, UC-15/UC-16) | no |
| `catalog:manage` | Create and configure shared catalogs and shared saved filters | no |
| `dashboard:manage` | Create and configure shared dashboards | no |
| `viewpoint:manage` | Manage viewpoints and the navigation tree (UC-12, UC-13) | no |
| `plateau:go-live` | Put a plateau into production (UC-11) | no |
| `webhook:manage` | Register and manage webhooks; payloads are filtered by the registrant's permissions (REQ-160) | no |
| `analytics:query` | Submit raw SQL/Cypher analytics queries under the restrictions of REQ-179 | no |
| `audit:read` | Read and export the audit log (incl. SIEM export) | no |
| `metamodel:write` | Change the metamodel: metatypes, property definitions (except visibility fields), connection types, business rules (ADR-018), metamodel labels, metamodel and continuum package import/export | **yes** |
| `visibility:configure` | Change `visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType` and the override role list (REQ-124, REQ-128, REQ-161) | **yes** |
| `visibility-scope:assign` | Create, change and delete connections of types used as `scopingConnectionType` (REQ-161) | **yes** |
| `role:assign` | Define custom roles, assign and revoke roles, manage IdP group mappings | **yes** |
| `person:manage` | Invite persons, change their lifecycle status, reset local credentials, set the account link to a Person entity | **yes** |
| `service-account:manage` | Create and deactivate service accounts, issue and rotate client secrets | **yes** |
| `auth-config:manage` | Configure authentication providers, 2FA policy, remote system admin mapping | **yes** |
| `settings:manage` | Change instance settings (limits, retention within its floor, feature flags) | **yes** |

Notes:

- The identifiers follow REQ-157 (`metamodel:write`, `settings:manage`, `auth-config:manage`); `portal:read` and `viewpoint:manage` are added. A permission for granting architecture waivers (concept §21.8 `waiver-grant`) is added when a waiver use case exists; `plateau-create` from §21.8 is covered by `entity:write`.
- Operations that only concern the caller (own profile, own logout, own personal dashboards and saved filters, own authentication methods) require an authenticated principal and are protected by object-level ownership checks, not by a catalog permission.
- Personal dashboards and saved filters can be created by every principal holding `entity:read` or `portal:read`.

### 4. Built-in roles and permission matrix

Built-in roles are shipped with the system, cannot be deleted and their permission sets cannot be changed. There is no role hierarchy; a person who needs several kinds of access receives several roles (e.g. Kurt: `enterprise-architect` + `metamodel-admin`). Administrative roles deliberately contain no content permissions.

| Permission | portal-reader | viewer | analyst | enterprise-architect | integration-writer | auditor | metamodel-admin | security-admin | system-admin |
|---|---|---|---|---|---|---|---|---|---|
| `portal:read` | x | x | x | x | | | | | |
| `entity:read` | | x | x | x | x | | | | |
| `entity:write` | | | | x | x | | | | |
| `entity:delete` | | | | x | | | | | |
| `entity:restore` | | | | x | | | | | |
| `catalog:manage` | | | | x | | | | | |
| `dashboard:manage` | | | | x | | | | | |
| `viewpoint:manage` | | | | x | | | | | |
| `plateau:go-live` | | | | x | | | | | |
| `webhook:manage` | | | | x | | | | | |
| `analytics:query` | | | x | | | | | | |
| `audit:read` | | | | | | x | | | |
| `metamodel:write` | | | | | | | x | | |
| `visibility:configure` | | | | | | | | x | |
| `visibility-scope:assign` | | | | | | | | x | |
| `role:assign` | | | | | | | | x | x |
| `person:manage` | | | | | | | | x | x |
| `service-account:manage` | | | | | | | | | x |
| `auth-config:manage` | | | | | | | | | x |
| `settings:manage` | | | | | | | | | x |
| **Privileged (MFA, REQ-171)** | no | no | no | no | no | no | **yes** | **yes** | **yes** |
| **Assignable to service accounts** | no | yes | no | no | yes | yes | no | no | no |

Role intent:

- `portal-reader` – consumers of curated content in the web portal (e.g. SH-05 CIO).
- `viewer` – read access to the whole repository (domain architects in consumption role).
- `analyst` – viewer plus raw analytics (REQ-179).
- `enterprise-architect` – modeling in the client app (SH-03, SH-04, SH-08).
- `integration-writer` – service accounts that write via the external write API (ADR-026).
- `auditor` – audit log and SIEM export; combined with `viewer` when content context is needed.
- `metamodel-admin` – metamodel configuration (UC-04; e.g. SH-07 Sabine, SH-03 Kurt).
- `security-admin` – who may see what: visibility configuration, scoping connections, role assignments, person lifecycle (UC-21, UC-23).
- `system-admin` – instance operation: authentication providers, settings, service accounts, persons and role assignments (SH-06 Max).

The property-visibility override (REQ-128, `overridesPropertyVisibility`) only widens which **properties** of objects a principal can already read; it grants no object access. The override role list is **empty by default** (Q14): access to a restricted property is granted through its `readRoles` like for any other role. A security admin may add roles to the override list for exceptional cases (troubleshooting, migration); every change is audited (SoD-6).

### 5. Custom roles

Principals with `role:assign` can define custom roles (e.g. `iso`, `dpo`, `external-auditor`) as sets of catalog permissions:

- A custom role without an explicit permission list has no permissions (REQ-157 AC3); unknown permission identifiers are rejected.
- A custom role may be flagged `assignableToServiceAccounts` only if it contains no admin-class permission.
- A custom role may be referenced in `readRoles`/`writeRoles` of property visibility (REQ-124). Roles used there may have an empty permission set: their only purpose may be property access.
- A role that is referenced by visibility configuration, by an IdP group mapping or by an active assignment cannot be deleted; it must first be unreferenced.

### 6. Separation of duties

The following rules are system invariants, enforced in the application layer, independent of configuration, and covered by tests:

- **SoD-1 – No self-administration**: no principal can create, change or revoke its own role assignments, change the permission set of a role it currently holds, create or change an IdP group mapping for a group contained in its own current token, or set its own account link to a Person entity. The local system admin account counts as the same principal as the person it is linked to (`optionallyLinkedTo`).
- **SoD-2 – Metamodel vs. security administration**: no role (built-in or custom) may contain `metamodel:write` together with `visibility:configure` or `visibility-scope:assign`. `metamodel-admin` cannot change visibility; `security-admin` cannot change the metamodel (REQ-157 AC4, REQ-161).
- **SoD-3 – Combined holdings are flagged**: a person may hold roles that together combine the permissions of SoD-2 (necessary in small SMEs with a single administrator). Such an assignment is accepted, flagged as SoD conflict in the audit event (`role.assigned` with `sodConflict: true`) and shown in the access review. A strict mode that rejects such combinations is a follow-up decision.
- **SoD-4 – Service accounts never hold privileged roles**: role assignments to service accounts are restricted to roles flagged `assignableToServiceAccounts`; roles with any admin-class permission are never assignable to service accounts, regardless of configuration (Q5, REQ-176). Service accounts cannot be targets of IdP group mappings.
- **SoD-5 – Built-in roles are immutable**: their permission sets change only with a release and are documented in this ADR.
- **SoD-6 – Self-benefiting visibility changes are flagged**: a change of visibility configuration or the override list that benefits the acting principal is audited and flagged (REQ-161 AC3).
- **SoD-7 – Administration cannot be lost**: there must always be at least one usable path to `system-admin`: an active local system admin account, an active IdP group mapping to `system-admin`, or an active assignment of `system-admin` to an active person. Revoking, expiring (setting `validTo`) or deactivating the last such path is rejected (HTTP 409).
- **SoD-8 – Privileged roles require MFA**: a privileged role becomes effective in a session only if the session was established with a second factor (local: passkey/TOTP; OIDC: `amr`/`acr` per REQ-020/REQ-171). Without it, the person is led to enrollment and holds only the non-privileged part of their permissions.

Collusion of two administrators (e.g. a security admin assigning `system-admin` to a colleague who reciprocates) is made visible by audit events, a notification to all administrators and the access review. Operators with more than one administrator can additionally enable **four-eyes approval** (Q15, setting `privilegedAssignmentApproval`, default off): an assignment of a privileged role then stays pending until a second administrator, who is neither the requester nor the assignee, approves it. The same applies to IdP group mappings to privileged roles and to adding an admin-class permission to a custom role (Q18), so that the approval cannot be bypassed.

### 7. Where roles come from

Effective permissions of a principal are the union of the permissions of all roles from the following sources that are active at request time:

1. **Local assignments** (`source = local`): created by a principal with `role:assign`, with `validFrom` and optional `validTo` (time-boxed access, e.g. external consultants). Assignments are never hard-deleted; revocation sets `revokedAt`/`revokedBy`. Assignments to persons in status `invited` are allowed (pre-provisioning); they become effective once the person is `active`.
2. **IdP group mappings** (`source = idp`): a mapping binds an immutable, provider-controlled group identifier from a configured issuer (Entra ID group object ID or app role, Authentik group UUID) to an authorization role. Display names are rejected (REQ-167). Group claims are evaluated at login and at every session refresh that involves the IdP; claims from other issuers are ignored; a missing or truncated groups claim (Entra overage) yields no IdP-derived roles (fail closed) and a warning event. The roles derived at login are recorded with the session for audit and access review. The remote system admin mapping of REQ-014/REQ-167 is an IdP group mapping to `system-admin`.
3. **Local system admin account**: holds the permissions of `system-admin` while it is active (UC-02).

Permissions are **never embedded in tokens or sessions**. OEA access tokens and sessions identify the principal and the session; the permission set is resolved server-side per request from an authorization cache with a maximum age of 60 seconds that is invalidated on every change of assignments, roles, mappings or person status. IdP access tokens, scopes or role claims never grant OEA permissions directly – only through configured IdP group mappings. Client credentials tokens of service accounts carry only the `client_id`.

**Removing access**:

| Change | Takes effect |
|---|---|
| Local assignment revoked or `validTo` reached | At the next request, at the latest after 60 s (cache); all sessions of the person are revoked (REQ-164) |
| IdP group mapping deleted or deactivated in OEA | At the next request, at the latest after 60 s, for all affected sessions |
| Custom role's permission removed | At the next request, at the latest after 60 s, for all holders |
| Person set to `inactive`/`offboarded` in OEA | All sessions revoked within 60 s (REQ-164) |
| Group membership removed in the IdP only | Within 15 minutes: group claims are re-evaluated at every token refresh, at the latest every 15 minutes (Q16, REQ-165). For immediate offboarding, the person's status in OEA is changed (60 s). |

### 8. Enforcement

Authorization is enforced in four layers. No layer relies on another one having filtered correctly.

| Layer | Where (ADR-028) | What | Requirement |
|---|---|---|---|
| L1 Authentication | Security filter chain (`config/`) | Every operation except an explicit allowlist (login/callback endpoints, health without repository data, static client assets) requires an authenticated principal; 401 otherwise | REQ-163 |
| L2 Function-level | API layer, generated from the contract | Each operation's declared permission(s) are checked before the controller runs; unmapped requests are denied for all principals (`denyAll` default); 403 with a generic problem body | REQ-157, REQ-158 |
| L3 Object-level | Application layer (`io.oea.app`) | Ownership (personal dashboards, saved filters), sharing scope (`portal:read` only for instance-scope objects), deletion state (`entity:restore`), readability of referenced entities in writes, external-ID namespace ownership (REQ-177), scoping connection types (`visibility-scope:assign`), SoD rules; identical 404 for "not existing" and "not permitted" where existence is sensitive | REQ-159, REQ-161, REQ-177 |
| L4 Property-level | Application layer, separate component; evaluation logic in `io.oea.core` | Property visibility (REQ-124 to REQ-128) applied in one read projection used by every read channel (entity, catalog, search, history, diff, audit, portal, aggregation, lineage, roadmap, export, events, webhooks, write responses) and one write guard used by every write path; filters, sorting and grouping on unreadable properties rejected server-side | REQ-160, REQ-162 |

Additional rules:

- **Contract declaration**: every operation in `api/openapi.yaml` carries a vendor extension `x-oea-authorization` with `access: public | authenticated | permission` and, for `permission`, either a non-empty `permissions` list (all required) or a non-empty `anyOfPermissions` list (at least one required, for reads where a managing permission includes read access), plus optional `conditions` (extra permissions required only for specific parameters or fields, e.g. `deleted=true` requires `entity:restore`). The permission catalog (`x-oea-permissions`) and the built-in role matrix (`x-oea-builtin-roles`) are kept as top-level extensions in the spec. `public` is allowed only for the allowlist of L1. CI (`scripts/check_openapi_authorization.py`) fails if an operation lacks the extension or the `401`/`403` responses (REQ-158 AC1). The standard OpenAPI `security` field lists only the authentication schemes; it is not used for permissions, so that cookie, bearer and client-credentials schemes need no duplicated permission lists.
- **Generated enforcement and tests**: the mapping operation → permission is generated from the contract into the security configuration; a generated authorization matrix test calls every operation with a principal lacking the permission and expects 403 (REQ-158 AC3).
- **Defense in depth**: application services re-assert the required permission for operations that can also be triggered outside HTTP controllers (import jobs, event stream, webhook delivery, scheduled jobs), using the same permission identifiers. Application services receive the principal explicitly as part of the command/query context.
- **Database level**: raw analytics run under a read-only database role on reporting views (REQ-179); the audit schema is owned by a separate role (REQ-174). These are additional barriers, not the primary enforcement.
- **No architecture-layer or domain scoping of permissions in v1**: consistent with the fully open layer model (ADR-017), permissions apply instance-wide; domain-specific restriction is expressed through property visibility (role-restricted or connection-scoped).

### 9. Clients: web portal and Electron client

- Both clients use the same API with the same enforcement. The portal is "read-only" because of the permissions of its users and the operations it calls, not because of the delivery channel; a portal session presenting a token with `entity:write` could call write operations and is treated exactly like the Electron client.
- No client-side check is relied upon. Clients obtain the caller's effective permissions (`GET /me`) only to hide or disable UI elements.
- The Electron client caches only data returned by the server after L3/L4 filtering; the offline cache is encrypted and deleted on logout or loss of access (REQ-181).
- Server-sent events and webhooks apply L3/L4 per recipient (REQ-160).

### 10. Auditing

Every change to the authorization configuration is audited (REQ-175): `role.created`, `role.changed`, `role.deleted`, `role.assigned`, `role.revoked`, `role.assignment-expired`, `idp-mapping.created|changed|deleted`, `person.account-linked`, `authz.denied` (403), plus the flags `sodConflict` and `selfBenefiting`. Assignments of privileged roles are audited with high severity.

## Consequences

### Positive Consequences

- SEC-01 is resolved at the architecture level: every operation has a declared, tested permission; unknown operations are denied.
- The owner's separation-of-duties decisions (Q4, Q5) are invariants rather than conventions; self-escalation through role assignments, visibility configuration or EA content is not possible for a single principal.
- Business roles become pure architecture content: architects can model "Process Owner" or "Data Owner" freely without security implications.
- Operators can federate access through IdP groups (SH-06 concern "identity federation") and define organization-specific roles (ISO, DPO) for property visibility.
- Revocation is fast and predictable because permissions are never frozen into tokens.
- Access reviews ("who has which permission since when, from which source") are simple queries over assignments and mappings.

### Negative Consequences / Trade-offs

- The permission catalog grows only with releases; operators cannot invent permissions for new operations.
- Object-level rules are code, not configuration; each new resource type needs explicit rules and tests in L3.
- A per-request permission resolution with a cache adds a small overhead and a cache-invalidation mechanism (multi-node: at most 60 s staleness).
- IdP-side group removals are only as fast as the next session refresh against the IdP; immediate offboarding requires a status change in OEA.
- Small instances with a single administrator need SoD-3 (flagged combined holdings) and cannot use a strict SoD mode by default.
- Collusion between two administrators is detected, not prevented.
- Existing artifacts (data model, `role.md`, API contract, concept §21.8, REQ-003 formal statement, UC-01) must be aligned.

### Follow-up Decisions

- Declarative provisioning of custom roles, IdP group mappings and service-account roles from the instance configuration file (GitOps, SH-06), including how such changes are audited (pending ADR)
- Four-eyes approval for self-benefiting visibility changes (concept §20.11) (pending; for privileged role assignments decided as optional setting, Q15)
- Strict SoD mode that rejects combined holdings of SoD-2 permissions (operator setting; design pending)
- SCIM provisioning for faster IdP-driven deprovisioning (pending, v1.x)
- Mechanism for principal-specific reporting views of raw analytics (row-level security vs. per-request views, REQ-179) (solution design)
- Waiver permission once a waiver use case exists

## Required Changes to Other Artifacts

### Data model changes (for the business engineer)

The business engineer updates `docs/data-model.puml` and the business objects; this ADR does not change them.

| # | Change | Affected |
|---|---|---|
| DM-1 | Split "role" into two business objects: `role.md` describes only the **business role** (EA content, metatype `role`, §8.2) – remove `permissionLevel` and the statement that roles are used as authorization roles; new BO `authorization-role` (id, name, description, `isBuiltin`, `assignableToServiceAccounts`, `overridesPropertyVisibility`, permission set) | `business-objects/role.md`, new `business-objects/authorization-role.md` |
| DM-2 | New seeded, read-only catalog `permissions` (id, category, `adminClass`) and join table `role_permissions` (role_id, permission_id) | `docs/data-model.puml` |
| DM-3 | Table `roles`: remove business attributes `type`, `permission_level`, `is_standard_role`; consider renaming to `authorization_roles`; add `assignable_to_service_accounts`, `overrides_property_visibility`; replace the note on built-in roles by the matrix of this ADR | `docs/data-model.puml` |
| DM-4 | Table `person_roles` → `role_assignments`: surrogate `id`; `person_id`, `role_id`, `valid_from`, `valid_to` (TIMESTAMPTZ), `source` (`local`), `granted_by`, `granted_at`, `revoked_by`, `revoked_at`, `reason`; remove `assignment_type` and `percentage` (they belong to the EA RoleAssignment connection, §8.3); no hard delete. New BO `role-assignment` or section in `authorization-role.md` | `docs/data-model.puml`, `business-objects/person.md` (`fillsRoles` refers to business roles only) |
| DM-5 | New table `idp_group_mappings`: `id`, `auth_provider_id`, `issuer`, `group_identifier` (immutable ID), `role_id`, `active`, `created_by`, `created_at` | `docs/data-model.puml` |
| DM-6 | Remote mode of the system admin account expressed as an IdP group mapping to `system-admin`; correct the example (`claimValue` display name → immutable ID, REQ-167); BR-03 aligned with REQ-171 | `business-objects/system-admin-account.md` |
| DM-7 | `persons`: nullable, unique account link `entity_id` to the Person entity used for connection-scoped visibility (REQ-127); the lifecycle `status` column from REQ-164/REQ-166 is a dependency | `docs/data-model.puml`, `business-objects/person.md` |
| DM-8 | Business role and RoleAssignment as EA content: metatype `role` and connection type `role-assignment` (Person entity → Role entity) in the starter package, without any reference to authorization tables | starter package / metamodel configuration |
| DM-9 | `readRoles`/`writeRoles` of `PropertyDefinition` (REQ-124) reference authorization role IDs; deleting a referenced role is rejected | `docs/data-model.puml`, `business-objects/metamodel-configuration.md` |
| DM-10 | Session store (REQ-164) records the IdP-derived roles per session for audit and access review | `docs/data-model.puml` |

### API contract changes (for a later step)

`api/openapi.yaml` is changed in a separate spec-first step; this ADR does not change it.

| # | Change |
|---|---|
| API-1 | Vendor extension `x-oea-authorization` (`access`, `permissions`) on every operation; CI lint script that fails on missing declarations and on `public` outside the allowlist |
| API-2 | `401` and `403` responses (generic problem body) on every operation; `404` documented as the response for not permitted, existence-sensitive objects |
| API-3 | Remove prose role hints ("Architect or higher", "Auditor or Admin", "portal-reader" scope, "requires role `integration-writer`" in the info block and the "Entities – Write" tag); write operations require `entity:write` for humans and service accounts alike |
| API-4 | New operations: `GET /admin/permissions`; `GET/POST /admin/roles`, `GET/PUT/DELETE /admin/roles/{roleId}` (includes the permission matrix, REQ-157 AC1); `GET/POST /admin/persons/{personId}/role-assignments`, `POST /admin/persons/{personId}/role-assignments/{assignmentId}/revoke`; `GET/POST /admin/idp-group-mappings`, `PUT/DELETE /admin/idp-group-mappings/{mappingId}`; `PUT /admin/persons/{personId}/entity-link`; `GET /admin/access-review` (assignments by source, SoD conflicts); `GET /me` including effective permissions and their sources |
| API-5 | `ServiceAccountWrite.roles`: only roles flagged `assignableToServiceAccounts`; `422` otherwise |
| API-6 | Replace `PropertyVisibilityEntry` (`visibilityByRole`) by the REQ-124 model (`visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType`) with authorization role IDs; add `redacted` to `PropertyValue`/`FieldDiff` (REQ-160) |
| API-7 | Security schemes: `oauth2` client credentials flow with token endpoint (REQ-176), cookie scheme for the web portal, bearer for the Electron client; scopes are not used for OEA permissions |
| API-8 | `/portal/audit/events` requires `audit:read` or is removed; `deleted` parameter of catalog/entity listings requires `entity:restore` |
| API-9 | Error codes for SoD violations: `403` for self-administration, `409` for removing the last administration path, `422` for invalid role definitions (SoD-2, unknown permissions, admin-class role for service accounts) |

### Other follow-ups

- Concept §21.8: replace the sentence on metamodel roles as authorization roles and the "Permission level / special permissions" list by a reference to this ADR (concept change with CHANGELOG entry).
- REQ-157: add `portal:read`, `viewpoint:manage` and the built-in role `analyst` to the minimum lists; REQ-003: formal statement refers to authorization role assignments instead of `Person.fillsRoles`; UC-01: precondition "active role" refers to authorization roles (requirements engineer).
- REQ-128: default override role list is empty (Q14).
- REQ-165: IdP group claims re-evaluated at every token refresh, at the latest every 15 minutes (Q16).
- New requirement: optional four-eyes approval for privileged role assignments (Q15).
- User stories for UC-23 (requirements engineer).

## Relation to Existing ADRs

| ADR | Relation |
|---|---|
| [ADR-006](./ADR-006-auth-stack-choice.md) | Partially superseded: the follow-up "claim mapping conventions … to OEA roles" is decided here (IdP group mappings with immutable identifiers; remote system admin = mapping to `system-admin`). Authentication stack unchanged. |
| [ADR-008](./ADR-008-gui-architecture-dual-track.md) | Partially superseded: portal consumers need an authenticated session and `portal:read` (Q1); the portal is not a separate trust zone. |
| [ADR-026](./ADR-026-external-system-integration.md) | Partially superseded (Security section): service accounts may hold any role flagged `assignableToServiceAccounts` (built-in: `viewer`, `integration-writer`, `auditor`), never a privileged role; write endpoints require the permission `entity:write`, not a role. |
| [ADR-013](./ADR-013-api-style.md) | Extended: the spec-first contract carries the authorization declaration. |
| [ADR-017](./ADR-017-architecture-layer-strategy.md) | Consistent: no layer- or domain-scoped permissions in v1. |
| [ADR-019](./ADR-019-soft-delete-strategy.md) | Refined: "admin role" for deleted entities becomes `entity:restore`. |
| [ADR-024](./ADR-024-audit-separation.md) | Used: authorization changes are audit events; `audit:read` corresponds to the auditor access. |
| [ADR-028](./ADR-028-backend-layered-architecture.md) | Applied: L2 in the API layer, L3/L4 in the application layer, evaluation logic for visibility in the core. |

## References

**Concept Chapters**:

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)
- [§8.2 Role, §8.3 Person](../concept/20-entities/08-organization-roles-persons.md)
- [§22 Analyzability](../concept/70-platform/22-analyzability.md)

**Related ADRs**:

- ADR-006, ADR-008, ADR-009, ADR-013, ADR-017, ADR-019, ADR-024, ADR-026, ADR-028

**Use Cases / Requirements**:

- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md)
- [UC-01: Login](../requirements/use-cases/UC-01-login.md), [UC-02: System Admin Bootstrapping](../requirements/use-cases/UC-02-system-admin-bootstrapping.md), [UC-21: Configure Property Visibility](../requirements/use-cases/UC-21-configure-property-visibility.md)
- [REQ-003](../requirements/req/REQ-003-access-without-role-assignment.md), [REQ-124](../requirements/req/REQ-124-propertydefinition-visibilitymode.md) to [REQ-128](../requirements/req/REQ-128-property-visibility-admin-override.md), [REQ-155](../requirements/req/REQ-155-service-account-management.md)
- [REQ-157](../requirements/req/REQ-157-permission-model.md), [REQ-158](../requirements/req/REQ-158-deny-by-default-authorization.md), [REQ-159](../requirements/req/REQ-159-object-level-authorization.md), [REQ-160](../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-161](../requirements/req/REQ-161-visibility-configuration-governance.md), [REQ-162](../requirements/req/REQ-162-property-write-protection-all-paths.md), [REQ-163](../requirements/req/REQ-163-no-anonymous-access.md)
- [REQ-164](../requirements/req/REQ-164-session-termination-revocation.md), [REQ-167](../requirements/req/REQ-167-immutable-admin-claim-mapping.md), [REQ-171](../requirements/req/REQ-171-mfa-privileged-accounts.md), [REQ-175](../requirements/req/REQ-175-security-event-logging.md), [REQ-176](../requirements/req/REQ-176-machine-credential-lifecycle.md), [REQ-179](../requirements/req/REQ-179-secured-raw-query-analytics.md)

**Security Review**:

- [Security review requirements baseline 2026-10](../security-reviews/requirements-baseline-2026-10.md) – SEC-01, SEC-03, SEC-05, SEC-15; owner decisions Q1, Q4, Q5, Q8, Q11

## Notes

- The task description used the example identifiers `metamodel:configure` and `system:configure`; this ADR keeps the identifiers already fixed in REQ-157 (`metamodel:write`, `settings:manage`, `auth-config:manage`) to avoid renaming accepted requirements.
- Owner decisions Q13–Q16 (2026-10-13) closed the open points on combined admin roles, the default override list, four-eyes approval and the IdP group re-evaluation interval.
