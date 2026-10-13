---
id: REQ-189
title: Data subject rights and minimization for persons
type: compliance
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-14
    - UC-10
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/60-integrations/20-grc-gdpr-isms-integration.md
  adrs:
    - ADR-024
    - ADR-033
supersedes: []
superseded_by: []
---

# REQ-189: Data Subject Rights and Minimization for Persons

## Statement

The system **MUST** allow an authorized operator to export all personal data stored about a person (profile, credential metadata, role assignments, audit events as actor, EA content references) in a machine-readable format, and to anonymize a person so that name and e-mail are removed from all application tables and from audit display while audit records remain linked to a pseudonymous identifier; personal data of actors **MUST NOT** be exposed to principals without `audit:read`.

## Rationale

OEA stores personal data of its own users and of modeled persons (e.g. process task assignments). Former employees can request access and erasure; at the same time the audit trail must be retained (REQ-083).

## Context

Source: security review SEC-28 (proposal PR-33). ADR-024 stored `actor_name` and `actor_email` as mandatory, immutable fields and the portal audit endpoint returned `actorEmail` to every reader. ADR-033 §3 replaces this with pseudonymous actors: audit records contain only the actor type and the surrogate key of the principal (`actor_person_id` or `actor_admin_id`), no names and no e-mail addresses; the protected identity mapping is the set of identity tables (`persons`, `system_admin_accounts`), from which names and e-mail addresses are resolved at read time and only for principals holding `audit:read`. Anonymization removes name and e-mail from the identity tables; append-only audit records are not modified. Failed logins with an unknown identifier store the attempted identifier only as a keyed HMAC. Names of modeled persons (EA content, metatype `person`) are not copied into audit records (`entity_name` is NULL; owner confirmation 2026-10-13). `/portal/audit/events` is removed or requires `audit:read`. The legal basis for retaining pseudonymous audit records must be documented in the operator documentation.

## Type-Specific Fields

### For type=compliance

**Regulatory framework**: GDPR (EU 2016/679), Swiss FADP

**Specific provision**: GDPR Art. 5(1)(c) (data minimization), Art. 15 (access), Art. 17 (erasure) incl. Art. 17(3)(b), Art. 20 (portability), Art. 30 (records of processing)

**Evidence requirement**: export and anonymization functions demonstrable; record of processing for OEA itself in the operator documentation

**Audit relevance**: yes

## Acceptance Criteria

**AC1**:

- Given: a person with audit history and process task assignments
- When: the operator runs the data export for that person
- Then: a JSON file containing all listed data categories is produced and the export is audited

**AC2**:

- Given: the same person anonymized
- When: audit events and entity history are retrieved
- Then: actor name and e-mail are replaced by a pseudonym and no table outside the protected identity mapping contains the original e-mail; the audit records themselves are unchanged and still reference the person's surrogate key

**AC5** (No identity data in the audit store):

- Given: audit events created by a person, a service account and the local system admin account, and an audit event for a change of a modeled person entity
- When: the columns of `audit.events` and `audit.event_changes` and the SIEM export are inspected
- Then: they contain actor type and surrogate keys only, no actor name or e-mail address, and the event of the modeled person has no `entity_name`

**AC3**:

- Given: a principal without `audit:read`
- When: it calls any portal or view endpoint
- Then: no actor e-mail is present in any response

**AC4** (Retention of ended sessions, owner decision Q20):

- Given: a session that ended by logout or expiry, and the default setting `sessionRetentionDays = 30`
- When: the session ends, and again 31 days later
- Then: the IdP refresh token and all refresh tokens of the session are deleted at the end; the session record (principal, times, client IP, user agent, end reason) is deleted after 30 days, while the login and logout events remain in the audit log

## Verification Method

- [ ] Method: test (automated) + inspection (record of processing in operator documentation)
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes (AC1–AC3, AC5)

## Dependencies

- **Prerequisites**: REQ-151, REQ-157
- **Follow-on effects**: REQ-083, REQ-175, REQ-190
- **Conflicts**: none known (ADR-024 `actor_email` NOT NULL, denormalized, is superseded by ADR-033 §3)

## Risks of Non-Fulfillment

- Risk 1: inability to fulfil data subject requests; e-mail harvesting for phishing – medium

## Implementation Notes

- Person anonymization uses `detailLevel: anonymized` in `person.md`, which has no process yet (follow-up for business engineer)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-28 (PR-33) |
| 0.2.0 | 2026-10-13 | Lukas Mathis | AC4: retention of ended sessions (owner decision Q20) |
| 0.3.0 | 2026-10-13 | requirements-engineer | ADR-033 §3: pseudonymous actor (actor type + surrogate key), identity resolved only for `audit:read`, no names of modeled persons in audit records; AC2 clarified, AC5 added; ADR-024 conflict resolved |
