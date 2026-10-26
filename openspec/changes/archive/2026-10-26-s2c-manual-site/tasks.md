# Tasks

## 1. VitePress project (D1, D2, D5)

- [x] 1.1 Create `manual/` as npm package with VitePress `2.0.0-alpha.20` (exact pin, owner decision of 2026-10-24) and the scripts `dev`, `build`, `preview` and `test` (D1); add the build output, the cache and the generated reference pages to `.gitignore`; verify `npm ci --ignore-scripts && npm run build` passes and `MANUAL_BASE=/OEA/ npm run build` produces asset links under `/OEA/`
- [x] 1.2 Configure the locales `root` (English) and `de`, the local search with German labels and one sidebar per locale from a shared chapter list (D2); create the chapter `index.md` pages of Introduction, Setup and first steps, Administration, Usage and API reference in both languages; verify in `npm run preview` that both locales show the five chapters and that the language switch leads to the same page
- [x] 1.3 Add the theme extension with the unreleased notice in English and German, shown when `MANUAL_VERSION` is not set (D5); verify in the preview that every page of both locales shows the notice and that a build with `MANUAL_VERSION=0.0.0` shows none
- [x] 1.4 Add the npm entry for `/manual` to `.github/dependabot.yml` (group `vitepress`, D7); verify the file parses as YAML and Dependabot lists the new entry after the merge (Insights → Dependency graph → Dependabot)

## 2. Coverage check (D3)

- [x] 2.1 Write `scripts/check_manual.py` (D3) and add it as step "User manual coverage" to the job "Requirements completeness"; verify with temporary, uncommitted changes that it fails and names page, entry and language for each spec scenario of "Every implemented function covered" and "Manual structure in two languages" (English page without German page, implemented screen not covered in German, story in `review` not covered, German page with different `covers`, unknown story ID, missing chapter `index.md`) and that it passes on the unchanged tree once group 4 is complete
- [x] 2.2 Describe the frontmatter `covers` and the check in `manual/README.md` (contributor notes for the manual: structure, languages, tone, coverage, local preview); verify markdown lint passes

## 3. API reference (D4)

- [x] 3.1 Write `manual/scripts/generate-api-reference.mjs` (D4) and run it from `npm run build` and `npm run dev`; verify that the build lists the 38 implemented operations in both locales with access, parameters, responses and problem types, lists no planned operation, and fails when the generator is changed temporarily to skip one operation
- [x] 3.2 Write `manual/scripts/generate-api-reference.test.mjs` with a fixture contract (implemented, planned and public operation) for the scenarios "Implemented operation listed" and "Planned operation left out"; verify `npm test` passes
- [x] 3.3 Write `api/index.md` in both languages (base URL, session and CSRF, problem responses, permissions and how to read the reference); verify the build passes without dead links

## 4. Manual content (D2, D8)

- [x] 4.1 Write the Introduction (`introduction/index.md`, home page) in English and German; verify the build passes
- [x] 4.2 Write Setup and first steps (`setup/installation.md`, `setup/first-administrator.md`, covering US-013) in English and German from the corresponding sections of `docs/operations.md`; verify the build and `python3 scripts/check_manual.py` pass for these pages
- [x] 4.3 Write Administration (`administration/identity-providers.md`, `roles-and-permissions.md`, `metamodel-packages.md`, `operations.md`, covering US-147, US-143, US-033) in English and German; verify the build passes
- [x] 4.4 Write Usage (`usage/login.md`, `catalogs.md`, `entities-batch.md`, covering US-001, US-046, SCR-028, US-049, SCR-020, US-141) in English and German; verify the build passes and `python3 scripts/check_manual.py` passes on the full tree
- [x] 4.5 Reduce `docs/operations.md` to the pointer of D8 and change the links in `README.md`, `docs/README.md`, `docs/walking-skeleton.md`, `docs/audit-hash-chain.md`, `docs/sprint-plan.md` and `REQ-082` to the manual pages; verify `python3 scripts/validate_links.py`, markdown lint and a search for `operations.md` (only the pointer users `compose.yaml` and `StartupGuardFailureAnalyzer` remain) pass

## 5. Publication and CI (D6, D7)

- [x] 5.1 Add the job "User manual" to `.github/workflows/ci.yml` (D7); verify it passes in the pull request and actionlint and zizmor report no findings
- [x] 5.2 Add `.github/workflows/pages.yml` (D6) and remove the Pages upload step and the job `pages` from `.github/workflows/screens.yml`; verify with actionlint and zizmor (no findings) that only the deploy job holds `pages: write` and `id-token: write`, and after the merge that the site root shows the manual with the unreleased notice and `/storybook/` shows Storybook (spec scenarios "Manual and Storybook on one site" and "Unreleased manual marked")
- [x] 5.3 Update ADR-031 (publication rule 4 and the note on `STORYBOOK_PAGES`: Storybook under `/storybook/` through `pages.yml`), `docs/development.md` (manual commands, Storybook URL), the README section "Development" and `docs/workflow-example.md`; verify link validation passes

## 6. Wrap-up

- [x] 6.1 Realign REQ-196 (target value and AC2 with status `review`) and add the owner-decision note to ADR-045 (D9); update `AGENTS.md` (repository structure: `manual/` exists) and mark S2c as partly done in `docs/sprint-plan.md` (`s2c-manual-site` done, `s2c-manual-release` open); no user story is implemented, so no story status changes; `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` are unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [x] 6.2 Run `python3 scripts/validate_links.py`, `python3 scripts/check_manual.py`, `python3 scripts/check_screens.py`, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, markdown lint and `npm test && npm run build` in `manual/` and verify all pass; after the merge, ask the repository owner to add "User manual" to the required checks of `main`
