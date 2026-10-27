# Proposal

## Why

The manual published by `s2c-manual-site` describes `main` only, has no screenshots and is replaced on every merge. ADR-045 rules 5 and 6 and REQ-196 AC4 to AC6 require screenshots that come from the released software and one manual per release that stays readable after later releases. Both have to exist before the first version tag, so that the first release ships with its own manual and S3 changes can add screenshots as part of their definition of done (ADR-045 rule 8).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (technical package; the screenshots show UC-01, UC-02 and UC-23 as far as they are implemented) |
| User Stories | – (no story is implemented; the walkthrough covers the functions of [US-001](../../../requirements/user-stories/US-001-oidc-login.md), [US-013](../../../requirements/user-stories/US-013-local-bootstrapping.md), [US-046](../../../requirements/user-stories/US-046-create-catalog.md) and [US-049](../../../requirements/user-stories/US-049-show-catalog-data.md)) |
| Requirements | [REQ-196: Multilingual user manual per release](../../../requirements/req/REQ-196-user-manual.md) (AC4, AC5, AC6), [REQ-192: Least privilege for CI/CD workflows](../../../requirements/req/REQ-192-ci-least-privilege.md) (publication jobs) |
| ADRs | [ADR-045](../../../adrs/ADR-045-user-manual.md) (rules 5, 6, 7), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) (development stack used for the walkthrough), [ADR-025](../../../adrs/ADR-025-i18n-strategy.md) (screenshot languages), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (Storybook under `/storybook/`) |
| Sprint / Milestone | S2c / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name follows the package (`s2c-`), like `s2c-manual-site`, because it implements ADR-045 and REQ-196, not a user story. It is the second of the two S2c changes (owner decision of 2026-10-24).

## What Changes

- **Walkthrough tests** (ADR-045 rule 5, REQ-196 AC4): Playwright tests in `manual/walkthrough/` drive the running application through the documented flows – setup and first administrator, login, second factor, catalogs (create, open the table) – and take every screenshot the manual references, under a stable name. They run against the development stack `compose.dev.yaml` with Keycloak and the seed of `scripts/dev_seed.py` (owner decision of 2026-10-26).
- **Screenshots in the manual**: pages reference screenshots by name through a `Screenshot` component. A build that publishes the manual fails if a referenced screenshot was not produced. Screenshots are generated in CI and never committed.
- **Screenshot language** (REQ-196 AC5): the walkthrough renders the screenshots in every language the user interface offers; while it offers only English, the German manual shows the English screenshots and switches to German ones without changes to the pages.
- **Versions per release** (ADR-045 rule 6, REQ-196 AC6): a version tag `vX.Y.Z` publishes the manual of that tag under `/vX.Y/` – a patch release replaces the manual of its minor version (owner decision of 2026-10-26) – and at the site root. A version selector lists all published versions and the development state; it reads the list at runtime, so older versions show newer releases without being rebuilt.
- **Development state under `/next/`**: once a release exists, every push to `main` publishes the manual of `main` (with screenshots and the unreleased notice) under `/next/` instead of the root. Until the first release, the root keeps showing `main` as today.
- **Publication branch `gh-pages`** (owner decision of 2026-10-26): published versions are kept in the branch `gh-pages`; one job with `contents: write` updates it, a separate job with `pages: write` deploys it (REQ-192). Storybook of `main` stays under `/storybook/`.
- **Walkthrough on every push to `main` and on every tag** (owner decision of 2026-10-26); pull requests keep the fast "User manual" job, whose build tolerates missing screenshots.
- **Links to the manual**: the startup error messages and `compose.yaml` link to the published manual instead of `docs/operations.md`; the pointer page `docs/operations.md` is removed (announced by `s2c-manual-site`).
- **Owner settings**: the environment `github-pages` allows deployments from tags `v*`; a tag ruleset restricts who may create `v*` tags.

## Out of Scope

- Release artifacts beyond the manual (backend image, frontend bundle, GitHub release notes, version numbers in `backend/pom.xml` and `frontend/package.json`); the first version tag itself is created by the repository owner after this change
- A web interface in the operator setup (`compose.yaml` ships only the backend); the walkthrough uses the development stack
- Translating the user interface (ADR-025); the walkthrough is prepared for more languages but renders English only
- Screenshots of functions that are only reachable through the API (identity providers, roles, metamodel packages, batch writes)
- Walkthrough tests on pull requests

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `platform/user-manual`: adds screenshots from walkthrough tests (with language fallback) and versions per release; changes the publication requirement so that the site root shows the latest release and `main` moves to `/next/` once a release exists

## Impact

- `manual/`: walkthrough tests (`walkthrough/`, Playwright test runner as development dependency), `Screenshot` component and version selector in the theme, screenshots on the pages of setup, login and catalogs in both languages, `README.md`
- `.github/workflows/pages.yml`: trigger on tags `v*.*.*`, walkthrough job with the development stack, publication job writing `gh-pages`, deploy job publishing the branch content; `.github/workflows/ci.yml` unchanged except for comments
- `backend/src/main/java/io/oea/config/startup/StartupGuardFailureAnalyzer.java` and `compose.yaml`: links to the published manual; `docs/operations.md` removed, links in `README.md` and `docs/` checked
- REQ-196 (implementation section, verification status), ADR-045 (note on `/next/` and minor-version directories), `docs/sprint-plan.md`, `docs/development.md`
- Repository settings (owner): branch `gh-pages`, deployment policy of the environment `github-pages`, tag ruleset
- No change to `api/openapi.yaml`, Flyway migrations, `docs/data-model.puml`, `business-objects/` or `docs/screens/SCREENS.md`; `frontend/` only if the walkthrough needs stable selectors
