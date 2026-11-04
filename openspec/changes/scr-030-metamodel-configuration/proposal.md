# Proposal

## Why

Metamodel administrators can so far change the metamodel only through the API: metatypes, property definitions, enum values, categories, property mappings with an effective date, the re-import setting and translated labels. UC-04 asks for a graphical configuration in which Kurt sees all metatypes, recognizes built-in and imported ones as read-only and maintains the organization's own content (US-032, REQ-032). Two administrators who change the same element at the same time overwrite each other today without noticing. UC-04 A5 and REQ-032 call for a warning on such a collision.

This is S7 change 4 of UC-04. It completes US-032 and REQ-032 except for the relations, which follow in S8 with the connection metatypes (US-036).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure Metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) (basic flow, A4, A5) |
| User Stories | [US-032: Create a New Entity Type in the Metamodel](../../../requirements/user-stories/US-032-create-entitytype.md), [US-137](../../../requirements/user-stories/US-137-change-property-mapping-effective-date.md), [US-140](../../../requirements/user-stories/US-140-load-dynamic-labels.md) |
| Requirements | [REQ-032: Configure Custom EntityTypes via GUI](../../../requirements/req/REQ-032-entitytype-gui-configuration.md) |
| Screens | SCR-030 Metamodel configuration ([SCREENS.md](../../../docs/screens/SCREENS.md)) |
| ADRs | [ADR-031: UI Prototyping with Storybook](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-049](../../../adrs/ADR-049-organization-extensions-of-packages.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Sprint / Milestone | S7 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-04:

- **Full scope including labels:** SCR-030 in the web portal covers everything the metamodel API offers:
  - metatypes with name, abbreviation, sort order and base type;
  - property definitions with data type, constraints, category and enum values;
  - property categories;
  - property mappings with necessity, effective date and default value, including inherited mappings and organization entries on package mappings (ADR-049);
  - the re-import setting per metatype and the global one;
  - translated labels per language.
- **Master-detail layout as in the legacy mockup:** one page `/metamodel` with the sections metatypes, properties, categories, translations and settings. In the metatypes section, the list is on the left and the selected metatype with its properties is on the right. `/metamodel/metatypes/{id}` opens a metatype directly. Elements are created and changed in dialogs. At phone width, the list and the detail are shown one after the other.
- **Read access for everyone with `entity:read`:** the page and the header entry appear for `entity:read` or `metamodel:write`. Only `metamodel:write` shows the actions to create, change and delete. Built-in and imported content never shows them; instead it carries a scope badge with the source package (REQ-032 AC5).
- **Optimistic locking now:** metatypes, property definitions and categories get a version. The API returns it in the body and as `ETag`. Every write accepts `If-Match` and rejects a stale version with 412 and the problem type `urn:oea:problem:stale-version`. Writes on mappings and the re-import setting check the version of the metatype, and writes on enum values check the version of the property definition. A request without `If-Match` stays allowed, so existing API clients and agents keep working. The portal always sends `If-Match`. On 412 it warns that someone else changed the element, shows the current state, and lets the administrator overwrite it or discard the input. This is the last-write-wins with a warning that UC-04 A5 describes.
- **Manual:** the page "Editing the metamodel" describes the screen in English and German, with walkthrough screenshots. The API examples stay as a reference for scripts.

## Out of Scope

- Relations between metatypes (US-032 AC1, REQ-032): these come in S8 with the connection metatypes (US-036). The legacy mockup's tab "Relations" is not built.
- Stereotypes and constraint rules (UC-04 A2, A3), and the tabs "Validation" and "Import / Export" of the mockup.
- Uploading a package file in the portal (UC-04 A1). The import of starter packages stays where it is.
- Locking of translated labels. `updateMetamodelLabels` merges single keys, so two administrators only collide on the same key. There the last write wins without a warning.
- The client app (Electron). SCR-030 becomes `CA+WP`, and the client app follows with its own changes.
- Visibility of properties (REQ-124, SCR-031), which remains its own screen.

## Capabilities

### New Capabilities

- `metamodel/configuration-screen`: the metamodel configuration in the web portal: who sees it and who may change it, the list of metatypes with scope, the detail with own and inherited properties, the dialogs for every element, translated labels, the re-import settings and the error and collision messages.
- `metamodel/optimistic-locking`: versions of metatypes, property definitions and categories, `ETag` and `If-Match` on their writes, and 412 on a stale version.

### Modified Capabilities

None. The new versions do not change the existing behavior of the metamodel writes.

## Impact

- `api/openapi.yaml`:
  - `version` (read-only) in `MetatypeDetail`, `PropertyDefinitionDetail` and `PropertyCategory`;
  - a read-only `scope` in `PropertyCategory`, so the portal knows which categories it may change;
  - `ETag` on `getMetatype` and on the responses of the changed writes;
  - an optional `If-Match` and the response 412 on `updateMetatype`, `deleteMetatype`, `createPropertyMapping`, `deletePropertyMapping`, `updateMetatypeReimportPolicy`, `updatePropertyCategory`, `deletePropertyCategory`, `updatePropertyDefinition`, `deletePropertyDefinition`, `createEnumValue` and `deleteEnumValue`;
  - the problem type `stale-version`.
- `backend/`:
  - Flyway V18 with a `version` column on `metatypes`, `property_categories` and `property_definitions`;
  - version checks and increments in metatype editing, property definitions, mappings and the package import;
  - the 412 mapping in `ApiExceptionHandler`.
- `frontend/`:
  - the route `/metamodel` and `/metamodel/metatypes/:id`, and the header entry;
  - the page and its dialogs in `src/pages/` and `src/components/metamodel/`;
  - API functions in `src/api/metamodel.ts`;
  - messages in `en.json` and `de.json`;
  - the screen story `SCR-030-metamodel-configuration.stories.ts`.
- `docs/screens/SCREENS.md`: SCR-030 becomes `CA+WP` and `implemented`, the legacy SVGs are deleted, and the images are generated.
- `manual/`: "Editing the metamodel" in English and German, with walkthrough screenshots.
- `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (version attributes), the audit catalog (no new events), US-032 and REQ-032 (status and implementation), UC-04 (A5 refined) and the sprint plan.
