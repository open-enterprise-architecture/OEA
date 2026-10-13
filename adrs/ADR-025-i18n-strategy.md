# ADR-025: I18N Strategy — Two-Layer with ETag Caching and SSE Invalidation

**Status**: accepted
**Date**: 2026-06-29
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Partially superseded**: 2026-10-13 by [ADR-030](./ADR-030-postgresql-only.md) — multi-database parts no longer apply (PostgreSQL only)

---

## Context

OEA is multilingual (ADR-023). Two categories of labels must be translated:

1. **Static UI labels**: menus, buttons, validation messages, system messages —
   these only change with a new software release.

2. **Dynamic configuration labels**: meta type names, property labels, enum values,
   category names — these are changed by administrators at runtime via the metamodel editor
   (SCR-030) and are stored in `i18n_entries` (DB).

For category 2 the challenge is: how does the frontend ensure that labels
are updated promptly after an admin change, without unnecessary API calls?

### Language Decisions

- **Record fields** (name, description of entities, business objects) are stored in the
  **corporate language** of the respective OEA instance — no multilingualism at the data level.
- **UI and configuration labels** are multilingual. The display language is determined by
  `persons.ui_language` (BCP 47: `de`, `en`, `fr`, `it`).

---

## Decision

### Layer 1 — Static Labels: vue-i18n Bundle

All fixed UI labels are embedded as static JSON files in the Vue 3 application
and resolved at runtime via `vue-i18n`. No API call, no latency.

```text
frontend/src/i18n/
  de.json   ← menus, buttons, validation messages
  en.json
  fr.json
  it.json
```

New language = new bundle. No database change needed.

### Layer 2 — Dynamic Labels: API Endpoint with ETag Caching

```http
GET /api/v1/i18n/{locale}
```

**Response:**

```http
HTTP/1.1 200 OK
ETag: "a3f8c2d1"
Cache-Control: max-age=300, stale-while-revalidate=3600
Content-Type: application/json

{
  "metatype.ac.name": "Application Component",
  "metatype.ac.description": "An application ...",
  "property.version.label": "Version",
  "enum.lifecycle.planned.label": "Planned",
  ...
}
```

The `ETag` value is a hash over `MAX(updated_at)` of all `i18n_entries` for the requested
locale. If no entry changes, the ETag remains identical.

**Subsequent requests (browser / Pinia store):**

```http
GET /api/v1/i18n/de
If-None-Match: "a3f8c2d1"

→ 304 Not Modified   (no body, no traffic — no stale problem)
→ 200 OK + new ETag (only on change by admin)
```

`max-age=300` (5 minutes): Client asks again at the latest every 5 minutes.
`stale-while-revalidate=3600`: Client shows cached labels immediately, checks in the background.

### Layer 2b — Immediate Invalidation: Server-Sent Events (optional, Enterprise)

When an admin saves labels in the metamodel editor, the server sends an SSE event:

```http
GET /api/v1/events/stream   (SSE channel, stays open)

data: {"type": "i18n_invalidated", "locale": "de"}
```

The Pinia store then immediately invalidates the i18n cache and reloads the endpoint.
All logged-in users see the change in real time without reloading the page.

In Community operation: can be disabled via `oea.sse.enabled=false`.

### Label Key Convention

```text
{entity_type}.{entity_id_or_slug}.{field}

metatype.ac.name
metatype.ac.description
property-def.{uuid}.label
enum-value.{uuid}.label
property-category.{uuid}.name
role.enterprise-architect.name
```

---

## Distinction

| What | Where |
|---|---|
| Menus, buttons, toast messages | vue-i18n bundle (static) |
| Meta type names/descriptions | `GET /api/v1/i18n/{locale}` + ETag |
| Property labels, enum values | `GET /api/v1/i18n/{locale}` + ETag |
| Record fields (name, description) | Corporate language, single-language |
| User language | `persons.ui_language`, set at login |

---

## Rejected Alternatives

### No caching (always reload)

Every navigation → API call → noticeable latency with large metamodels. **Rejected**.

### Infinity cache (load once)

Stale problem on admin changes without page reload. **Rejected**.

### Embed labels in every API response

Every endpoint would have to be i18n-aware; massive overhead with large payloads. **Rejected**.

### WebSocket instead of SSE

SSE suffices for unidirectional server→client invalidation; WebSocket is overengineering here.
**Simplified to SSE**.

---

## Consequences

**Positive:**

- Stale problem solved via ETag (304 on unchanged state)
- No unnecessary traffic (no payload on 304)
- Admin changes propagate within max. 5 minutes passively,
  or immediately via SSE (Enterprise)
- Static and dynamic labels clearly separated — easy to test

**Negative / Compromises:**

- Backend must maintain the ETag hash over `i18n_entries` (simple: `MAX(updated_at)`)
- SSE channel increases the number of open connections (one per logged-in user)
- Label key convention must be adhered to consistently in app and DB

---

## Affected Concept Chapters

- §21 (Tech Stack Frontend), §9 (Metamodel), §15 (Multilingualism)

## Related ADRs

- ADR-023: Multi-DB strategy (i18n_entries table)
- ADR-008/009: Frontend platform (Vue 3)
