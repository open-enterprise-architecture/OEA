---
id: US-081
title: Configure and Switch the UI Language
use_case: UC-04
requirement: REQ-079
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-081: Configure and Switch the UI Language

**As** a Lead Enterprise Architect (Kurt, SH-03)  
**I want** to activate additional UI languages as admin and, as a user, choose my preferred language in the profile settings,  
**so that** international teams can use OEA in their working language.

## Acceptance Criteria

- [ ] Admin area: activate/deactivate languages; upload language packs as JSON
- [ ] Profile settings: dropdown with all activated languages; selection switches the UI immediately (no reload)
- [ ] Instance default language configurable during bootstrapping and afterwards in the admin area
- [ ] All UI strings (navigation, buttons, error messages, validation texts) are translated
- [ ] EntityType display names (`displayName` map) follow the active language
- [ ] Fallback to English if a key is missing in the active language (no empty string)
- [ ] Built-in language packs: EN + DE are shipped with OEA

## Technical Notes

- vue-i18n; language JSON files separated by namespace
- `EntityTypeDefinition.displayName: Record<string, string>` instead of `string`
- API response always contains the complete `displayName` object; client-side language selection
