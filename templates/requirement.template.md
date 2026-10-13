---
id: REQ-{{NNN}}
title: {{Short title of the requirement}}
type: functional  # functional | non-functional | constraint | business-rule | data | interface | compliance
priority: must  # must | should | could | wont (MoSCoW)
status: proposed  # proposed | approved | realized | verified | rejected | deferred | superseded
version: 0.1.0
created: YYYY-MM-DD
last_updated: YYYY-MM-DD
author: requirements-engineer
references:
  use_cases:
    - UC-NN
  business_objects:
    - {{object-id}}
  business_rules:
    - BR-NN
  stakeholders:
    - SH-NN
  concept:
    - concept/...
  adrs:
    - ADR-NNN
supersedes: []  # IDs of previous requirements, if superseded
superseded_by: []  # If this requirement has been replaced
---

# REQ-{{NNN}}: {{Short Title}}

<!--
Usage: One atomic requirement per file.
File name: requirements/req/REQ-NNN-shortname.md (numbered with 3 digits)
-->

## Statement

The system **MUST / SHOULD / MUST NOT** {{concrete requirement in one sentence}}.

<!-- Use RFC 2119 keywords:
  MUST = mandatory
  SHOULD = recommended, may deviate with justification
  MUST NOT = prohibited behavior
  MAY = optional
-->

## Rationale

Why does this requirement exist? Which stakeholder demands it? What danger exists without it?

## Context

Background information, if the statement alone is not sufficient. Which situation? Which prerequisites?

## Type-Specific Fields

<!-- Different mandatory fields depending on the type. Fill in only the matching block. -->

### For type=functional

**Inputs**:

- {{what comes in}}

**Processing**:

- {{what must happen}}

**Outputs**:

- {{what comes out}}

**Error cases**:

- {{when and how the system reacts to errors}}

### For type=non-functional

**Category**: performance | scalability | reliability | availability | security | maintainability | usability | portability | compatibility | sustainability

**Measurable target values**:

| Metric | Target Value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| {{metric}} | {{value + unit}} | {{value}} | {{value}} | {{at which data volume}} |

**Example**: "p95 response time < 200ms with 1000 entities in the repository"

### For type=constraint

**Type of constraint**: technical | legal | organizational | physical

**Source**: {{Who/what prescribes the constraint – standard, law, ARB decision}}

**Binding strength**: hard (cannot be circumvented) | soft (exception possible)

**Impact of non-compliance**: {{consequence}}

### For type=business-rule

**Trigger**: onCreate | onUpdate | onDelete | onRead | continuous

**Affected business objects**: [{{object-id}}](../business-objects/{{object-id}}.md)

**Formal statement** (if possible in formal notation, e.g. JSON Logic, OCL):

```text
{{rule expression}}
```

**Example**: "Customer with `isPersonalData=true` MUST have `legalBasis` set (GDPR Art. 6)"

### For type=data

**Affected data structure**: {{business object or attribute}}

**Data type**: string | integer | decimal | boolean | date | datetime | enum | reference | composite

**Constraints**:

- Mandatory field: yes/no
- Format: {{e.g. ISO 8601, E.164}}
- Value range: {{e.g. 0-100, regex...}}
- Uniqueness: {{locally / globally unique}}

**Default value**: {{if applicable}}

### For type=interface

**Interface**: REST | GraphQL | AsyncAPI | CLI | file format

**Specification**: {{path to OpenAPI/AsyncAPI/etc.}}

**Contracts**:

- Endpoint/path: {{e.g. POST /api/v1/applications}}
- Request schema: {{reference}}
- Response schema: {{reference}}
- Error codes: {{200, 400, 401, 403, 404, 500}}

### For type=compliance

**Regulatory framework**: {{GDPR, ISO 27001, DORA, NIS2, FINMA circulars...}}

**Specific provision**: {{article, section, paragraph}}

**Evidence requirement**: {{what must be documented, how it is to be evidenced}}

**Audit relevance**: {{yes/no, frequency of audits if applicable}}

## Acceptance Criteria

Verifiable conditions that must be met. Preferably Given-When-Then, alternatively a checklist.

**AC1**:

- Given: {{initial situation}}
- When: {{action or condition}}
- Then: {{expected result}}

**AC2**:

- Given:
- When:
- Then:

<!-- At least one AC is mandatory. -->

## Verification Method

How is it verified whether the requirement is met?

- [ ] Method: test (automated) | inspection (manual) | demonstration | analysis | monitoring
- [ ] Test setup: {{description}}
- [ ] Measuring tool: {{tool/framework}}
- [ ] Pass criterion: {{concrete}}
- [ ] Integrated in CI: yes/no

## Dependencies

- **Prerequisites**: REQ-XXX, REQ-YYY (must be fulfilled beforehand)
- **Follow-on effects**: REQ-XXX (is enabled by this one)
- **Conflicts**: REQ-XXX (is in tension with this requirement)

## Risks of Non-Fulfillment

What happens if this requirement is not fulfilled?

- Risk 1: {{description, severity}}
- Risk 2: {{...}}

## Trade-offs

Which other requirements may be restricted in order to fulfill this one?

- vs. REQ-XXX: {{description of conflict}}

## Implementation Notes

Notes for the solution architect and engineering agents – but **no solution prescriptions**:

- {{note guiding the implementation}}
- {{known technical challenges}}

<!-- ATTENTION: Concrete technology choices do NOT belong here.
"System MUST use Redis as cache" → that is solution design, not a requirement.
Instead: "System MUST have a cache layer that achieves a >95% hit rate for repeated lookups" -->

## Implementation

<!-- Filled in during the build. -->

- ADR(s): {{reference to decisions made for the implementation}}
- Implemented by: {{user stories, commits, PRs}}
- Tests: {{reference to test suite}}
- Verification status: {{not-verified | verified | failed}}

## Notes

Discussion, open questions, history.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | YYYY-MM-DD | requirements-engineer | Initial draft from UC-NN |
