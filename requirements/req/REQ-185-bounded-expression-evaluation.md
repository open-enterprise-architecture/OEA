---
id: REQ-185
title: Bounded evaluation of user-defined expressions
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-20
  business_objects:
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs:
    - ADR-018
supersedes: []
superseded_by: []
---

# REQ-185: Bounded Evaluation of User-Defined Expressions

## Statement

User-defined expressions (CEL rules, regular expressions in property patterns and in CEL `matches`) **MUST** be checked against a cost budget when saved or imported, **MUST** be evaluated with a hard limit that actually stops evaluation, and regular expressions **MUST** be evaluated with a linear-time engine (RE2-compatible).

## Rationale

A timeout on a future does not stop the evaluating thread; nested comprehensions or catastrophic regular expressions over all entities exhaust CPU and make the instance unavailable.

## Context

Source: security review SEC-21, SEC-22 (proposal PR-29). ADR-018 needs amendment (cost limits, bounded executor, removal or restriction of the SpEL fallback) – follow-up for the solution architect.

## Type-Specific Fields

### For type=non-functional

**Category**: security / availability

**Measurable target values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Validation time of a pattern on a 50-character adversarial input | < 100 ms | 50 ms | 100 ms | any configured property pattern |
| Expressions above the cost budget accepted on save/import | 0 | – | ≥ 1 | all CEL rules |
| Evaluation threads | ≤ configured pool size | – | above pool size | conformance analysis over 10,000 entities |

## Acceptance Criteria

**AC1**:

- Given: a CEL expression with nested comprehensions exceeding the cost budget
- When: saved or imported
- Then: it is rejected with a message naming the limit

**AC2**:

- Given: the pattern `(a+)+$` and a 50-character input `aaaa…!`
- When: an entity is validated
- Then: validation completes in under 100 ms

**AC3**:

- Given: 10,000 entities and a rule at the cost limit
- When: the conformance analysis runs
- Then: the number of threads used for evaluation never exceeds the configured pool size, and evaluations that exceed the runtime limit are stopped

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-018, REQ-032, REQ-149
- **Follow-on effects**: REQ-033 (import of rules and patterns)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: denial of service by a single rule or pattern – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-21/SEC-22 (PR-29) |
