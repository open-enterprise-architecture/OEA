# Audit Event Catalog

**Status:** living document – every change that writes a new event type adds it here
**Background:** [ADR-033](../adrs/ADR-033-audit-trail-hardening.md) §3 and §8, [REQ-175](../requirements/req/REQ-175-security-event-logging.md), business object [audit event](../business-objects/audit-event.md)

All audit records are rows of `audit.events` (with optional details in `audit.event_changes`) and links of one hash chain ([audit hash chain](audit-hash-chain.md)). Event type names are stable; a renamed event type is a new event type.

## Common Fields

| Field | Content |
|---|---|
| `event_type` | Stable name from this catalog |
| `event_category` | `change`, `security`, `access` or `system` |
| `severity` | `info`, `warning`, `high` or `critical` |
| `outcome` | `success`, `failure` or `denied` |
| `occurred_at` | Time of the event (database time, UTC) |
| `source` | `UI`, `API`, `Job`, `Import` or `CLI`; HTTP requests are recorded as `API` until clients are distinguished; `CLI` is a host-side command such as `admin recover` |
| `source_ip` | Remote address of the HTTP request; empty for jobs and commands |
| `request_id` | Correlation ID (`X-Request-Id`), also present in the application log |
| `actor_type` | `person`, `service-account`, `system-admin-account`, `system` or `unauthenticated` |
| `actor_person_id` / `actor_admin_id` | Pseudonymous key of the actor; never names or e-mail addresses |
| `target_type` / `target_id` | Affected object |
| `entity_id` / `entity_name` | Affected entity for `entity.*` events; the name is denormalized and empty for modeled persons |
| `agent_label` / `agent_run` | Informational values of the headers `X-Agent-Id` and `X-Agent-Run` (control characters removed, at most 100 characters); never used for decisions (ADR-033 §7) |
| `details` | Event-type-specific supplement (JSON); never secrets, tokens, passwords or hashes of them |

## Flood Suppression

Repeated security events of the same type, actor, source address and operation are recorded individually up to 20 per minute; further events of that minute are counted and recorded as one **summary event** of the same type after the minute has ended ([REQ-175](../requirements/req/REQ-175-security-event-logging.md) AC5). It applies to `authz.denied` and to `system.bootstrap-rejected` with reason `throttled`. A summary event has the actor of the suppressed events, the source address but no request ID, no target ID, and the details `summary` (`true`), `suppressedCount`, `windowStart`, `windowEnd` and `operation`. The counters are kept in memory of the backend; on a regular shutdown, open windows are written as summaries.

## Event Types

### `system.bootstrapped`

