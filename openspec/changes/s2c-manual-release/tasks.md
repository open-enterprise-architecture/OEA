# Tasks

## 1. Screenshots in the manual (D3)

- [x] 1.1 Write `manual/scripts/screenshots.mjs` (copy `manual/screenshots/` to `manual/public/screenshots/`, write `.vitepress/screenshots.json`, mode `MANUAL_SCREENSHOTS=required` fails naming page and screenshot) and run it before `vitepress build` and `vitepress dev`; add the generated directories to `.gitignore`; write `manual/scripts/screenshots.test.mjs` for the scenarios "Referenced screenshot missing" (required mode fails, default mode passes) and the collection of names from Markdown; verify `npm test` passes
- [x] 1.2 Add the theme component `Screenshot` (language of the page, English fallback, placeholder when missing) and register it globally; verify in the preview with a hand-made fixture manifest of `en` only that `/de/` pages show the English image, with `en` and `de` that they show the German one (scenarios "User interface only in English" and "User interface also in German"), and without manifest that a labelled placeholder appears; remove the fixture afterwards

## 2. Walkthrough tests (D1, D2)

- [x] 2.1 Add `@playwright/test` (exact version, same minor as Playwright in `frontend/`) to `manual/`, `manual/playwright.config.ts` (one worker, 1280 × 800, light scheme, reduced motion, `OEA_WALKTHROUGH_URL`) and the script `npm run walkthrough`; write the helper `shoot(page, name)` with masking and manifest; verify `npm ci --ignore-scripts` and `npx playwright test --list` pass
- [ ] 2.2 Write the flows setup and system admin login with second factor (D1 flows 1 and 2) and reference `setup-required`, `setup-form`, `setup-done`, `login` and `second-factor-enroll` from `setup/first-administrator.md` and `usage/login.md` in both languages; verify against a fresh `scripts/dev.sh` stack that the screenshots appear in `manual/screenshots/en/` and the QR code and secret are masked
- [ ] 2.3 Run `scripts/dev_seed.py` from the walkthrough with the enrolled TOTP secret, write the flows starter import as `dev-metamodel-admin` and catalogs as `dev-architect` with a fixed entity set written through `POST /api/v1/entities/batch` (D1 flows 3 to 5), and reference `starter-import`, `catalogs`, `catalog-create` and `catalog-table` from `administration/metamodel-packages.md` and `usage/catalogs.md` in both languages; verify two consecutive runs against fresh stacks produce visually identical screenshots
- [ ] 2.4 Write `scripts/walkthrough.sh` (start `compose.dev.yaml` with fresh volumes, wait for health and Vite, read the setup token, run `npm run walkthrough`, stop the stack); verify locally that it produces all screenshots and that `MANUAL_SCREENSHOTS=required npm run build` then passes and fails after deleting one screenshot

## 3. Versions and selector (D4, D6)

- [x] 3.1 Add the version selector to the theme (reads `versions.json` from the site root at runtime, links to the same page in the target version, shows only its own version if the file is missing) and the release display of `MANUAL_VERSION` (D6); verify in `npm run preview` with a hand-made `versions.json` of two releases that the selector offers both releases and "Development (main)" and that a build with `MANUAL_VERSION=v0.1.0` shows no notice
- [x] 3.2 Write `manual/scripts/publish-site.mjs` (places the built parts into a `gh-pages` checkout according to the table of D4 and updates `versions.json`; root only for the newest minor version) with `manual/scripts/publish-site.test.mjs` for the scenarios "Latest release at the root", "Patch release replaces its minor version", "Development state after the first release", "Manual and Storybook on one site" and an older patch after a newer minor; verify `npm test` passes

## 4. Publication workflow (D5, D8)

- [ ] 4.1 Rewrite `.github/workflows/pages.yml` with the triggers of D5 and the jobs build (walkthrough via `scripts/walkthrough.sh`, Playwright report on failure, builds per trigger, artifact `site-parts`), publish (`contents: write`, runs `publish-site.mjs`, pushes `gh-pages`) and deploy (`pages: write`, `id-token: write`, publishes the `gh-pages` commit); verify with actionlint and zizmor (no findings) that only publish holds `contents: write` and only deploy holds `pages: write` and `id-token: write`
- [ ] 4.2 Ask the repository owner to apply D8 (environment `github-pages` allows tags `v*`, tag ruleset for `v*`, ruleset for `gh-pages` against deletion and force push); verify through the API that the policies exist
- [ ] 4.3 After the merge: verify that the push run creates `gh-pages`, that the site root and `/next/` show the manual of `main` with screenshots and the notice, and that `/storybook/` shows Storybook (scenario "Manual and Storybook on one site")
- [ ] 4.4 After the first version tag set by the repository owner (e.g. `v0.1.0`): verify that the site root and `/v0.1/` show the release manual without notice, `/next/` keeps the manual of `main`, `versions.json` lists the release and the selector of `/next/` offers it (scenarios "Latest release at the root", "All versions offered", "Development state after the first release"); the other release scenarios are covered by the tests of 3.2

## 5. Links and documentation (D7)

- [ ] 5.1 Change the links in `StartupGuardFailureAnalyzer` and `compose.yaml` to the published manual and delete `docs/operations.md`; update links in `README.md` and `docs/`; verify `./mvnw -q test -Dtest=StartupGuardFailureAnalyzer*` (or the tests that assert the message), `python3 scripts/validate_links.py` and `python3 scripts/check_compose_secrets.py` pass
- [x] 5.2 Describe the walkthrough, screenshots and versions in `manual/README.md` and `docs/development.md` (local run with `scripts/walkthrough.sh`, names, `MANUAL_SCREENSHOTS`); add a note to ADR-045 (minor-version directories, `/next/`, `gh-pages`, owner decisions of 2026-10-26); verify markdown lint passes

## 6. Wrap-up

- [ ] 6.1 No user story changes status (none is implemented); update REQ-196 (implementation section: AC4–AC6 by this change, verification status) and `docs/sprint-plan.md` (S2c done); regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`)
- [ ] 6.2 Run `python3 scripts/validate_links.py`, `python3 scripts/check_manual.py`, `python3 scripts/check_screens.py`, `python3 scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, markdown lint and `npm test && npm run build` in `manual/` and verify all pass
