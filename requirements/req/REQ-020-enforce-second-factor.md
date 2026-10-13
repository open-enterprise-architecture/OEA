---
id: REQ-020
title: Instance-wide enforcement of a second factor for regular logins
type: business-rule
priority: should
status: proposed
version: 0.2.0
created: 2026-06-24
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
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

# REQ-020: Instance-wide enforcement of a second factor for regular logins

## Statement

The operator MUST be able to specify at instance commissioning that all persons may log in only with a second factor (passkey or username/password with TOTP, see REQ-009/REQ-010); if this enforcement is active, the minimal variant without a second factor (REQ-011) MUST NOT be usable for regular persons. The [system admin account](../../business-objects/system-admin-account.md) from UC-02 is exempt from this enforcement only for its first bootstrap login; independently of this setting, the local system admin account and every person holding an `admin`-class permission MUST use a second factor after that first login (REQ-171).

## Rationale

Organizations with higher security requirements want to be able to rule out, already at commissioning, that persons ever log in with a plain password without a second factor. This decision must be made centrally and instance-wide by the operator (SH-06) instead of leaving it to each person individually.

The explicit exclusion of the system admin account is necessary because, according to [UC-02](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) and [ADR-006](../../adrs/ADR-006-auth-stack-choice.md), it deliberately stands outside the regular person/role model and serves the bootstrapping purpose (chicken-and-egg problem); it is secured via its own mechanisms (REQ-017: secure token handover), not via this policy.

Security review SEC-10 (2026-10-13): a permanent exemption leaves the most privileged account protected by a password only, reachable through the same login page as everyone else. The exemption is therefore limited to the first bootstrap login, which breaks the chicken-and-egg problem; at that login the second factor is enrolled (REQ-171).

## Context

Concerns the instance configuration from UC-01 (regular person logins: REQ-001 OIDC, REQ-009 passkey, REQ-010 TOTP, REQ-011 minimal variant). Does not concern UC-02 (system admin bootstrapping).

## Type-Specific Fields

### For type=business-rule

**Trigger**: onCreate/onUpdate of the instance authentication configuration; onLogin (enforcement)

**Affected Business Objects**: [person](../../business-objects/person.md) (affected), [system-admin-account](../../business-objects/system-admin-account.md) (not affected by this setting; mandatory second factor after the first login per REQ-171)

**Formal Statement** (simplified):

```text
if instanceConfig.enforceSecondFactor == true:
    disallow(REQ-011 for any Person login)
    require(login.factorCount >= 2) for OIDC-Logins (e.g., via amr/acr claim check, if provided by the IdP)
# SystemAdminAccount (UC-02): exempt only for the first bootstrap login;
# afterwards a second factor is always required (REQ-171), independent of this setting
```

**Example**: An operator with elevated security requirements activates "enforce second factor" at commissioning. Afterwards REQ-011 (password without TOTP) can no longer be enabled for regular persons, even if an admin tries. The system admin account from bootstrapping completes its first login without a second factor and must enroll one before any administrative action (REQ-171).

## Acceptance Criteria

**AC1**:

- Given: an instance on which "enforce second factor" was activated at commissioning
- When: an attempt is made to enable REQ-011 (username/password without second factor) for regular persons
- Then: this is rejected as long as the enforcement is active

**AC2**:

- Given: the same instance with active enforcement
- When: the local system admin account from UC-02 performs its first bootstrap login
- Then: the login succeeds without a second factor, and the account must enroll a second factor before any administrative action is possible; every later local login of this account requires the second factor (REQ-171)

**AC3**:

- Given: an instance without activated enforcement (default)
- When: the authentication setup is checked
- Then: REQ-011 can still be enabled according to its own conditions (no default compulsion)

## Verification Method

- [x] Method: test (automated) + inspection (Default-Konfiguration)
- [x] Test setup: instance with enforcement activated, attempt to enable REQ-011; check system admin login in parallel
- [x] Measurement tool: test suite of the auth module, configuration inspection
- [x] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-009, REQ-010, REQ-011 (all three local mechanisms must exist so that this rule has something to restrict)
- **Downstream effects**: restricts REQ-011 once active
- **Extension**: REQ-029 (configure role-based exemption – the operator can exempt roles from the 2FA requirement)
- **Related**: REQ-171 (MFA for privileged accounts, applies independently of this setting)
- **Conflicts**: REQ-013/REQ-014 (system admin bootstrapping) are excluded from this rule only for the first bootstrap login – see Rationale

## Risks of Non-Fulfillment

- Risk 1: Without an instance-wide enforcement option, security-conscious operators could not prevent weak password-only logins from staying active for regular persons
- Risk 2: If the enforcement also affected the first bootstrap login, bootstrapping (UC-02) could be blocked before a second factor can even be registered – hence the exclusion of exactly that login

## Trade-offs

- vs. REQ-011: restricts its usability once enforcement is active – deliberate conflict of goals, intended by the operator side

## Implementation Notes

- Enforcement for OIDC logins (REQ-001) is only possible to the extent that the IdP provides MFA information (e.g., `amr`/`acr` claims per OIDC Core); if not, OEA cannot technically guarantee enforcement for OIDC logins but must rely on the IdP's governance – this limitation should be communicated to the operator upon activation
- Configuration should be part of the same instance authentication configuration as REQ-011/REQ-012

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

The exclusion of the system admin account is an explicit business decision (not derivable from UC-01/UC-02 themselves) and was specified directly by the repository owner. Version 0.2.0 narrows it to the first bootstrap login following security review SEC-10; this narrowing is not covered by an explicit owner decision of 2026-10-13 and needs owner confirmation.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-10: system admin exemption limited to the first bootstrap login; AC2 rewritten; reference to REQ-171 |
