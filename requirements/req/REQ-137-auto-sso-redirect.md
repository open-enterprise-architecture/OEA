---
id: REQ-137
title: Automatic SSO Redirect to the Configured Identity Provider
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-137: Automatic SSO Redirect to the Configured Identity Provider

## Statement

The system **SHOULD** be configurable to redirect automatically and without user interaction to the configured OIDC identity provider when the login page is called (auto SSO redirect), provided the operator has enabled this option and OIDC is configured as the only authentication method.

## Rationale

In the enterprise context, all users use the same identity provider (e.g. Entra ID). Explicitly clicking "Sign in via corporate SSO" is then an unnecessary step. Tools such as Jira, Confluence, and GitHub Enterprise redirect automatically in such setups — users experience OEA as seamlessly integrated into the existing IT infrastructure. SH-03 (Kurt) and SH-04 typically work in exactly this context.

## Context

Auto SSO redirect only applies if:
- `auth.oidcEnabled = true` (at least one IdP configured)
- `auth.localAuthEnabled = false` (no local authentication active) — in a mixed setup, auto redirect is inherently ambiguous and is not triggered
- `auth.autoSsoRedirect = true` (operator has explicitly enabled auto redirect)

The requirement concerns only the initial call of the login page. Already authenticated sessions are unaffected (UC-01 A1).

## Type-Specific Fields

**Inputs**:
- Instance configuration: `auth.autoSsoRedirect: boolean` (default: `false`)
- Optional configuration: `auth.autoSsoRedirectDelay: integer` (milliseconds, default: `0`)
- Query parameter `?noredirect=1` as an escape hatch for operators and emergency access

**Processing**:
1. User calls the OEA login URL (or is redirected there after an expired session)
2. System checks: `autoSsoRedirect = true` AND `oidcEnabled = true` AND `localAuthEnabled = false`
3. Condition met: display a short transition page "Redirecting to [IdP name] …" for `autoSsoRedirectDelay` ms, then trigger the OIDC redirect immediately
4. `?noredirect=1` in the URL: suppress auto redirect, display the normal login screen (escape hatch for operator emergency access)
5. Condition not met: normal login screen according to REQ-136

**Outputs**:
- HTTP redirect (302 / client-side redirect) to the OIDC authorization URL of the configured provider
- The transition page shows: OEA logo, provider name, short loading animation, link "Sign in another way" (activates `?noredirect=1`)

**Error Cases**:
- IdP unreachable (timeout on redirect): OEA shows an error message with the option to open the login screen manually (`?noredirect=1`)
- Auto redirect in a loop (e.g. IdP redirects back without successful login): after 3 failed redirect cycles, show the login screen and disable auto redirect for this session

## Acceptance Criteria

**AC1** — Auto redirect active:
- Given: `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = false`
- When: the user calls the login URL (without `?noredirect=1`)
- Then: the transition page appears briefly, followed by an automatic redirect to the OIDC authorization URL; no login form visible

**AC2** — Escape hatch:
- Given: `autoSsoRedirect = true` (auto redirect would be active)
- When: the user calls the login URL with `?noredirect=1`
- Then: the normal login screen is displayed; no automatic redirect

**AC3** — Mixed setup: no auto redirect:
- Given: `autoSsoRedirect = true`, `oidcEnabled = true`, `localAuthEnabled = true`
- When: the user calls the login URL
- Then: normal login screen with both options; no auto redirect

**AC4** — "Sign in another way" link on the transition page:
- Given: auto redirect is active, the transition page is displayed
- When: the user clicks "Sign in another way"
- Then: the redirect is aborted, the normal login screen appears (equivalent to `?noredirect=1`)

**AC5** — Redirect loop:
- Given: auto redirect active, IdP returns no successful login 3 times
- When: OEA detects a loop
- Then: login screen with the error message "Automatic sign-in failed" and link "Sign in manually"

## Verification Method

- [ ] Method: test (automated) + demonstration (manual)
- [ ] Test setup: Playwright with OIDC mock provider; configuration variants via API interceptor
- [ ] Pass criterion: AC1–AC5 all green; redirect URL contains correct OIDC parameters (state, nonce, redirect_uri)
- [ ] Integrated in CI: yes (after implementation)

## Dependencies

- **Prerequisites**: REQ-136 (operator configuration `auth.localAuthEnabled`, `auth.oidcEnabled`); ADR-006 (auth stack: OIDC via Entra ID / Authentik)
- **Consequences**: none
- **Conflicts**: REQ-136 AC3 defines when auto redirect is explicitly not triggered

## Risks of Non-Fulfillment

- Risk 1: enterprise users have to click the SSO button manually on every login → perceived sluggishness compared to comparable tools (low to medium)
- Risk 2: no escape hatch → operator locked out during an IdP outage → recovery effort (high, hence escape hatch as mandatory in AC2)

## Implementation Notes

- `?noredirect=1` must also be preserved after the OIDC callback (carry along in the state parameter) so that auto redirect is not triggered again after an error
- `autoSsoRedirectDelay` (default 0) allows operators to build in a short delay so that users notice the transition page — recommended value if set: 800–1500ms

## Implementation

- ADR(s): ADR-006
- Implemented by: (still open)
- Tests: (still open)
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft from mockup session feedback |
