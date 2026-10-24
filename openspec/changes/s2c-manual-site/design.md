# Design

## Context

- **Operator and user knowledge**: `docs/operations.md` (280 lines) describes requirements, database roles, provisioning, migration, application start, request limits, key-encryption key and rotation, audit trail verification, PostgreSQL upgrades, the setup phase, login through an identity provider, starter configuration and batch writes – mostly as `curl` sequences. It is linked from `README.md`, `compose.yaml`, `docs/` and from the startup error messages of `StartupGuardFailureAnalyzer`.
- **Functions of the walking skeleton**: the frontend routes `/`, `/setup`, `/login`, `/me`, `/me/second-factor`, `/catalogs`, `/catalogs/:id`. `docs/screens/SCREENS.md` marks SCR-020 (catalog browser) and SCR-028 (create catalog dialog) as `implemented`. User stories by status: `review` US-001, US-013; `in-progress` US-033, US-049, US-141, US-143, US-147; `realized` US-046. Story files carry the status as `**Status**: <value>`.
- **Contract**: `api/openapi.yaml` (OpenAPI 3.1, version `0.1.0-SNAPSHOT`) has 126 operations, 88 of them with `x-oea-status: planned` (change `s2b-ci-and-contract`). Every operation declares `x-oea-authorization` (`access` `public`, `authenticated` or `permission`, with `permissions` or `anyOfPermissions`). Problem types appear as `urn:oea:problem:<name>` in response descriptions, not as structured fields.
- **GitHub Pages**: enabled with build type "GitHub Actions" (`https://open-enterprise-architecture.github.io/OEA/`). `.github/workflows/screens.yml` uploads Storybook as Pages artifact and deploys it, gated by the repository variable `STORYBOOK_PAGES`. A repository has one Pages site and one deployment at a time.
- **Tooling**: the frontend uses Vite 8, Vue 3.5 and Node.js 24. VitePress 1.6 is the stable release (Vite 5); VitePress 2.0 (Vite 8) is in alpha. Markdown lint covers `**/*.md` except agent directories and `node_modules`. No Python test framework is used for the scripts; S2b verified scripts with temporarily broken inputs.
- **Required checks**: the ruleset of `main` requires the job names of `ci.yml`.

## Goals / Non-Goals

**Goals:**

- A manual whose structure, language pairs and coverage are checked on every pull request, so that S3 and later changes can extend it under ADR-045 rule 8
- An API reference that cannot drift from the contract and does not show planned operations
- One Pages site with the manual at the root and Storybook under `/storybook/`, published from `main`
- One operator documentation instead of two: the manual replaces `docs/operations.md`

**Non-Goals:**

- Screenshots, walkthrough tests and versions per release (change `s2c-manual-release`)
- Reusing product Vue components in the manual (possible later; not needed for text and the reference)
- A full schema browser for request and response bodies (the reference names the schema; rendering schemas can follow when external integrators need it)

## Architecture Constraints

- ADR-045 rules 1–4 and 7: location `manual/`, English root and German `/de/`, same chapters, frontmatter coverage, generated API reference without planned operations, one Pages site with Storybook under `/storybook/`; rule 8 starts to apply to the changes after this one
- ADR-031: Storybook stays reachable for prototype reviews, now under `/storybook/`; the screen images stay in `docs/screens/` and are still rendered and committed by the screens workflow
- ADR-013: the contract is the only source of the reference; nothing is maintained by hand
- ADR-043: point 1 limits the license policy to components distributed with OEA (backend jar, frontend bundle); the manual and its tooling are not part of a release artifact and are not listed in `THIRD-PARTY-LICENSES.md`; VitePress and its dependencies are MIT/ISC licensed anyway
- REQ-192: the Pages build job holds `contents: read`; only the deploy job holds `pages: write` and `id-token: write` and runs no third-party code besides the pinned deploy action

## Decisions

### D1 – VitePress 1.6 as separate package `manual/`

`manual/` is its own npm package (`package.json`, `package-lock.json`, `private: true`) with VitePress 1.6, Vue 3.5 and `yaml` (generator), Node.js 24, and the scripts `dev`, `build`, `preview` and `test`. `npm run build` runs the API reference generator (D4) and then `vitepress build`. The site base comes from the environment variable `MANUAL_BASE` (default `/`), so the same build serves local preview, the Pages site and the version directories of `s2c-manual-release`. VitePress fails the build on dead internal links, which also checks the links between manual pages.

