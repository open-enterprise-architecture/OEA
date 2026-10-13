---
id: REQ-012
title: Configurable token lifetime for local authentication
type: data
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
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-012: Configurable token lifetime for local authentication

## Statement

For local authentication (passkey, username/password, with or without TOTP – see REQ-009–REQ-011), the validity period of access token (authorization token) and refresh token MUST be configurable instance-wide.

## Rationale

Unlike external identity providers (Entra ID, Authentik), where the token validity is dictated by the IdP (see REQ-001, AC4), local authentication has no external instance that sets these values. OEA itself must therefore provide sensible, configurable defaults, since different operators (e.g., SH-03 single-user SME vs. more security-conscious organizations) make different security/usability trade-offs.

## Context

Concerns exclusively sessions created via the local authentication mechanisms from REQ-009 (passkey), REQ-010 (username/password + TOTP), and REQ-011 (username/password without second factor).

## Type-Specific Fields

### For type=data

**Affected Data Structure**: Instance-wide authentication configuration (not a Person or Role object)

**Data Type**: composite (two values: `accessTokenLifetime`, `refreshTokenLifetime`, each a duration)

**Constraints**:

- Mandatory field: yes, both values must be set (with a sensible default if not explicitly configured)
- Format: ISO 8601 duration (e.g., `PT15M` for 15 minutes) or an equivalent configuration notation
- Value range: `accessTokenLifetime` < `refreshTokenLifetime`; both > 0
- Uniqueness: uniform instance-wide (not configurable per person)

**Default Value**: `accessTokenLifetime: PT15M`, `refreshTokenLifetime: PT12H`; the refresh token lifetime never exceeds the absolute session lifetime of REQ-165

## Acceptance Criteria

**AC1**:

- Given: an instance with a locally configured access token lifetime of 15 minutes
- When: a person logs in via a local mechanism (passkey, username/password)
- Then: the access token expires after 15 minutes

**AC2**:

- Given: an expired access token but a still-valid refresh token from local authentication
- When: a refresh is requested
- Then: a new access token with the configured lifetime is issued

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: configuration of different lifetime values, check of expiry and refresh behavior
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-009, REQ-010, REQ-011 (at least one of the local mechanisms must exist)
- **Downstream effects**: none known
- **Conflicts**: REQ-001 AC4 (for external IdPs the opposite rule applies: no lifetime set by OEA itself)

## Risks of Non-Fulfillment

- Risk 1: A fixed, non-configurable lifetime could be too short (frustration) or too long (security risk with stolen tokens) for some operators

## Trade-offs

- vs. security: a longer lifetime increases usability but also the risk of token theft – operators must be able to make this trade-off themselves, OEA only provides sensible defaults

## Implementation Notes

- Configuration should be part of the instance-wide auth configuration (same place as enabling REQ-011)
- Does not apply to externally issued tokens (REQ-001, AC4) – there the IdP decides

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Resolves the question left open in UC-01, "How long is a session valid, and how is refresh handled?", for the local authentication case; for external IdPs the question is answered by REQ-001 AC4 (IdP lifetime as upper bound) together with REQ-165 (OEA idle and absolute session limits).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft, clarifies token lifetime separately for external IdP (REQ-001) and local authentication |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Refresh token default PT12H, bounded by the absolute session lifetime of REQ-165 (security review SEC-06) |
