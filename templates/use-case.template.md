---
id: UC-{{NN}}
title: {{Descriptive Title}}
status: draft  # draft | reviewed | approved | implemented | verified
priority: must  # must | should | could | wont (MoSCoW)
target_release: walking-skeleton  # walking-skeleton | v1.0 | v1.x | future
complexity: medium  # trivial | small | medium | large | xl
version: 0.1.0
created: YYYY-MM-DD
last_updated: YYYY-MM-DD
primary_actor: SH-NN
secondary_actors:
  - SH-NN
references:
  business_objects:
    - {{object-id}}
  concept:
    - concept/...
  related_use_cases:
    - UC-NN
---

# UC-{{NN}}: {{Descriptive Title}}

<!--
Usage: One use case per file, goal-oriented from the user's perspective.
File name: requirements/use-cases/UC-NN-shortname.md

IMPORTANT: Use cases describe WHAT someone wants to achieve, not HOW the tool works.
Bad:  "Create ApplicationComponent"
Good: "Capture a new application in the landscape repository so that it appears in views"
-->

## Goal in Context

In which larger context does this use case sit? What is the persona's overarching goal, and what does the UC contribute to it?

## Persona and Story

**Primary Actor**: [{{Name}}](../../business-analysis/stakeholders/SH-NN-kurzname.md)
**Other Stakeholders**: [{{Name}}](../../business-analysis/stakeholders/SH-NN-kurzname.md) (optional)

**Story**: As {{persona}}, I want {{action/capability}} so that {{benefit}}.

## Trigger

What triggers this use case?
- External occasion: {{e.g. workshop preparation, customer request}}
- Timing: {{e.g. quarterly, ad hoc}}
- Predecessor use case: {{e.g. after UC-NN}}

## Preconditions

What must hold before the use case can start?

- [ ] Persona is logged in to the system
- [ ] Persona has role X
- [ ] Business object Y exists
- [ ] {{further preconditions}}

## Postconditions

### On Success

What is true after a successful flow?

- {{e.g. Application Component exists in the repository}}
- {{Audit log entry was written}}
- {{Persona sees the success confirmation}}

### On Failure

What remains unchanged if the UC fails?

- {{e.g. no data was written}}
- {{Persona receives a clear error message}}

## Basic Flow

Step by step, what the persona does (and what the system responds):

1. **Persona**: {{action}}
2. **System**: {{reaction}}
3. **Persona**: {{action}}
4. **System**: {{reaction}}
5. **Persona**: {{action}}
6. **System**: {{confirmation}}

<!-- 5-10 steps are typical. More → probably a process rather than a UC. -->

## Alternative Flows

Branches in the basic flow:

**A1 – {{Alternative}}**: At step {{N}}, if {{condition}}, then:
1. {{deviating step}}
2. {{...}}
- Rejoins at step {{M}} of the basic flow or ends here

**A2 – {{further alternative}}**:
- {{...}}

## Exception Flows

What must the system do if something goes wrong?

**E1 – {{Error}}**:
- Condition: {{when does the error occur}}
- Expected reaction: {{error message, rollback, fallback}}
- Resumption: {{can the user continue? Where?}}

**E2 – {{Error}}**:
- {{...}}

## Data Flow

Which data flows how?

| Step | Data | Direction | Remark |
|---|---|---|---|
| 1 | {{input}} | Persona → System | |
| 2 | {{validation}} | System internal | |
| 3 | {{persistence}} | System → DB | |
| 4 | {{confirmation}} | System → Persona | |

## Involved Business Objects

Which business objects are read, written, changed?

| Business Object | Operation | Note |
|---|---|---|
| [{{object-id}}](../../business-objects/{{object-id}}.md) | read \| create \| update \| delete | |
| | | |

## Acceptance Criteria

Verifiable conditions that must be met for the use case to count as implemented.

- [ ] Basic flow can be run through completely
- [ ] Preconditions are checked
- [ ] Postconditions are reached
- [ ] Alternative flows A1, A2 work
- [ ] Error cases E1, E2 are handled correctly
- [ ] Audit log contains {{expected entries}}
- [ ] {{further concrete and measurable criteria}}

## Out of Scope

What deliberately does NOT belong to this use case, even if it seems close.

- {{similar scenario that is a separate UC, however}}
- {{feature coming in v2}}

## Concept References

At least one concept chapter is mandatory:

- [§N {{Chapter}}](../../concept/...)

## Implementation Notes

Notes for the solution architect and engineering agents:

- EntityTypes: {{used in the metamodel}}
- Relations: {{relevant in the metamodel}}
- Constraints: {{to be observed}}
- Performance notes: {{expected orders of magnitude}}

## Realizing Components

<!-- To be filled once requirements/user stories exist. -->

- Requirements: REQ-NNN, REQ-NNN
- User Stories: [US-NNN: Title](../user-stories/US-NNN-kurzname.md)
- ADRs: ADR-NNN
- Test Cases: {{reference to test suite}}
- Implementation: {{commit hash or PR link}}

## Open Questions

What must be clarified before implementation?

- [ ] Question 1
- [ ] Question 2

## Notes

Free text, discussions, history.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | YYYY-MM-DD | {{Author}} | Initial draft |
