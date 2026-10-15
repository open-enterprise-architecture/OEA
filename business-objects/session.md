---
identifier: session
name_de: Sitzung
name_en: Session
version: 0.3.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Login Session
  - Refresh Token Family
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
  - adr: adrs/ADR-032-authorization-model.md
---

# Business Object: Session

## Definition

A session is the server-side record of one interactive login of a human person or of the local system admin account in one client, including its refresh tokens, its timeouts, its second-factor state and the roles derived from the identity provider; it can be revoked server-side at any time.

## Description

OEA controls session lifetime itself (REQ-165); a session established through an IdP additionally ends when the IdP refresh token expires or is rejected, so OEA never extends a token issued by the IdP (owner decision 2026-10-15, REQ-001). OEA access tokens identify the session (`sid`); permissions are never embedded in tokens or sessions but resolved per request (cache ≤ 60 s, ADR-032 §7). Revoking a session (logout, offboarding, role change, password reset, refresh-token reuse) makes all its tokens invalid within 60 s (REQ-164).

**Refresh tokens** belong to exactly one session (the session is the token family). Every use rotates the token; presenting an already rotated token is treated as reuse: the session and all its tokens are revoked and an audit event is written (REQ-165 AC2). Only hashes are stored; the plaintext token lives in an `HttpOnly` cookie (web portal) or the OS credential store (Electron).

**IdP-derived roles** (DM-10, Q16): for OIDC sessions, the groups claim is evaluated at login and at every refresh against the IdP, at the latest every 15 minutes. The roles derived through [IdP group mappings](./idp-group-mapping.md) are recorded per session (`session_idp_roles`) for audit and access review; each change of the set is audited. The IdP refresh token needed for re-evaluation is stored encrypted (REQ-188) and never returned.

Service accounts have no sessions; their client-credentials access tokens are short-lived (≤ 900 s, REQ-176).

## Attributes

### Session

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID, opaque | Session identifier (`sid` in OEA access tokens) |
| tokenHash | binary | required | | SHA-256, unique | Hash of the web cookie value `__Host-OEA_SESSION` (256 random bits); the value itself is never stored (REQ-187) |
| principal | reference | required | | exactly one of: person (human), system admin account | Logged-in principal |
| clientType | enum | required | | `[web-portal, electron]` | Client |
| authMethod | enum | required | | `[oidc, passkey, password-totp, password]` | How the session was established |
| authProvider | reference | conditional | | required iff `authMethod = oidc` | Identity provider |
| secondFactorVerified | boolean | required | false | local: passkey/TOTP; OIDC: `amr`/`acr` | Privileged roles become effective only if true (SoD-8, REQ-171) |
| requiredAction | enum | optional | | `[change-password, enroll-second-factor]` | Session is restricted to this action (REQ-173 AC2, REQ-171 AC4) |
| createdAt | datetime | required | | | Login time |
| authenticatedAt | datetime | required | | | Last (re-)authentication; step-up checks (REQ-172) |
| lastActivityAt | datetime | required | | | Idle timeout basis (default 30 min, REQ-165) |
| absoluteExpiresAt | datetime | required | | createdAt + absolute lifetime (default 12 h, max. 7 days) | Hard end of the session |
| clientIp | string | optional | | INET | Source address (security events, REQ-175) |
| userAgent | string | optional | | | Client description |
| idpRefreshTokenEncrypted | string | optional | | AES-256-GCM; OIDC only | IdP refresh token for group re-evaluation |
| idpRefreshTokenKeyId | string | conditional | | required iff `idpRefreshTokenEncrypted` is set | Key ID of the key-encryption key (rotation with `oea secrets reencrypt`) |
| idpClaimsEvaluatedAt | datetime | optional | | ≤ 15 min old while the session is used (Q16) | Last evaluation of the groups claim |
| idpGroupsClaimStatus | enum | optional | | `[present, missing, truncated]`; OIDC only | Result of the last evaluation; `missing`/`truncated` → no IdP-derived roles |
| idpGroupIdentifiers | list of strings | optional | | OIDC only; identifiers of the provider's issuer | Group identifiers of the last evaluation; used for the self-administration check (SoD-1) and access review |
| idpTokenExpiresAt | datetime | optional | | OIDC only; NULL if the IdP sends no refresh token expiry | Expiry of the IdP refresh token; the session ends at the first of idle timeout, absolute lifetime and this time |
| revokedAt | datetime | optional | | | Revocation time |
| revokedReason | enum | conditional | | `[logout, person-status-change, role-change, credential-revoked, password-reset, factor-reset, refresh-token-reuse, idp-refresh-failed, admin-action]` | Reason |
| revokedBy | principal | optional | | | Acting principal (none for automatic revocation) |

### Derived IdP Role (per session)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| idpGroupMapping | reference | required | | target: idp-group-mapping | Mapping that matched |
| role | reference | required | | target: authorization-role; copied from the mapping | Derived role |
| derivedAt | datetime | required | | | Time of derivation |

