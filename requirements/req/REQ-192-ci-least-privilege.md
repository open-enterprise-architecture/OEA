---
id: REQ-192
title: Least privilege for CI/CD workflows
type: constraint
priority: should
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-031
supersedes: []
superseded_by: []
---

# REQ-192: Least Privilege for CI/CD Workflows

## Statement

CI workflows **MUST** declare minimal `permissions` (default `contents: read`), **MUST** pin third-party actions to commit SHAs, and jobs that execute third-party code (dependency installation, builds, tests) **MUST NOT** hold write tokens; write operations **MUST** run in a separate job that only consumes artifacts.

## Rationale

The screenshot workflow (ADR-031) runs hundreds of third-party packages in a job that can push to `main`; a malicious package could use the token to bypass review.

## Context

Source: security review SEC-29, SEC-30 (proposal PR-36). ADR-031 needs amendment (split build and commit jobs, synthetic mock data) – follow-up for the solution architect; the CI file changes are a follow-up outside `requirements/`.

## Type-Specific Fields

**Type of constraint**: organizational / technical

**Source**: security review 2026-10 (SEC-29, SEC-30)

**Binding strength**: hard

**Impact of non-compliance**: unreviewed changes to `main` through compromised dependencies

## Acceptance Criteria

**AC1**:

- Given: any workflow file
- When: linted (e.g. zizmor or actionlint with a policy)
- Then: every workflow declares `permissions` and every `uses:` of a third-party action references a full commit SHA

**AC2**:

- Given: the screenshot workflow (ADR-031)
- When: it runs
- Then: the job running `npm ci` has a read-only token and the commit is created by a separate job that touches only `docs/screens/*.png`

## Verification Method

- [ ] Method: inspection + CI lint
- [ ] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-191
- **Conflicts**: ADR-031 (single job with `contents: write`)

## Risks of Non-Fulfillment

- Risk 1: repository compromise via CI – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-29/SEC-30 (PR-36) |
