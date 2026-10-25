# User Manual

The user manual of OEA for operators, administrators and users ([ADR-045](../adrs/ADR-045-user-manual.md), [REQ-196](../requirements/req/REQ-196-user-manual.md)). It is a [VitePress](https://vitepress.dev/) site; contributor documentation stays in [`docs/`](../docs/README.md). This file is not part of the published site.

## Structure

| Path | Content |
|---|---|
| `index.md`, `<chapter>/` | English pages (root locale) |
| `de/index.md`, `de/<chapter>/` | German pages, same paths as the English pages |
| `api/reference/`, `de/api/reference/` | API reference, generated from `api/openapi.yaml` at build time (not committed) |
| `.vitepress/config.mts` | locales, navigation and sidebar (chapter list) |
| `.vitepress/theme/` | default theme with the "unreleased" notice |
| `scripts/generate-api-reference.mjs` | generator of the API reference, with tests |

The chapters are Introduction, Setup and first steps, Administration, Usage and API reference. A new page is added to the chapter list in `.vitepress/config.mts` and created in both languages.

## Languages and tone

- English is the source; the German page is written in the same pull request. A page exists in both languages or in neither.
- The manual addresses the reader directly: "you" in English, the informal "du" in German.
- Labels of the user interface are quoted as the interface shows them (currently English only, also in the German manual).

## Coverage

Every page declares in its frontmatter which screens of [`docs/screens/SCREENS.md`](../docs/screens/SCREENS.md) and which user stories it covers, as flow lists:

```yaml
---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-049]
---
```

`python3 scripts/check_manual.py` (CI job "Requirements completeness") fails if

- a page exists in only one language, or a chapter index page is missing,
- the German page covers other screens or stories than the English page,
- a page names an unknown screen or user story,
- a screen with status `implemented` or a user story with status `in-progress`, `review` or `realized` is not covered in both languages.

Every change with user-visible behavior updates the manual in both languages in the same pull request (ADR-045 rule 8).

## Local preview

```bash
cd manual
npm ci --ignore-scripts
npm run dev        # http://localhost:5173, regenerates the API reference first
npm test           # tests of the API reference generator
npm run build      # static site in .vitepress/dist; fails on dead links and on a reference mismatch
```

`MANUAL_BASE` sets the base path of the build (default `/`; the Pages workflow uses the path of the repository site). Without `MANUAL_VERSION`, every page shows that it describes the unreleased state of `main`.
