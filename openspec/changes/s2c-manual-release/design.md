# Design

## Context

- **Manual site** (change `s2c-manual-site`): VitePress `2.0.0-alpha.20` in `manual/`, English root and German `/de/`, base path from `MANUAL_BASE`, "unreleased" notice whenever `MANUAL_VERSION` is not set (`manual/.vitepress/theme/`). The pages contain no images. `npm run build` generates the API reference and builds the site; the CI job "User manual" runs it on every pull request and is a required check.
- **Publication**: `.github/workflows/pages.yml` builds the manual and Storybook on every push to `main` and deploys them as one Pages artifact (root and `/storybook/`); build type "GitHub Actions". The environment `github-pages` allows deployments only from the branch `main` (custom branch policy). No branch `gh-pages` and no tag exist; `backend/pom.xml` has version `0.0.1-SNAPSHOT`.
- **Development stack** (ADR-036): `compose.dev.yaml` with profile `dev` starts PostgreSQL, the migration step, Keycloak with the realm `oea-dev` (users per role, passwords `<user>-dev-only`), the backend in Spring profile `dev` and the Vite development server on `localhost:5173`, which proxies `/api` to the backend. The backend image is built from `backend/Dockerfile` (Maven inside the build stage). `scripts/dev_seed.py` creates, as the local system admin account, the identity provider for the realm, persons with identity bindings and the IdP group mappings; during the setup phase it enrolls a TOTP second factor and prints its secret.
- **Fresh instance**: no system admin account (setup token in `/run/oea/setup-token` inside the backend container), no entity types (the start page offers *Import starter configuration* to `metamodel:write`), no entities, no catalogs.
- **Frontend routes**: `/`, `/setup`, `/login`, `/me`, `/me/second-factor`, `/catalogs`, `/catalogs/:id`. The user interface is English only (ADR-025 not implemented). Playwright is already a development dependency of `frontend/` (screen images, story tests).
- **Links to `docs/operations.md`**: `StartupGuardFailureAnalyzer` (startup error message) and two comments in `compose.yaml`; the page itself is only a pointer to the manual.

## Goals / Non-Goals

**Goals:**

- Screenshots that always match the published version, produced by one test run per publication
- Released manuals that stay reachable and unchanged after later releases, with a selector that also knows newer releases
- A publication path in which only one small job can write to the repository and only one job can deploy

**Non-Goals:**

- Visual regression testing: the walkthrough compares nothing with earlier screenshots
- Walkthrough tests as functional end-to-end tests of the application; they assert only what they need to reach each screen
- Deleting or rebuilding published versions (a broken release is fixed by a patch release)

## Architecture Constraints

- ADR-045 rule 5: screenshots from Playwright walkthrough tests against the real stack with Keycloak and seed data, stable names, English fallback while the user interface is English only
- ADR-045 rule 6: release workflow on version tags, versions directory per release, latest at the root, version selector, published versions kept and not rebuilt; the owner decision of 2026-10-26 sets the directory to the minor version (`/vX.Y/`) – within the wording of rule 6 (`/vX.Y/`), noted in ADR-045
- ADR-045 rule 7 and ADR-031: one Pages site, Storybook of `main` under `/storybook/`
- ADR-036: the development stack and its credentials are development-only values; the walkthrough uses them only inside the CI runner and publishes no credential
- REQ-192: jobs that run third-party code hold `contents: read`; only the publication job holds `contents: write`, only the deploy job holds `pages: write` and `id-token: write`
- ADR-043: the manual and its walkthrough tooling are not distributed with OEA and stay outside `THIRD-PARTY-LICENSES.md`

## Decisions

### D1 – Walkthrough tests with the Playwright test runner in `manual/walkthrough/`

The walkthrough is part of the manual package: `@playwright/test` (exact version, same Playwright minor as `frontend/`) as development dependency of `manual/`, configuration `manual/playwright.config.ts`, tests in `manual/walkthrough/*.spec.ts`, script `npm run walkthrough`. The tests run serially in one worker against `OEA_WALKTHROUGH_URL` (default `http://localhost:5173`), with a fixed viewport of 1280 × 800, light color scheme, reduced motion and the locale of the rendered language.

Flows, in this order, each building on the previous one:

