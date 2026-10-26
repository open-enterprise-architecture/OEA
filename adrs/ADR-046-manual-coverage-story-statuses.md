# ADR-046: User Story Statuses Covered by the User Manual Check

**Status**: accepted
**Date**: 2026-10-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Requirements Engineer
**Informed**: all contributors

## Context and Problem

[ADR-045](./ADR-045-user-manual.md) rule 3 makes "every function is described" checkable: `scripts/check_manual.py` fails if a user story with status `in-progress` or `realized` is not covered by a manual page in both languages. While implementing the change `s2c-manual-site`, two gaps in this status list became visible:

1. **`review` was missing.** The user story template (`templates/user-story.template.md`) defines the lifecycle `backlog | ready | in-progress | review | done`. A story leaves `in-progress` for `review` once it is implemented; with the original list, a story in review dropped out of the check. The repository owner decided on 2026-10-24 to include `review`; ADR-045 and REQ-196 (version 0.2.0) record this only as a note.
2. **`done` is missing.** The status list of ADR-045 uses `realized` from the status transition `accepted → realized` in `AGENTS.md`, while the template uses `done` as the final status. Both values occur: on 2026-10-26, US-015, US-016 and US-017 carry `done` and US-046 carries `realized`. Stories with status `done` are not checked, so the bootstrapping stories US-015 to US-017 are implemented but not required to be described in the manual.

The status list is a rule of the quality gate, not an implementation detail of the script, so it is decided here instead of in notes.

## Decision Drivers

- **No implemented function without description**: ADR-045 and REQ-196 AC2 require every function to be described; a status list that lets implemented stories drop out undermines the check
- **Description written with the change**: ADR-045 rule 8 expects the manual to be updated in the same change that implements the behavior; the check has to apply from the moment implementation starts, not only after acceptance
- **One rule for both status vocabularies**: stories use `done` (template) and `realized` (`AGENTS.md`); the check must not depend on which word a story happens to use
- **Stable rule when the lifecycle changes**: a new intermediate status should not silently remove stories from the check

## Considered Options

### Option 1: Keep the list of ADR-045 plus `review` (`in-progress`, `review`, `realized`)

- **Pro**: matches the owner decision of 2026-10-24 and the current check; no further manual pages needed now
- **Con**: stories with status `done` stay unchecked, although they are implemented (US-015 to US-017)
- **Con**: every new or renamed status needs another decision, otherwise stories drop out unnoticed

### Option 2: Every status from `in-progress` onward (`in-progress`, `review`, `done`, `realized`)

- **Pro**: every story whose implementation has started is covered, whatever final status word it carries
- **Pro**: closes the gap of US-015 to US-017 now
- **Con**: the manual has to describe US-015 to US-017 before the check can be tightened (setup token handover, single bootstrapping, audit entry of the bootstrapping – all on the operator side, mostly in "First administrator")
- **Con**: the check still lists statuses explicitly; a new status after `in-progress` must be added to the list

### Option 3: Every status except the ones before implementation (all but `backlog`, `ready`, `proposed`, `accepted`, `rejected`)

- **Pro**: a new status after `in-progress` is checked automatically
- **Con**: a new status before implementation (or a typo) would make the check fail for stories nobody has implemented yet
- **Con**: harder to read in the requirement: REQ-196 names the statuses that are checked, not the ones that are not

## Decision

We choose **Option 2: every status from `in-progress` onward** – `in-progress`, `review`, `done` and `realized`.

**Rationale**: it is the only option under which every implemented story is checked regardless of the status vocabulary, while a story that is not yet in implementation can never break the build. The explicit list stays readable in REQ-196; a later change of the lifecycle has to touch this list deliberately.

Rule 3 of ADR-045 reads with this decision: "A CI check fails if an implemented screen of `docs/screens/SCREENS.md` or a user story with status `in-progress`, `review`, `done` or `realized` is not covered in both languages, or if the German page covers something different from the English page."

## Consequences

### Positive Consequences

- No implemented user story can drop out of the manual because of its status word
- The owner decision of 2026-10-24 on `review` becomes a decision record instead of a note
- The check starts with the implementation, in line with ADR-045 rule 8

### Negative Consequences / Trade-offs

- Before `scripts/check_manual.py` checks `done`, the manual needs sections for US-015, US-016 and US-017 in English and German; otherwise the build fails
- REQ-196 (target table and AC2) needs a version bump to name `done`
- The two status vocabularies (`done` and `realized`) remain; harmonizing them is not part of this decision

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-045](./ADR-045-user-manual.md) (user manual, rule 3 refined by this decision)

**Use Cases / Requirements**:

- [REQ-196: Multilingual user manual](../requirements/req/REQ-196-user-manual.md) (AC2)
- [US-015](../requirements/user-stories/US-015-no-parallel-bootstrapping.md), [US-016](../requirements/user-stories/US-016-audit-log-bootstrapping.md), [US-017](../requirements/user-stories/US-017-secure-setup-token-handover.md) (status `done`, covered by "First administrator" since this decision)

## Notes

Accepted by the repository owner on 2026-10-26. Implemented in the same pull request: `done` added to `STORY_STATUSES` in `scripts/check_manual.py`; US-015 to US-017 covered in `manual/setup/first-administrator.md` in both languages (token validity and handover, simultaneous attempts, audit entries); REQ-196 (version 0.3.0), the main spec `platform/user-manual`, rule 3 of ADR-045 and `manual/README.md` updated.
