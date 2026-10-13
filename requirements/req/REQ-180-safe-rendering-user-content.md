---
id: REQ-180
title: Safe rendering of user-supplied content
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-09
    - UC-07
  business_objects:
    - arc42
    - entity
  business_rules: []
  stakeholders:
    - SH-04
    - SH-05
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-180: Safe Rendering of User-Supplied Content

## Statement

The clients **MUST** render all user-supplied content (Markdown, entity names and descriptions, labels, Mermaid, PlantUML output, draw.io XML/SVG) through an allowlist sanitizer, **MUST** run Mermaid with `securityLevel: strict` and without init directives, **MUST** isolate the draw.io editor on a separate origin and validate postMessage origins, **MUST** serve the web portal and the Electron renderer with a Content Security Policy that disallows inline scripts and `unsafe-eval`, and **MUST** render content locally without sending it to external services unless the operator configures such a service explicitly.

## Rationale

Stored XSS in a shared repository reaches the CIO in the portal and admins in the Electron client, where it can call admin APIs or, through an IPC bridge, the local file system.

## Context

Source: security review SEC-18 (proposal PR-24); owner decision Q9: PlantUML, draw.io and spell checking are self-hosted/local by default; external services only if the operator configures them explicitly. REQ-068 and REQ-078 were aligned on 2026-10-13.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-18), owner decision Q9, OWASP XSS Prevention Cheat Sheet

**Binding strength**: hard

**Impact of non-compliance**: script execution in other users' sessions; code execution on architect workstations via Electron

## Acceptance Criteria

**AC1**:

- Given: an Arc42 answer containing `<img src=x onerror=alert(1)>`, `[x](javascript:alert(1))`, a Mermaid `click` callback and an `%%{init}%%` directive
- When: it is rendered in the web portal and the Electron client
- Then: no script executes and the links are inert

**AC2**:

- Given: a draw.io cell with `html=1` and `value="<svg onload=alert(1)>"`
- When: the diagram preview is rendered
- Then: no script executes

**AC3**:

- Given: a message posted to the editor window from an origin other than the draw.io origin
- When: received
- Then: it is ignored

**AC4**:

- Given: any HTML response of the web portal and the Electron renderer
- When: headers or meta tags are inspected
- Then: a CSP without `unsafe-inline` and `unsafe-eval` for `script-src` is present

**AC5**:

- Given: a default installation without configured external rendering or spell-checking services
- When: content with Mermaid, PlantUML and draw.io blocks is rendered and edited
- Then: no request carrying content leaves the instance

## Verification Method

- [ ] Method: test (automated XSS payload corpus, e.g. end-to-end browser tests) + inspection
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-068, REQ-070, REQ-078, REQ-080, REQ-181
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: stored XSS with admin session abuse – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-18 (PR-24), owner decision Q9 |
