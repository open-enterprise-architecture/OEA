# Design

## Context

- **Schema:**
  - `property_definitions` already has `visibility_mode` (`public`, `role-restricted` or `connection-scoped`, default `public`) and `scoping_connection_type_id` (V5).
  - A check ties the column to the mode.
  - The read and write roles have no table yet: `property_visibility_roles` exists only in `docs/data-model.puml`.
- **Contract:**
  - `getPropertyVisibility` and `updatePropertyVisibility` (`/admin/property-visibility`, `visibility:configure`) are planned.
  - `PropertyVisibilityEntry` holds `propertyDefId`, `propertyName`, `label` and the `PropertyVisibility` fields.
  - `PropertyDefinition.visibility` is declared read-only, but the metamodel endpoints do not fill it yet.
- **Fail-closed today:**
  - The catalog query shows only `public` properties (`CatalogColumns`).
  - The entity write rejects every value of a property that is not `public` ("Restricted properties not writable yet", `EntityValidator`).
  - So a configured restriction cannot leak values before its enforcement exists.
- **Scoping connections:**
  - `createConnection` and `batchWriteEntities` declare the condition "a connection of a type used as scopingConnectionType is written" → `visibility-scope:assign`.
  - Both handlers mark it with `@ConditionsNotReachable`, because no type can be a scoping type yet. This change makes it reachable.
- **Roles:**
  - `security-admin` holds `visibility:configure`, `visibility-scope:assign`, `role:assign` and `person:manage`, but neither `entity:read` nor `metamodel:write`.
  - It can read the roles (`listAuthorizationRoles`), but not the metatypes.
- **Frontend:**
  - SCR-030 (`MetamodelPage`) lists metatypes and properties for metamodel editors.
  - There is no admin page for security administration yet.
  - SCR-032 is a legacy SVG mockup.

## Goals / Non-Goals

**Goals:**

- The visibility configuration as data, with the validation of REQ-124, governed by `visibility:configure` and audited.
- Scoping connections need `visibility-scope:assign` as soon as a scoping type exists.
- SCR-032 for security administration, and the visibility visible read-only in SCR-030.

**Non-Goals:**

- Enforcing the modes on read channels or opening restricted properties for writing (changes 2 and 3).
- The override role list (change 4) and the account link to a Person entity (change 3).

## Architecture Constraints

- **ADR-032:**
  - §5: role lists hold authorization role IDs.
  - SoD-2: `metamodel:write` never changes visibility.
  - SoD-6: self-benefiting changes are flagged.
  - Conditions of `x-oea-authorization`: enforced by the handler and marked `@ConditionsEnforcedByHandler`.
- **ADR-033:** audit events through `AuditWriter` in the business transaction.
- **ADR-028:**
  - service `io.oea.app.metamodel.PropertyVisibility`;
  - SQL in the service or a store, as `PropertyDefinitions` does;
  - controller in `io.oea.api.admin`.
- **ADR-031:** the screen is a Storybook story first; CI generates its images.
- **ADR-013:** the contract changes first.

## Decisions

### D1 Storage

- **Migration:** `V23__property_visibility_roles.sql` creates `property_visibility_roles`:
  - columns `property_def_id`, `role_id`, `access`, with `access` either `read` or `write`;
  - primary key over all three;
  - foreign keys to `property_definitions` and `authorization_roles`;
  - owned by `oea_owner`, with `SELECT`, `INSERT` and `DELETE` for `oea_app`.
- **Write roles:** the rule "every write role is a read role" is checked by the service, not by the database.
- **Mode and roles:** mode and scoping type stay in `property_definitions`. A change replaces the roles of the property: delete, then insert.

### D2 Operations

- **`getPropertyVisibility`:** every property definition that is not retired, ordered by name, with:
  - `propertyName` and `label` (session locale, as in the metamodel);
  - `metatypes`: the names of the metatypes that map the property today;
  - the visibility.
- **`updatePropertyVisibility`:** accepts a list of entries and changes exactly these properties in one transaction.
  - **Validation order per entry:**
    1. the property exists and is not retired (else `[i].propertyDefId`);
    2. the mode rules of REQ-124 (`[i].readRoles`, `[i].writeRoles`, `[i].scopingConnectionType`);
    3. roles exist (`[i].readRoles`).
  - **Errors:** 422 `validation-failed` with all `invalidFields`. A property listed twice is invalid (`[i].propertyDefId`).
  - **Changed property:** its `version` grows by one (`MetamodelVersions`), so that a stale metamodel write is noticed. An entry without a change leaves version and audit untouched.
  - **Answer:** the changed entries in their new state.
