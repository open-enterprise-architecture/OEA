# US-117: Find Outdated TRM Categories via Review Date Filter

**ID**: US-117
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want `lastReviewedAt` to be updated automatically on every save and to be able to filter by outdated categories in the TRM overview, so that I can conduct periodic TRM governance reviews efficiently.

## Reference

**Use Case**: [UC-19](../use-cases/UC-19-configure-trm.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-117](../req/REQ-117-trm-review-date.md)

## Acceptance Criteria

**AC1** (Automatic lastReviewedAt):
- When: I save a TRM category
- Then: `lastReviewedAt` is set to the current UTC date

**AC2** (Filter by review date):
- When: I activate the filter "Last reviewed > 12 months ago" in the TRM overview
- Then: categories with `lastReviewedAt` < today − 12 months or `lastReviewedAt = null` are highlighted

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
