---
id: REQ-020
title: Instance-wide enforcement of a second factor for regular logins
type: business-rule
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
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

The operator MUST be able to specify at instance commissioning that all persons may log in only with a second factor (passkey or username/password with TOTP, see REQ-009/REQ-010); if this enforcement is active, the minimal variant without a second factor (REQ-011) MUST NOT be usable for regular persons. This enforcement does NOT apply to the [system admin account](../../business-objects/system-admin-account.md) from UC-02.

## Rationale

Organizations with higher security requirements want to be able to rule out, already at commissioning, that persons ever log in with a plain password without a second factor. This decision must be made centrally and instance-wide by the operator (SH-06) instead of leaving it to each person individually.

The explicit exclusion of the system admin account is necessary because, according to [UC-02](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) and [ADR-006](../../adrs/ADR-006-auth-stack-choice.md), it deliberately stands outside the regular person/role model and serves the bootstrapping purpose (chicken-and-egg problem); it is secured via its own mechanisms (REQ-017: secure token handover), not via this policy.

## Context

Concerns the instance configuration from UC-01 (regular person logins: REQ-001 OIDC, REQ-009 passkey, REQ-010 TOTP, REQ-011 minimal variant). Does not concern UC-02 (system admin bootstrapping).

## Type-Specific Fields

### For type=business-rule

**Trigger**: onCreate/onUpdate of the instance authentication configuration; onLogin (enforcement)

**Affected Business Objects**: [person](../../business-objects/person.md) (affected), [system-admin-account](../../business-objects/system-admin-account.md) (explicitly NOT affected)

**Formal Statement** (simplified):

```text
if instanceConfig.enforceSecondFactor == true:
    disallow(REQ-011 for any Person login)
    require(login.factorCount >= 2) for OIDC-Logins (e.g., via amr/acr claim check, if provided by the IdP)
# does not apply to SystemAdminAccount logins (UC-02)
```

**Example**: An operator with elevated security requirements activates "enforce second factor" at commissioning. Afterwards REQ-011 (password without TOTP) can no longer be enabled for regular persons, even if an admin tries. The system admin account from bootstrapping remains unaffected.

## Acceptance Criteria

**AC1**:

- Given: an instance on which "enforce second factor" was activated at commissioning
- When: an attempt is made to enable REQ-011 (username/password without second factor) for regular persons
- Then: this is rejected as long as the enforcement is active

**AC2**:

- Given: the same instance with active enforcement
- When: the system admin account from UC-02 logs in (local or remote)
- Then: this login is unaffected by the enforcement and works as specified in UC-02

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
- **Conflicts**: REQ-013/REQ-014 (system admin bootstrapping) are deliberately excluded from this rule – see Rationale

## Risks of Non-Fulfillment

- Risk 1: Without an instance-wide enforcement option, security-conscious operators could not prevent weak password-only logins from staying active for regular persons
- Risk 2: If the enforcement accidentally also affected the system admin account, bootstrapping (UC-02) could be blocked before a second factor can even be registered – hence the explicit exclusion

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

The exclusion of the system admin account is an explicit business decision (not derivable from UC-01/UC-02 themselves) and was specified directly by the repository owner.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft |
