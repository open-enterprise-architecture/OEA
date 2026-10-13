---
id: REQ-112
title: Activate Embedded Continuum Packages
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-18
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - trm-category
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-112: Activate Embedded Continuum Packages

## Statement

The system MUST offer a list of embedded continuum packages (at least: "TOGAF 10 – Technical Reference Model"). Before import, each package MUST show a preview: number of ABBs, SBBs, patterns, reference architectures, and TRM categories, as well as the most important category names. Activation MUST be executable by clicking "Import" — without a file upload.

## Rationale

Manually recreating the TOGAF TRM would be prohibitively laborious for operators. Built-in packages lower the entry barrier to a single click and enable immediate productive operation with an industry-recognized standard framework.

## Acceptance Criteria

**AC1** (Package preview):
- When: the package list is opened
- Then: "TOGAF 10 TRM" is visible with a preview (47 ABBs, 38 TRM categories, 12 patterns)

**AC2** (Import by click):
- When: "Import" is clicked
- Then: the import is executed atomically; an import log shows the result

**AC3** (Already imported package):
- When: a package has already been imported
- Then: the UI shows an "Already imported" badge and a note about conflict handling

## Dependencies

- **Prerequisites**: REQ-111 (scope protection; imported building blocks receive `scope=built-in`)
- **Consequences**: REQ-114 (atomic import transaction also applies to built-in packages)

## Implementation Notes

- Built-in packages embedded as JSON files in the server code (no external download)
- Import endpoint: `POST /api/continuum/packages/{packageId}/activate`
- Import log: number of imported building blocks per type + any skipped conflicts

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
