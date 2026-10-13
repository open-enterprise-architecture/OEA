# US-140: Reload Dynamic Labels in the Frontend

**ID**: US-140
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a user, I want to see MetaType names, property labels, and enum values in my configured language, and after an admin change these labels should be updated without a page reload, so that I always see the current designations.

## Reference

**Use Case**: [UC-04: Configure Metamodel](../use-cases/UC-04-configure-metamodel.md)
**Persona**: All logged-in users
**Requirements**: REQ-152

## Acceptance Criteria

**AC1**:

- Given: user with ui_language=`de` is logged in
- When: app loads
- Then: Pinia store loads `GET /api/v1/i18n/de`; MetaType names visible in German

**AC2**:

- Given: ETag of the current label bundle known
- When: store refresh after 5 minutes
- Then: `If-None-Match` header sent; if state unchanged → 304; no re-render

**AC3**:

- Given: SSE enabled (`oea.sse.enabled=true`); admin changes label
- When: `i18n_invalidated` event received
- Then: store reloads bundle; affected labels in the UI updated without reload

## Technical Notes

- Pinia store `useI18nStore` manages bundle + ETag
- Refresh interval: 5 minutes (Cache-Control: max-age=300)
- SSE listener on `GET /api/v1/events/stream` (EventSource API in the browser)
- Static UI labels (menus, buttons) continue via the vue-i18n bundle — no API call
- `persons.ui_language` determines the locale of the API call

## Definition of Done

- [ ] Acceptance criteria fulfilled
- [ ] ETag handling implemented in the Pinia store
- [ ] SSE listener optional (feature flag)
- [ ] Unit test: store loads correct locale; ETag update correct

## Dependencies

- Waits for: US-001 (login; ui_language from JWT claim or profile)
- Waits for: US-033 (metamodel imported; labels exist in i18n_entries)
