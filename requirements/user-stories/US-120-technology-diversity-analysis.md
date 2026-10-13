# US-120: Analyze Technology Diversity and Export as PDF/CSV

**ID**: US-120
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see per TRM category how many different SBBs are actually used, and to export the analysis with an executive summary as PDF or CSV, so that I can make standardization potential visible and inform decision makers.

## Reference

**Use Case**: [UC-20](../use-cases/UC-20-analyze-continuum-conformance.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-120](../req/REQ-120-technology-diversity-analysis.md)

## Acceptance Criteria

**AC1** (Highlight standardization potential):

- When: the TRM category "Container Orchestration" has 3 different SBBs in use
- Then: this category is highlighted as "Standardization potential"

**AC2** (List missing standards):

- When: TRM categories without a configured `preferredStandard` exist
- Then: they appear in a separate list "Missing standards"

**AC3** (PDF export with executive summary):

- When: I trigger the export as PDF
- Then: the document contains an executive summary section with the top 3 risks (prohibited entities, gap rate, diversity hotspots) and detail tables per analysis section

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
