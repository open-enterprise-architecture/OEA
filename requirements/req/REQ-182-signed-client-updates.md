---
id: REQ-182
title: Signed client builds and verified updates
type: constraint
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-009
    - ADR-035
supersedes: []
superseded_by: []
---

# REQ-182: Signed Client Builds and Verified Updates

## Statement

Every client release **MUST** be integrity-protected without paid certificates: installers and update packages **MUST** be published with SHA-256/SHA-512 checksums and a build provenance attestation from the release workflow (GitHub artifact attestations / Sigstore, free for public repositories); the auto-update mechanism **MUST** download over HTTPS only from the update source of the effective update mode (REQ-195: official release source, or the organization's OEA server), **MUST** verify each package against the checksum of the official release metadata before installing, and **MUST** reject downgrades.

Operating-system code signing (Windows Authenticode, macOS notarization) is an **open point**: it is introduced once Enterprise license revenue covers the certificate costs and then becomes mandatory for Enterprise builds. Until then, Community builds are unsigned at OS level and the resulting operating-system warnings are documented in the installation guide.

## Rationale

A tampered update feed (misconfigured bucket, HTTP inside a corporate network) would install malware on every architect's machine.

## Context

Source: security review SEC-19, SEC-29 (proposal PR-26). ADR-009 treats signing as a documentation task; this REQ makes integrity protection a release prerequisite.

Owner decision (2026-10-13): code signing certificates (Windows Authenticode about EUR 200–500 per year, Apple Developer Program USD 99 per year) cause costs without income for an open-source product. They are deferred until paid Enterprise licenses cover them. The free measures above already protect against tampered updates; OS signing additionally removes SmartScreen/Gatekeeper warnings and binds the binary to the publisher identity.

ADR-035 §10/§10a (partially superseding ADR-009): the update source is fixed at build time; a different source can be set only through device policy or operator setting (REQ-195), never through the renderer or user settings, and only with `https://`; TLS errors are never ignored. Update packages are verified against SHA-512 from the release metadata; installation happens only after user confirmation; pre-release channels require an explicit opt-in. Without OS code signing, automatic in-app updates are not available on macOS (owner confirmation 2026-10-13); macOS users are notified and update manually or via central software distribution. Electron security fixes are shipped within 14 days (REQ-181).

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-19, SEC-29)

**Binding strength**: hard

**Impact of non-compliance**: supply-chain compromise of all client installations

## Acceptance Criteria

**AC1**:

- Given: an update package whose checksum does not match the release metadata
- When: the client installs the update
- Then: the update is rejected, nothing is installed and the user is informed

**AC2**:

- Given: an update feed URL with `http://`
- When: configured
- Then: the client refuses to use it

**AC3**:

- Given: a release
- When: its artifacts are inspected
- Then: a checksum file and a build provenance attestation are published for every installer and update package, and the attestation can be verified with `gh attestation verify`

**AC4** (open point, Enterprise builds only):

- Given: an Enterprise build after OS code signing has been introduced
- When: its installers are inspected
- Then: Windows installers carry a valid Authenticode signature and macOS builds are notarized

**AC5** (No downgrade):

- Given: an installed client version 1.4.0 and an update feed that offers version 1.3.0 with valid checksums
- When: the client checks for updates
- Then: the older version is neither offered nor installed

## Verification Method

- [ ] Method: test (automated update test with a tampered package) + release checklist inspection
- [ ] Pass criterion: AC1–AC3 and AC5 green; AC4 only once OS code signing is introduced
- [ ] Integrated in CI: yes (AC1, AC2, AC5); AC3 as release gate

## Dependencies

- **Prerequisites**: ADR-009, ADR-035
- **Follow-on effects**: REQ-191 (supply chain)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: malicious updates on all clients – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-19/SEC-29 (PR-26) |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Integrity protection without paid certificates (checksums, build provenance attestation); OS code signing deferred as open point until Enterprise revenue covers it |
| 0.3.0 | 2026-10-13 | requirements-engineer | ADR-035 consistency: update source per effective update mode (REQ-195), verification against the official release checksum, downgrades rejected (AC5); no automatic updates on macOS without OS signing; ADR-035 referenced |
