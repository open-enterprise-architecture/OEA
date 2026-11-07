# Proposal

## Why

Properties such as investment costs or security classifications must not be visible to every reader of an entity ([UC-21](../../../requirements/use-cases/UC-21-configure-property-visibility.md)).

- **Today:** every property is `public`. The catalog leaves out other properties, and writing them is rejected, so nothing leaks. But nobody can restrict a property, and the planned operation `updatePropertyVisibility` answers 501.
- **This change:** security administration can configure the visibility of each property definition. The rules that enforce it on reads follow in the next changes.

This is S10 change 1 of the cut of 2026-11-07 (see below).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-21: Configure Property Visibility](../../../requirements/use-cases/UC-21-configure-property-visibility.md) |
| User Stories | [US-124: Configure the visibility mode](../../../requirements/user-stories/US-124-configure-property-visibilitymode.md), [US-125: Visibility configuration form](../../../requirements/user-stories/US-125-property-visibility-configuration-form.md) |
| Requirements | [REQ-124: visibilityMode of a property definition](../../../requirements/req/REQ-124-propertydefinition-visibilitymode.md), [REQ-125: Visibility configuration form](../../../requirements/req/REQ-125-property-visibility-configuration-form.md), [REQ-161: Governance of the visibility configuration](../../../requirements/req/REQ-161-visibility-configuration-governance.md) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (§5, SoD-2, SoD-6), [ADR-013: API Style](../../../adrs/ADR-013-api-style.md) (spec-first), [ADR-031: UI prototyping with Storybook](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S10 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-07:**

- **The cut of S10 into five changes:**
  1. this change: the visibility configuration (US-124, US-125);
  2. enforcement of `role-restricted` in the catalog, the portal and the batch write, including the catalog columns of [#68](https://github.com/open-enterprise-architecture/OEA/issues/68) (US-126);
  3. `connection-scoped`, including the link of a person account to the Person entity of the EA model (US-127);
  4. the override role list (US-128);
  5. creating a solution in project mode (US-038). Its plateau fields (AC3, AC4) follow with the plateaus in S14, and US-039 moves to S11 with the deltas.
- **Own page SCR-032 "Property visibility":**
  - Visibility can only be changed with `visibility:configure` (role `security-admin`).
  - This role has no access to the metamodel configuration SCR-030 (SoD-2).
  - So visibility is edited on its own page, and SCR-030 only shows it.

**Contract (`api/openapi.yaml`):**

- `getPropertyVisibility` and `updatePropertyVisibility` are implemented (`x-oea-status: planned` is removed).
  - The GET lists the visibility of every property definition that is not retired.
  - The PUT changes the listed entries in one transaction. An invalid entry answers 422 and changes nothing.
- The validation rules of REQ-124:
  - `role-restricted` needs at least one read role, and the write roles must be among the read roles;
  - `connection-scoped` needs a connection metatype;
  - the roles must exist, and the fields of the other modes stay empty.
- `listMetatypes` also accepts `visibility:configure`, so that the page can offer the connection metatypes.
- The metamodel endpoints return `visibility` of a property definition read-only, as the contract already declares.

**Backend:**

- Migration `property_visibility_roles` for the read and write roles (data model as designed).
- Audit event `visibility.changed` per changed property. A change that grants the acting person a role they hold is flagged as self-benefiting (SoD-6, REQ-161 AC3).
- **Scoping connections (REQ-161):** as soon as a connection metatype is used as the scoping type of a property, `createConnection` and `batchWriteEntities` write connections of that type only with `visibility-scope:assign`, otherwise 403. Today this condition is marked as not reachable.
- A connection metatype that is used as a scoping type cannot be deleted (409 `in-use`).

**Frontend:**

- SCR-032 "Property visibility" (US-124, US-125), linked in the header for holders of `visibility:configure`. It shows:
  - the list of properties with their mode and a badge for restricted ones, and a name filter;
  - an edit dialog with the mode, the read and write roles or the connection metatype, and inline validation before the request.
- SCR-030 shows a "Restricted" badge in the property list and the visibility of the property read-only (US-125 AC1, REQ-125).
- A story prototype of SCR-032 replaces the legacy mockup (ADR-031).

**Manual:** a page "Property visibility" under administration, in English and German.

**Realigned sections:**

- US-124 and REQ-125 place the form in the metamodel editor; it moves to SCR-032 (owner decision).
- US-124 AC1–AC3 speak of Kurt; under SoD-2 the person holding `security-admin` configures.
- `PropertyVisibilityEntry` gets the property's metatype names for the list.

## Out of Scope

- Enforcing the modes on reads and writes:
  - `role-restricted` follows in change 2;
  - `connection-scoped` follows in change 3.
  - Until then the catalog shows only `public` properties, and writing a value of another property stays rejected, as today.
- The override role list and its operations (US-128, change 4).
- The account link to the Person entity (`PersonEntityLink`, change 3).

## Capabilities

### New Capabilities

- `metamodel/property-visibility`: the visibility configuration of property definitions: modes, read and write roles, scoping type, governance, audit, and the page SCR-032.

### Modified Capabilities

- `repository/connection-write`: connections of a scoping type need `visibility-scope:assign`.
- `metamodel/configuration-screen`: the badge and the read-only visibility of a property.
- `metamodel/metatype-editing`: a connection metatype used as a scoping type cannot be deleted.

## Impact

- `api/openapi.yaml`:
  - the two visibility operations;
  - `listMetatypes` authorization;
  - `PropertyVisibilityEntry`.
- `backend/`:
  - migration `V23__property_visibility_roles.sql`;
  - `io.oea.app.metamodel` (visibility service, audit);
  - the admin controller;
  - the condition check in `EntityBatch` and `createConnection`;
  - the metamodel mapping.
- `frontend/`:
  - the page and story SCR-032 and its API client;
  - the badge and section in SCR-030;
  - the header link.
- `manual/`: the visibility page, in English and German.
- Documentation:
  - REQ-124, REQ-125, REQ-161, US-124, US-125;
  - `docs/audit-event-catalog.md`, `docs/data-model.puml`, `docs/screens/SCREENS.md`;
  - the sprint plan, with the S10 cut.
