---
id: REQ-182
title: Signed client builds and verified updates
type: constraint
priority: must
status: proposed
version: 0.2.0
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
supersedes: []
superseded_by: []
---

# REQ-182: Signed Client Builds and Verified Updates

## Statement

Every client release **MUST** be integrity-protected without paid certificates: installers and update packages **MUST** be published with SHA-256/SHA-512 checksums and a build provenance attestation from the release workflow (GitHub artifact attestations / Sigstore, free for public repositories); the auto-update mechanism **MUST** download over HTTPS only from the configured official release source and **MUST** verify each package against the checksum from the release metadata before installing.

Operating-system code signing (Windows Authenticode, macOS notarization) is an **open point**: it is introduced once Enterprise license revenue covers the certificate costs and then becomes mandatory for Enterprise builds. Until then, Community builds are unsigned at OS level and the resulting operating-system warnings are documented in the installation guide.

## Rationale

A tampered update feed (misconfigured bucket, HTTP inside a corporate network) would install malware on every architect's machine.

## Context

Source: security review SEC-19, SEC-29 (proposal PR-26). ADR-009 treats signing as a documentation task; this REQ makes integrity protection a release prerequisite.

Owner decision (2026-10-13): code signing certificates (Windows Authenticode about EUR 200–500 per year, Apple Developer Program USD 99 per year) cause costs without income for an open-source product. They are deferred until paid Enterprise licenses cover them. The free measures above already protect against tampered updates; OS signing additionally removes SmartScreen/Gatekeeper warnings and binds the binary to the publisher identity.

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

## Verification Method

- [ ] Method: test (automated update test with a tampered package) + release checklist inspection
- [ ] Pass criterion: AC1–AC3 green; AC4 only once OS code signing is introduced
- [ ] Integrated in CI: yes (AC1, AC2); AC3 as release gate

## Dependencies

- **Prerequisites**: ADR-009
- **Follow-on effects**: REQ-191 (supply chain)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: malicious updates on all clients – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-19/SEC-29 (PR-26) |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Integrity protection without paid certificates (checksums, build provenance attestation); OS code signing deferred as open point until Enterprise revenue covers it |