*Alternatives*: VitePress 2.0 alpha (Vite 8 like the frontend) – rejected while in alpha; the upgrade is a Dependabot pull request once 2.0 is stable. An npm workspace with `frontend/` – rejected, the two packages need different Vite majors and the frontend lockfile would change with every manual dependency.

### D2 – Locales, chapters and page layout

`manual/.vitepress/config.mts` defines the locales `root` (English, `lang: en`) and `de` (German, `lang: de`, `link: /de/`), the local search (`search.provider: 'local'` with German labels) and one sidebar per locale, built from a shared chapter list with a title per language. Each chapter is a directory with an `index.md`; the German tree under `manual/de/` mirrors the English tree file by file.

| Chapter | Pages (English path; German under `de/`) | Covers |
|---|---|---|
| Introduction | `index.md` (home), `introduction/index.md` (what OEA is, editions, concepts: metamodel, catalogs, entities) | – |
| Setup and first steps | `setup/index.md`, `setup/installation.md` (operator Compose file, secrets, provisioning, migration, start), `setup/first-administrator.md` (setup phase, setup token, second factor) | US-013 |
| Administration | `administration/index.md`, `administration/identity-providers.md` (OIDC provider, persons, bindings), `administration/roles-and-permissions.md` (permission catalog, built-in roles, group mappings), `administration/metamodel-packages.md` (starter configuration, package import), `administration/operations.md` (database roles, key rotation, audit trail verification, request limits, PostgreSQL upgrade) | US-147, US-143, US-033 |
| Usage | `usage/index.md`, `usage/login.md` (login through the identity provider and locally, second factor, profile), `usage/catalogs.md` (create a catalog, catalog table, paging and sorting), `usage/entities-batch.md` (writing entities in batch through the API) | US-001, US-046, SCR-028, US-049, SCR-020, US-141 |
| API reference | `api/index.md` (base URL, authentication, CSRF, problem responses, permissions), `api/reference/<tag>.md` generated (D4) | – |

The frontmatter of a page declares its coverage:

```yaml
---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-049]
---
```

Text follows ADR-045 rule 2: English is written first, German in the same pull request, "you" and the informal "du".

### D3 – Coverage check `scripts/check_manual.py`

A Python script with the standard library only, run in the job "Requirements completeness" next to `check_screens.py`. It reads:

- the screens with status `implemented` from the tables of `docs/screens/SCREENS.md`
- the user stories whose `**Status**:` is `in-progress`, `review` or `realized` from `requirements/user-stories/US-*.md`
- the frontmatter `covers` of every Markdown page under `manual/` except `.vitepress/`, `node_modules/` and the generated `api/reference/`

It fails, listing every finding, if an English page has no German page at the same relative path or the other way round; if the `covers` of a German page differ from its English page; if an implemented screen or a story in scope is not covered in English or in German; if `covers` names an unknown screen or story; or if a chapter `index.md` of D2 is missing in a locale. The output names the page, the screen or story and the language. Coverage of a story whose status is out of scope is allowed (a page may describe more than it must).

*Alternative*: the check as a VitePress build plugin – rejected, the inputs are repository documents like those of `check_screens.py`, and the check belongs to the requirements gates, not to the site build.

### D4 – API reference generator

`manual/scripts/generate-api-reference.mjs` reads `../api/openapi.yaml` and writes one page per tag that has at least one implemented operation to `manual/api/reference/<tag-slug>.md` and `manual/de/api/reference/<tag-slug>.md`, plus the sidebar entries as JSON for `config.mts`. Generated files are listed in `.gitignore` and never committed. For every operation without `x-oea-status` the page shows method and path, summary and description, the required access (`public`, `authenticated`, the permissions of `permissions` or `anyOfPermissions`), the parameters (name, location, required, type, description), the request body (media types and schema name) and the responses (status, description including referenced shared responses, and the problem types matched by `urn:oea:problem:[a-z0-9-]+`). German pages use German labels and headings; the operation texts stay in the English of the contract.

After writing, the generator compares the operation IDs on the pages with the implemented operation IDs of the contract and exits non-zero on any difference. `manual/scripts/generate-api-reference.test.mjs` (`node --test`) runs the generator against a fixture contract with an implemented, a planned and a public operation and checks the listed operations, access, parameters and problem types.

*Alternatives*: an OpenAPI renderer plugin for VitePress (interactive, schema browser) – needs a filtered contract as input, cannot show `x-oea-authorization` without customization and adds client-side code to every page; can be reconsidered for the schema browser. Committing the generated pages – rejected, they would drift from the contract between builds.