Local bootstrapping completed (UC-02, [REQ-016](../requirements/req/REQ-016-audit-log-bootstrapping.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | `system-admin-account` – the account just created |
| Target | `system-admin-account`, ID of the account |
| Details | `mode` (`local`), `instanceSlug` (no login name: ADR-033 §3) |

### `system.bootstrap-rejected`

A bootstrapping attempt was rejected (REQ-015, REQ-016, REQ-017).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `denied` |
| Actor | `unauthenticated` |
| Target | – |
| Details | `mode` (`local`), `reason` (`invalid-setup-token`, `already-bootstrapped`, `validation-failed` or `throttled`: the source address is throttled after too many wrong setup tokens, REQ-168), `fields` (names of invalid fields for `validation-failed`); `throttled` rejections are subject to the [flood suppression](#flood-suppression) with the operation `completeSetup` |

### `audit.checkpoint`

Checkpoint of the chain head; its `chain_seq` and `record_hash` are also written to the application log (ADR-033 §6).

| Field | Value |
|---|---|
| Category / severity / outcome | `system` / `info` / `success` |
| Actor | `system` |
| Source | `Job` |
| Target / details | – |

### `auth.login`

Successful login of the local system admin account (US-013 AC2), of a person with a password (US-011) or of a person through an identity provider (US-001, [REQ-005](../requirements/req/REQ-005-audit-log-login.md), REQ-175).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | `system-admin-account` or `person` |
| Target | `session`, ID of the new session |
| Details | Local login of the system admin account: `method` (`password` or `password-totp`), `setupPhase` (`true` if the login needed no second factor, [ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md)). Password login of a person ([US-011](../requirements/user-stories/US-011-password-minimal.md)): `method` (`password`, `password-totp` after the TOTP step, [US-010](../requirements/user-stories/US-010-totp-login.md), `password-passkey` after the passkey step, or `passkey` for the passwordless login, [US-009](../requirements/user-stories/US-009-passkey-login.md)), `secondFactor`, `temporaryPassword` (`true` for an initial or reset password), `passwordExpired` (`true` if the self-chosen password exceeded the expiry of the password policy, [US-027](../requirements/user-stories/US-027-configure-password-policies.md)); the session must change the password first if either is `true`. OIDC login: `method` (`oidc`), `providerId`, `secondFactor` (from `amr`) |

### `auth.login-failed`

Rejected local login step ([REQ-168](../requirements/req/REQ-168-local-auth-throttling.md), REQ-175). Written independently of the request transaction; contains no login name, so unknown and existing names cannot be told apart.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `failure` |
| Actor | `unauthenticated` |
| Target | – |
| Details | Local login: `reason` (`invalid-credentials` for the password step, including throttled attempts; `wrong-second-factor` for the TOTP or passkey step). Passwordless passkey login ([US-009](../requirements/user-stories/US-009-passkey-login.md)): `method` (`passkey`), `reason` (`invalid-credentials` for every failure, including an unknown passkey and a throttled address). OIDC login: `mechanism` (`oidc`), `providerId` if known, `reason` (`state-invalid`, `cancelled`, `provider-error`, `provider-unavailable`, `token-invalid`, `identity-unknown`, `person-not-active`) |

### `auth.throttled`

A login name or a source address reached its throttling threshold (REQ-168), or an authenticated principal reached a rate limit (REQ-186); written once per lock or limit window, not for every throttled attempt.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `denied` |
| Actor | `unauthenticated` for the scopes `account` and `address`; the throttled principal for the scope `principal` |
| Target | – |
| Details | `scope` (`account`: 5 failures of one login name within 15 minutes; `address`: 100 failures of local logins and setup-token attempts from one source address within 10 minutes; `principal`: the principal reached a rate limit), for `principal` also `limit` (`requests` or `entity-batch`) and `limitPerMinute` |

### `auth.logout`

A session was ended by its principal ([REQ-164](../requirements/req/REQ-164-session-termination-revocation.md) AC1).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | the principal of the session |
| Target | `session`, ID of the session |
| Details | – |

### `auth.idp-roles-derived`

Roles derived from IdP group mappings for a session, at login and whenever a re-evaluation changes the set ([US-147](../requirements/user-stories/US-147-map-idp-group-to-role.md), ADR-032 DM-10).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | `person` – the session's person |
| Target | `session`, ID of the session |
| Details | `trigger` (`login` or `refresh`), `roles` (role IDs), `mappings` (mapping IDs), `groupsClaim` (`present`, `missing`, `truncated` or the reason of a withdrawal) |

### `auth.idp-groups-unusable`

The group claims of an IdP session could not be used, so no IdP-derived role applies (fail closed, US-147 AC3, REQ-165).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `failure` |
| Actor | `person` – the session's person |
| Target | `session`, ID of the session |
| Details | `reason` (`missing` or `truncated` groups claim, `idp-unreachable`, `refresh-unavailable`) |

### `session.revoked`

A session was ended by the system ([REQ-164](../requirements/req/REQ-164-session-termination-revocation.md), REQ-165); logouts are `auth.logout`.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | `system` |
| Target | `session`, ID of the session |
| Details | `reason` (`idp-refresh-failed`: the IdP refresh token expired or was rejected; `person-status-change`, `password-reset` and `factor-reset`: ended by an administrator, who is then the actor) |

### `authz.denied`

A request was refused with 403 by an authorization decision ([REQ-175](../requirements/req/REQ-175-security-event-logging.md) AC1, ADR-032 §8): a permission declared by the contract is missing, a permission of a contract condition is missing, the setup phase requires a second factor, or a separation-of-duties rule applies. Subject to the [flood suppression](#flood-suppression). Not written for requests to operations without a contract declaration and for rejections of the CSRF/`Origin` check.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `denied` |
| Actor | the requesting principal |
| Target | `target_id`: the path variable `id` of the request, or its only path variable, if any; `target_type`: the affected object type where the decision knows it, e.g. `idp-group-mapping` |
| Details | `reason` (`missing-permission`, `condition`, `second-factor-required`, `self-administration`: SoD-1, US-147 AC5, or `admin-reset-restricted`: a password or factor reset of an administrator by someone else than the system admin account, REQ-173 AC3), `operation` (operation ID of the contract); summary events additionally `summary`, `suppressedCount`, `windowStart`, `windowEnd` |

### `auth.factor-added`

A second factor was enrolled and confirmed by the system admin account ([REQ-171](../requirements/req/REQ-171-mfa-privileged-accounts.md)) or a person ([US-021](../requirements/user-stories/US-021-totp-enrollment.md), REQ-025 AC1).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the account or person that enrolled the factor |
| Target | `credential`, ID of the local credential |
| Details | `type` (`totp` or `passkey`, [US-022](../requirements/user-stories/US-022-passkey-enrollment.md)), `label`; never the secret, a code or a public key |

### `auth.factor-enrollment-failed`

The confirmation of a TOTP enrollment failed ([US-024](../requirements/user-stories/US-024-audit-log-enrollment.md), REQ-025 AC2). Written in its own transaction.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `failure` |
| Actor | the account or person that confirmed |
| Target | `credential`, ID of the pending credential as sent |
| Details | `type` (`totp` or `passkey`), `reason` (`invalid-code`: wrong code, the enrollment stays pending; `verification-failed`: the passkey response failed the WebAuthn verification, the registration is used up; `enrollment-expired`: unknown, foreign or expired enrollment); never the secret, a code or a public key |

### `auth.factor-removed`

A principal removed one of its own second factors (change `us-021-person-totp`); the other sessions of the principal were revoked.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the principal itself |
| Target | `credential`, ID of the removed credential |
| Details | `type`, `label`, `otherSessionsRevoked` (count) |

### `system.setup-phase-ended`

The first action that gives another identity access ended the setup phase ([ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | `system-admin-account` |
| Target | `system-settings`, `1` |
| Details | `triggeringAction` (`identity-binding` or `idp-group-mapping`) |

### `system-admin-account.deactivated`

The local system admin account was deactivated, or the deactivation was refused because it is the last usable path to `system-admin` ([US-019](../requirements/user-stories/US-019-disable-local-admin.md), ADR-032 SoD-7).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success`, or `security` / `warning` / `denied` |
| Actor | the administrator |
| Target | `system-admin-account`, ID of the account |
| Details | `sessionsRevoked` (count) on success; `reason` (`last-admin-path`) on denial |

### `system.admin-recovered`

The break-glass command `admin recover` restored the local system admin account after a complete lockout ([REQ-173](../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC4, UC-02 E4).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Source | `CLI`, without source address and request ID |
| Actor | `system` (the operator is not an OEA principal) |
| Target | `system-admin-account`, ID of the account |
| Details | `reactivated`, `secondFactorReplaced`, `sessionsRevoked` (count); never the password or the TOTP secret |

### `role.assigned`

A role was assigned locally to a person ([US-144](../requirements/user-stories/US-144-assign-and-revoke-local-role.md), ADR-032 §7).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` for a privileged role, otherwise `info` / `success` |
| Actor | the administrator |
| Target | `role-assignment`, ID of the assignment |
| Details | `personId`, `roleId`, `validFrom`, `validTo` if set, `reason` if set, `sodConflict` |

### `role.revoked`

A local assignment was revoked; all sessions of the person were revoked with it (REQ-164).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` for a privileged role, otherwise `info` / `success` |
| Actor | the administrator |
| Target | `role-assignment`, ID of the assignment |
| Details | `personId`, `roleId`, `reason` if given, `sessionsRevoked` (count) |

### `role.assignment-expired`

A local assignment reached its `validTo`; written once by a job that runs every minute. The role stops applying at `validTo` regardless of when the event is written.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Source | `Job` |
| Actor | `system` |
| Target | `role-assignment`, ID of the assignment |
| Details | `personId`, `roleId`, `validTo` |

### `auth-provider.changed`

An OIDC provider was created or changed through the admin API (REQ-175, [REQ-188](../requirements/req/REQ-188-secrets-key-management.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `auth-provider`, ID of the provider |
| Details | `action` (`created` or `updated`), `issuer`, `enabled`, `clientSecretChanged`; never the client secret |

### `person.created`

A person was created in status `invited` (pre-provisioning).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `status` (`invited`); no name or e-mail address (ADR-033 §3) |

### `person.activated`

The first successful login of an invited person changed its status to `active` ([REQ-007](../requirements/req/REQ-007-lifecycle-transition-invited-active.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | `person` – the person itself |
| Target | `person`, ID of the person |
| Details | `trigger` (`first-login`) |

### `person.status-changed`

An administrator changed the status of a person ([US-148](../requirements/user-stories/US-148-revoke-access-on-offboarding.md), REQ-164 AC2). The ended sessions are recorded as `session.revoked` (reason `person-status-change`), the revoked assignments of an offboarding as `role.revoked` (reason `offboarding`).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` (`info` for the return to `active`) / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `from`, `to`, `reason` if given, `sessionsRevoked`, `assignmentsRevoked` (counts) |

### `person.password-set`

An administrator set the initial password of a person ([US-023](../requirements/user-stories/US-023-initial-password-admin.md), REQ-024).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `origin` (`initial`), `expiresAt`, `generated` (`true` if OEA generated the password, [US-026](../requirements/user-stories/US-026-password-generator.md)); never the password |

### `person.password-reset`

An administrator reset the local password of a person ([US-030](../requirements/user-stories/US-030-password-reset-by-admin.md), REQ-031): the old password was revoked and replaced by a generated temporary one. The ended sessions are recorded as `session.revoked` (reason `password-reset`). A refused reset of an administrator is recorded as `authz.denied` (reason `admin-reset-restricted`).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `expiresAt`, `sessionsRevoked` (count), `reason` if given; never the password |

### `person.second-factors-reset`

An administrator reset the second factors of a person who lost every device ([REQ-173](../requirements/req/REQ-173-account-recovery-temporary-passwords.md) AC5). The ended sessions are recorded as `session.revoked` (reason `factor-reset`). A refused reset of an administrator is recorded as `authz.denied` (reason `admin-reset-restricted`).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `credentialsRevoked`, `sessionsRevoked` (counts), `reason` if given |

### `settings.second-factor-policy-changed`

An administrator changed the enforcement of a second factor or the exempt roles ([US-031](../requirements/user-stories/US-031-configure-2fa-enforcement.md), [US-028](../requirements/user-stories/US-028-role-based-2fa-exemption.md), REQ-020, REQ-029).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `settings`, `second-factor-policy` |
| Details | `old`, `new`: each with `enforced` and `exemptRoles` |

### `settings.password-policy-changed`

An administrator changed the password policy or the switch of password login of persons ([US-027](../requirements/user-stories/US-027-configure-password-policies.md), REQ-028).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `settings`, `password-policy` |
| Details | `old`, `new`: each with `minLength`, `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars`, `expiryDays`, `historyCount`, `personPasswordLogin` |

### `auth.password-changed`

A person or the system admin account changed the own local password (REQ-173 AC2); the other sessions of the principal were revoked.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the principal itself |
| Target | `credential`, ID of the new password credential |
| Details | `otherSessionsRevoked` (count); never a password |

### `person.identity-bound` / `person.identity-unbound`

A person was bound to, or unbound from, the pair of issuer and subject of a provider ([REQ-166](../requirements/req/REQ-166-oidc-hardening-identity-binding.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `person`, ID of the person |
| Details | `identityId`, `authProviderId`; `linkMethod` (`admin-entered`) for bindings |

### `idp-mapping.created` / `idp-mapping.changed` / `idp-mapping.deleted`

An IdP group mapping was created, changed or (softly) deleted ([REQ-167](../requirements/req/REQ-167-immutable-admin-claim-mapping.md), US-147).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the administrator |
| Target | `idp-group-mapping`, ID of the mapping |
| Details | `authProviderId`, `groupIdentifier`, `roleId`, `active` (state after the change) |

### `metamodel.package-imported`

A metamodel package was imported ([US-033](../requirements/user-stories/US-033-import-metamodel.md), [REQ-033](../requirements/req/REQ-033-metamodel-import.md), ADR-002).

| Field | Value |
|---|---|
| Category / severity / outcome | `change` / `info` / `success` |
| Actor | the importing principal |
| Target | `metamodel-package`, package identifier |
| Details | `packageId`, `version`, `batchId`, `metatypesCreated`, `metatypesUpdated` |

### `metamodel.metatype-imported`

A metatype was created or changed by a package import; one event per metatype with the batch ID of the import (US-033 AC1).

| Field | Value |
|---|---|
| Category / severity / outcome | `change` / `info` / `success` |
| Actor | the importing principal |
| Target | `metatype`, ID of the metatype |
| Details | `batchId`, `packageId`, `action` (`created` or `updated`) |

### `metamodel.package-rejected`

A package import by an authorized principal was rejected; nothing was stored. Written independently of the rolled-back import.

| Field | Value |
|---|---|
| Category / severity / outcome | `change` / `warning` / `failure` |
| Actor | the importing principal |
| Target | `metamodel-package` |
| Details | `reason` (`invalid-package`, `unsupported-package-content`, `conflict`), `packageId` if valid |

### `entity.created` / `entity.updated`

An entity was created or changed ([US-141](../requirements/user-stories/US-141-entity-batch-import.md), ADR-022, ADR-033 §3). A write that changes nothing is not audited; rejected items are not audited.

| Field | Value |
|---|---|
| Category / severity / outcome | `change` / `info` / `success` |
| Actor | the writing person |
| Target | `entity`, ID of the entity; `entity_id` and `entity_name` |
| Details | `batchId` (one per request), `metatypeId`, `externalId` if set, `version` (after the write) |
| Changes | one `audit.event_changes` row per changed system field (`name`, `description`, `is_logical`) and property with old and new value as text; on create the old values are empty |

### `catalog.created`

A catalog was created ([US-046](../requirements/user-stories/US-046-create-catalog.md), [REQ-043](../requirements/req/REQ-043-create-catalog.md)). Rejected creations (name collision, invalid input, missing permission) are not audited.

| Field | Value |
|---|---|
| Category / severity / outcome | `change` / `info` / `success` |
| Actor | the creating person |
| Target | `catalog`, ID of the catalog |
| Details | `name`, `primaryMetatypeId`, `scope` (`instance` or `personal`) |

## Planned Event Types

Named in ADR-033 §8 and written by later changes: `role.assigned`, `role.revoked`, `settings.changed`, `settings.audit-retention-changed`, `visibility.changed`, `visibility.override-changed`, `service-account.created`, `service-account.secret-rotated`, `service-account.deactivated`, `analytics.query.executed`, `analytics.query.rejected`, `audit.exported`, `audit.retention-purged`, `audit.integrity-verified`, `audit.integrity-violation`, `entity.deleted` and further `entity.*`, `metamodel.*`.
