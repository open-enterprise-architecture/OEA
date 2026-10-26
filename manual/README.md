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
| `walkthrough/`, `playwright.config.ts` | walkthrough tests that take the screenshots (ADR-045 rule 5) |
| `scripts/screenshots.mjs` | prepares the screenshots for the build, with tests |
| `scripts/publish-site.mjs` | places a build into the branch `gh-pages` (CI only), with tests |

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
- a screen with status `implemented` or a user story with status `in-progress`, `review`, `done` or `realized` is not covered in both languages.

Every change with user-visible behavior updates the manual in both languages in the same pull request (ADR-045 rule 8).

## Screenshots

Screenshots are never made by hand. The walkthrough tests in `walkthrough/` drive the development stack from a fresh instance through setup, login, second factor, starter configuration and catalogs, and save each screenshot under a stable name in `screenshots/<language>/<name>.png`. A page shows a screenshot by name:

```md
<Screenshot name="catalog-table" alt="Table of a catalog with application components" />
```

The component shows the screenshot in the language of the page and falls back to English while the user interface offers only English. A new screenshot needs a `shoot(page, '<name>')` call in the walkthrough; a changed screen or flow updates the walkthrough in the same pull request (ADR-045 rule 8).

```bash
scripts/walkthrough.sh   # from the repository root: fresh development stack, walkthrough, stack removed
```

Builds without screenshots (pull requests, local preview) show a placeholder. Publication builds set `MANUAL_SCREENSHOTS=required` and fail if a referenced screenshot is missing.

## Versions and publication

`.github/workflows/pages.yml` runs the walkthrough and publishes into the branch `gh-pages`:

| Event | Published |
|---|---|
| push to `main` | manual of `main` under `/next/` (and at the root while no release exists), Storybook under `/storybook/` |
| tag `vX.Y.Z` | manual of the tag under `/vX.Y/` (a patch release replaces its minor version) and, for the newest minor version, at the root |

`versions.json` at the site root lists the releases; the version selector reads it at runtime, so older manuals also offer newer releases.

## Local preview

```bash
cd manual
npm ci --ignore-scripts
npm run dev        # http://localhost:5173, regenerates the API reference first
npm test           # tests of the API reference generator
npm run build      # static site in .vitepress/dist; fails on dead links and on a reference mismatch
```

`MANUAL_BASE` sets the base path of the build (default `/`), `MANUAL_SITE_ROOT` the root of the site where `versions.json` lives (default: `MANUAL_BASE`). Without `MANUAL_VERSION` (the tag of a release), every page shows that it describes the unreleased state of `main`.
