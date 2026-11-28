# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml`:
  - `getLineageModel` and `updateLineageModel` (`/metamodel/lineage-model`, `If-Match`);
  - `queryLineage` (`POST /views/lineage/query`, D4) with the request limits (`maxDepth` 1–100, `metatypeIds` at most 50, `direction` with `impact`, `dataObjectId`), the response with `truncated` and `carriedByTruncated`, and 400, 403, 404, 422 and 429;
  - remove the planned `getLineageView` and the schemas `LineageView` and `LineageNode`;
  - `CONNECTION_DEPTH_EXCEEDED` on `createConnection` and the batch;
  - the package section `lineageModel` and `lineageModelFilled` in the import result;
  - the 422 `LINEAGE_MODEL_INCOMPLETE`.

  Verification: Redocly, and both OpenAPI checks with their tests.

## 2. Packages and database

- [x] 2.1 `packages/oea-starter-togaf-classic.json` 1.5.0 (D1).

  Verification, extending the starter package tests:
  - an update over 1.4.0 with data keeps the entities and their values, and no data object is incomplete;
  - "Carries Data" from a data flow to a data object is accepted;
  - "Carries Data" from an application component gives 422 `SOURCE_NOT_ALLOWED`;
  - "Carries Data" to a data flow gives 422 `TARGET_NOT_ALLOWED`;
  - "Data Flow" between two application components, and between two system software elements, is accepted, and "Data Flow" is allowed in both landscape viewpoints;
  - parameterized text limits, each with 422 naming the property on the invalid side: "Personal data categories" 500 accepted and 501 rejected, "Transformation description" 1000 and 1001, "Data owner" 255 and 256;
  - the package lists ten metatypes.
- [x] 2.2 `packages/oea-starter-bpmn.json` 1.2.0 (D2).

  Verification: an update over 1.1.0 fills data input and data output, lists them in the import result, and keeps the types, the viewpoint and the diagrams.
- [x] 2.3 V54 `lineage_model`.

  Verification: the migration test.

## 3. Backend

- [x] 3.1 The depth rule in the connection write (D1, `repository/connection-write`).

  Verification, for `createConnection` and the batch:
  - a "Realized By" from a "Carries Data" connection, and a connection with it as target, give 422 `CONNECTION_DEPTH_EXCEEDED` naming the endpoint, and nothing is stored;
  - "Carries Data" from a data flow between two elements, and a data flow from a data flow between elements (T-relationship), stay accepted.
- [x] 3.2 The lineage model store and endpoints, and the package section (D3).

  Verification:
  - each part of the wrong kind gives 422 naming the part;
  - each of the carries-data, data-input and data-output constraints gives 422 when it does not match, and is accepted with an open list and with a base type listed;
  - 403 without `metamodel:write`, with nothing audited, and read access with `entity:read` or `metamodel:write` only;
  - 412, 428 and `metamodel-locked`;
  - a deleted metatype unsets its part;
  - the import fills only unset parts, and a metatype outside the package gives 422;
  - the audit with the changed parts, and no event and no new version for an unchanged write.
- [x] 3.3 `queryLineage` (D4).

  Verification, parameterized where possible:
  - from a data object: downstream and upstream of AC1 and AC2, with the origin, intermediate and sink roles, the depths and the paths in both orders; `both` as the union;
  - the tasks: writers with `enrich` upstream, readers downstream, both with `both`; no readers without the data-input part, no writers without the data-output part;
  - a data object that no flow carries gives 200 with empty nodes, edges and paths;
  - a deleted flow, a deleted carries-data connection, a deleted endpoint and a deleted task are left out;
  - from a system: downstream and upstream with the carried data objects on the edges; `dataObjectId` restricts the flows (UC-08 AC4); `both` does not turn around; `impact` equals downstream with `impact` true;
  - `carriedBy` with 50 carried data objects has `carriedByTruncated` false, with 51 lists 50 and true;
  - the cycle A → B → C → A, with the lowest ID as origin, `cycleDetected` true and the walk ending; a diamond A → B, A → C, B → D, C → D has `cycleDetected` false;
  - `maxDepth` 1 and 100 are accepted, 0 and 101 give 400, and the default is 50 (a chain of 51 hops returns 50);
  - nodes: 5000 give `truncated` false, 5001 give 5000 nodes with `truncated` true;
  - edges: 10000 give `truncated` false, 10001 give 10000 edges with `truncated` true (fixtures written directly);
  - `metatypeIds` with 50 entries is accepted and with 51 gives 400, an unknown metatype gives 422, and the filter includes subtypes and keeps the edges;
  - 400 without `startEntityId` and for `dataObjectId` with a data object start; 422 for a `dataObjectId` that is no data object;
  - 403 without `entity:read`, also for a service account without it;
  - 404 for an unknown or deleted start; 422 naming `startEntityId` for a connection as start;
  - 422 `LINEAGE_MODEL_INCOMPLETE` for each unset part of data object, data flow and carries-data;
  - a restricted transformation type gives `transformationRestricted` true without a value, also when no value is set;
  - a read through the override writes one `property-override-access` event with the channel `lineage`, and a read without it writes none;
  - the 429 above the per-principal rate limit.
- [x] 3.4 Cucumber `data-lineage.feature`, on an instance of its own:
  - US-065 AC1 and AC2;
  - REQ-062 AC3 to AC5;
  - REQ-081 AC6;
  - US-064 AC1 to AC4;
  - UC-08 AC3 to AC6;
  - US-068 AC1, AC2 and the empty filter result of AC4.

## 4. Frontend

- [x] 4.1 The section "Lineage model" on SCR-030, in en and de, read-only in `import-only`, with the 412 handling as in "Organization model".

  Verification: unit specs and a story.

## 5. Documentation

- [x] 5.1 The manual pages "Metamodel packages" (TOGAF 1.5.0 with the name conflicts of D1, BPMN 1.2.0, "Lineage model") and a new page "Data lineage" (modelling data flows and carried data, and the query through the API; the page view follows in change 2), each in en and de.
- [x] 5.2 The documents to update:
  - ADR-010 and the business object entity BR-04, with an implementation note on the constraint and the depth rule instead of the flag;
  - the business object metamodel configuration and `docs/data-model.puml` (`lineage_model`);
  - the audit catalog;
  - REQ-060, REQ-061, REQ-062, REQ-064 (AC1, AC2), REQ-081 (AC6, realigned: writers upstream as producers, readers downstream as consumers), US-063, US-064, US-065, US-068 (AC1, AC2) and UC-08;
  - US-068 and the sprint plan, noting that US-068 AC3 and AC4 (CSV export) move out of S18 to a later story for a general catalog export (no story file in this change);
  - the sprint plan, with the S18 cut into `us-065-lineage-model-and-query` and `us-066-lineage-view`.

  Then run `generate_traceability.py`.

## 6. Checks

- [x] 6.1 All local checks:
  - the backend verify;
  - in the frontend: unit tests, `vue-tsc --build` and story tests;
  - `openspec validate`;
  - the links, screens, manual and traceability scripts;
  - markdownlint on every changed Markdown file;
  - Redocly.
