# Tasks

## 1. Contract and migrations

- [ ] 1.1 Extend `api/openapi.yaml` as in the API section of the design: `GET /admin/system-admin-account`, `POST /admin/system-admin-account/deactivate` (permission `auth-config:manage`, `409` with problem type `urn:oea:problem:last-admin-path`, `429`), schema `SystemAdminAccountState`, `lastMatchedAt` and `warnings` on `IdpGroupMapping`, `CLI` in the audit source enums; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [ ] 1.2 Flyway migration `V8__admin_safeguards.sql`: `idp_group_mappings.last_matched_at`, `system_admin_accounts.deactivated_by_person_id` with the check that at most one `deactivated_by_*` is set; verify the existing migration and schema tests pass and the columns exist after a fresh start
- [ ] 1.3 Audit migration `V3__cli_source.sql` extending the source check by `CLI` and `AuditEvent.Source.CLI`; verify an `AuditWriter` integration test writes a record with source `CLI` and `oea audit verify` passes on it

## 2. Last match and warning (US-018, D2, D4)

- [ ] 2.1 Record `last_matched_at` in `IdpRoles` for effective derived roles at login and re-evaluation (at most one write per mapping and hour), and expose `lastMatchedAt` in the mapping view; verify with integration tests for the spec scenarios "Mapping that has granted its role", "Mapping that has never matched", "Effective role recorded" and "Role without second factor not recorded"
- [ ] 2.2 Return the warning `group-not-seen` from create and update of a mapping to `system-admin` (D4); verify with integration tests for "Mapping to a group never seen" (stored, warning) and "Mapping to a group with a current member session" (no warning), and that mappings to other roles never carry a warning
- [ ] 2.3 Cucumber scenario in `identity/access-administration.feature` for US-018 AC1/AC2; verify it passes through `CucumberIT`
- [ ] 2.4 Document `lastMatchedAt` and the warning in `manual/administration/roles-and-permissions.md` and its German page; verify the manual coverage check passes

## 3. Remote system administration (US-014, D1)

- [ ] 3.1 Cucumber scenarios in `identity/idp-roles.feature` for "Member of the admin group" and "Person outside the admin group" (bound persons, group mapped to `system-admin`, second factor at the test IdP, `listIdpGroupMappings` answered with 200 / 403); verify they pass through `CucumberIT`
- [ ] 3.2 Describe the hand-over of system administration to an IdP group (map the group to `system-admin`, second factor at the IdP, test login, then deactivate the local account) in `manual/administration/roles-and-permissions.md` and its German page; verify the manual coverage check passes

## 4. Deactivating the local account (US-019, D3, D7)

- [ ] 4.1 Add `AdministrationPaths` (usable paths to `system-admin` as in D3) and the local-account service with `state` and `deactivate` (row lock, sessions revoked, `deactivated_by_*` set, audit `system-admin-account.deactivated` with outcome `success` or `denied`/`last-admin-path`); verify with unit/integration tests for the path rules (inactive, unapproved, deleted, disabled provider, never matched mapping do not count)
- [ ] 4.2 Add the controller operations `getSystemAdminAccount` and `deactivateSystemAdminAccount` in `io.oea.api.admin` with the 409 mapping in `ApiExceptionHandler`; verify with integration tests for "Deactivation with a working remote mapping", "Deactivation without another path" and "Deactivation without the permission", repeated deactivation (200, no new event), and that `AuthorizationMatrixTests` and `UncoveredOperationTests` cover the new operations
- [ ] 4.3 Cucumber scenarios for US-019 AC1 and the adjusted AC2 in `identity/access-administration.feature`; verify they pass through `CucumberIT`
- [ ] 4.4 Document the deactivation (precondition, 409, effect on sessions) in `manual/administration/operations.md` and its German page; add `system-admin-account.deactivated` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 5. Break-glass command (UC-02 E4, D5, D6)

- [ ] 5.1 Add `io.oea.app.recovery` with the recovery logic (inputs validated before any write, one transaction as in D5 step 5, audit `system.admin-recovered` with source `CLI`) and `AdminRecoverCommand` with console/stdin input and exit codes 0/1/2, dispatched as `admin recover` in `OeaBackendApplication` with `oea.mode=admin-recover` in `ConfigurationGuards`; verify with integration tests (Testcontainers, scripted stdin) for "Recovery after the setup phase", "Recovery during the setup phase", "Wrong confirmation code", "Instance not bootstrapped", "Password outside the length limits" and "Recovery in the audit trail" (no password or secret in records or captured log output)
- [ ] 5.2 Extend `scripts/compose_smoke_test.sh` with a recovery step: after bootstrapping, run `docker compose exec -T backend java -jar /app/oea-backend.jar admin recover` with the password on standard input (setup phase, no TOTP step) and log in with the new password; verify the CI job `compose smoke test` passes
- [ ] 5.3 Document the break-glass procedure (when to use it, the command, the TOTP step, clearing the terminal, container access as trust boundary) in `manual/administration/operations.md` and its German page, with a pointer from `manual/setup/first-administrator.md` and its German page; add `system.admin-recovered` and the source `CLI` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 6. Wrap-up

- [ ] 6.1 Set US-014, US-018 and US-019 to `realized` with the implementation hash; add realignment notes to US-018 AC1, US-019 AC2, REQ-018, REQ-019, REQ-014 and REQ-173 AC4 as listed in the proposal; answer the open question on E4 in UC-02 and update the S3 entry in `docs/sprint-plan.md`; verify markdown lint passes
- [ ] 6.2 Update `docs/data-model.puml` (`last_matched_at`, `deactivated_by_person_id`, audit source `CLI`), `business-objects/idp-group-mapping.md` and `business-objects/system-admin-account.md` as in the Data Model section; `docs/screens/SCREENS.md` is unchanged (no screen); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 6.3 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate us-019-admin-safeguards`, `./mvnw verify` and the frontend tests and verify all pass
