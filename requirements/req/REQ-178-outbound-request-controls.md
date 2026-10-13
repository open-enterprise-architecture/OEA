---
id: REQ-178
title: Controls for server-side outbound requests
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-09
    - UC-02
  business_objects: []
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

# REQ-178: Controls for Server-Side Outbound Requests

## Statement

Every server-initiated outbound HTTP request to a user- or admin-configured URL (webhooks, PlantUML server, LanguageTool server, OIDC discovery and provider test) **MUST** use HTTPS (configurable exception for operator-approved hosts), **MUST** resolve and reject loopback, link-local, private and cloud metadata addresses unless explicitly allowlisted by the operator, **MUST NOT** follow redirects to disallowed addresses, and **MUST NOT** return response bodies to the caller.

## Rationale

Configurable target URLs turn the server into a proxy into the internal network (SSRF): cloud metadata services, the database container or internal admin interfaces become reachable, and delivery logs reveal their responses.

## Context

Source: security review SEC-16, SEC-18 (proposal PR-22). Owner decision Q9: external rendering services are only used if the operator configures them; their requests are subject to this REQ.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-16, SEC-18); OWASP API7 (SSRF)

**Binding strength**: hard (exceptions only via operator allowlist)

**Impact of non-compliance**: internal network scanning and data exfiltration via the OEA server

## Acceptance Criteria

**AC1**:

- Given: a webhook URL `http://169.254.169.254/latest/meta-data/`
- When: it is registered
- Then: the registration is rejected

**AC2**:

- Given: a public webhook host that redirects to `http://10.0.0.5/`
- When: a delivery is attempted
- Then: the redirect is not followed and the delivery is marked failed

**AC3**:

- Given: a hostname that resolves to a private address at delivery time (DNS rebinding)
- When: a delivery is attempted
- Then: the request is not sent

**AC4**:

- Given: an operator-configured PlantUML server in the internal network
- When: the operator adds it to the allowlist
- Then: rendering requests to it succeed, while other private addresses remain blocked

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-068, REQ-080, REQ-147
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: SSRF into internal infrastructure – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-16/SEC-18 (PR-22) |
