---
id: REQ-079
title: Multilingual user interface (i18n)
type: functional
priority: should
status: proposed
version: 0.1.0
use_case: UC-04
created: 2026-06-26
author: requirements-engineer
---

# REQ-079: Multilingual user interface (i18n)

## Description

The OEA frontend (client app + web portal) supports multiple UI languages. The default is English. Additional languages are configured by the admin as language packs. Each user selects their preferred language in the profile settings.

## Scope

All UI strings are affected:
- Navigation, buttons, menus, dialogs, error messages, validation texts
- EntityType display names (e.g., "Application Component" → "Anwendungskomponente")
- Catalog column headers (derived from EntityType property definitions)
- Tooltips, placeholder texts, success/status messages

Not affected (content layer, no automatic translation):
- User-entered content (entity names, Arc42 texts, descriptions)
- Metamodel configuration (free-text fields from the admin)

## Acceptance Criteria

**AC1** (Default English):
- A freshly set up OEA instance shows the UI entirely in English without further configuration

**AC2** (Switch language):
- When the admin has activated an additional language
- Then every user can select this language in the profile settings
- And the UI switches immediately after selection (no restart needed)

**AC3** (EntityType display name multilingual):
- EntityTypeDefinition supports `displayName: { en: "...", de: "..." }` as a language map
- The UI always shows the display name in the active user language; fallback: English; second fallback: technical identifier

**AC4** (Fallback for missing translation):
- No empty string in the UI: if a key is missing in the active language, the English text appears (no `undefined`)

**AC5** (Language packs importable):
- Admin can upload and activate JSON language packs via the admin UI
- Format: flat JSON `{ "entity.create": "Entität anlegen", ... }`

**AC6** (Instance default configurable):
- Selectable during bootstrapping (UC-02): which language is the instance default language (fallback for users without their own preference)

**AC7** (Offline-capable):
- Language packs are loaded and cached at app start; no external CDN access at runtime (REQ-075)

## Technical Notes

- Frontend: **vue-i18n** (MIT, official Vue 3 i18n plugin)
- Language files: JSON files in the format `{ "namespace.key": "Text" }`, separated by namespaces (e.g., `entity`, `catalog`, `canvas`, `admin`)
- Built-in EntityTypes (ADR-002): are shipped with EN + DE translations; further languages via starter pack or admin import
- `EntityTypeDefinition.displayName` is extended from `string` to `Record<string, string>`; the API always returns the complete language map object; the client chooses the right variant
- Backend: no translation logic needed on the server; only the `displayName` map in the EntityType object
