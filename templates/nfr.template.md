# NFR-NN: {{Descriptive Title}}

<!--
Usage: One non-functional requirement per file.
File name: nfr/NFR-NN-shortname.md (e.g. NFR-03-response-time.md)

NFRs differ from functional use cases: they describe quality attributes
of the system, not actions. Mandatory: measurable target values with verification method.
-->

**ID**: NFR-NN
**Category**: performance | scalability | reliability | availability | security | maintainability | usability | portability | compatibility | cost | sustainability
**Priority**: must | should | could | wont
**Status**: proposed | approved | realized | verified

## Requirement

A concise statement in one sentence of what must be fulfilled.

## Context and Rationale

Why is this requirement important? Which stakeholder, which use case justifies it?

## Measurable Target Value

NFRs without measurable target values are wishful thinking. Examples:

- **Performance**: "p95 response time < 200ms with 1000 entities in the repository"
- **Scalability**: "Repository scales to 10,000 entities without architecture change"
- **Availability**: "99.5% availability per quarter (≤ approx. 11 hours of downtime)"
- **Security**: "Property-level authorization is enforced in every API response"
- **Maintainability**: "New EntityType can be added via schema configuration without code change"
- **Usability**: "A new user can create their first Application Component in <5 min without reading documentation"

| Metric | Target Value | Threshold (warning) | Threshold (critical) |
|---|---|---|---|
| | | | |

## Scope

- **Applies to**: all EntityTypes | specific EntityTypes ([list]) | specific operations ([list])
- **Does not apply to**: <exceptions, if any>
- **Temporal**: permanent | from release X | until release Y

## Verification Method

How is it verified whether the requirement is met?

- [ ] Method: test | inspection | demonstration | analysis | monitoring
- [ ] Test setup:
- [ ] Measuring tool:
- [ ] Pass criterion:

## Acceptance Criteria

- [ ] Target value measurable with a defined test setup
- [ ] Test can be executed automatically (CI-capable)
- [ ] Alerting on threshold violation (for run-time NFRs)

## References

**Concept reference**:
- [§N (Chapter)](../../concept/...)

**Stakeholders**:
- [SH-NN: Name](../stakeholders/SH-NN-kurzname.md)

**Related use cases / stories**:
- [UC-NN: Title](../use-cases/UC-NN-kurzname.md)

**Related ADRs**:
- ADR-NN (e.g. persistence decision, because performance-relevant)

## Risks of Non-Fulfillment

What happens if this NFR is not fulfilled?

- Risk 1:
- Risk 2:

## Trade-offs

Which other requirements may be restricted in order to fulfill this one?

- vs. NFR-XX:

## Notes
