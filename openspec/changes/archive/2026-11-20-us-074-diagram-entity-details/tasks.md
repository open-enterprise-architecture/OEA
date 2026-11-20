# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1, D1a, D2):
  - `getDiagramEntityDetails` with `DiagramEntityDetails` and `DiagramEntityField`;
  - `searchDiagramReferenceTargets` answering `DiagramEntityPage`;
  - `entityUpdates` (`DiagramEntityUpdate`) in `DiagramViewWrite`, `description` and `properties` in `DiagramNewEntity` (its description no longer "name and metatype only");
  - the 409 of `saveDiagramView` with `entity-version-conflict` (`DiagramEntityConflictProblem`) and `entity-blocked`, and its description (entity changes, version check, order of the answers).

  Verify: Redocly lint, `check_openapi_authorization.py` and its test, `check_openapi_conventions.py` on the bundle (no new allowlist entry; `PropertyWriteValue.value` stays the only gap reached) and its test.

## 2. Backend

- [x] 2.1 The read `getDiagramEntityDetails` (D1).

  Verify with tests:
  - the details of a shown entity, with redaction, `readable` and `writable` per property and `missingMandatory`;
  - `writable` false for a reader without `entity:write`, false for a role-restricted property without a write role and true with one, and for a connection-scoped property true only for an entity in scope;
  - 404 for an entity without a shape, a deleted entity, an unknown and a deleted diagram;
  - 403 without `entity:read`;
  - `entityId` 0 rejected with 400 and 1 accepted (404 when not shown).
- [x] 2.2 The read `searchDiagramReferenceTargets` (D1a).

  Verify with tests:
  - only entities of exactly the referenced metatype, not deleted, filtered by `q` with `%` and `_` literal;
  - 422 naming `propertyDefId` for an unknown and for a non-reference property;
  - 404 for an unknown diagram; 403 without `entity:read`;
  - limits: `q` of 255 accepted and 256 rejected; `pageSize` 1 and 200 accepted, 0 and 201 rejected; `page` 0 accepted and −1 rejected.
- [x] 2.3 The save with entity changes (D2), and `EntityBatch.updateInTransaction`.

  Verify with tests:
  - a name, description and value change is versioned and audited as `entity.updated` with the same details and changes as a batch update, atomic with the layout; an update without an effective change writes no version and no audit;
  - a missing mandatory value of a changed incomplete entity gives 422 `MANDATORY_MISSING` on `entityUpdates[i].properties` and nothing changes, the layout included;
  - completing it removes `missingMandatory` from the returned view;
  - a new entity with description and values is created with them and is not incomplete; an invalid value of a new entity gives 422 on `newEntities[i]...`;
  - a restricted property without a write role gives 403 for an update and for a new entity, the denial is audited and nothing changes; with a write role it is written;
  - an entity with an open issue gives 409 `entity-blocked` naming it, and nothing changes;
  - an update of an entity without a shape in the request, and one named twice, give 422 naming `entityUpdates[i].entityId`;
  - a version mismatch gives 409 `entity-version-conflict` naming every stale entity with its current version, and nothing changes; the expected version equal to the current one is accepted;
  - `updateInTransaction` without a transaction throws `IllegalTransactionStateException`;
  - the batch API still writes without a version;
  - limits: 500 updates accepted and 501 rejected; name 255 accepted and 256 rejected, a blank name 422; description 2000 accepted and 2001 rejected (updates and new entities); `expectedVersion` 1 accepted and 0 rejected; `entityId` 1 accepted and 0 rejected;
  - body size: 500 updates each with a name of 255, a description of 2000 and five values of 50 characters, with 500 shapes, are accepted; a body over 2 MB gives 413 as before.
- [x] 2.4 Cucumber: extend `features/diagram/` with the API scenarios of the delta spec (details, reference targets, completing, mandatory still missing, new entity with values, restricted property, open issue, entity not shown, version conflict, batch unchanged).
  - Reuse the shared instance and the sessions of `diagram-canvas.feature`.
  - Use only own metatypes, properties and visibility settings with a unique prefix (as the `Dgc` names of change 11), so that no other feature sees a mandatory or restricted property.
  - Leave out the limit scenario (covered by the tests of 2.3).

  Verify: the full `./mvnw verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes (`getDiagramEntityDetails`, `searchDiagramReferenceTargets`, the new save body and the 409 problems); `PropertyInput.vue` moved to a shared place with a reference search prop, SCR-061 dialogs passing the baseline search.

  Verify: the existing specs of `SolutionModifyDialog` and `SolutionDeltaDialog` pass unchanged in behavior; a spec of `PropertyInput` with an injected search.
- [x] 3.2 The canvas model (D3): edits per entity with the version read, the dirty state, `entityUpdates` and the new entities' values in `writeRequest`, the client limit of 500 changed entities, the rejected fields of `entityUpdates[i]` and `newEntities[i]`.

  Verify with specs: the 500th changed entity accepted and the 501st refused; only changed fields sent; values of a new entity sent with it; clearing after a successful save.
- [x] 3.3 `EntityDetailsPanel.vue` (D3), with `i18n` in en and de.

  Verify with specs:
  - open on selection, read-only for a reader, redacted values as "Restricted", inputs only where `writable`;
  - edits mark the diagram as dirty and are sent as `entityUpdates` with the version;
  - required fields are marked and missing ones named;
  - on 409 `entity-version-conflict` the reload of one entity keeps the other changes;
  - on 409 `entity-blocked`, 403, 413 and 422 the messages and marked fields;
  - a reference value chosen through the reference search;
  - keyboard access (`Tab`, `Escape`).
- [x] 3.4 The SCR-021 story with the panel (read-only, editing, incomplete, conflict) and the SCREENS.md entry of SCR-021.

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D4.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - `python3 scripts/check_screens.py`;
  - markdownlint;
  - `openspec validate us-074-diagram-entity-details --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
