# US-118: View ABB Coverage Analysis and Gap Rate

**ID**: US-118
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see for all approved ABBs how many conforming entities and implementing SBBs exist, and to read off a gap rate, so that I can identify governance gaps between defined standards and the current-state landscape.

## Reference

**Use Case**: [UC-20](../use-cases/UC-20-analyze-continuum-conformance.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-118](../req/REQ-118-abb-coverage-analysis.md)

## Acceptance Criteria

**AC1** (Gap rate):

- When: 5 approved ABBs exist, 2 of them without an instance
- Then: the analysis shows gap rate = 40%; the table shows all 5 ABBs with instance counts

**AC2** (ABB detail view):

- When: I open an ABB in the analysis table
- Then: I see implementing SBBs and conforming entities as clickable links

**AC3** (Empty state):

- When: no ABBs are configured yet
- Then: the system shows the note "No ABBs configured yet; start with UC-17"

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
