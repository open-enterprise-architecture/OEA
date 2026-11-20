# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D2, D4): `DiagramEdge`, `edges` and `missingMandatory` in the view, `edges` and `newConnections` in the save (with `maxItems` 4000, 50 and 500, `maxLength` on every string), the condition `visibility-scope:assign` and the extended order of answers on `saveDiagramView`, `findDiagramConnections` (`POST /views/diagrams/{id}/connections/query`, `UuidIdParam`), and the details and `entityUpdates` for connections shown by edges.

  Verify: Redocly lint, `check_openapi_authorization.py` with `test_check_openapi_authorization.py`, and `check_openapi_conventions.py` on the bundle without new allowlist entries.

## 2. Backend

- [x] 2.1 The migration `V48__diagram_edges.sql` and `DiagramEdgeStore` in `core.diagram` with the replace order of D1 (hidden edges kept, edges of removed shapes cascaded).

  Verify: the ArchUnit rules of ADR-051 pass (SQL only in `core`); a store test keeps a hidden edge across a replace and removes it with its shape.
- [x] 2.2 `EntityBatch.createConnectionsInTransaction` (D3 step 4).

  Verify with tests: a connection between existing entities created and audited as `entity.created` with the batch ID; the name "Source → Target" when blank; created with a missing mandatory value (warning); `SOURCE_NOT_ALLOWED`, `TARGET_NOT_ALLOWED` and `SOURCE_NOT_FOUND` collected for all faulty inputs with nothing written; `IllegalTransactionStateException` without a transaction; the batch API unchanged.
- [x] 2.3 The save with edges and new connections (D3), the scoping check in the controller with `@ConditionsEnforcedByHandler` and the probe diagram in `HandlerEnforcedConditionsTests`, the request limit of 4 MB (`oea.web.request-size.diagram-view`), and the details for edges (D4).

  Verify with tests:
  - a saved edge with its bend points; a connection with two edges;
  - a new connection between existing entities and one between new entities, created and audited like a batch connection;
  - a stored edge whose connection type the viewpoint no longer allows is kept on a save;
  - 422 for: wrong shapes, reversed direction, a shape not in the layout, a disallowed connection type, an endpoint not allowed by the connection metatype (with the batch code, path `newConnections[i]`), a new connection without an edge, a duplicate new connection key, edges of one new connection between different entities, a deleted connection, a non-connection entity, a duplicate edge ID, an edge ID of another diagram, both or neither of `connectionId` and `newConnectionKey`, and an `entityUpdates` item for a connection no edge of the request shows;
  - 403 for a scoping connection type without `visibility-scope:assign`, before 412/428 and every 422, nothing written; accepted with the permission;
  - the `HandlerEnforcedConditionsTests` probe answers 403;
  - edges of a deleted connection hidden and shown again;
  - removing a shape with edges in the same save;
  - a new connection created incomplete, its edge naming the missing property;
  - the details and `entityUpdates` of a connection shown by an edge; 404 for the details of a connection without an edge and of one whose edge is hidden;
  - limits (parameterized where they test the same rule):
    - 4000 edges accepted, 4001 rejected with 400;
    - 50 bend points on an edge accepted, 51 rejected with 400;
    - 500 new connections accepted, 501 rejected with 400;
    - bend point coordinates −100 000 and 100 000 accepted, −100 000.01 and 100 000.01 rejected with 400; 0.01 accepted, 0.001 rejected with 400;
    - a body of 4 MB accepted by the size filter, 4 MB + 1 byte rejected with 413;
  - the audit counts of edges and created connections; nothing audited on a rejected save.
- [x] 2.4 `findDiagramConnections` (D2).

  Verify:
  - only connections with one end among `entityIds` and the other among `entityIds` or `shownEntityIds`, not deleted, of types the viewpoint allows;
  - `entityIds` of 1 and of 2000 accepted, of 0 and of 2001 rejected with 400; `shownEntityIds` of 0 and of 2000 accepted, of 2001 rejected with 400;
  - no matching connection answered with an empty list;
  - 5000 matches answered with `truncated` false, 5001 with 5000 items and `truncated` true;
  - 403 without `entity:read`;
  - 404 for a deleted diagram.
- [x] 2.5 Cucumber: `features/diagram/diagram-connections.feature` with the API-level scenarios of the delta spec of `diagram/diagram-connections`, and the new scenarios of the MODIFIED requirements in `diagram-entity-details.feature` and `diagram-canvas.feature`. The editor scenarios (no allowed type, the offer, the connected element, edge selected) are covered by the frontend specs of 3.2. Reuse the shared instance and the sessions; use own metatypes and diagrams with a unique prefix (e.g. "Dcn"); the limit scenarios with thousands of items stay in the Java tests of 2.3 and 2.4.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client, fakes and the canvas model (edges, new connections, the save body).
- [x] 3.2 Edges, drawing with the type choice, bend points, the offer, "Add connected element", the details panel for edges, and keyboard access (D5), with `i18n` in en and de.

  Verify with specs:
  - the type choice (none, one, several), and a `target-to-source` type drawn with the source and target swapped;
  - the offer after placing the second end, nothing added without a choice, "Show all", "Choose…" and "Dismiss", no offer for a new entity, and connections that already have an edge between the shapes not offered;
  - the connected element at the stored length and at 90 pt;
  - removing a shape removes its edges, drops a new connection without an edge and discards the edits of a connection no edge shows;
  - bend points added, moved and removed;
  - the edge limits: the 4000th edge, the 50th bend point and the 500th new connection accepted, the 4001st, the 51st and the 501st blocked with a notice;
  - the details panel for an existing and a new connection;
  - the save body.
- [x] 3.3 The SCR-021 stories (edges, offer, connected element) and the SCR-021 entry in `docs/screens/SCREENS.md`.

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D6.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - `python3 scripts/check_screens.py`;
  - markdownlint;
  - `openspec validate us-074-diagram-connections --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`;
  - Redocly lint, `python3 scripts/check_openapi_authorization.py`, `python3 scripts/test_check_openapi_authorization.py` and `check_openapi_conventions.py`;
  - the full `mvn verify` and the frontend tests.
