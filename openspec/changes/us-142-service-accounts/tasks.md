# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design, section API):
  - the six operations under `/admin/service-accounts` lose `x-oea-status: planned` and 501;
  - `ServiceAccount` gets `secretExpiresAt` and `secretExpiresSoon`, and `roles` becomes required;
  - length limits on `name` (1–100) and `description` (≤ 1000);
  - `ServiceAccountWrite.secretValidityDays` and the new optional body `ServiceAccountRotation`;
  - the responses 401 `reauthentication-required`, 409 for an inactive account, and 422 for roles and taken names.

  Verify: Redocly lint passes, the generated API compiles, `check_openapi_authorization.py` and the contract authorization tests pass, and no service account operation answers 501.

## 2. Backend: storage

- [ ] 2.1 Flyway migration `V30__service_accounts.sql` (design, section Data Model):
  - `machine_credentials` with the partial unique index on active `client_id`, the check `expires_at > created_at` and the trigger for `person_type = 'service-account'`;
  - `persons.description` with a length check;
  - the unique index on `lower(name)` of service accounts;
  - grants for `oea_app`.

  Verify with migration tests on the runtime role:
  - a second active row for the same `client_id` is rejected, while a `rotated` row next to an active one is accepted;
  - a row for a human person is rejected;
  - `expires_at = created_at` is rejected;
  - a description of 1000 characters is accepted and one of 1001 is rejected;
  - "CI-Scanner" next to "ci-scanner" is rejected for service accounts and accepted for two humans.
- [ ] 2.2 `ServiceAccountStore` in `core` (D1, D5): insert the person and the secret, list and read with the active secret, rotate (old row `rotated` with `revoked_at` and `revoked_by`), deactivate (person `inactive`, secret `revoked`), and read the active secret by `client_id` for change 4.

  Verify with `ServiceAccountStoreTests`: after two rotations, three rows share one `client_id` and exactly one is active; after deactivation no row is active; the list filter `active` returns only active or only inactive accounts.

## 3. Backend: service accounts

- [ ] 3.1 `ServiceAccounts` in `app/access` (D2–D7):
  - creation with the secret, the hash, the expiry and the role assignments through `RoleAssignments`, in one transaction;
  - the role check before anything is stored, with `authz.denied`;
  - the `role:assign` condition, update with the role difference, rotation and deactivation;
  - the re-authentication of create, rotate and deactivate;
  - the configuration `oea.service-accounts.secret-max-validity-days`, checked at startup;
  - the audit events `service-account.created`, `.updated`, `.secret-rotated` and `.deactivated`, without secret or hash.

  Verify with `ServiceAccountsTests`:
  - positive: the secret starts with `oea_cs_`, has 50 characters and matches the stored argon2id hash; without roles the account holds `integration-writer`; `viewer`, `integration-writer` and `auditor` together are accepted;
  - validity boundaries: 1 and 365 days are accepted, 0 and 366 days give 400 (maximum 365); without a validity the secret expires after 90 days; a configured maximum of 0 or 3651 stops the startup and 1 and 3650 are accepted;
  - expiring soon: an expiry in exactly 14 days is marked and one in 14 days and 1 minute is not (fixed `Clock`);
  - name boundaries: 100 characters are accepted, while 101 characters and an empty or blank name give 400; "CI-Scanner" next to "ci-scanner" gives 422;
  - role count boundaries: 20 roles are accepted and 21 give 400;
  - negative: `system-admin` and an unknown role give 422 naming the role, create nothing and write one `authz.denied`; roles without `role:assign` give 403; an unchanged role list without `role:assign` is accepted;
  - update: `viewer` → `integration-writer` writes one `role.assigned` and one `role.revoked`; the account's permissions from `PermissionResolver` contain `entity:write` afterwards, and with only `viewer` they do not;
  - rotation: same `client_id`, new secret, the old row `rotated`; an unknown `client_id` gives 404;
  - deactivation: status `inactive`, secret `revoked`, no granted assignment; then update, rotate and deactivate give 409 and change nothing;
  - re-authentication: 4:59 minutes after the authentication rotate is accepted, and 5:01 minutes gives 401 `reauthentication-required` with the old secret still active; the same holds for create and deactivate;
  - no audit event and no log line contains the secret or its hash.