- **No `If-Match`:** the contract has no version per entry. The page always sends the state it read, and the last write wins (risk below).
- **Metamodel endpoints:** they fill `PropertyDefinition.visibility`, and `updatePropertyDefinition` ignores it, as the contract says.
- **`listMetatypes`:** `x-oea-authorization` gains `visibility:configure` in `anyOfPermissions`, so that SCR-032 can offer the connection metatypes. The list holds no entity data.
- **Scope:** every property definition can be restricted, including built-in, imported and solution properties. Visibility is not part of the metamodel and stays outside package imports.

### D3 Audit

- **Event:** `visibility.changed`, one per changed property: category `SECURITY`, severity `WARNING`, target `property-definition`.
- **Details:**
  - `property`;
  - `oldMode` and `newMode`;
  - `oldReadRoles`, `newReadRoles`, `oldWriteRoles` and `newWriteRoles`;
  - `oldScopingType` and `newScopingType`;
  - `selfBenefiting`.
- **`selfBenefiting`:** true if a read or write role is added that the acting person holds (effective roles of the session).

### D4 Scoping connections

- **Rule:** a connection metatype is a scoping type while a property definition names it in `scoping_connection_type_id`.
- **Check:** `createConnection` and `batchWriteEntities` check the metatypes of the connections they write.
  - If one of them is a scoping type and the principal lacks `visibility-scope:assign`, the request is answered with 403 through `PermissionDeniedException`, which audits the denial.
  - The batch is rejected as a whole, before any item is written (REQ-161 AC4 allows the atomic answer).
- **Markers:** the handlers change from `@ConditionsNotReachable` to `@ConditionsEnforcedByHandler`, naming the check.
- **Deletion:** deleting a connection metatype that is a scoping type answers 409 `in-use`. This follows the existing in-use checks of `MetatypeEditing`, with `count` as the number of properties.

### D5 Frontend

- **Page SCR-032:** `PropertyVisibilityPage` at `/admin/property-visibility`, linked in `AppHeader` for `visibility:configure`.
  - **Table:** name, label, metatypes, mode (with the badge "Restricted" when not `public`) and roles; a name filter.
  - **Edit dialog:**
    - the mode as a radio group;
    - read and write roles as multi-selects from `listAuthorizationRoles`, shown only for "Role-based";
    - the connection metatype from `listMetatypes` filtered by `isConnection`, shown only for "Connection-based".
  - **Inline validation:** the rules of D2, without a request. A 422 maps its `invalidFields` to the fields.
  - **Hidden fields:** the dialog keeps its height when fields are hidden (US-125 AC2).
- **SCR-030:**
  - the property list shows the badge;
  - the property detail shows a read-only block "Visibility" with mode, roles and scoping type, and the hint "Changed by security administration on the page Property visibility".
- **Story:**
  - `SCR-032-property-visibility.stories.ts` with list, dialog (each mode) and the inline error;
  - `SCREENS.md` moves SCR-032 from `legacy-mockup` to `implemented`, and the legacy SVGs are removed.
- **Translations:** labels in English and German.

### D6 Realignment of requirements

- **US-124 AC1–AC3 and REQ-125:**
  - they name the persona Kurt and the metamodel editor;
  - with SoD-2 and the owner decision, the security administrator configures on SCR-032, and SCR-030 shows the visibility read-only.
  - The ACs keep their content, only the screen and the role change.
- **US-125 AC2 and AC3:** realized on SCR-032.

## Data Model

- **New table:** `property_visibility_roles` as already drawn in `docs/data-model.puml`. The note gains "V23, us-124-property-visibility".
- **`business-objects/metamodel-configuration.md`:** already describes `visibilityMode`, `readRoles` and `writeRoles`. It gets the reference to the migration.

## API

Delta to `api/openapi.yaml`:

- **`getPropertyVisibility` and `updatePropertyVisibility`:**
  - `x-oea-status: planned` removed;
  - descriptions with the rules of D2;
  - 422 with `invalidFields`.
- **`PropertyVisibilityEntry`:** a read-only `metatypes` (array of metatype names).
- **`listMetatypes`:** `visibility:configure` in `anyOfPermissions`, and the description says why.
- **`createConnection` and `batchWriteEntities`:** the 403 description names the scoping condition.
- **`deleteMetatype`:** the 409 `in-use` description names the scoping type.

## Risks / Trade-offs

- **Last write wins on the visibility list.** Two security administrators editing at once can overwrite each other. → Both changes are audited with old and new values, and the property version grows. `If-Match` per entry can follow if needed.
- **A restricted property is hidden in the catalog and not writable until change 2.** → This is the current behavior for non-public properties, so nothing is opened too early. The page says that enforcement for reading and writing follows.
- **`visibility:configure` can list metatypes.** → The list holds metamodel names only, no entity data. SoD-2 is about changing the metamodel, not reading it.
