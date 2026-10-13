---
id: REQ-172
title: Re-authentication and notification for sensitive account actions
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-03
    - UC-02
  business_objects:
    - local-credential
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-172: Re-Authentication and Notification for Sensitive Account Actions

## Statement

The system **MUST** require a fresh authentication (within the last 5 minutes, configurable) before adding or removing authentication methods, changing the own password, creating or rotating service account secrets and changing authentication provider configuration, and **MUST** notify the affected person in-app when an authentication method is added or removed.

## Rationale

A stolen session (XSS, unlocked device) could otherwise be turned into persistent access by registering an attacker's passkey or creating a long-lived service account secret.

## Context

Source: security review SEC-11 (proposal PR-16). REQ-026 states that the session serves as proof of authentication; this REQ restricts that for the listed actions.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Request for one of the listed sensitive actions; time of the last authentication of the session

**Processing**:

- If the last authentication is older than the configured window (default 5 min): require re-authentication (password + second factor, passkey assertion, or OIDC re-login with `max_age`)
- After adding/removing an authentication method: create an in-app notice for the person and an audit event

**Outputs**:

- Re-authentication prompt; notice on next login

**Error cases**:

- Re-authentication fails → action not executed; counted per REQ-168

## Acceptance Criteria

**AC1**:

- Given: a session authenticated 30 minutes ago
- When: the user tries to register a new passkey
- Then: the system requires re-authentication before the registration ceremony starts

**AC2**:

- Given: an admin session authenticated more than 5 minutes ago
- When: the admin rotates a service account secret
- Then: re-authentication is required

**AC3**:

- Given: a new TOTP credential added to a person
- When: that person next logs in
- Then: a notice lists the new credential with time and label, and an audit event exists

## Verification Method

- [ ] Method: test (automated, controlled clock)
- [ ] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-026, REQ-155
- **Follow-on effects**: REQ-031
- **Conflicts**: REQ-026 (session as sufficient proof)

## Risks of Non-Fulfillment

- Risk 1: persistent account compromise from a short-lived session theft – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-11 (PR-16) |
