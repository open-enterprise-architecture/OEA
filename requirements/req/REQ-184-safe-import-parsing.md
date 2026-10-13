---
id: REQ-184
title: Safe parsing of imported files
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-18
  business_objects:
    - metamodel-configuration
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs:
    - ADR-018
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-184: Safe Parsing of Imported Files

## Statement

All file imports (YAML, JSON, XML, ZIP) **MUST** use parsers configured without type instantiation, external entities, DTDs or unbounded alias expansion, **MUST** enforce one instance-wide configurable maximum upload size (default 10 MB) and limits on nesting depth, element count and decompressed size, and **MUST** validate content against a schema before processing.

## Rationale

Metamodel packages, viewpoints and continuum packages are shared via community libraries and are untrusted input. YAML global tags can execute code, alias bombs exhaust memory, XML external entities read local files.

## Context

Source: security review SEC-21 (proposal PR-28). The size limits of REQ-033 (1 MB proposal), REQ-059 (256 KB), REQ-113 (10 MB) and `SystemSettings.maxUploadMb` were harmonized to this single limit on 2026-10-13.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-21); CVE-2022-1471 (SnakeYAML); OWASP XXE Prevention Cheat Sheet

**Binding strength**: hard

**Impact of non-compliance**: remote code execution, local file disclosure, denial of service

## Acceptance Criteria

**AC1**:

- Given: a YAML file with a global tag such as `!!javax.script.ScriptEngineManager`
- When: imported
- Then: it is rejected with 422 and no class is instantiated

**AC2**:

- Given: a YAML alias bomb ("billion laughs") or a JSON document nested 10,000 levels deep
- When: imported
- Then: it is rejected within 2 s without exhausting memory

**AC3**:

- Given: an XML import containing an external entity referencing `file:///etc/passwd`
- When: processed
- Then: the entity is not resolved

**AC4**:

- Given: a file larger than the configured maximum
- When: uploaded to any import endpoint
- Then: the response is 413 before the body is fully parsed

**AC5**:

- Given: a ZIP archive whose decompressed size exceeds the limit
- When: imported
- Then: extraction stops and the import is rejected

## Verification Method

- [ ] Method: test (automated, malicious file corpus)
- [ ] Pass criterion: AC1–AC5 green on every import endpoint
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-033, REQ-059, REQ-113, REQ-114, REQ-078 (draw.io XML)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: code execution through a malicious package – medium to high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-21 (PR-28) |
