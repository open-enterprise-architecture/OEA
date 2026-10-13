# Project Glossary

Project-specific term definitions. Designed to stand alone so that new team members can get up to speed quickly.

## Conventions

- Sorted alphabetically
- Per term: definition, possibly references to concept chapters
- Mention synonyms and distinctions

## Terms

### Account Link

Link from a person account (OEA principal) to the Person entity in the EA model. Needed for connection-scoped property visibility; set only with `person:manage`, never for the own account. See [person](../business-objects/person.md), [ADR-032](../adrs/ADR-032-authorization-model.md).

### Admin-Class Permission

Permission that makes a role privileged (e.g. `metamodel:write`, `role:assign`, `settings:manage`). Holders need a second factor; service accounts never receive such permissions. See [authorization role](../business-objects/authorization-role.md).

### ADR (Architectural Decision Record)

Documented architecture decision with context, options, choice, and consequences. See [§5 (Principles & Standards)](../concept/10-foundations/05-principles-standards-adrs.md).

### Audit Trail

Append-only, tamper-evident record of all audit events (changes, security events, accesses, system events) in the schema `audit`; actors are stored pseudonymously, retention at least 1 year (default 7 years). Not the entity history. See [audit event](../business-objects/audit-event.md), [ADR-033](../adrs/ADR-033-audit-trail-hardening.md).

### Authorization Role

Named set of permissions from the fixed permission catalog that grants access in OEA (built-in, e.g. `viewer`, `security-admin`, or custom, e.g. `iso`). Not to be confused with a business role. See [authorization role](../business-objects/authorization-role.md).

### Business Role

Responsibility in the modeled enterprise (e.g. "Process Owner"), modeled as EA content (metatype `role`). Grants no access in OEA. See [role](../business-objects/role.md).

### EA (Enterprise Architecture)

Discipline that views, plans, and steers the business and IT landscape as a whole. In the narrower sense, also the tool to be built here.

### Four-Eyes Approval (Privileged Assignment)

Optional operator setting (`privilegedAssignmentApproval`, default off): an assignment of a privileged role stays `pending` until a second administrator, neither requester nor assignee, approves it. See [role assignment](../business-objects/role-assignment.md).

### Hash Chain (Audit)

Sequence of SHA-256 hashes linking every audit record to its predecessor, computed by the database; together with checkpoints exported outside the database it makes modification, deletion and truncation of audit records detectable. See [audit event](../business-objects/audit-event.md).

### Identity Binding

Immutable pair (`issuer`, `subject`) of an identity provider that identifies a person account at OIDC login; e-mail is never used for matching. See [person](../business-objects/person.md).

### IdP Group Mapping

Binding of an immutable IdP group or app-role identifier to an authorization role; group members hold the role for the duration of their session. See [IdP group mapping](../business-objects/idp-group-mapping.md).

### Permission

Stable identifier `<resource>:<action>` (e.g. `entity:write`) from the fixed, release-versioned permission catalog; every API operation declares the permissions it requires. See [ADR-032](../adrs/ADR-032-authorization-model.md).

### Plateau

Temporally stable architecture state. E.g. "baseline-2026", "target-2028". See [§11 (Temporal Model)](../concept/30-dynamics/11-temporal-model.md).

### Principal

Authenticated actor in OEA: a human person account, a service account or the local system admin account. There is no anonymous principal.

### Privileged Role

Authorization role containing at least one admin-class permission (e.g. `metamodel-admin`, `security-admin`, `system-admin`). Effective only in sessions with a verified second factor.

### Protected Identity Mapping

The identity tables (person accounts, system admin account) that alone hold names and e-mail addresses of actors; audit records reference actors only by key. Anonymizing a person changes only this mapping. See [person](../business-objects/person.md).

### Reporting Schema

Database schema `reporting` of masking views over the repository content, the only objects reachable by raw SQL analytics; restricted property values are masked per principal. See [ADR-034](../adrs/ADR-034-secured-raw-analytics.md).

### Role Assignment

Local grant of one authorization role to one person account with validity period; never hard-deleted. Distinct from the EA connection "RoleAssignment" between a Person entity and a business role. See [role assignment](../business-objects/role-assignment.md).

### Separation of Duties (SoD)

System invariants of the authorization model (SoD-1 to SoD-8), e.g. no self-administration, metamodel administration separate from security administration. See [ADR-032](../adrs/ADR-032-authorization-model.md).

### Service Account

Person account with `personType = service-account` for an external system; authenticates only with OAuth2 client credentials. See [machine credential](../business-objects/machine-credential.md).

### Session

Server-side record of an interactive login with timeouts, rotating refresh tokens, second-factor state and IdP-derived roles; revocable at any time. See [session](../business-objects/session.md).

### TOGAF

Framework for enterprise architecture from The Open Group. Provides the ADM (methodology) and the Content Metamodel.

<!-- Add further terms as needed -->