1. **Setup**: start page with *Setup required*, setup form filled (token read by the job from the container and passed as environment variable), confirmation – screenshots `setup-required`, `setup-form`, `setup-done`
2. **System admin login and second factor**: login form, profile, enrollment of the authenticator app (the QR code and the secret are masked), confirmation – screenshots `login`, `second-factor-enroll`
3. **Seed**: `scripts/dev_seed.py` with the TOTP secret from flow 2 creates the provider, persons and group mappings (no screenshot)
4. **Login through Keycloak**: as `dev-metamodel-admin`, start page with *Import starter configuration*, import – screenshot `starter-import`
5. **Catalogs**: as `dev-architect`, a small fixed set of entities written through `POST /api/v1/entities/batch` with the session of the browser context (as the manual describes), catalog list, dialog *New catalog*, catalog table – screenshots `catalogs`, `catalog-create`, `catalog-table`

A helper `shoot(page, name)` waits for network idle and fonts, then writes `screenshots/<lang>/<name>.png` and records the name in `screenshots/manifest.json` (`{ "languages": [...], "names": [...] }`). Masked regions, timestamps and generated IDs are hidden through Playwright's `mask` option so that consecutive runs produce stable images.

*Alternatives*: the frontend test setup (`frontend/`, Vitest browser mode) – rejected, it renders components, not the running stack, and its dependencies are part of the distributed frontend's lockfile. A separate npm package `walkthrough/` – rejected, the screenshots belong to the manual and its build consumes them.

### D2 – Development stack as walkthrough environment

The workflow starts `compose.dev.yaml` with profile `dev` (backend image built in the job), waits for `GET /api/v1/health` and the Vite server, reads the setup token with `docker compose exec backend cat /run/oea/setup-token` and runs the walkthrough. Data volumes are fresh in every run. Credentials are the public development values of ADR-036; nothing from repository secrets is used.

*Alternative*: operator `compose.yaml` with a built frontend and Keycloak – rejected by the owner (2026-10-26): the operator setup has no web interface yet; switching the walkthrough to it follows once it has one.

### D3 – `Screenshot` component with language fallback

Pages reference screenshots only by name: `<Screenshot name="catalog-table" alt="…" />` (globally registered theme component). At build time a small script (`manual/scripts/screenshots.mjs`) copies `walkthrough` output from `manual/screenshots/` into `manual/public/screenshots/` and writes `manual/.vitepress/screenshots.json`. The component chooses `screenshots/<page language>/<name>.png` if that language was rendered, otherwise `screenshots/en/<name>.png` (REQ-196 AC5). Generated screenshot directories are ignored by Git.

The same script runs in two modes:

- `MANUAL_SCREENSHOTS=required` (publication builds, D5): collects every `name` used in `manual/**/*.md` and fails, naming page and screenshot, if one is missing in the manifest (REQ-196 AC4)
- default (pull requests, local preview): missing screenshots render as a labelled placeholder box; the build passes, so the fast "User manual" job and contributors without a running stack keep working

*Alternatives*: plain Markdown images with language-specific paths – rejected, the German pages would have to change when German screenshots appear (AC5). Committed screenshots – rejected by ADR-045 rule 5 (screenshots of the release, not of an earlier state).

### D4 – Branch `gh-pages` as publication store, versions file and version selector

The branch `gh-pages` (orphan, no workflow files, never merged) holds the complete site:

| Path | Content | Written by |
|---|---|---|
| `/` | manual of the latest release (before the first release: manual of `main`) | tag, or `main` while no release exists |
| `/vX.Y/` | manual of the latest patch release of minor version X.Y | tag |
| `/next/` | manual of `main` | `main` |
| `/storybook/` | Storybook of `main` | `main` |
| `/versions.json` | `{ "latest": "v0.2", "releases": [{ "path": "v0.2", "tag": "v0.2.1" }, …], "next": true }` | every publication |

The version selector is a theme component in the navigation bar. It fetches `versions.json` from the site root at runtime (base path without the version directory) and offers every release, newest first, plus "Development (main)"; on a page it links to the same page in the target version. If the file cannot be loaded (local preview), it shows only the own version. Because the list is read at runtime, old versions show new releases without a rebuild (spec "Older manual lists newer releases").

*Alternatives*: release assets as store (rejected by the owner, 2026-10-26: more workflow logic, depends on GitHub releases); a version list compiled into each build (old versions would not know newer releases).

### D5 – One workflow, three jobs, two triggers

`.github/workflows/pages.yml` gets the trigger `push: tags: ['v[0-9]+.[0-9]+.[0-9]+']` next to `push: branches: [main]` (paths filter kept for `main`; tags always run) and `workflow_dispatch`.

