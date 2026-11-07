# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design, section API):
  - implement `getPropertyVisibility` and `updatePropertyVisibility`;
  - add `metatypes` to `PropertyVisibilityEntry`;
  - add `visibility:configure` to `listMetatypes`;
  - describe the 403 scoping condition of `createConnection` and `batchWriteEntities`, and the 409 of `deleteMetatype`.

  Verify with Redocly lint, `python3 scripts/check_openapi_authorization.py` and `ContractViolationStatusTests`.

## 2. Backend: configuration

- [ ] 2.1 Add migration `V23__property_visibility_roles.sql` (D1). Verify with `PropertyVisibilitySchemaTests`:
  - the owner is `oea_owner`;
  - `oea_app` has `SELECT`, `INSERT` and `DELETE`, and no `UPDATE`;
  - the key rejects a duplicate;
  - an unknown role and an unknown property are rejected by the foreign keys;
  - `access` other than `read` or `write` is rejected.
- [ ] 2.2 Add `PropertyVisibility` (read, validate, change, version, audit; D2, D3) and the controller methods. Verify with `PropertyVisibilityTests` (MockMvc):
  - "New property is public", "Existing properties", "Restrict a property", "Back to public" and "One invalid entry among several";
  - "Role-restricted without read role", "Write role that is no read role" and "Connection-scoped without connection metatype";
  - an unknown property, a retired property, an unknown role, a property listed twice, and roles or a scoping type on `public`, all 422 with the entry named;
  - boundaries:
    - read roles: 1 valid, 0 rejected (422);
    - write roles: equal to the read roles valid, one more rejected (422);
    - role lists: 50 roles valid, 51 rejected (400, contract `maxItems`);
  - "Metamodel administrator" (403 for GET and PUT) and an enterprise architect (403);
  - "Change audited", "Self-benefiting change", no event for an unchanged entry, and the version growing only on a change.
- [ ] 2.3 Fill `PropertyDefinition.visibility` in the metamodel endpoints and leave it out of `updatePropertyDefinition` (D2). Verify in `MetamodelEditingTests`:
  - "Visibility in the metamodel";
  - a property definition update with a `visibility` in the body leaves the visibility unchanged.
- [ ] 2.4 Allow `listMetatypes` with `visibility:configure` (D2). Verify in `MetatypeCatalogTests`:
  - a security administrator lists the metatypes with `isConnection`;
  - a portal reader, who holds none of the permissions, gets 403.
- [ ] 2.5 Cucumber: `features/metamodel/property-visibility.feature` with "Restrict a property", "Role-restricted without read role" and "Metamodel administrator". Verify with the `CucumberIT` run.

## 3. Backend: scoping connections

- [ ] 3.1 Check the scoping type in `createConnection` and `batchWriteEntities`, and replace `@ConditionsNotReachable` with `@ConditionsEnforcedByHandler` (D4). Verify with `ScopingConnectionTests`:
  - "Architect creates a scoping connection" (403, nothing written, denial audited);
  - "Scoping connection in a batch" (the whole batch rejected, entity not written);
  - "With the permission";
  - "Type no longer used for scoping";
  - a connection of another metatype still written with `entity:write`;
  - `HandlerEnforcedConditionsTests` and `UncoveredOperationTests` still pass.
- [ ] 3.2 Reject deleting a connection metatype that is a scoping type with 409 `in-use` (D4). Verify in `MetamodelEditingTests`:
  - "Delete the scoping type" (409);
  - the same metatype deleted after its property went back to `public`.
- [ ] 3.3 Cucumber: "Architect creates a scoping connection" in `features/repository/connection-write.feature`. Verify with the `CucumberIT` run.

## 4. Frontend

- [ ] 4.1 Add `src/api/visibility.ts`, `PropertyVisibilityPage.vue` with its route, the header link and the i18n keys in English and German (D5). Verify with `PropertyVisibilityPage.spec.ts`:
  - the list with mode, badge and name filter;
  - "Role selection appears", "Saving without role" (no request) and "Only connection metatypes";
  - saving each mode sends the expected entry;
  - a 422 marks the named field;
  - "Page without the permission".

  Verify the header in `AppHeader.spec.ts`: the link is shown with `visibility:configure` and hidden without it.
- [ ] 4.2 Show the badge and the read-only visibility in SCR-030 (D5). Verify in `MetamodelPage.spec.ts`:
  - "Restricted property in the list" (badge only for non-public properties);
  - "Visibility in the detail" (no control to change it).
- [ ] 4.3 Add the story `screens/SCR-032-property-visibility.stories.ts` (List, Dialog per mode, InlineError), update the SCR-030 story with a restricted property, and update `docs/screens/SCREENS.md`:
  - SCR-032 `implemented`;
  - the legacy SVGs deleted (ADR-031).

  Verify with `npm run test:stories` and `python3 scripts/check_screens.py`.

## 5. Manual

- [ ] 5.1 Add `manual/administration/property-visibility.md` and `manual/de/administration/property-visibility.md`, covering SCR-032. Add:
  - the navigation entry;
  - the link from the administration index;
  - a sentence in the metamodel editing page on the badge;
  - the walkthrough screenshot of SCR-032.

  Verify with `python3 scripts/check_manual.py` and the manual build.

## 6. Wrap-up

- [ ] 6.1 Update the documentation:
  - realign US-124 AC1–AC3, US-125 and REQ-125 (D6) and add implementation notes to REQ-124 and REQ-161;
  - add `visibility.changed` to `docs/audit-event-catalog.md`;
  - add the note to `property_visibility_roles` in `docs/data-model.puml`;
  - add the migration reference to `business-objects/metamodel-configuration.md`;
  - add the status of S10 change (1) to the sprint plan.

  Leave the hash for the archive PR.
- [ ] 6.2 Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [ ] 6.3 Run `./mvnw verify`, the frontend type-check, tests, build and story tests, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.