- [ ] 3.2 `ServiceAccountController` in `api`, implementing the six operations.

  Verify with controller tests:
  - 201 with `clientSecret` on create, and 200 with `clientSecret` on rotate;
  - `getServiceAccount` and `listServiceAccounts` never contain `clientSecret`;
  - 204 on delete;
  - 403 for an enterprise architect on every operation, with `authz.denied`;
  - 404 for an unknown `client_id`.
- [ ] 3.3 Cucumber `features/identity/service-accounts.feature` with the scenarios of the spec `identity/service-accounts`:
  - "Service account created", "Default role", "Without permission";
  - "Name length boundaries", "Name taken", "Secret shown once";
  - "Default expiry", "Validity boundaries", "14-day boundary";
  - "Secret rotated", "Unknown service account";
  - "Privileged role rejected", "Assignable roles", "Role count boundaries", "Without role:assign";
  - "Role replaced", "Without integration-writer";
  - "Service account deactivated", "Deactivated account stays inactive";
  - "Authentication too old", "Recent authentication";
  - "Active filter".

  Verify that the feature passes.

## 4. Frontend: SCR-041

- [ ] 4.1 Story first: `SCR-041-service-accounts.stories.ts` with the states `List`, `Create`, `Secret`, `EditRoles` and `Deactivate`, and a fake backend in `test/service-accounts.ts`. Verify that the stories render without a11y violations.
- [ ] 4.2 `api/service-accounts.ts`, `ServiceAccountsPage.vue` on `/admin/service-accounts`, and the header link `header-service-accounts` for `service-account:manage` (D8). Texts go in `i18n` en/de.

  Verify with `ServiceAccountsPage.spec.ts`:
  - the list shows status, roles, last use and expiry, with "Expires soon" for 10 days and without it for 30 days;
  - "Edit roles" offers only roles with `assignableToServiceAccounts`, and saving sends the role list;
  - create shows the secret dialog with a copy button, and after closing it the secret is not in the DOM;
  - cancelling the confirmation of "Deactivate" sends no request; confirming it shows the account as inactive without row actions;
  - a 422 is shown in the dialog, and a 401 `reauthentication-required` shows the re-login notice;
  - the header link and the page are present with `service-account:manage` and absent without it.
- [ ] 4.3 `docs/screens/SCREENS.md`: add SCR-041 "Service accounts" (CA+WP, UC-02, UC-23, should) with status `implemented`. Verify with `python3 scripts/check_screens.py`.

## 5. Documentation

- [ ] 5.1 Realign US-142 (AC1, AC4, technical notes) and REQ-155 (inputs, error cases) as listed in the proposal, with a change history entry in REQ-155. Add a note to REQ-176 that the secret expiry and the 14-day notice are implemented here and enforced in change 4.
- [ ] 5.2 `docs/audit-event-catalog.md`: the events `service-account.created`, `service-account.updated`, `service-account.secret-rotated` and `service-account.deactivated` with their details. `business-objects/machine-credential.md` and `business-objects/person.md`: the secret format, the configured maximum and the description (section Data Model).
- [ ] 5.3 User manual `administration/service-accounts.md` in English and German: creating an account, storing the secret once, the expiry and the marker, rotating, roles, and final deactivation; the configuration value in `setup/` or `administration/operations.md`. Verify the manual build and `scripts/check_manual.py`.

## 6. Wrap-up

- [ ] 6.1 Set US-142 and US-149 to `in-progress`, with an Implementation line that names this change and the acceptance criteria left for change 4. They become done with change 4. Update the S12 status in `docs/sprint-plan.md`.
- [ ] 6.2 Synchronize `docs/data-model.puml` (`persons.description`, the name index), `business-objects/` and `docs/screens/SCREENS.md`; regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [ ] 6.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.
