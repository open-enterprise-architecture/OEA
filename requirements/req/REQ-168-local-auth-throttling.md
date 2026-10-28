---
id: REQ-168
title: Brute-force protection for local authentication
type: non-functional
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-02
    - UC-03
  business_objects:
    - local-credential
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

# REQ-168: Brute-Force Protection for Local Authentication

## Statement

The application itself **MUST** protect local authentication (password, TOTP, setup token, bootstrap login) against brute force by per-account throttling with exponential backoff and a temporary – never permanent – lock, a per-source-address rate limit on login endpoints, counting of TOTP failures per login transaction, identical generic responses for existing and non-existing accounts, and security events in the audit log.

## Rationale

The portal may be exposed to the internet (owner decision Q2). Without throttling, a 6-digit TOTP falls in minutes after a phished password, and password spraying is unbounded. Only the application knows the account context, so the protection must be implemented there. A permanent lock would enable denial of service against named users.

## Context

Source: security review SEC-09 (proposal PR-12); owner decisions Q2 (priority must) and the additional owner decision of 2026-10-13: protection in the application; a reverse proxy with rate limiting is documented as a recommended additional layer for internet exposure, not as a replacement. With an external OIDC IdP, the IdP protects its own login; this REQ applies to local accounts and bootstrap.

**Implementation (change s3-throttling-and-denials, 2026-10-28):** failed setup-token attempts count towards the same per-source-address counter as failed local logins (100 within 10 minutes); a throttled address gets 429 with `Retry-After` on `POST /setup` without the token being evaluated (spec `identity/bootstrapping`). The per-account and per-address throttling of local logins was implemented earlier (spec `identity/local-login`).

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable target values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Failed attempts per account before backoff starts (default) | 5 within 15 min | – | > 10 configured | per account identifier, incl. non-existent identifiers |
| Backoff | exponential, starting at 1 s, temporary lock capped at 15 min (default) | – | permanent lock | per account identifier |
| Failed attempts per source address (default) | 100 within 10 min, then throttled | – | unlimited | login, TOTP and setup-token endpoints |
| Wrong TOTP codes per login transaction | 5, then transaction invalidated | – | > 10 | per login transaction |
| Response difference existing vs. non-existing account | none (content, status, timing within REQ-006) | – | any | all local login endpoints |

## Acceptance Criteria

**AC1**:

- Given: 5 consecutive failed password attempts for one username within 15 minutes
- When: a 6th attempt is made
- Then: it is delayed or rejected for at least the configured backoff, the response is identical for existing and non-existing usernames, and the account is never locked permanently

**AC2**:

- Given: a login transaction in the TOTP step
- When: 5 wrong codes are entered
- Then: the transaction is invalidated and the password step must be repeated

**AC3**:

- Given: 100 failed attempts from one IP address across different usernames within 10 minutes
- When: further attempts arrive
- Then: they are throttled and a security event `auth.throttled` is written to the audit log

**AC4**:

- Given: a temporarily locked account
- When: the lock period has elapsed
- Then: the correct credentials succeed again without administrator intervention

**AC5**:

- Given: the operator documentation
- When: inspected
- Then: it recommends a rate-limiting reverse proxy as an additional layer for internet exposure and states that it does not replace the in-application protection

## Verification Method

- [ ] Method: test (automated, controlled clock) + inspection (operator documentation)
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes (AC1–AC4)

## Dependencies

- **Prerequisites**: REQ-006, REQ-009, REQ-010, REQ-011, REQ-013, REQ-030
- **Follow-on effects**: REQ-175 (security events), REQ-186 (general rate limits)
- **Conflicts**: REQ-008 (login latency) – backoff delays apply only after failures

## Risks of Non-Fulfillment

- Risk 1: account takeover of local accounts on an internet-exposed instance – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-09 (PR-12) with owner decisions Q2 and 2026-10-13 (in-application protection) |
| 0.2.0 | 2026-10-28 | Change s3-throttling-and-denials | Setup-token endpoint throttled per source address (security re-review WS-04, #61) |
