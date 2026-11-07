# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design D5):
  - add `CatalogColumn.readable`;
  - describe unreadable columns in the 400 of `queryCatalog` and `queryPortalCatalog`;
  - describe the 403 for restricted properties in `batchWriteEntities` and `createConnection`.

  Verify with Redocly lint, `python3 scripts/check_openapi_authorization.py` and `ContractViolationStatusTests`.

## 2. Backend: who may read and write

- [ ] 2.1 Add `PropertyAccess` (D1). Verify with `PropertyAccessTests`:
  - `public` is readable and writable;
  - `role-restricted` is readable with a read role and writable with a write role;
  - a read role alone does not allow writing, and no role allows nothing;
  - `connection-scoped` is neither;
  - boundaries:
    - one matching effective role suffices, none denies;
    - a held but not effective privileged role (no second factor) denies;
    - a changed visibility applies to the next request.

## 3. Backend: catalog and portal

- [ ] 3.1 Offer every non-retired mapped property in the column configuration, and keep the default `Status` column public-only (D2). Verify with `CatalogColumnsTests`:
  - "Restricted property offered";
  - a retired property still rejected (422);
  - the default columns unchanged for a restricted `Status`.
- [ ] 3.2 Mark columns readable and redact unreadable columns in `CatalogColumns`, `CatalogQueries` and the store, and reject sort and filter on them (D2). Verify with `CatalogVisibilityTests`:
  - "Catalog column empty without read role" and "Column readable with the read role";
  - "Sort by an unreadable column" (400 `sort`) and "Filter without read role" (400 `filter`), also on a join column and an endpoint column;
  - "Filter with read role";
  - "Connection-scoped property";
  - the total equals the total without the column;
  - the portal: "Restricted property" for a portal reader, readable for a portal reader holding a read role;
  - existing catalog tests still pass.
- [ ] 3.3 Cucumber: "Catalog column empty without read role" in `features/catalog/catalog-query.feature`. Verify with the `CucumberIT` run.

## 4. Backend: writes and audit values

- [ ] 4.1 Add migration `db/migration-audit/V5__audit_value_keys.sql` (D4). Verify with `AuditValueKeySchemaTests`:
  - the owner;
  - `oea_app` has `SELECT`, `INSERT` and `UPDATE` only on `wrapped_key` and `kek_id`, and no `DELETE`;
  - the table is not chained.
- [ ] 4.2 Add `AuditValueCipher` and the protected changes in `AuditWriter` (D4). Verify with `AuditValueCipherTests` and `AuditWriterTests`:
  - "Restricted value written": encrypted, key ID set, no plaintext, decrypts to old and new;
  - "Public value written" stays plain;
  - an empty old value stays `NULL`;
  - the data key is created once and reused;
  - a wrong AAD (another event) does not decrypt;
  - "Chain stays verifiable" with `AuditVerifyCommandTests`.
- [ ] 4.3 Add `audit.value_keys` to `ReencryptCommand` (D4). Verify in `SecretRotationTests`: "Rotation of the key-encryption key", where the values still decrypt after key A is removed.
- [ ] 4.4 Enforce the write roles in `EntityValidator`, the batch and `createConnection` (D3). Verify with `RestrictedWriteTests`:
  - "Write attempt without write role" (403, nothing written, denial audited);
  - "Write with write role", where the audit change is encrypted;
  - "Restricted value kept";
  - removing a value without a write role is 403;
  - "Connection-scoped property" (`RESTRICTED_PROPERTY_NOT_SUPPORTED`);
  - "Mandatory restricted property of a writer" and "Mandatory property the writer cannot change";
  - a read role without the write role is 403;
  - `EntityValidatorTests` updated.
- [ ] 4.5 Cucumber: "Write attempt without write role" and "Write with write role" in `features/repository/entity-write.feature`. Verify with the `CucumberIT` run.

## 5. Frontend

- [ ] 5.1 Show unreadable columns with lock icon and empty cells and without sort in `CatalogTable`, and leave them out of `CatalogFilterBar` (D6). Verify with tests in `CatalogPage.spec.ts` and `PortalCatalogPage.spec.ts`:
  - an unreadable column: lock, empty cells, no sort control, not in the filter bar;
  - a readable column unchanged.

  Add a story variant of SCR-020 with an unreadable column, and update `docs/screens/SCREENS.md` if the images change. Verify with `npm run test:stories` and `python3 scripts/check_screens.py`.

## 6. Manual

- [ ] 6.1 Update in English and German:
  - the catalogs page: empty restricted columns with the lock;
  - the portal page: the same rule;
  - the property visibility page: enforcement for role-based properties, and connection-based still to follow.

  Verify with `python3 scripts/check_manual.py` and the manual build.

## 7. Wrap-up

- [ ] 7.1 Update the documentation:
  - US-126 `in-progress` with the realignment of AC1 and AC2;
  - implementation notes in REQ-126 and REQ-160 (AC2, AC4);
  - `audit.value_keys` in `docs/data-model.puml`;
  - `docs/audit-hash-chain.md`, if it names the encrypted fields;
  - the status of S10 change (2) in the sprint plan.

  Leave the hash for the archive PR. The implementation PR refers to #68 without closing it; change 3 closes it with `connection-scoped`.
- [ ] 7.2 Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [ ] 7.3 Run `./mvnw verify`, the frontend type-check, tests, build and story tests, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.
