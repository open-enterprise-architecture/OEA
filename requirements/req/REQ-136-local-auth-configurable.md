---
id: REQ-136
title: Local Authentication Configurable by Operator
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-02
  business_objects:
    - system-admin-account
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-136: Local Authentication Configurable by Operator

## Statement

The operator **MUST** be able to configure per OEA instance whether local authentication (username/password, passkey, TOTP) is offered as a login option in the user interface. OEA **MUST NOT** mark an authentication method as recommended or preselect it — this decision lies exclusively with the operator.

## Rationale

Whether local authentication makes sense for an organization depends on its security policies and IT infrastructure. Companies with an existing identity provider (Entra ID, Authentik) want to offer OIDC exclusively; smaller operators without an IdP need local authentication. OEA as the vendor does not make this decision — it lies with the operator (SH-06). A built-in "recommended" label would restrict operator autonomy and raise false expectations.

## Context

The requirement concerns exclusively the visibility of options in the UI (login screen, bootstrapping wizard). The technical implementation of local authentication methods remains unaffected — only whether they are offered to the end user is configurable.

## Type-Specific Fields

**Inputs**:
- Instance configuration: `auth.localAuthEnabled: boolean` (default: `true`)
- Instance configuration: `auth.oidcEnabled: boolean` (default: `false`, becomes `true` as soon as an IdP is configured)

**Processing**:
1. At startup, the UI reads the instance configuration (via API or embedded config)
2. `localAuthEnabled: false` → fields for username/password and the passkey link are not displayed; the bootstrapping wizard shows only the OIDC option
3. `localAuthEnabled: true` and `oidcEnabled: true` → both options are offered equally, none is premarked or labeled "recommended"
4. `localAuthEnabled: true` and `oidcEnabled: false` → show only local options
5. None of the provider names or method names may carry a "recommended" label

**Outputs**:
- The login screen shows exactly the options released by the operator
- The bootstrapping wizard shows exactly the bootstrapping modes released by the operator

**Error Cases**:
- `localAuthEnabled: false` and `oidcEnabled: false` at the same time (no provider active): the system shows an error state "No authentication method configured" and blocks login until an operator activates a method

## Acceptance Criteria

**AC1** — Local auth disabled:
- Given: `auth.localAuthEnabled = false`
- When: the login screen is opened
- Then: no username/password form, no passkey link, no local option visible

**AC2** — OIDC disabled:
- Given: `auth.oidcEnabled = false`
- When: the login screen is opened
- Then: no SSO button visible; local options are displayed (if enabled)

**AC3** — Both active, no recommendation:
- Given: `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
- When: the login screen is opened
- Then: both options are displayed equally; no option carries the label "recommended", "default", or similar

**AC4** — Bootstrapping wizard follows config:
- Given: `auth.localAuthEnabled = false`
- When: the bootstrapping wizard is opened
- Then: only the OIDC bootstrapping option is offered; local mode cannot be displayed

**AC5** — No active provider:
- Given: `auth.localAuthEnabled = false` and `auth.oidcEnabled = false`
- When: the login screen is accessed
- Then: error state with a clear message for the operator; no normal login possible

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: Playwright tests with various configuration combinations; config mocking via API interceptor
- [ ] Pass criterion: AC1–AC5 all green; no "recommended" label in the DOM with mixed configuration
- [ ] Integrated in CI: yes (after implementation)

## Dependencies

- **Prerequisites**: ADR-006 (auth stack choice); instance configuration concept (no ADR of its own yet)
- **Consequences**: REQ-137 (auto SSO) builds on the `oidcEnabled` flag
- **Conflicts**: none

## Risks of Non-Fulfillment

- Risk 1: an operator with a pure OIDC setup sees unnecessary local auth options → confusion and support effort (medium)
- Risk 2: a "recommended" label leads the operator in a direction unsuitable from a security perspective → loss of trust (medium)

## Implementation Notes

- Configuration as part of the instance configuration (environment variable or admin config file), not as a database setting — must be evaluable before the first login
- The frontend reads the config once at app start; no reloading during the session necessary

## Implementation

- ADR(s): ADR-006
- Implemented by: (still open)
- Tests: (still open)
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; separation of operator configuration from vendor default |
