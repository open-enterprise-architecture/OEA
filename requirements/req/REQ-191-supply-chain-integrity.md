---
id: REQ-191
title: Supply chain integrity
type: constraint
priority: must
status: proposed
version: 0.1.0
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
    - ADR-018
    - ADR-027
supersedes: []
superseded_by: []
---

# REQ-191: Supply Chain Integrity

## Statement

Every release **MUST** publish an SBOM (CycloneDX or SPDX) for the backend image and the Electron client, **MUST** sign container images and checksums, **MUST** be built from pinned dependency versions (lock files, no version ranges, no floating tags), and CI **MUST** fail on dependencies with known vulnerabilities of severity High or Critical unless a documented exception exists.

## Rationale

A compromised transitive dependency pulled in by a floating range reaches every self-hosted instance and every architect workstation. Operators need an SBOM to check whether they are affected by a newly published vulnerability.

## Context

Source: security review SEC-29 (proposal PR-35). `SECURITY.md` lists SBOM, signing and reproducible builds as TBD; ADR-018 uses a version range for the CEL library.

## Type-Specific Fields

**Type of constraint**: technical / organizational

**Source**: security review 2026-10 (SEC-29)

**Binding strength**: hard (exceptions documented per vulnerability)

**Impact of non-compliance**: undetected supply-chain compromise of releases

## Acceptance Criteria

**AC1**:

- Given: a release tag
- When: the release artifacts are listed
- Then: SBOMs for backend and client and a signature for the container image are present and verify with the published key

**AC2**:

- Given: a pull request that introduces a dependency with a known Critical CVE
- When: CI runs
- Then: the dependency scan job fails

**AC3**:

- Given: the backend build file and the frontend lock file
- When: inspected
- Then: no version range or floating tag is used

## Verification Method

- [ ] Method: inspection + CI test
- [ ] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-182, REQ-192
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: compromised releases – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-29 (PR-35) |
