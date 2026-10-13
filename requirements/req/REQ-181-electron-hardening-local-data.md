---
id: REQ-181
title: Electron runtime hardening and local data protection
type: constraint
priority: must
status: proposed
version: 0.2.0
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
    - ADR-035
supersedes: []
superseded_by: []
---

# REQ-181: Electron Runtime Hardening and Local Data Protection

## Statement

The Electron client **MUST** enable `sandbox` and `contextIsolation` and disable `nodeIntegration` for all renderers, **MUST** block navigation to origins other than the app origin and deny all new windows, **MUST** open external links only after URL scheme validation (`https`), **MUST** validate all IPC arguments against a schema, **MUST** set restrictive Electron fuses, **MUST** route all API calls through a proxy in the main process so that the renderer never holds access or refresh tokens, **MUST** store tokens in OS-protected storage and persist neither tokens nor the offline cache when no OS keyring is available, **MUST** encrypt the offline cache and delete it on logout, on loss of access and at the end of the absolute session lifetime (REQ-165), and **MUST** be built only on an upstream-supported Electron major version, adopting Electron security fixes rated High or Critical in an OEA patch release within 14 days of their publication.

## Rationale

An XSS in the renderer of an under-hardened Electron app becomes code execution on the architect's machine. The offline cache of a laptop holds repository data, including values the user may have lost access to.

## Context

Source: security review SEC-19 (proposal PR-25). ADR-009 covers `contextIsolation`, `preload` and `nodeIntegration=false`; ADR-035 (partially superseding ADR-009) defines the binding baseline enforced by CI: window factory with fixed `webPreferences`, bundled frontend only via a custom scheme with CSP, intent-level preload API with sender and payload validation, fuses, main-process API proxy (`app://oea/api/…` forwarded only to the configured `https://` server), `safeStorage` for tokens and the cache data key, deletion rules and the Electron version policy. Owner confirmation (2026-10-13): main-process API proxy, offline cache limited to the absolute session lifetime (default 12 h, at most 7 days), Electron security fixes (High/Critical) within 14 days, no token or offline cache without an OS keyring (e.g. Linux backend `basic_text`); the user then logs in at each start and works online only.

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

**AC6** (No tokens in the renderer):

- Given: a logged-in user in the packaged app
- When: renderer code inspects request headers, `localStorage`, `sessionStorage`, IndexedDB and cookies of the app origin, and API calls are made
- Then: no access or refresh token is found in the renderer; API requests reach only the configured OEA server via the main-process proxy

**AC7** (No OS keyring):

- Given: a Linux system where `safeStorage` reports no encryption backend (`basic_text`)
- When: the user logs in, works and closes the client
- Then: neither a refresh token nor an offline cache exists on disk, and the client shows why offline mode is unavailable

**AC8** (Session lifetime bound):

- Given: an offline cache created at login and an absolute session lifetime of 12 h
- When: the client is started 13 h after login without network access
- Then: the offline cache has been deleted and a new login is required

**AC9** (Supported Electron version):

- Given: the pinned Electron major version of a release candidate
- When: the CI version check compares it with the upstream supported majors
- Then: the build fails if the major is out of upstream support

## Verification Method

- [ ] Method: test (automated) + inspection; process check for the 14-day patch window (release log against Electron security advisories)
- [ ] Pass criterion: AC1–AC9 green; every Electron High/Critical security fix adopted in an OEA patch release within 14 days
- [ ] Integrated in CI: yes (AC1–AC3, AC6–AC9)

## Dependencies

- **Prerequisites**: ADR-009, ADR-035
- **Follow-on effects**: REQ-165 (token storage, absolute session lifetime), REQ-180, REQ-191 (pinned dependencies)
- **Conflicts**: none known (ADR-008 offline cache without encryption is constrained by ADR-035 §8)

## Risks of Non-Fulfillment

- Risk 1: workstation compromise via renderer XSS – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-19 (PR-25) |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-035: navigation only to the app origin, all new windows denied; main-process API proxy (AC6); no persistence without OS keyring (AC7); offline cache bound to the absolute session lifetime (AC8); supported Electron versions and security fixes within 14 days (AC9); ADR-008 conflict resolved |
