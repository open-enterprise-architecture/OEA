# Tasks

## 1. Contract

- [x] 1.1 Add `POST /admin/persons/{personId}/status` (`changePersonStatus`) and the schema `PersonStatusChange` to `api/openapi.yaml` as in the API section of the design; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`

## 2. Person lifecycle (US-148)

- [x] 2.1 Add `PersonLifecycle` with the transition table of D2 and the transaction of D3, `Removal.person` in `AdministrationPaths`, `RoleAssignments.revokeAllForOffboarding`, the controller operation and the 409 mapping of `invalid-status-transition`; verify with integration tests for "Person set to inactive and back", "Transition not allowed", "Missing permission", "Sessions revoked on offboarding", "Assignments revoked on offboarding" (local assignment revoked, IdP login refused), "Assignments kept while inactive", "History retained", "Own status", "Service account" and "Status change of the last holder"
- [x] 2.2 Cucumber scenarios in `identity/person-lifecycle.feature` (new) for US-148 AC1, AC2 and AC5 and in `identity/access-administration.feature` for US-146 AC5; verify they pass through `CucumberIT`
- [x] 2.3 Describe the statuses and offboarding (operation, effects on sessions and assignments, rules) in `manual/administration/identity-providers.md` and its German page with US-148 in `covers`; add `person.status-changed` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 3. Wrap-up

- [x] 3.1 Set US-148 to `done` with the realignment notes of the proposal (the implementation hash is recorded when the change is archived); add the US-146 AC5 note; add notes to REQ-164 (AC2) and `business-objects/person.md`; record the S4 status in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 3.2 `docs/data-model.puml` and `docs/screens/SCREENS.md` are unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 3.3 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate us-148-person-lifecycle`, `./mvnw verify` and the frontend type check and tests, and verify all pass
