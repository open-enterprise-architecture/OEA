# Proposal

## Why

After changes 1–3 an instance has administrators and persons, but no metamodel: without entity types there is nothing to create, import or show in a catalog. Walking-skeleton step 5 ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)) therefore imports the starter package `oea-starter-togaf-classic` (ADR-005), so that the following changes (US-141 batch entities, US-046 catalog, US-049 catalog data) have `Application Component` and the other TOGAF types available.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure the Metamodel Collaboratively](../../../requirements/use-cases/UC-04-configure-metamodel.md) (import alternative), [UC-18: Import Continuum Package](../../../requirements/use-cases/UC-18-import-continuum-package.md) (embedded packages) |
| User Stories | [US-033: Import Metamodel from File](../../../requirements/user-stories/US-033-import-metamodel.md) (walking-skeleton part: package import and AC1 with a shipped package; AC2–AC4 later) |
| Requirements | [REQ-033](../../../requirements/req/REQ-033-metamodel-import.md) (validation and audit; preview later), [REQ-112](../../../requirements/req/REQ-112-activate-builtin-packages.md) (embedded package list with summary, import without upload), [REQ-114](../../../requirements/req/REQ-114-atomic-import-conflict-handling.md) (atomic import, update only of `imported` content of the same package), [REQ-184](../../../requirements/req/REQ-184-safe-import-parsing.md) (size limit, schema validation) |
| ADRs | [ADR-002](../../../adrs/ADR-002-continuum-repository.md) (scope model, package import), [ADR-005](../../../adrs/ADR-005-app-vs-tech-default.md) (starter packages), [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (property definitions), [ADR-026](../../../adrs/ADR-026-external-system-integration.md) (package format), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`metamodel:write`, SoD-8) |
| Sprint / Milestone | S2 / Walking skeleton 2/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 4, the first of part 2/2. Owner decisions of 2026-10-15: walking-skeleton part of US-033 only; import through the API plus a small card in the frontend; only `oea-starter-togaf-classic` for now.

## What Changes

- **Metamodel tables** (ADR-002, ADR-022): metamodel configuration (one instance configuration), metatypes, property categories, property definitions with enum values, metatype–property mappings, connection constraints, and a record of package imports. Metatypes and property definitions carry the `scope` of ADR-002 (`built-in`, `imported`, `organization`) and the package they come from.
- **Package import** (`POST /admin/metamodel/import`, strategy `merge`; REQ-033, REQ-114, REQ-184): a JSON package is validated against the package schema and within size and element limits, then imported in one transaction. New metatypes and properties are created with `scope=imported`; content of the same package is updated on re-import; content of another package or with `scope=organization` is never overwritten (conflict). Every import is audited with one batch ID for the package and each imported metatype.
- **Shipped starter packages** (REQ-112, ADR-005): `packages/oea-starter-togaf-classic.json` with the seven types of ADR-005 (Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software, Network Node) and a `Status` property (planned, active, phasing out, retired) for the catalog of US-049; listed with a summary and imported by identifier, without file upload.
- **Reading the metamodel**: `GET /metamodel/metatypes` and `GET /metamodel/metatypes/{id}` with scope, origin and property mappings.
- **Frontend**: a card "Import starter configuration" on the start page for persons with `metamodel:write` while the instance has no metatype; it lists the shipped packages with their summary and imports one.
- **Contract**: `listStarterPackages`, `importStarterPackage`; `MetamodelPackage` gains package identity (`packageId`, `packageVersion`, `publisher`); `MetatypeDetail` and `PropertyDefinitionDetail` gain `scope` and `sourcePackage`. **BREAKING** for the contract: `MetatypeDetail.isBuiltin` is replaced by `scope` (no client exists yet).

## Out of Scope

- US-033 AC2–AC4: file upload with preview (diff), conflict display per entry, partial import, YAML – a later change; US-033 stays `in-progress`
- Strategy `replace`, export (`exportMetamodelPackage`), viewpoints, shape assignments and i18n bundles in packages (rejected with 422 for now)
- Creating, changing, forking or deleting metatypes through the metamodel API (US-032, UC-04 basic flow); the protection of `imported` types against direct changes follows with those operations
- The starter package `oea-starter-product-org` (ADR-005 variant B)
- Reporting views per metatype (ADR-034 DM-6), architecture layers, business rules
- Screen story for the card (screen stories with change 6)

## Capabilities

### New Capabilities

- `metamodel/package-import`: import of metamodel packages – validation, limits, atomic import, scope and origin of imported content, conflicts, audit
- `metamodel/starter-packages`: packages shipped with OEA – listing with summary and import by identifier
- `metamodel/metatype-catalog`: reading the metatypes of the instance with scope, origin and property mappings

### Modified Capabilities

- none

## Impact

- `backend/`: metamodel entities and repositories in `io.oea.core`; import, validation and starter-package services in `io.oea.app`; controllers in `io.oea.api`; packaged starter packages as classpath resources
- Flyway: `db/migration/V5__metamodel.sql`
- `packages/oea-starter-togaf-classic.json` (repository root, ADR-005 follow-up), copied into the backend artifact
- `api/openapi.yaml`: new operations and fields as above
- `frontend/`: start-page card, API client for packages and metatypes
- Docs: `docs/data-model.puml`, `business-objects/metamodel-configuration.md`, `docs/audit-event-catalog.md`, `docs/operations.md` (starter configuration), `docs/walking-skeleton.md`; US-033 AC2 vs. ADR-005 (Application Component is imported, not built-in) clarified in US-033
