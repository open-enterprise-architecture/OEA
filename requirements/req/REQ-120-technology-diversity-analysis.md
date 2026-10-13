---
id: REQ-120
title: Technology Diversity Analysis with Executive Summary Export
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-20
  business_objects:
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-120: Technology Diversity Analysis with Executive Summary Export

## Statement

The system MUST offer a technology diversity analysis that reports, per TRM category, the number of distinct SBBs actually in use (via `entity.instanceOfSBBId`). TRM categories with more than one SBB in use MUST be highlighted as "Standardization potential". TRM categories without a configured `preferredStandard` MUST be listed separately. The analysis MUST be exportable as PDF or CSV and MUST contain an executive summary section (top 3 risks: prohibited entities, gap ratio, diversity hotspots).

## Rationale

Technology diversity is a frequent cost driver. Visibility of categories with many different products enables targeted standardization initiatives and gives decision makers exportable analysis bases.

## Acceptance Criteria

**AC1** (Highlight standardization potential):

- When: the TRM category "Container Orchestration" has 3 distinct SBBs in use
- Then: this category is highlighted as "Standardization potential"

**AC2** (List missing standards):

- When: TRM categories without a configured `preferredStandard` exist
- Then: they appear in a separate list "Missing standards"

**AC3** (PDF export with executive summary):

- When: the export as PDF is triggered
- Then: the document contains an executive summary section with the top 3 risks (prohibited entities, gap ratio, diversity hotspots) and detail tables per analysis section

## Dependencies

- **Prerequisites**: REQ-118 (gap ratio for executive summary), REQ-119 (prohibited entities for executive summary), REQ-116 (preferredStandard)
- **Consequences**: none

## Implementation Notes

- Diversity calculation: `COUNT(DISTINCT entity.instance_of_sbb_id) GROUP BY entity.trm_category_id`
- PDF export: server-side generation (e.g. via Puppeteer or WeasyPrint); CSV via stream
- Executive summary: top 3 by severity; prohibited > gap > diversity hotspot

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
