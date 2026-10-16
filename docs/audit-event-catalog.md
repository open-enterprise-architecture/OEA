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
| `source` | `UI`, `API`, `Job` or `Import`; HTTP requests are recorded as `API` until clients are distinguished |
| `source_ip` | Remote address of the HTTP request; empty for jobs |
| `request_id` | Correlation ID (`X-Request-Id`), also present in the application log |
| `actor_type` | `person`, `service-account`, `system-admin-account`, `system` or `unauthenticated` |
| `actor_person_id` / `actor_admin_id` | Pseudonymous key of the actor; never names or e-mail addresses |
| `target_type` / `target_id` | Affected object |
| `entity_id` / `entity_name` | Affected entity for `entity.*` events; the name is denormalized and empty for modeled persons |
| `agent_label` / `agent_run` | Informational values of the headers `X-Agent-Id` and `X-Agent-Run` (control characters removed, at most 100 characters); never used for decisions (ADR-033 §7) |
| `details` | Event-type-specific supplement (JSON); never secrets, tokens, passwords or hashes of them |

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
| Details | `mode` (`local`), `reason` (`invalid-setup-token`, `already-bootstrapped` or `validation-failed`), `fields` (names of invalid fields for `validation-failed`) |

### `audit.checkpoint`

Checkpoint of the chain head; its `chain_seq` and `record_hash` are also written to the application log (ADR-033 §6).

| Field | Value |
|---|---|
| Category / severity / outcome | `system` / `info` / `success` |
| Actor | `system` |
| Source | `Job` |
| Target / details | – |

### `auth.login`

Successful login of the local system admin account (US-013 AC2) or of a person through an identity provider (US-001, [REQ-005](../requirements/req/REQ-005-audit-log-login.md), REQ-175).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `info` / `success` |
| Actor | `system-admin-account` or `person` |
| Target | `session`, ID of the new session |
| Details | Local login: `method` (`password` or `password-totp`), `setupPhase` (`true` if the login needed no second factor, [ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md)). OIDC login: `method` (`oidc`), `providerId`, `secondFactor` (from `amr`) |

### `auth.login-failed`

Rejected local login step ([REQ-168](../requirements/req/REQ-168-local-auth-throttling.md), REQ-175). Written independently of the request transaction; contains no login name, so unknown and existing names cannot be told apart.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `failure` |
| Actor | `unauthenticated` |
| Target | – |
| Details | Local login: `reason` (`invalid-credentials` for the password step, including throttled attempts; `wrong-second-factor` for the TOTP step). OIDC login: `mechanism` (`oidc`), `providerId` if known, `reason` (`state-invalid`, `cancelled`, `provider-error`, `provider-unavailable`, `token-invalid`, `identity-unknown`, `person-not-active`) |

### `auth.throttled`

A login name or a source address reached its throttling threshold (REQ-168); written once per lock, not for every throttled attempt.

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `denied` |
| Actor | `unauthenticated` |
| Target | – |
| Details | `scope` (`account`: 5 failures of one login name within 15 minutes; `address`: 100 failures from one source address within 10 minutes) |

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
| Details | `reason` (`idp-refresh-failed`: the IdP refresh token expired or was rejected) |

### `authz.denied`

A request was refused by a separation-of-duties rule (ADR-032).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `warning` / `denied` |
| Actor | the requesting principal |
| Target | the affected object type, e.g. `idp-group-mapping` |
| Details | `reason` (`self-administration`: SoD-1, US-147 AC5), `operation` |

### `auth.factor-added`

A second factor was enrolled and confirmed ([REQ-171](../requirements/req/REQ-171-mfa-privileged-accounts.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | the account that enrolled the factor |
| Target | `credential`, ID of the local credential |
| Details | `type` (`totp`), `label` if given; never the secret or a code |

### `system.setup-phase-ended`

The first action that gives another identity access ended the setup phase ([ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md)).

| Field | Value |
|---|---|
| Category / severity / outcome | `security` / `high` / `success` |
| Actor | `system-admin-account` |
| Target | `system-settings`, `1` |
| Details | `triggeringAction` (`identity-binding` or `idp-group-mapping`) |

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

## Planned Event Types

Named in ADR-033 §8 and written by later changes: `role.assigned`, `role.revoked`, `settings.changed`, `settings.audit-retention-changed`, `visibility.changed`, `visibility.override-changed`, `service-account.created`, `service-account.secret-rotated`, `service-account.deactivated`, `analytics.query.executed`, `analytics.query.rejected`, `audit.exported`, `audit.retention-purged`, `audit.integrity-verified`, `audit.integrity-violation`, `entity.deleted` and further `entity.*`, `metamodel.*`.
