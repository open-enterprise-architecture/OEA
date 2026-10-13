---
id: REQ-195
title: Steerable client updates and version compatibility
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: Lukas Mathis
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-035
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-195: Steerable Client Updates and Version Compatibility

## Statement

The desktop client **MUST** support the update modes `public` (official release source), `server` (the organization's OEA server serves the matching client release) and `managed` (no self-update; deployment by central software distribution such as Intune, ConfigMgr or Jamf). The effective mode **MUST** follow the precedence device policy > operator setting on the OEA server > default `public`, and a lower level **MUST NOT** weaken a higher one. Independently of the mode, the client **MUST** refuse to work with a server whose `minClientVersion` it does not meet and **MUST** verify every update package against the official release checksum and build provenance (REQ-182).

## Rationale

Small organizations want updates without effort; enterprises distribute software centrally and must be able to disable self-updates on managed devices. A server-side minimum version enforces security updates in every mode, and serving the client from the OEA server keeps client and server versions compatible in networks without internet access.

## Context

Owner decision Q22 (2026-10-13); design in ADR-035 section 10a. Device policy: Windows `HKLM\Software\Policies\OEA` (ADMX template for Intune/Group Policy), macOS managed preferences (configuration profile), Linux `/etc/oea/policy.json`. Operator settings: `clientUpdateMode`, `minClientVersion`, `recommendedClientVersion`; public endpoint `GET /api/v1/client-compatibility`. On macOS, automatic updates require OS code signing (REQ-182, open point Q12); until then only `managed` and manual updates apply there.

## Type-Specific Fields

### For type=functional

**Inputs**: device policy, operator settings, client version, release metadata

**Processing**: determine the effective update mode by precedence; check version compatibility before login; download and verify updates only in modes `public` and `server`

**Outputs**: effective update mode, compatibility state (`compatible`, `update-recommended`, `update-required`), update offer or notice

**Error Cases**: compatibility endpoint unreachable (client starts, shows a warning, re-checks at login); update package fails verification (nothing installed, user informed)

## Acceptance Criteria

**AC1** (Device policy wins):

- Given: a Windows device with the policy value `UpdateMode = managed` and an OEA server with `clientUpdateMode = server`
- When: the client starts
- Then: the client does not download or offer updates and shows its effective mode as `managed (device policy)`

**AC2** (Server mode):

- Given: no device policy and an OEA server with `clientUpdateMode = server` offering client version 1.4.0
- When: a client with version 1.3.2 checks for updates
- Then: it downloads the update from the OEA server, verifies it against the official release checksum and attestation, and installs it only after user confirmation

**AC3** (Tampered package from the server):

- Given: `clientUpdateMode = server` and a package on the OEA server whose checksum differs from the official release metadata
- When: the client downloads it
- Then: nothing is installed, the user is informed, and a security event is reported to the server

**AC4** (Minimum version enforced in managed mode):

- Given: a managed device with client version 1.2.0 and an OEA server with `minClientVersion = 1.3.0`
- When: the user starts the client
- Then: the client refuses to connect and shows "Please update – contact your IT department"; it does not try to update itself

**AC5** (Recommended version):

- Given: client version 1.3.0, `minClientVersion = 1.2.0`, `recommendedClientVersion = 1.4.0`
- When: the user logs in
- Then: the client works normally and shows a non-blocking update notice

**AC6** (Public compatibility endpoint without instance data):

- Given: an unauthenticated request
- When: `GET /api/v1/client-compatibility` is called
- Then: the response contains only `clientUpdateMode`, `minClientVersion`, `recommendedClientVersion` and the server API version – no instance name, users or content

## Verification Method

- [ ] Method: test (automated) + inspection (installer and ADMX template)
- [ ] Test setup: client integration tests with simulated device policy and a test server per mode; tampered package fixture
- [ ] Pass criterion: AC1–AC6 green; MSI deployment tested once via Intune or ConfigMgr before the first Enterprise release
- [ ] Integrated in CI: yes (AC2–AC6); AC1 per platform in the release test

## Dependencies

- **Prerequisites**: REQ-182 (integrity-protected releases), REQ-181 (Electron hardening), ADR-035
- **Downstream effects**: release workflow publishes MSI/PKG and the ADMX template; `api/openapi.yaml` endpoint and settings
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: enterprises cannot disable self-updates on managed devices and block the client – high
- Risk 2: outdated clients with known vulnerabilities keep working against the server – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | Lukas Mathis | Initial draft from owner decision Q22 (ADR-035 section 10a) |
