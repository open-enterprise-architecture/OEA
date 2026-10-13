---
id: REQ-117
title: Automatically Set and Filter TRM Category Review Date
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-117: Automatically Set and Filter TRM Category Review Date

## Statement

The system MUST automatically set `lastReviewedAt` to the current UTC date on every save of a TRM category entry. The system MUST offer a filter "Last reviewed more than N months ago" (configurable, default: 12 months) in the TRM overview that highlights categories with an outdated or missing review date.

## Rationale

Without review date tracking, the TRM becomes outdated silently and unnoticed. The filter makes outdated categories directly findable and supports periodic TRM governance reviews.

## Acceptance Criteria

**AC1** (Automatic lastReviewedAt):
- When: a TRM category is saved
- Then: `lastReviewedAt` is set to the current UTC date

**AC2** (Filter by review date):
- When: the filter "Last reviewed more than 12 months ago" is activated in the TRM overview
- Then: categories with `lastReviewedAt` < today − 12 months or `lastReviewedAt = null` are highlighted

## Dependencies

- **Prerequisites**: REQ-115 (TRM categories must exist)
- **Consequences**: none

## Implementation Notes

- `lastReviewedAt` as a UTC timestamp; set server-side, not taken from the request body
- Filter: query parameter `?staleSince=12` (months); SQL: `WHERE last_reviewed_at < NOW() - INTERVAL '12 months' OR last_reviewed_at IS NULL`
- UI: highlight as a yellow border or icon in the category list

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
