# Proposal

## Why

A `connection-scoped` property such as a security classification can be configured since S10 change 1, but nobody can read or write it ([US-127](../../../requirements/user-stories/US-127-connection-scoped-property-domain-architect.md)): OEA does not know which element of the EA model a logged-in person is, so it cannot follow the scoping connection from the person to an entity. This change links person accounts to their element and enforces `connection-scoped` on the channels of change 2. It is S10 change 3 and closes [#68](https://github.com/open-enterprise-architecture/OEA/issues/68).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-21: Configure Property Visibility](../../../requirements/use-cases/UC-21-configure-property-visibility.md) |
| User Stories | [US-127: Connection-scoped property for the domain architect](../../../requirements/user-stories/US-127-connection-scoped-property-domain-architect.md) |
| Requirements | [REQ-127: Enforcement of connection-scoped visibility](../../../requirements/req/REQ-127-property-visibility-enforcement-connection-scoped.md), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-162: Write protection on all write paths](../../../requirements/req/REQ-162-property-write-protection-all-paths.md), [REQ-161: Governance](../../../requirements/req/REQ-161-visibility-configuration-governance.md) |
| Issues | [#68: role-restricted and connection-scoped properties in catalog rows](https://github.com/open-enterprise-architecture/OEA/issues/68) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (SoD-1, DM-7 account link), [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-013: API Style](../../../adrs/ADR-013-api-style.md) |
| Sprint / Milestone | S10 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-10:**

- **Who links:**
  - `setPersonEntityLink` needs `person:manage`, as the contract plans (roles `security-admin` and `system-admin`).
  - An own account is rejected with 403 (SoD-1).
- **Which elements:**
  - Only entities of a configured *person metatype* (or one of its subtypes) can be linked; others answer 422.
  - The instance has no person metatype until security administration sets one.

**Person metatype (assumption, open for review):**

- **Where:** a new operation `PUT /admin/property-visibility/person-metatype` with `visibility:configure`, shown on SCR-032 as "Entity type of persons".
- **Why there:** choosing which elements anchor connection-scoped visibility is a decision of security administration, like the scoping type; the generic system settings are still planned.
- **Governance:** a change is audited (`visibility.person-metatype-changed`). Links to entities of another metatype stop counting, but stay stored.

**Account link (`setPersonEntityLink`, planned → implemented):**

- **Rules:**
  - sets or removes the link from a person account to an active element of the person metatype, audited as `person.account-linked`;
  - an element can be linked to one account only (409);
  - a service account is rejected with 422;
  - a deleted element ends the link's effect.
- **Visibility:** the person's link is shown in the person administration API (`PersonEntityLinkResult`).

**Reading `connection-scoped` (REQ-127, US-127 AC1–AC3):**

- **Rule:** a value is readable for a person if their linked element is connected to the value's entity by a connection of the property's scoping type, in either direction, depth 1.
- **Without a link, or for the system admin account:** nothing is readable.
- **Catalog and portal, per row:**
  - the column is readable when the caller has a link;
  - each row shows the value only if its entity is in scope, else the cell is empty;
  - sort and filter treat out-of-scope values as empty, so the result never depends on a hidden value (REQ-160, REQ-127 AC4);
  - without a link the column stays as in change 2: `readable: false`, no sort, no filter.

**Writing `connection-scoped` (REQ-162):**

- a value can be written or removed only for an entity in the writer's scope; otherwise the whole batch or connection is rejected with 403, as for `role-restricted`;
- a new entity has no scoping connection yet, so its connection-scoped value is written after the connection exists;
- `RESTRICTED_PROPERTY_NOT_SUPPORTED` disappears.

**Audit values:** connection-scoped values are encrypted like those of change 2; nothing changes there.

**Performance (US-127 AC5):** `CatalogQueryLatencyIT` gets a scenario with a connection-scoped column for a person with 500 scoped entities. Its 95th percentile must stay within 200 ms above the same query without the column.

**Manual:** the property visibility page (person metatype, account link, connection-based enforcement) and the catalogs page, in English and German.

**Realigned sections:**

- **US-127 AC4 (cache with a TTL of 60 s):** dropped. The scope is evaluated in the query itself, so a new or removed scoping connection applies at once.
- **REQ-127 "via their Person entity":** this is the linked element of the configured person metatype.

## Out of Scope

- Deeper traversal (domain → sub-domain → entity).
- A user interface for person administration (the account link is set through the API).
- The override role list (change 4).

## Capabilities

### New Capabilities

- `identity/person-entity-link`: the account link of a person to the element of the person metatype, and the configured person metatype.

### Modified Capabilities

- `catalog/catalog-query`: rows of a connection-scoped column show the value only in scope; sort and filter treat out-of-scope values as empty.
- `catalog/catalog-filters`: filters on a connection-scoped column consider only rows in scope.
- `portal/portal-catalogs`: the same as the catalog query.
- `repository/entity-write`: writing connection-scoped values in scope.
- `metamodel/property-visibility`: the person metatype on SCR-032.

## Impact

- **`api/openapi.yaml`:**
  - `setPersonEntityLink` is implemented;
  - new `getPersonMetatype` and `setPersonMetatype` under `/admin/property-visibility/person-metatype`;
  - `Person` shows the link (`entityId`);
  - descriptions of the catalog and write operations.
- **`backend/`:**
  - migration `V24` (`persons.entity_id` with a unique index, `system_settings.person_metatype_id`);
  - `PropertyAccess` with the scope anchor;
  - the scope predicate in `CatalogQueryStore`;
  - `EntityBatch` checks the scope;
  - the link and the person metatype services and their audit;
  - `CatalogQueryLatencyIT`.
- **`frontend/`:** the person metatype selector on SCR-032; the catalog needs no change, since a scoped column is readable and empty cells look as before.
- **`manual/`**, in English and German.
- **Documentation:**
  - US-127, REQ-127, REQ-160, REQ-162;
  - `docs/data-model.puml` and `business-objects/person.md`;
  - the audit event catalog;
  - the sprint plan;
  - #68 closed by the implementation PR.