### Refresh Token

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID | Primary key |
| tokenHash | string | required | | SHA-256 of a 256-bit random value; unique | Never stored in plaintext |
| status | enum | required | active | `[active, rotated, revoked]`; at most one `active` per session | Rotation state |
| issuedAt | datetime | required | | | Issue time |
| expiresAt | datetime | required | | ≤ session `absoluteExpiresAt` | Expiry |
| rotatedAt | datetime | optional | | | Time of use/rotation |
| replacedBy | reference | optional | | target: refresh token | Successor after rotation |
| revokedAt | datetime | optional | | | Revocation time |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| of | [person](./person.md) or [system-admin-account](./system-admin-account.md) | n:1 | no | Principal |
| derivedRoles | [authorization-role](./authorization-role.md) via [idp-group-mapping](./idp-group-mapping.md) | n:m | yes | IdP-derived roles (DM-10) |
| refreshTokens | Refresh Token | 1:n | yes | Token family |

## Lifecycle

```text
active → expired   (idle timeout or absolute lifetime)
   ↓
revoked            (logout, revocation triggers of REQ-164, token reuse)
```

**Transitions**:

- `→ active`: successful login (UC-01); audit `auth.login`
- `active → expired`: idle or absolute timeout; next request 401
- `active → revoked`: logout (`auth.logout`) or revocation (`auth.session-revoked`) on: person leaves `active`, role assignment revoked/expired, credential revoked, password or factor reset, refresh-token reuse, failed IdP refresh

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | A session can be created only for a person in status `active` or `invited` (with matching identity binding) or for an active system admin account | onLogin | REQ-004 |
| BR-02 | All sessions of a person are revoked within 60 s when the person leaves `active`, loses a role assignment, has a credential revoked or the password reset | onUpdate | REQ-164 |
| BR-03 | Refresh tokens rotate on every use; reuse of a rotated token revokes the session and all its tokens and is audited | onRefresh | REQ-165 |
| BR-04 | Idle timeout (default 30 min) and absolute lifetime (default 12 h) are enforced by OEA; sessions established through an IdP end no later than their IdP refresh token | onRequest | REQ-165, REQ-001 |
| BR-05 | Group claims are re-evaluated at every IdP refresh, at the latest every 15 minutes; if the IdP rejects the refresh or the refresh token has expired, the session ends (`idp-refresh-failed`); if the IdP cannot be reached, the IdP-derived roles no longer apply until a later evaluation succeeds | onRefresh, onRequest | Q16, Q17 |
| BR-06 | Permissions, tokens and secrets are never returned from the session store; tokens are stored only as hashes, the IdP refresh token only encrypted | onRead | REQ-165, REQ-188 |
| BR-07 | When a session ends (revoked or expired), the encrypted IdP refresh token is removed and all refresh tokens of the session are deleted immediately; the session record (principal, times, client IP, user agent, end reason) is deleted after the setting `sessionRetentionDays` (default 30). Security events remain in the audit log (REQ-175) | onSessionEnd, daily job | owner decision Q20, REQ-189 |

## Examples

**Example 1**: OIDC session of an architect

```yaml
id: "5c1e…"
principal: { person: 1042 }
clientType: electron
authMethod: oidc
authProvider: entra-prod
secondFactorVerified: true
idpGroupsClaimStatus: present
idpClaimsEvaluatedAt: 2026-10-14T09:10:00Z
derivedRoles:
  - { idpGroupMapping: "…", role: viewer }
absoluteExpiresAt: 2026-10-14T20:55:00Z
```

## Distinction from Similar Objects

- **NOT** [local-credential](./local-credential.md): the credential makes a login possible; the session is the result of a login.
- **NOT** a machine credential: service accounts receive short-lived access tokens without a session.

## Usage in Use Cases

- [UC-01: Login](../requirements/use-cases/UC-01-login.md)
- [UC-23: Manage Roles and Role Assignments](../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (revocation, access review)

## Concept Reference

- [§21.8 Security and Authorization](../concept/70-platform/21-api-architecture.md)

## Notes

Created for REQ-164, REQ-165, ADR-032 DM-10 and owner decision Q16 (security review SEC-06: no session/refresh-token store existed). Open: retention period of ended sessions and their derived roles (needed for access review; personal data, SEC-28).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Business Engineer | Initial draft from REQ-164, REQ-165, ADR-032 DM-10, Q16 |
| 0.2.0 | 2026-10-13 | Lukas Mathis | BR-07: retention of ended sessions (secrets immediately, record 30 days configurable; owner decision Q20) |
| 0.3.0 | 2026-10-15 | Business Engineer | `tokenHash` for the web session cookie; the CSRF token is derived from the cookie value and not stored (change `us-013-system-admin-login`, REQ-187) |
| 0.4.0 | 2026-10-15 | Business Engineer | `idpRefreshTokenKeyId`, `idpGroupIdentifiers`, `idpTokenExpiresAt`; session lifetime bounded by the IdP refresh token, BR-04/BR-05 clarified (change `us-001-oidc-login`, owner decision 2026-10-15) |
