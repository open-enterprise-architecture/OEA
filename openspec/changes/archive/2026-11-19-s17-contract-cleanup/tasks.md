# Tasks

## 1. Contract and rules

- [x] 1.1 Move the four package paths to `/metamodel` (D1) in `api/openapi.yaml`, keeping tag, `operationId`, `x-oea-authorization`, responses and limits.
- [x] 1.2 Add descriptions to the 47 operations without one (D2), and add `redocly.yaml` with `extends: [recommended]` and `operation-description: error`.

  Verify:
  - Redocly lint 2.63.0 from the repository root passes, with the 5 ignored problems unchanged;
  - it fails on a temporary operation without a description.
- [x] 1.3 Replace the 14 inline UUID parameters `id` with `UuidIdParam` (D3).
- [x] 1.4 Add `scripts/check_openapi_conventions.py` (D3 to D5, standard library only, reading a JSON bundle from `redocly bundle api/openapi.yaml --ext json`) with its test `scripts/test_check_openapi_conventions.py`, the allowlists with a reason per entry (3 `id` parameters, the request string properties of the D4 baseline, 4 envelopes), and add bundle, script and test to the CI job `openapi-lint`.

  Verify:
  - the test covers each rule positive and negative, the exemptions, an allowlisted entry and a stale allowlist entry;
  - the string-limit test reaches properties through `$ref`, `allOf` and `items`;
  - the script passes on the contract; removing one allowlist entry makes it fail;
  - `check_openapi_authorization.py` and its test pass with unchanged counts.

## 2. Backend

- [x] 2.1 Change `RequestSizeConfiguration.IMPORT_PATH` to `/api/v1/metamodel/import`, regenerate the interfaces, and make every test, Cucumber step and test support class follow the new paths.

  Verify:
  - each moved operation keeps its results on the new path (export 200; import 200, 409 `package-conflict`, 413; starter package list 200 and import 200; 403 without `metamodel:write`);
  - each old path answers an authenticated request with a valid CSRF header like an unknown path (404) and changes no state;
  - the package limit holds on the new import path: a body at the configured limit is accepted, one byte above is rejected with 413 `package-too-large`; the percent-encoded new path keeps the limit;
  - `mvn verify`, including ArchUnit and the full Cucumber suite, passes.

## 3. Frontend

- [x] 3.1 Regenerate the schema types, and update `api/metamodel.ts` and `StarterConfigurationCard.spec.ts` for the new paths.

  Verify: unit tests, type check and story tests pass, and no `admin/metamodel` remains in `frontend/src`.

## 4. Documentation and wrap-up

- [x] 4.1 Write the documentation of D6, including the regenerated API reference of the manual (`npm run generate:api` in `manual/`).
- [x] 4.2 Run the checks and verify they pass:
  - Redocly lint 2.63.0, `python3 scripts/check_openapi_authorization.py` and `python3 scripts/test_check_openapi_authorization.py`;
  - `python3 scripts/check_openapi_conventions.py` on the bundle and `python3 scripts/test_check_openapi_conventions.py`;
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-contract-cleanup --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`;
  - `python3 scripts/check_screens.py`;
  - `grep -rn "admin/metamodel"` finds only the review document, the security reviews of 2026-10, archived changes and changelogs.
