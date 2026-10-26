# Proposal

## Why

OEA has no documentation for the people who install, administer and use it: operator knowledge sits in `docs/operations.md` and the archived changes, and the API is described only by a contract in which 88 of 126 operations are planned. ADR-045 and REQ-196 require a user manual in English and German that grows with every change; from S3 onward each user-visible change has to update it (ADR-045 rule 8), so the manual, its structure and its coverage check must exist before S3 starts.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (technical package; the manual describes UC-01, UC-02, UC-03, UC-04, UC-06 and UC-23 as far as they are implemented) |
| User Stories | – (no story is implemented; the manual covers [US-001](../../../requirements/user-stories/US-001-oidc-login.md), [US-013](../../../requirements/user-stories/US-013-local-bootstrapping.md), [US-033](../../../requirements/user-stories/US-033-import-metamodel.md), [US-046](../../../requirements/user-stories/US-046-create-catalog.md), [US-049](../../../requirements/user-stories/US-049-show-catalog-data.md), [US-141](../../../requirements/user-stories/US-141-entity-batch-import.md), [US-143](../../../requirements/user-stories/US-143-seed-permission-catalog-built-in-roles.md), [US-147](../../../requirements/user-stories/US-147-map-idp-group-to-role.md)) |
| Requirements | [REQ-196: Multilingual user manual per release](../../../requirements/req/REQ-196-user-manual.md) (AC1, AC2, AC3), [REQ-192: Least privilege for CI/CD workflows](../../../requirements/req/REQ-192-ci-least-privilege.md) (Pages workflow) |
| ADRs | [ADR-045](../../../adrs/ADR-045-user-manual.md) (user manual, rules 1–4 and 7), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (Storybook on Pages), [ADR-013](../../../adrs/ADR-013-api-style.md) (contract as source of the API reference), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md) (license policy) |
| Sprint / Milestone | S2c / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name follows the package (`s2c-`) instead of a user story, like the S2b changes, because it implements ADR-045 and REQ-196, not a story. Package S2c is split into two changes (owner decision of 2026-10-24): this change builds the manual site; `s2c-manual-release` follows with the walkthrough tests, the screenshots and the release workflow with versions.

## What Changes

- **VitePress project `manual/`** (ADR-045 rule 1, 2): English as root locale, German under `/de/`, the chapters Introduction, Setup and first steps, Administration, Usage and API reference with the same pages in both languages; local search; the reader is addressed directly ("you", informal "du").
- **Content for the walking skeleton**: installation with the operator Compose file, the setup phase and the first administrator, identity providers, persons and group mappings, roles and permissions, starter configuration and metamodel packages, operation tasks (database roles, migration, key rotation, audit trail verification, request limits), login, catalogs and the catalog table, and writing entities in batch through the API. The operator content of `docs/operations.md` moves into the manual; `docs/operations.md` keeps a short pointer so that existing links keep working.
- **Coverage check** (ADR-045 rule 3, REQ-196 AC2): every manual page declares the screens and user stories it covers in its frontmatter. `scripts/check_manual.py` fails if a page exists in only one language, if the German page covers something different from the English page, or if an implemented screen of `docs/screens/SCREENS.md` or a user story with status `in-progress`, `review` or `realized` is not covered in both languages. The status `review` is included by owner decision of 2026-10-24, because the stories of login (US-001) and bootstrapping (US-013) are implemented and in review.
- **API reference from the contract** (ADR-045 rule 4, REQ-196 AC3): generated from `api/openapi.yaml` at build time with every implemented operation, its parameters, responses, problem types and required permission (`x-oea-authorization`); planned operations (`x-oea-status: planned`) are left out, and the build fails if the reference does not contain exactly the implemented operations.
- **One Pages site** (ADR-045 rule 7): a new workflow publishes the manual of `main` at the site root and Storybook under `/storybook/` on every push to `main`; until the first release the manual carries an "unreleased" notice (owner decision of 2026-10-24). The Storybook publication leaves the screens workflow, which keeps rendering and committing the screen images.
- **CI**: the job "Requirements completeness" runs the coverage check; a new job "User manual" builds the site (including the API reference check) on every pull request. The repository owner adds "User manual" to the required checks of `main`.
- **Realigned sections**: REQ-196 target values and AC2 (status `review` added); ADR-045 rule 3 gets a dated note of the owner decision. No planned contract operation is implemented.

## Out of Scope

- Playwright walkthrough tests, screenshots in the manual, localized screenshots (ADR-045 rule 5, REQ-196 AC4, AC5) – change `s2c-manual-release`
- Release workflow with one manual per version tag and the version selector (ADR-045 rule 6, REQ-196 AC6) – change `s2c-manual-release`; until then the root shows the manual of `main`
- Translating the user interface (ADR-025)
- Correcting the status of SCR-001, SCR-002 and SCR-003 in `docs/screens/SCREENS.md` (`legacy-mockup`, although login, setup and second factor are part of the running application); the manual covers these functions through US-001 and US-013
- Status changes of user stories (none is implemented by this change)

## Capabilities

### New Capabilities

- `platform/user-manual`: the published user manual – its structure in two languages, the coverage of implemented functions, the API reference generated from the contract and its publication next to Storybook

### Modified Capabilities

- none

## Impact

- New `manual/` (VitePress, own `package.json` and lockfile; build and site tooling, not part of a release artifact, so outside the scope of ADR-043 point 1) with `manual/scripts/` for the API reference generator
- New `scripts/check_manual.py`; `.github/workflows/ci.yml` (coverage step in "Requirements completeness", new job "User manual"); new `.github/workflows/pages.yml`; `.github/workflows/screens.yml` without the Pages jobs; `.github/dependabot.yml` (npm in `/manual`); `.gitignore`
- `docs/operations.md` (pointer to the manual), `README.md`, `docs/README.md`, `docs/development.md`, `docs/workflow-example.md`, `docs/sprint-plan.md`, `AGENTS.md` (repository structure), ADR-031 (publication rule), ADR-045 (note), REQ-196 (target values, AC2)
- No change to `backend/`, `frontend/`, `api/openapi.yaml`, Flyway migrations, `docs/data-model.puml`, `business-objects/` or `docs/screens/SCREENS.md`
