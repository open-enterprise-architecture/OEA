---
id: US-082
title: Spell Checking in the WYSIWYG Editor
use_case: UC-09
requirement: REQ-080
priority: could
status: proposed
story_points: 3
created: 2026-06-26
---

# US-082: Spell Checking in the WYSIWYG Editor

**As** a Solution Architect (Michael, SH-04)  
**I want** to use spell checking when writing Arc42 chapter texts,  
**so that** documentation quality is ensured without external tools.

## Acceptance Criteria

- [ ] The TipTap editor has `spellcheck="true"` → the browser marks errors natively (0 additional effort)
- [ ] Admin can optionally configure `languageToolUrl` (self-hosted LanguageTool server)
- [ ] If configured: errors appear inline (spelling red, grammar blue, style gray); click → correction suggestions
- [ ] Check language follows the user's UI language (REQ-079); can be overridden in the editor
- [ ] Text is sent to LanguageTool only after an idle pause (> 500ms)
- [ ] If LanguageTool is unreachable: browser spellcheck stays active, no error in the UI
- [ ] User can disable the check via a toolbar button

## Technical Notes

- TipTap custom extension for LanguageTool (`POST /v2/check`)
- Docker image: `languagetool/languagetool` (LGPL, self-hostable, REQ-075-compliant)
- Derive BCP-47 language codes from the REQ-079 i18n key
