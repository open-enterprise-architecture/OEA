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

## Planned Event Types

Named in ADR-033 §8 and written by later changes: `auth.login`, `auth.logout`, `session.revoked`, `authz.denied`, `auth.throttled`, `role.assigned`, `role.revoked`, `idp-mapping.changed`, `auth-provider.changed`, `settings.changed`, `settings.audit-retention-changed`, `visibility.changed`, `visibility.override-changed`, `service-account.created`, `service-account.secret-rotated`, `service-account.deactivated`, `analytics.query.executed`, `analytics.query.rejected`, `audit.exported`, `audit.retention-purged`, `audit.integrity-verified`, `audit.integrity-violation`, `entity.*`, `metamodel.*`.
