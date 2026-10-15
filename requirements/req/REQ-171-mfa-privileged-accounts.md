---
id: REQ-171
title: Mandatory MFA for privileged accounts
type: business-rule
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-15
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-02
  business_objects:
    - system-admin-account
    - person
    - authorization-role
    - session
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
    - ADR-042
supersedes: []
superseded_by: []
---

# REQ-171: Mandatory MFA for Privileged Accounts

## Statement

The local system admin account and every person holding an `admin`-class permission (REQ-157) – in particular system, security and metamodel administrators – **MUST** use a second factor (passkey or TOTP) for every local login – for the local system admin account from the end of the setup phase on ([ADR-042](../../adrs/ADR-042-setup-phase-without-second-factor.md)), which the account must reach with an enrolled and confirmed second factor before any other identity gets access; no configuration, including role-based 2FA exemptions (REQ-029) and the instance-wide setting (REQ-020), **MUST** be able to exempt them.

## Rationale

The most privileged accounts are reachable through the same login page as everyone else. A guessed, reused or phished admin password must not give full control of the instance, including authentication provider configuration, service accounts and visibility settings.

## Context

Source: security review SEC-10 (proposal PR-15); additional owner decision of 2026-10-13: MFA is mandatory for all privileged accounts (system, security and metamodel admins), no exemption. The first bootstrap login of the local system admin account is the only exception (chicken-and-egg problem); the second factor is enrolled during that login. For OIDC logins, enforcement depends on `amr`/`acr` claims of the IdP (see REQ-020 Implementation Notes). `business-objects/system-admin-account.md` BR-03 needs alignment (follow-up for business engineer).

## Type-Specific Fields

**Trigger**: onLogin; onUpdate of the 2FA configuration; onUpdate of role assignments

**Affected business objects**: system-admin-account, person, role

**Formal statement**:

```text
privileged(principal) = principal is SystemAdminAccount
                        or effectivePermissions(principal) ∩ adminClass ≠ ∅
if privileged(principal) and not firstBootstrapLogin:
    require secondFactor for local login
twoFactorExemptRoles must not contain a role with adminClass permissions
exempt(person) only if all active roles are exempt
```

**Example**: Sabine holds `metamodel-admin`; she must enroll and use a passkey or TOTP even if `enforce2FA=false`.

## Acceptance Criteria

**AC1** (setup phase, ADR-042):

- Given: a freshly bootstrapped local system admin account in the setup phase
- When: it logs in with login name and password and then tries to create the first identity binding, IdP group mapping, role assignment, person credential or service account
- Then: the login succeeds without a second factor, but the action is rejected until a second factor is enrolled and confirmed in the session; with the first such action the setup phase ends, and every later login of the account requires the second factor

**AC2**:

- Given: `twoFactorExemptRoles` contains a role with an `admin`-class permission
- When: the configuration is saved
- Then: it is rejected

**AC3**:

- Given: a person with one exempt and one non-exempt role
- When: they log in locally
- Then: a second factor is required

**AC4**:

- Given: `enforce2FA=false` and a person who is assigned the role `metamodel-admin` or `security-admin`
- When: the person next logs in locally without an enrolled second factor
- Then: the enrollment required action (UC-03) is triggered and no administrative action is possible until a second factor is set up

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157 (`admin`-class permissions), REQ-009/REQ-010 (second factors), REQ-013
- **Follow-on effects**: REQ-020, REQ-029 (aligned 2026-10-13)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: full instance takeover via a single admin password – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-10 (PR-15) with owner decision of 2026-10-13 (no exemption for privileged accounts) |
| 0.2.0 | 2026-10-15 | requirements-engineer | Statement and AC1 aligned with ADR-042: setup phase of the local system admin account without second factor until the first access of another identity (owner decision of 2026-10-15) |
