---
id: REQ-152
title: Dynamic Labels with ETag Caching
type: interface
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-06
  adrs:
    - ADR-025
supersedes: []
superseded_by: []
---

# REQ-152: Dynamic Labels with ETag Caching

## Statement

The system **MUST** provide configurable labels (MetaType names, property labels, enum values) via `GET /api/v1/i18n/{locale}` and **MUST** support ETag-based caching so that unchanged label sets are answered with HTTP 304.

## Rationale

MetaType names and property labels are changed by administrators at runtime. Without a cache mechanism, labels would be displayed stale in the frontend for up to several minutes after an admin change (if updated at all). ETag solves the staleness problem without unnecessary traffic.

## Context

Two label categories: static UI labels (vue-i18n bundle, no API call) and dynamic configuration labels (`i18n_entries` table, via API). ETag value = SHA-256 over `MAX(updated_at)` of all `i18n_entries` for the requested locale. (ADR-025)

## Type-Specific Fields

**Interface**: REST

**Specification**: `api/openapi.yaml` → `GET /i18n/{locale}`

**Contracts**:

- Endpoint: `GET /api/v1/i18n/{locale}` (locale: de | en | fr | it)
- Response schema: I18nBundle (flat key-value map)
- Header: `ETag`, `Cache-Control: max-age=300, stale-while-revalidate=3600`
- 304 Not Modified on unchanged ETag

## Acceptance Criteria

**AC1**:

- Given: label set for `de` not changed since the last request
- When: `GET /api/v1/i18n/de` with `If-None-Match: "<etag>"`
- Then: 304 Not Modified; no response body

**AC2**:

- Given: admin changes a MetaType name in the metamodel editor
- When: `GET /api/v1/i18n/de` without If-None-Match
- Then: 200 OK; new ETag; bundle contains the updated label

**AC3** (SSE, optional):

- Given: client has `GET /api/v1/events/stream` open
- When: admin saves a label change
- Then: SSE event `{"type":"i18n_invalidated","locale":"de"}` received within 2s

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test; change a label; send a second request with ETag
- [ ] Pass criterion: 304 on unchanged state; 200 after change
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-156 (PostgreSQL; `i18n_entries` table)
- **Consequences**: US-140

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-025 |
