---
id: REQ-130
title: Accessibility WCAG 2.1 Level AA
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-06
    - UC-07
  business_objects:
    - entity
    - catalog
    - dashboard
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-05
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-130: Accessibility WCAG 2.1 Level AA

## Statement

The OEA web interface MUST meet conformance level **WCAG 2.1 Level AA** for all pages outside the diagram canvas. The diagram canvas (UC-05, UC-10) is exempt from the WCAG AA obligation and MUST meet at least WCAG 2.1 Level A; for the canvas, an accessible alternative representation (list view of the entities and connections) SHOULD be provided.

**Specific requirements (selection)**:

- All interactive elements are reachable and operable via keyboard (focus management)
- Color contrast: minimum contrast ratio of 4.5:1 for normal text, 3:1 for large text and UI components
- All non-text content (icons, diagrams) has text alternatives (`aria-label`, `alt`)
- Error messages name the affected field specifically and describe the corrective action
- No purely color-based information carriers (status indicators must additionally have an icon or text)

## Rationale

OEA is an open-source tool with the aspiration of broad usability. Accessibility is relevant for public procurement (EU Directive 2016/2102) and for large enterprises with diversity requirements. In addition, WCAG conformance improves usability for all users (e.g. keyboard operation for power users).

## Context

**In scope**: all web portal pages (login, catalog, dashboard, metamodel editor, navigation tree, entity detail pages)

**Exception**: diagram canvas (UC-05, UC-10) — complex graphical editors fall under a WCAG exemption (the conformance requirement is reduced to Level A); an alternative representation as a list SHOULD be provided

## Type-Specific Fields

### For type=non-functional

**Category**: accessibility

**Measurable Target Values**:

| Metric | Target Value | Scope |
|---|---|---|
| WCAG 2.1 Level AA conformance | 0 critical violations | all pages except canvas |
| WCAG 2.1 Level A conformance (canvas) | 0 critical violations | diagram canvas |
| Automated Axe scan (CI) | 0 violations (severity: critical, serious) | all pages |
| Keyboard navigation: all primary workflows | fully operable via keyboard | web portal |

## Acceptance Criteria

**AC1** (Automated scan green):

- When: an Axe or Lighthouse accessibility scan runs against all web portal pages
- Then: 0 violations with severity `critical` or `serious`

**AC2** (Keyboard navigation login):

- When: the user uses the Tab key from page load
- Then: all login fields, buttons, and links are reachable; focus indicator visible

**AC3** (Contrast):

- When: a contrast check is performed against the design system tokens
- Then: all text/background combinations meet 4.5:1 (normal) / 3:1 (large text)

**AC4** (Canvas alternative representation):

- When: the user opens the canvas
- Then: a link/button "Show as list" is present; it lists all entities and connections as a structured table

## Verification Method

- [x] Method: automated-scan (Axe-Core in CI via Playwright or Cypress)
- [x] Method: manual-test (screen reader test with NVDA/VoiceOver for login and catalog)
- [ ] Integrated in CI: yes (Axe scan), no (screen reader test)

## Dependencies

- **Prerequisites**: design system with color tokens and focus styles (UI design phase)
- **Consequences**: canvas alternative representation requires additional implementation time

## Risks of Non-Fulfillment

- Exclusion from public tenders (EU 2016/2102)
- Users with visual impairments or motor limitations cannot use the tool

## Trade-offs

- Canvas WCAG AA would be ideal, but is technically very costly for complex graphical editors (D3.js, Vue Flow have limited native accessibility); Level A + alternative representation is a pragmatic compromise
- An automated scan covers ~30–40 % of the WCAG criteria; manual tests are necessary for full conformance

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
