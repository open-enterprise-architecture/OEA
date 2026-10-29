# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design (four operations implemented with `429`, problem types `sod-conflict`, `last-admin-path`, `conflict`, `409` of the mapping operations aligned); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V9__role_assignments.sql` (D1, data model section); verify with a schema test: the CHECK constraints reject two granters, `valid_to` ≤ `valid_from` and an inconsistent revocation, and `oea_app` cannot delete a row

## 2. Assignments (US-144)

- [x] 2.1 Add `RoleAssignments` in `io.oea.app.access` (create with D5 rules, list with the derived status of D1, revoke with session revocation of D3, audit `role.assigned`/`role.revoked`, `evictAll()`) and the controller operations; verify with integration tests for "Assignment stored and audited", "Invalid validity", "Missing permission", "Own assignment", "Privileged role for a service account", "Conflict acknowledged", a duplicate assignment, revoking an already revoked assignment, and "Role revoked"
- [x] 2.2 Add local grants to `PermissionResolver` (D2); verify with integration tests for "Assignment effective within 60 seconds" (clock advanced), "Pre-provisioning", a privileged local role without second factor not effective, and the current principal listing source `local`
- [x] 2.3 Add the expiry job (D4); verify with tests for "Expired assignment" (403 after T + 60 s, exactly one `role.assignment-expired` after two job runs)
- [x] 2.4 Implement `getAuthorizationRole` (D7); verify with a test for "Preview of a privileged role" and 404 for an unknown role
- [x] 2.5 Cucumber scenarios in `identity/role-assignments.feature` (new) for US-144 AC1, AC3 and AC4; verify they pass through `CucumberIT`

## 3. Last path to system-admin (US-146)

- [x] 3.1 Extend `AdministrationPaths` with `requireRemainingPath` and the advisory lock (D6); use it in `LocalAdminAccount.deactivate`, `RoleAssignments.revoke`, `IdpGroupMappings.update` and `delete`; verify with integration tests for "Revoking the last holder", "Another path exists", "Deleting the last usable mapping" (delete, deactivate, change of role), "Local account with a person as the other path", "Parallel revocations" (two threads), and that the existing tests of `LocalAdminAccountTests` and the mapping tests still pass
- [x] 3.2 Cucumber scenario in `identity/access-administration.feature` for US-146 AC1 and AC4; verify it passes through `CucumberIT`

## 4. Documentation

- [x] 4.1 Describe local assignments (assign, validity, revoke, effect within 60 s, SoD rules, preview, last path) in `manual/administration/roles-and-permissions.md` and its German page with the stories US-144 and US-146 in `covers`; add `role.assigned`, `role.revoked`, `role.assignment-expired` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 5. Wrap-up

- [x] 5.1 Set US-144 and US-146 to `done` with realignment notes as listed in the proposal (the implementation hash is recorded when the change is archived); add notes to REQ-164 (AC3 implemented for local assignments); verify markdown lint passes
- [x] 5.2 Update `docs/data-model.puml` and `business-objects/role-assignment.md` as in the data model section; `docs/screens/SCREENS.md` is unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [x] 5.3 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate us-144-local-role-assignments`, `./mvnw verify` and the frontend type check and tests, and verify all pass
