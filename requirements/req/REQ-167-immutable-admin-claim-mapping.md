---
id: REQ-167
title: Immutable claim values for remote admin mapping
type: business-rule
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
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

# REQ-167: Immutable Claim Values for Remote Admin Mapping

## Statement

The remote system admin mapping **MUST** reference an immutable, provider-controlled identifier (e.g. Entra ID group object ID or app role, Authentik group UUID), **MUST** accept the claim only from the configured issuer, and **MUST** fail closed when the claim is missing or truncated.

## Rationale

Group display names are mutable and not unique; in many Entra ID tenants every user may create a group with the mapped name and thereby obtain system admin rights.

## Context

Source: security review SEC-08 (proposal PR-11). Applied to REQ-014 on 2026-10-13. The example in `business-objects/system-admin-account.md` (`claimValue: "oea-system-admins"`) must be corrected (follow-up for the business engineer).

## Type-Specific Fields

**Trigger**: onCreate/onUpdate of the mapping; onLogin

**Affected business objects**: system-admin-account

**Formal statement**:

```text
onSave: claimValue matches identifierFormat(provider, claimType) else reject
onLogin: grantAdmin = token.iss == mapping.issuer
                      and claim present and not truncated (no overage indicator)
                      and claimValue in token[claim]
         if claim missing or truncated -> grantAdmin = false, log warning
```

**Example**: Entra ID mapping with group object ID `3f2b…` instead of the display name "oea-system-admins".

## Acceptance Criteria

**AC1**:

- Given: the admin enters a value that is not in the identifier format of the selected provider (e.g. a display name)
- When: saving the mapping
- Then: it is rejected with an explanatory message

**AC2**:

- Given: a token with an Entra ID groups overage indicator instead of a `groups` claim
- When: the user logs in
- Then: no admin permission is granted and a warning event is logged

**AC3**:

- Given: a token from an issuer other than the configured one containing the mapped claim
- When: login completes
- Then: no admin permission is granted

## Verification Method

- [ ] Method: test (automated, mock IdP)
- [ ] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-014, REQ-166
- **Follow-on effects**: REQ-018 (warning on empty claim)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: any tenant user becomes system admin – high
- Risk 2: silent lockout of legitimate admins on groups overage – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-08 (PR-11) |
