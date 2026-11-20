# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1): `previewEntityDeletion`, `deleteEntity` (implemented, 200 `EntityDeletionResult`, without `reason`), `restoreDeletedEntity`; the `visibility-scope:assign` conditions; `restored`, `EXTERNAL_ID_DELETED` and the `entity:restore` condition of `batchWriteEntities`; `entityDeleted` on `SolutionDelta`, `deletionConflicts` on `SolutionGoLivePreview` and `PlateauGoLiveSolution`, and the 409 `entity-deleted` of both go-lives for `modified` and `retiring`.

  Verify: Redocly lint, `check_openapi_authorization.py` and `test_check_openapi_authorization.py`, `check_openapi_conventions.py` with its tests.

## 2. Backend

- [x] 2.1 Flyway migration V49 `entity_deletion_origin` (Data Model): `deleted_with_entity_id`, its check and partial index.

  Verify: the migration test; the check rejects an origin on an active row and accepts it on a deleted one.
- [x] 2.2 The cascade collection and the soft delete in `core.entity`, `EntityDeletion` with the optional origin, and the preview and delete in `app.entity` (D2); the go-live passes the origin for the connections it retires.

  Verify with tests:
  - children on 1 and on 10 levels (the deepest possible) and their connections counted and deleted with one time, actor and origin, the entity itself without origin;
  - the response lists all IDs; the audit events with `triggeredByEntityId`;
  - a connection deleted alone;
  - 403 without `entity:delete`; 403 for a scoping connection in the cascade without `visibility-scope:assign`, and 200 with it;
  - 404 for an unknown or deleted entity; 422 for a structuring aid and an automatic composition connection;
  - 409 for the entity, a child or a connection blocked by open issues, nothing deleted;
  - 412 and 428;
  - the preview names active solutions and not implemented or archived ones, and changes nothing;
  - a go-live retiring an entity records it as origin of its retired connections; the hierarchy deletions record none.
- [x] 2.2a `restoreEntity` and `previewEntityRestore` answer 409 `entity-deleted` for a deleted entity.

  Verify: 409 and no version for a deleted entity; restore as before after the undelete.
- [x] 2.3 The restore (D3).

  Verify:
  - the cascade restored, versions unchanged, `entity.undeleted` per entity;
  - a connection whose other endpoint is still deleted, and one deleted earlier, stay deleted; the first is named with `endpoint-deleted`;
  - an automatic composition connection whose child got another parent is named with `parent-changed`;
  - a child restored alone with its descendants goes to the top level as a new version with `entity.parent-changed`;
  - the depth boundary: a restored subtree ending exactly 10 levels below the root keeps its parent, one ending at 11 goes to the top level;
  - two deletions in the same instant by the same actor are not mixed;
  - an entity deleted before V49 (no origin) is restored alone;
  - a connection restored directly with a deleted endpoint gives 409 `connection-not-restorable`;
  - 409 `entity-not-deleted`; 403 without `entity:restore` and for a scoping connection without `visibility-scope:assign`; 404, 412 and 428.
- [x] 2.4 The batch and #70 (D4).

  Verify:
  - an `upsert` of a deleted external ID with `entity:restore` restores with the cascade, updates, and answers `restored: true`; the same for a connection with matching active endpoints;
  - without it the item gets `EXTERNAL_ID_DELETED`; an atomic batch changes nothing; a non-atomic batch rejects only the item;
  - `create-only` and `skip-existing` keep `EXTERNAL_ID_DELETED` with `entity:restore`.
- [x] 2.5 The solutions and plateaus (D5).

  Verify:
  - `entityDeleted` in the delta list; the deletion conflict in both previews;
  - both go-lives answer 409 `entity-deleted` for a `modified` and for a `retiring` delta and write nothing;
  - removing the delta or restoring the entity resolves it;
  - a delta `new` is unaffected; an entity retired by an earlier solution of the same plateau keeps today's handling.
- [x] 2.6 Cucumber: `features/repository/entity-delete.feature` (preview, delete, restore, the 403/409/412 cases) and scenarios in the batch and go-live features for #70 and the deletion conflict. Reuse the shared instance and the sessions, use own metatypes and names with a unique prefix, and assert only on the entities a scenario creates (no global counts).

  Verify: the full `./mvnw verify` passes.

## 3. Frontend

- [x] 3.1 API client (regenerated types) and fakes.
- [x] 3.2 "Delete from repository" on the canvas with the preview dialog (D6), and the deletion conflicts on SCR-061 and SCR-066, with `i18n` in en and de.

  Verify with specs:
  - the menu entry and Ctrl+Delete only with `entity:delete`, and not for unsaved entities and connections;
  - the dialog with the counts and solutions, and the message of a 422 preview;
  - after the delete the shapes and edges of all deleted IDs are gone, unsaved connections to them are discarded, and other unsaved changes stay;
  - the 412, 409 and 403 handling;
  - the marker on a delta of a deleted entity, the conflicts in the go-live notice and in SCR-066, and the 409 of both go-lives.
- [x] 3.3 The stories SCR-021 (deletion dialog), SCR-061 and SCR-066 (deletion conflict), and SCREENS.md.

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py`.

## 4. Wrap-up

- [x] 4.1 The documentation of D7: ADR-019 amendment, the manual (en/de), the audit catalog, `docs/data-model.puml` and the business object of the entity, US-074 and REQ-146 realigned, the sprint plan.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - `python3 scripts/check_screens.py`;
  - markdownlint;
  - `openspec validate s17-entity-delete --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
