---
id: REQ-175
title: Security event logging
type: compliance
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-14
    - UC-01
  business_objects:
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-024
    - ADR-033
    - ADR-034
supersedes: []
superseded_by: []
---

# REQ-175: Security Event Logging

## Statement

The system **MUST** record in the audit log, with timestamp, principal, source address, outcome and correlation ID: logout and session revocation, authorization failures (403), role and role-assignment changes, authentication provider and settings changes, visibility configuration changes, service account creation, rotation and deactivation, throttling and lockout events, analytics queries and bulk exports, using the event types of the audit event catalog; repeated authorization failures, throttling and failed-login events of the same principal or source address and operation **MUST** be recorded individually up to 20 per minute and beyond that as one summary event per minute carrying the count; application logs **MUST NOT** contain secrets, tokens, passwords or TOTP codes.

## Rationale

Without these events an incident responder cannot reconstruct a 403 probe storm, a self-granted role or which service account wrote which entities.

## Context

Source: security review SEC-14 (proposal PR-19). Existing audit REQs: REQ-005 (login), REQ-016 (bootstrap), REQ-025 (enrollment), REQ-031 (reset), REQ-128 (override reads).

ADR-033 §8: security events are records of the same audit trail and hash chain as change events (no separate security log), with the common fields `event_category`, `severity`, `outcome`, pseudonymous actor fields (REQ-189), `agent_id`, `source`, `source_ip`, `request_id`, `target_type`, `target_id` and event-specific `details`. The event catalog with stable type names (e.g. `auth.login`, `auth.logout`, `session.revoked`, `authz.denied`, `auth.throttled`, `role.assigned`, `role.revoked`, `idp-mapping.changed`, `auth-provider.changed`, `settings.changed`, `settings.audit-retention-changed`, `visibility.changed`, `visibility.override-changed`, `service-account.created|secret-rotated|deactivated`, `audit.exported`, `audit.checkpoint`, `audit.retention-purged`, `audit.integrity-verified|violation`) is maintained in `docs/audit-event-catalog.md` (follow-up of ADR-033). Because audit records cannot be deleted before the retention period, the suppression rule bounds audit flooding (owner decision Q2); rate limits (REQ-186) apply in addition.

ADR-034 §6: raw analytics produce exactly one event per authenticated request, `analytics.query.executed` or `analytics.query.rejected`, with principal, query hash, literal-free query fingerprint, referenced reporting views, outcome and metrics (duration, row count, result bytes); missing permission produces `authz.denied` instead.

**Implementation (change s3-throttling-and-denials, 2026-10-28):** `authz.denied` is written at every authorization decision (contract permissions, contract conditions, second factor of the setup phase, separation of duties) with principal, operation ID, target ID, source address and request ID (AC1). The summary event of the suppression rule (AC5) is an `authz.denied` record with `summary`, `suppressedCount` and the window, not a separate event type; the counters are kept per backend instance (spec `platform/authorization`, `docs/audit-event-catalog.md`).

## Type-Specific Fields

### For type=compliance

**Regulatory framework**: ISO/IEC 27001:2022, DORA

**Specific provision**: ISO/IEC 27001 Annex A 8.15 (logging), A 8.16 (monitoring); DORA Art. 12

**Evidence requirement**: event catalog document; sample export; CI log scan result

**Audit relevance**: yes

## Acceptance Criteria

**AC1**:

- Given: a viewer calls an admin endpoint
- When: the request is rejected with 403
- Then: an audit event `authz.denied` with principal, operation and target ID exists

**AC2**:

- Given: an admin assigns a role to a person
- When: saved
- Then: an audit event `role.assigned` with actor, target person, role and validity exists

**AC3**:

- Given: the full test suite runs with log capture
- When: the logs are scanned for the test passwords, TOTP secrets, client secrets and bearer tokens used in the tests
- Then: none is found

**AC4**:

- Given: audit events for a period
- When: exported via the API by a principal with `audit:read`
- Then: the export is available as JSON Lines suitable for SIEM ingestion, and the export itself is audited

**AC5** (Flood suppression):

- Given: a principal that triggers 50 requests rejected with 403 on the same operation within one minute
- When: the audit log for that minute is inspected
- Then: it contains 20 individual `authz.denied` events and one summary event with the count of the further 30 rejections

**AC6** (Analytics events):

- Given: a principal with `analytics:query` that submits one valid query and one query rejected by the statement validator
- When: the audit log is inspected
- Then: it contains one `analytics.query.executed` and one `analytics.query.rejected` event, each with principal, query hash, query fingerprint and outcome

## Verification Method

- [ ] Method: test (automated) + log scan in CI
- [ ] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-151, REQ-174
- **Follow-on effects**: REQ-161, REQ-164, REQ-168, REQ-176, REQ-179, REQ-186
- **Conflicts**: REQ-189 (personal data in audit records is pseudonymized; resolved by the pseudonymous actor fields of ADR-033 §3)

## Risks of Non-Fulfillment

- Risk 1: undetectable abuse and missing forensic evidence – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-14 (PR-19) |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-033: reference to the audit event catalog and common fields; suppression rule for repeated events (statement, AC5). ADR-034: analytics event types `analytics.query.executed`/`analytics.query.rejected` (AC6) |
| 0.3.0 | 2026-10-28 | Change s3-throttling-and-denials | AC1 and AC5 implemented for permission denials (security re-review WS-01, #75); summary event as `authz.denied` record |