### D5 – "Unreleased" notice

A small theme extension (`manual/.vitepress/theme/`) renders a notice above every page if the build-time variable `MANUAL_VERSION` is not set: English "This manual describes the unreleased development state of OEA (`main`).", German "Dieses Handbuch beschreibt den unveröffentlichten Entwicklungsstand von OEA (`main`).". `s2c-manual-release` sets `MANUAL_VERSION` on release builds and moves the build of `main` to `/next/`.

### D6 – Pages workflow `.github/workflows/pages.yml`

Runs on push to `main` (paths `manual/**`, `frontend/**`, `api/openapi.yaml`, the workflow itself) and on `workflow_dispatch`; `concurrency: pages` without cancelling a running deployment.

1. Job `build` (`contents: read`, `persist-credentials: false`): `actions/configure-pages` provides the base path (`/OEA/`); `npm ci --ignore-scripts` and `npm run build` in `manual/` with `MANUAL_BASE=<base path>/`; `npm ci --ignore-scripts` and `npm run build-storybook` in `frontend/`; copies `frontend/storybook-static/` to `manual/.vitepress/dist/storybook/`; uploads the result with `actions/upload-pages-artifact`.
2. Job `deploy` (`pages: write`, `id-token: write`, environment `github-pages`): `actions/deploy-pages`.

Storybook uses relative asset paths and works under `/storybook/` without configuration. The jobs `pages` and the Pages upload step leave `screens.yml`, and the repository variable `STORYBOOK_PAGES` is no longer read; the screens workflow keeps rendering and committing the screen images. All actions are pinned by SHA like the existing workflows.

*Alternative*: a publication branch `gh-pages` – needed for keeping older versions and introduced by `s2c-manual-release`; for one site built from `main` the artifact deployment is simpler and needs no write token.

### D7 – CI job "User manual"

A new job in `ci.yml`: Node.js 24, `npm ci --ignore-scripts`, `npm test` and `npm run build` in `manual/`. It runs on every pull request, so a broken page, a dead link or a reference mismatch blocks the merge. The coverage check stays in "Requirements completeness" (D3), because it needs only Python. The repository owner adds "User manual" to the required checks of `main`. Dependabot gets an npm entry for `/manual` (weekly, group `vitepress` for `vitepress` and `vue`); the license workflow of S2b runs on those pull requests too and finds `THIRD-PARTY-LICENSES.md` unchanged.

### D8 – `docs/operations.md` moves into the manual

The sections of `docs/operations.md` move to the manual pages of D2 (setup: requirements, provisioning, migration, start, key-encryption key; first administrator: setup phase; administration: database roles, key rotation, audit trail verification, PostgreSQL upgrade, request limits, identity providers, starter configuration; usage: batch writes). The `curl` sequences stay as examples. `docs/operations.md` is reduced to a pointer that maps each former section to its manual page, because `StartupGuardFailureAnalyzer` and `compose.yaml` link to it; `README.md` and `docs/` link to the manual instead. The pointer is removed when the startup messages link to the published manual of the release (`s2c-manual-release`).

### D9 – Realigned requirement and ADR note

REQ-196: the target value "User stories `in-progress` or `realized` covered" and AC2 name `in-progress`, `review` or `realized`. ADR-045 gets a note "Owner decision of 2026-10-24: the coverage check of rule 3 includes user stories with status `review`; until the first release, the site root shows the manual of `main` with an unreleased notice." Both are clarifications within the decision of ADR-045, not a change of it.

## Data Model

No change.

## API

No change to `api/openapi.yaml`; the contract is only read.

## Risks / Trade-offs

- [VitePress 1.6 brings Vite 5 next to the Vite 8 of the frontend] → separate package and lockfile; the manual is not distributed; upgrade to 2.0 when stable
- [German API reference pages contain the English texts of the contract] → labels and headings are German; translating the contract is out of scope; the pages exist in both languages as rule 1 requires
- [The coverage check reads the story status from Markdown] → same pattern as the US completeness check of the job; a story with an unparsable status is reported, not skipped
- [Moving `docs/operations.md` touches many links] → pointer file keeps old links working; link validation and the VitePress dead-link check cover the rest
- [Storybook under `/storybook/` changes the review URL] → `README.md` (section "Development") and `docs/development.md` link to it, the manual navigation does not (ADR-045 rule 7); ADR-031 states the new URL
- [A new required check needs a ruleset change] → the owner adds "User manual" after the first green run; until then the job runs but does not block

## Open Questions

None.
