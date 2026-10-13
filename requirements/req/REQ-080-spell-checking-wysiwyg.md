---
id: REQ-080
title: Spell checking in the WYSIWYG editor
type: functional
priority: could
status: proposed
version: 0.1.0
use_case: UC-09
created: 2026-06-26
author: requirements-engineer
---

# REQ-080: Spell checking in the WYSIWYG editor

## Description

All WYSIWYG editors in OEA (Arc42 chapters, entity descriptions, free-text properties) support spell checking. The base function uses the browser-native spellcheck (no implementation effort). Optionally, a LanguageTool server can be configured for extended grammar and style checking.

## Acceptance Criteria

**AC1** (Browser-native basis):
- TipTap editor element has `spellcheck="true"` set
- The browser marks misspelled words with a red underline
- Right-click → browser correction suggestions (default behavior)

**AC2** (Language of the check):
- Browser spellcheck follows the system language of the browser/operating system
- If LanguageTool is active: check language follows the user's UI language selected in REQ-079; explicitly overridable in the editor

**AC3** (LanguageTool configurable):
- Admin can set `languageToolUrl` in the instance settings (e.g., `http://languagetool:8010`)
- If configured: editor sends text to the LanguageTool API and displays errors with colored underlining
- Click on an underlined word → popup with correction suggestions; click on a suggestion → replaces text

**AC4** (Error types with LanguageTool):
- Spelling errors: red underline
- Grammar errors: blue underline
- Style hints: gray underline (can be disabled per user setting)

**AC5** (Fallback when unreachable):
- If LanguageTool is not reachable: no error in the UI; browser-native spellcheck remains active
- Connection error is not displayed prominently (no red banner); optionally: small indicator icon in the toolbar

**AC6** (Offline / air-gapped):
- Self-hosted LanguageTool (`languagetool/languagetool` Docker image, LGPL) configurable; consistent with REQ-075
- No external service call if `languageToolUrl` is not set

**AC7** (Can be disabled):
- User can disable spell checking in the editor via a toolbar button (preference is saved)

## Technical Notes

- TipTap natively supports the `spellcheck` attribute on the editor root element
- LanguageTool integration: TipTap custom extension; sends text blocks via `POST /v2/check` to the LanguageTool server; errors are displayed as TipTap decorations
- LanguageTool Docker image: `languagetool/languagetool` (LGPL); ports: 8010; no API key for self-hosted
- Language codes follow BCP 47 (`en-US`, `de-DE`, `fr-FR`); mapping from REQ-079 i18n language key → BCP 47 code
- Performance: send text to LanguageTool only after an idle pause (> 500ms since last input), not on every keystroke
