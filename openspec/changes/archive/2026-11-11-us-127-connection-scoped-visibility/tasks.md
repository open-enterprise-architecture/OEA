# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design, section API):
  - implement `setPersonEntityLink`;
  - add `getPersonMetatype` and `setPersonMetatype` with `PersonMetatype`;
  - add `Person.entityId`;
  - update the descriptions of `deleteMetatype`, `queryCatalog`, `queryPortalCatalog`, `batchWriteEntities` and `createConnection`.

  Verify with Redocly lint, `python3 scripts/check_openapi_authorization.py` and `ContractViolationStatusTests`.

## 2. Backend: person metatype and account link

- [x] 2.1 Add migration `V24__person_entity_link.sql` (D1). Verify in `PersonEntityLinkTests`:
  - the unique index rejects a second account on the same element;
  - `NULL` is allowed for many accounts;
  - an unknown entity and an unknown metatype are rejected by the foreign keys.
- [x] 2.2 Add the person metatype service and operations (D2). Verify in `PersonEntityLinkTests`:
  - "Set the person metatype" (audited);
  - "Connection metatype" (422);
  - unknown metatype (422);
  - unset with `null`;
  - "Without visibility:configure" (403 for `system-admin` and `metamodel-admin`);
  - deleting the person metatype (409), deletable again after unsetting it.
- [x] 2.3 Implement `setPersonEntityLink` and `Person.entityId` (D3). Verify with `PersonEntityLinkTests`:
  - "Link an account", "Element of another entity type", "Element already linked", "Own account", "Service account" and "Remove the link";
  - an element of a subtype of the person metatype is accepted;
  - a deleted element is rejected;
  - without a person metatype: 422;
  - a system administrator with `person:manage` links another person;
- [x] 2.4 Cucumber: `features/identity/person-entity-link.feature` with "Link an account" and "Own account". Verify with the `CucumberIT` run.

## 3. Backend: reading and writing in scope

- [x] 3.1 Add the anchor and the scope to `PropertyAccess`, and `ScopeStore.inScope` (D4). Verify in `CatalogScopeTests`:
  - no link: not readable;
  - a link: readable per entity;
  - a link to an element of another metatype after the person metatype changed: no anchor;
  - a deleted linked element: no anchor;
  - `inScope` in both directions;
  - a deleted connection, and a connection of another type: not in scope.
- [x] 3.2 Wrap the expressions of scoped columns in `CatalogQueryStore` and pass the scope from `CatalogQueries` (D5). Verify with `CatalogScopeTests`:
  - "Own domain and other domain";
  - "Sort and filter in scope only": a filter on a value excludes out-of-scope rows, and a sort puts them last;
  - "Assignment removed";
  - "Connection-scoped property" without a link (redacted);
  - a join column and an endpoint column in scope;
  - "Portal reader with a linked account";
  - walking the pages by cursor with a sort on the scoped column equals the pages by number.
- [x] 3.3 Check the scope in `EntityBatch` and `createConnection`, and extend `MetatypeRules.forWriter` (D6). Verify with `RestrictedWriteTests`:
  - "Connection-scoped value in scope" (stored, audit encrypted);
  - "Connection-scoped property" outside the scope and for a new entity (403, nothing written);
  - removing an in-scope value;
  - a mandatory connection-scoped property, which is checked only in scope.
- [x] 3.4 Cucumber: "Own domain and other domain" in `features/catalog/catalog-query.feature`. Verify with the `CucumberIT` run.

## 4. Backend: latency

- [x] 4.1 Add the scenario of D7 to `CatalogQueryLatencyIT` and run it. Verify that the difference of the 95th percentiles stays below 200 ms locally and in CI, and record the values in the design and in REQ-127.

## 5. Frontend

- [x] 5.1 Add the selection "Entity type of persons" to SCR-032 with its API client, and the i18n keys in English and German (D8). Verify with `PropertyVisibilityPage.spec.ts`:
  - "Choose the person metatype" (only element metatypes offered, the request sent);
  - the hint without a person metatype.

  Update the SCR-032 story and its images, and verify with `npm run test:stories` and `python3 scripts/check_screens.py`.

## 6. Manual

- [x] 6.1 Update in English and German:
  - the property visibility page: person metatype, account link through the API, connection-based enforcement;
  - the catalogs and portal pages: values per row;
  - the identity providers and persons page: the account link.

  Verify with `python3 scripts/check_manual.py` and the manual build.

## 7. Wrap-up

- [x] 7.1 Update the documentation:
  - US-127 `done`, with the realignment of AC4;
  - implementation notes in REQ-127, REQ-160 and REQ-162;
  - `visibility.person-metatype-changed` and `person.account-linked` in `docs/audit-event-catalog.md`;
  - `persons.entity_id` and `visibility_settings` in `docs/data-model.puml`;
  - `business-objects/person.md`;
  - the status of S10 change (3) in the sprint plan.

  Leave the hash for the archive PR. Close #68 with the implementation PR.
- [x] 7.2 Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 7.3 Run `./mvnw verify`, the frontend type-check, tests, build and story tests, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.
