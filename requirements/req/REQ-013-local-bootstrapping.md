---
id: REQ-013
title: Local bootstrapping when no system admin account exists
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - system-admin-account
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-013: Local bootstrapping when no system admin account exists

## Statement

The system MUST detect when no [system admin account](../../business-objects/system-admin-account.md) exists for an instance yet and, in that case, offer a local bootstrapping process that works without an external identity provider.

## Rationale

A freshly installed OEA instance by definition has no person/role data and possibly no configured IdP. Without a path that works without an external dependency, an instance without an already configured IdP could never be administered. UC-02, Basic Flow.

## Context

Concerns exclusively the state "no system admin account present" of an instance. As soon as an account exists, REQ-015 applies (no repeated bootstrapping processes).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Start of the instance without an existing system admin account
- Initial credential (password) chosen by the operator or accepted generated setup token

**Processing**:

- Check at startup/first access whether a system admin account exists
- If not: generate a one-time setup token or prompt for setting a password
- Create the system admin account with `mode: local` after the credential is confirmed

**Outputs**:

- Local system admin account in the state `bootstrapped`
- Confirmation to the operator that bootstrapping is complete

**Error Cases**:

- Process is aborted before the credential is confirmed → no account is created, the process can be restarted (see UC-02, E1)

## Acceptance Criteria

**AC1**:

- Given: a freshly installed instance without a system admin account
- When: it is started or accessed for the first time
- Then: the system offers the local bootstrapping process without requiring an external IdP

**AC2**:

- Given: a completed local bootstrapping process
- When: the operator logs in with the chosen credential
- Then: they gain access with system admin permissions

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: start an instance without existing configuration, run through the bootstrapping process
- [x] Measurement tool: test suite of the auth module / integration test on a fresh instance
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: none
- **Downstream effects**: REQ-015 (no repeated processes), REQ-016 (audit log), REQ-017 (secure token handover)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a local bootstrapping path, every instance without a previously configured IdP would be unadministrable – severe, blocks any use

## Trade-offs

- vs. security: a local bootstrap path is an additional attack vector that should possibly be disabled after initial configuration (see REQ-019)

## Implementation Notes

- Detection of "no account present" must be race-condition safe (see REQ-015)
- For setup token handover see REQ-017

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, Basic Flow.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
