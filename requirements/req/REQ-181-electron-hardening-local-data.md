---
id: REQ-181
title: Electron runtime hardening and local data protection
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-05
    - UC-01
  business_objects:
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-008
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-181: Electron Runtime Hardening and Local Data Protection

## Statement

The Electron client **MUST** enable `sandbox` and `contextIsolation` and disable `nodeIntegration` for all renderers, **MUST** block navigation and new windows to origins other than the app and the configured OEA server, **MUST** open external links only after URL scheme validation (`https`), **MUST** validate all IPC arguments against a schema, **MUST** set restrictive Electron fuses, **MUST** store tokens in OS-protected storage, and **MUST** encrypt the offline cache and delete it on logout or loss of access.

## Rationale

An XSS in the renderer of an under-hardened Electron app becomes code execution on the architect's machine. The offline cache of a laptop holds repository data, including values the user may have lost access to.

## Context

Source: security review SEC-19 (proposal PR-25). ADR-009 covers `contextIsolation`, `preload` and `nodeIntegration=false`; the remaining items need an amending ADR (follow-up for the solution architect).

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-19); Electron security checklist

**Binding strength**: hard

**Impact of non-compliance**: remote code execution on client machines; data left on lost devices

## Acceptance Criteria

**AC1**:

- Given: the packaged app
- When: an automated check (e.g. Electronegativity plus a fuse check) runs in CI
- Then: it reports no enabled `nodeIntegration`, no disabled `sandbox` or `contextIsolation`, no `webview` tag, and no enabled `RunAsNode` fuse

**AC2**:

- Given: renderer code calls `window.open('https://evil.example')` or navigates there
- When: executed
- Then: navigation is blocked

**AC3**:

- Given: a preload API that writes files
- When: called with a path outside the user-selected export directory
- Then: it is rejected

**AC4**:

- Given: a user logs out or the server revokes the session (REQ-164)
- When: the client next starts
- Then: the offline cache of that user no longer exists on disk

**AC5**:

- Given: the offline cache on disk
- When: inspected without the client
- Then: it is encrypted with a key held in OS-protected storage

## Verification Method

- [ ] Method: test (automated) + inspection
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes (AC1–AC3)

## Dependencies

- **Prerequisites**: ADR-009
- **Follow-on effects**: REQ-165 (token storage), REQ-180
- **Conflicts**: ADR-008 (offline cache without encryption)

## Risks of Non-Fulfillment

- Risk 1: workstation compromise via renderer XSS – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-19 (PR-25) |
