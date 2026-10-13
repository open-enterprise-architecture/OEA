---
id: REQ-109
title: Create and Manage Architecture Patterns
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-17
  business_objects:
    - architecture-pattern
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-109: Create and Manage Architecture Patterns

## Statement

The system MUST allow creating, editing, and deleting `ArchitecturePattern` entries. Mandatory fields: `name`, `problem` (Markdown), `solution` (Markdown). Optional: `applicability`, `consequences`, `maturityLevel`, `tags`, `relatedPatterns` (multi-select). `problem` and `solution` MUST both be set; a pattern without a problem description MUST be rejected with HTTP 422.

## Rationale

Patterns without a problem description are not patterns — they are incomprehensible and not applicable. The mandatory field validation ensures that the library contains only complete, usable patterns.

## Acceptance Criteria

**AC1** (Create pattern):
- When: a pattern is created with `problem` and `solution`
- Then: the system responds with HTTP 201; the pattern is visible in the library

**AC2** (Mandatory field problem):
- When: a pattern is to be saved without the `problem` field
- Then: the system responds with HTTP 422 "problem is a mandatory field"

**AC3** (Link relatedPatterns):
- When: `relatedPatterns` are linked to other patterns via multi-select
- Then: the links are saved and displayed in the detail view

## Dependencies

- **Prerequisites**: REQ-111 (scope protection for imported patterns)
- **Consequences**: REQ-110 (reference architecture references patterns), REQ-118 (coverage analysis)

## Implementation Notes

- `problem` and `solution` as LONGTEXT/TEXT fields with Markdown rendering in the UI
- `relatedPatterns` as a many-to-many self-relation; no cycle check necessary
- `tags` as a string array; free-text input with autocomplete from existing tags

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
