# Derived from: requirements/req/req-130-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: automated-scan (Axe-Core in CI via Playwright or Cypress)

@non-functional @performance @should @UC-01 @UC-06 @UC-07
Feature: REQ-130 – Accessibility WCAG 2.1 Level AA

  The OEA web interface MUST meet conformance level **WCAG 2.1 Level AA**
  for all pages outside the diagram canvas.
  The diagram canvas (UC-05, UC-10) is exempt from the WCAG AA obligation
  and MUST meet at least WCAG 2.1 Level A; for the
  canvas an accessible alternative representation (list view of the
  entities and connections) SHOULD be provided.

  @AC1
  Scenario: AC1 – Automated scan green – Axe or Lighthouse accessibility scan runs against all web portal pages
    Given the precondition according to the requirement is met
    When  Axe or Lighthouse accessibility scan runs against all web portal pages
    Then  0 violations with severity `critical` or `serious`

  @AC2
  Scenario: AC2 – Keyboard navigation login – user uses the Tab key from page load
    Given the precondition according to the requirement is met
    When  user uses the Tab key from page load
    Then  all login fields, buttons and links reachable; focus indicator visible

  @AC3
  Scenario: AC3 – Contrast – contrast check is performed against design system tokens
    Given the precondition according to the requirement is met
    When  contrast check is performed against design system tokens
    Then  all text/background combinations meet 4.5:1 (normal) / 3:1 (large text)

  @AC4
  Scenario: AC4 – Canvas alternative representation – user opens the canvas
    Given the precondition according to the requirement is met
    When  user opens the canvas
    Then  link/button "Show as list" present; lists all entities and connections as a structured table