1. **build** (`contents: read`): starts the stack (D2), runs the walkthrough (D1) – on failure uploads the Playwright report as artifact –, then builds the manual with `MANUAL_SCREENSHOTS=required`:
   - on a tag: `MANUAL_VERSION=vX.Y.Z` twice, with `MANUAL_BASE=<site>/vX.Y/` and `<site>/` (the root copy needs its own base so that existing deep links keep working)
   - on `main`: without `MANUAL_VERSION`, with `MANUAL_BASE=<site>/next/` and, while `versions.json` of `gh-pages` names no release, also with `<site>/`; plus Storybook

   The job uploads the built directories as workflow artifact `site-parts`.
2. **publish** (`contents: write`, no third-party actions besides checkout): checks out `gh-pages` (created as orphan on the first run), replaces exactly the paths of the table in D4 that this trigger owns, updates `versions.json`, commits as `github-actions[bot]` and pushes. For a tag it refuses a version older than the latest published minor version only for the root (an older patch, e.g. v0.1.3 after v0.2.0, updates `/v0.1/` but not the root).
3. **deploy** (`pages: write`, `id-token: write`): checks out `gh-pages` at the commit of step 2, uploads it as Pages artifact and deploys it.

The concurrency group `pages` (no cancellation) serializes runs, so two publications cannot overwrite each other's branch state. A push by `GITHUB_TOKEN` to `gh-pages` triggers no workflow.

Before the first run under this workflow, the root of `gh-pages` is filled from `main` as today; the transition to `/next/` happens automatically with the first tag.

*Alternatives*: a separate release workflow – rejected, two workflows writing one branch would need a shared lock beyond the concurrency group of one workflow; Pages build type "Deploy from a branch" – rejected, it would need no deploy job but runs Jekyll unless disabled and changes the owner settings again.

### D6 – Release root and unreleased notice

`MANUAL_VERSION` drives the theme: set, the notice disappears and the navigation shows the version; not set, the notice of `s2c-manual-site` stays. On `/next/` the notice additionally links to the latest release once `versions.json` names one.

### D7 – Links from the product to the manual

`StartupGuardFailureAnalyzer` and the comments in `compose.yaml` link to `https://open-enterprise-architecture.github.io/OEA/administration/operations` (and the installation page for the KEK hint), which shows the latest release. `docs/operations.md` is deleted; `scripts/validate_links.py` confirms that no repository link points to it.

### D8 – Owner settings

- The environment `github-pages` additionally allows tags matching `v*` (deployment branch and tag policy), otherwise the deploy job of a tag is rejected.
- A tag ruleset for `v*` restricts creation, update and deletion of version tags to the repository owner, so that only the owner can publish a release manual.
- `gh-pages` gets a ruleset that blocks deletion and force pushes; only the workflow pushes to it.

## Data Model

No change.

## API

No change to `api/openapi.yaml`. The walkthrough uses existing operations only (setup, local login, second factor, entities batch, metamodel starter import through the user interface).

## Risks / Trade-offs

- [The walkthrough adds the full stack to every push to `main` (backend image build, Keycloak start): about 8–12 minutes] → only on `main` and tags, not on pull requests (owner decision); Maven and npm caches; a failed walkthrough blocks only the publication, not the merge
- [A user interface change breaks the walkthrough after the merge] → the Playwright report is uploaded; ADR-045 rule 8 already requires the change to update its walkthrough; selectors use roles and labels, not CSS classes
- [Screenshots differ between runs (time, IDs, QR code)] → masked regions and fixed seed data (D1)
- [The development stack differs from an operator installation (Spring profile `dev`, Vite server)] → the user interface is the same build input; the manual states that screenshots show the development stack's sample data; switching to the operator stack follows with its web interface
- [A wrong tag publishes a manual that cannot be removed by the workflow] → tag ruleset (D8); a later patch release replaces `/vX.Y/`; manual cleanup of `gh-pages` stays possible for the owner
- [`gh-pages` grows with every publication (screenshots are binary)] → one directory per minor version and history of a few megabytes per release; acceptable, revisited if the branch exceeds 500 MB
- [The first tag must happen after this change is merged] → the change only prepares; the owner sets the tag

## Migration Plan

1. Merge; the first push run creates `gh-pages` with the root and `/storybook/` from `main` (now with screenshots) and deploys it – same URLs as before.
2. Owner applies D8 (environment policy for tags, tag ruleset, `gh-pages` ruleset).
3. First tag (e.g. `v0.1.0`): root and `/v0.1/` show the release; the next push to `main` publishes to `/next/`.

Rollback: revert the workflow change; the old workflow deploys `main` as an artifact again, `gh-pages` stays unused.
