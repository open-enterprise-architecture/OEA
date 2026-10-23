# Tasks

## 1. Planned contract operations (A1)

- [ ] 1.1 Add the shared response `NotImplemented` and `x-oea-status: planned` with the 501 response on every unimplemented operation of `api/openapi.yaml` (API section of the design); extend `scripts/check_openapi_authorization.py` so that `x-oea-status` only allows `planned` and the 501 response appears exactly on planned operations, and verify with a temporarily broken copy of the contract (wrong value, missing 501) that the script fails; verify Redocly lint, the script, `npm run generate:api` and the backend build pass
- [ ] 1.2 Read the marker in `ContractAuthorizationRules` and extend `OperationCoverageCheck` (D1); verify with `OperationCoverageCheckTests` for the spec scenarios "Planned marker matches the implementation", "Implemented operation still marked as planned" and "Unimplemented operation without planned marker"
- [ ] 1.3 Add `NotImplementedInterceptor` after `AuthorizationInterceptor` (D2); verify with integration tests for "Authorized call of a planned operation" (501, `urn:oea:problem:not-implemented`, no state change), "Planned operation without session" (401) and "Planned operation without permission" (403)
- [ ] 1.4 Write the Cucumber feature `platform/api-contract.feature` for the scenarios of "Answer of planned operations" on the shared instance; verify it passes through `CucumberIT`
- [ ] 1.5 Add the A1 rule to the proposal rules in `openspec/config.yaml` (list the REQ/US sections the change realigns; parameter and payload details of requirements are non-binding until then) and describe the marker in `openspec/README.md`; verify `openspec validate --specs --strict` and `scripts/sync_agent_config.py --check` pass

## 2. Storybook tests and spec validation in CI (A4)

- [ ] 2.1 Add `@storybook/addon-vitest` and `@vitest/browser-playwright` as dev dependencies, the Vitest project `storybook` and the script `test:stories` (D3); verify locally that all stories pass, that a story with an accessibility violation (temporary, not committed) fails the run, and that `npm test` still runs only the jsdom tests
- [ ] 2.2 Install Chromium and run `npm run test:stories` in the job "Frontend build"; run `openspec validate --specs --strict` with the version of D4 in the job "Requirements completeness"; verify both jobs pass in the PR and actionlint/zizmor report no new findings for `ci.yml`
- [ ] 2.3 Regenerate `THIRD-PARTY-LICENSES.md` if the dev dependencies change the report (they are not distributed); verify `scripts/generate_third_party_licenses.py --check` passes

## 3. License regeneration on Dependabot PRs (A5)

- [ ] 3.1 Add `.github/workflows/dependabot-licenses.yml` with the jobs `generate` and `commit` of D5 and the trigger `workflow_dispatch` in `ci.yml`; verify with actionlint and zizmor (no findings), that the `generate` job holds only `contents: read`, and that the commit step rejects any path other than `THIRD-PARTY-LICENSES.md`
- [ ] 3.2 Verify the workflow end to end on a test branch `dependabot/npm_and_yarn/test-licenses` with an outdated `THIRD-PARTY-LICENSES.md` (workflow run with the condition on the author temporarily widened to the owner): the file is committed, `ci.yml` is dispatched for the branch and the required checks report on the new head commit; delete the branch and restore the condition afterwards
- [ ] 3.3 Add the workflow as evidence of REQ-192 AC2 and mention the automatic regeneration in AGENTS.md quality gate 7; verify link validation and `scripts/sync_agent_config.py --check` pass

## 4. Local development guide (A9)

- [ ] 4.1 Write `docs/development.md` (D6) and link it from the README section "Development" and from `docs/workflow-example.md`; verify markdown lint and link validation pass

## 5. Wrap-up

- [ ] 5.1 Mark A1, A4, A5 and A9 as implemented in `docs/retrospectives/2026-10-walking-skeleton.md` and S2b as done in `docs/sprint-plan.md`; no user story is referenced, so no story status changes; `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` are unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 5.2 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `./mvnw verify` and the frontend checks and verify all pass
