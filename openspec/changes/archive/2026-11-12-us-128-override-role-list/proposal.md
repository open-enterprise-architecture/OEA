# Proposal

## Why

Since S10 changes 2 and 3, restricted properties are hidden from everyone without a read role or scope, administrators included (owner decision Q14). Exceptional work such as error analysis or a data migration then needs every property's read roles changed. [US-128](../../../requirements/user-stories/US-128-admin-override-property-visibility.md) adds an explicit, audited exception instead: the override role list. Organizations also need their own roles for it and for read roles (`iso`, `migration-support`), which OEA cannot create yet ([UC-23](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) A5). This change delivers both. It is S10 change 4.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-21: Configure Property Visibility](../../../requirements/use-cases/UC-21-configure-property-visibility.md), [UC-23: Manage Roles and Role Assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A5) |
| User Stories | [US-128: Explicit, audited override for restricted properties](../../../requirements/user-stories/US-128-admin-override-property-visibility.md), [US-151: Define a custom authorization role](../../../requirements/user-stories/US-151-define-custom-authorization-role.md) (new) |
| Requirements | [REQ-128: Admin override for property visibility](../../../requirements/req/REQ-128-property-visibility-admin-override.md), [REQ-161: Governance](../../../requirements/req/REQ-161-visibility-configuration-governance.md), [REQ-157: Permission model](../../../requirements/req/REQ-157-permission-model.md), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (§4 override, SoD-2, SoD-4, SoD-6), [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S10 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-11:**

- **Custom roles in this change:** `createAuthorizationRole` is implemented, with the API and an own page that lists the roles and creates one in a dialog.
- **Audit of override reads:** one `property-override-access` event per catalog or portal query that shows values only thanks to the override, with the properties and entity IDs in its details. No event when read roles or the scope grant the value anyway.
- **Override list on an own page:** the override role list gets a page of its own, next to SCR-032.

**Override role list (`getPropertyVisibilityOverride`, `updatePropertyVisibilityOverride`, planned → implemented):**

- **Who:** only with `visibility:configure` (security administration); metamodel administrators get 403 (REQ-128 AC5).
- **What:** up to 20 existing roles (built-in or custom); an unknown role answers 422, a repeated role 400. Empty by default (AC6).
- **Storage:** the existing column `authorization_roles.overrides_property_visibility`; `AuthorizationRole.overridesPropertyVisibility` shows it.
- **Governance:** a change writes `visibility.override-changed` with the old and new list, flagged `selfBenefiting` when it adds a role the actor holds (SoD-6). An unchanged list writes nothing.
- **Effect:** at once, without a cache (US-128 AC3 asks for 60 s at most).

**Reading with the override (REQ-128 AC1, AC4, AC7):**

- A principal holding an override role reads every property of the rows it can already read, in catalog and portal queries, including join results: `role-restricted` without a read role and `connection-scoped` outside the scope or without an account link. Sort and filter follow, since the column is readable.
- The override grants no object access: queries still need `entity:read` or `portal:read`.
- A role not on the list grants nothing beyond its read roles (no hierarchy).
- Writes are unchanged; the override is read-only.

**Audit of override reads (REQ-128 AC2):**

- One `property-override-access` event per query (page) that returns at least one value readable only through the override.
- Details: the properties and, per property, the IDs of the entities whose value was returned through the override (rows and join results of the page).
- Values readable through a read role or the scope do not count; a page without such values writes no event.

**Custom roles (`createAuthorizationRole`, planned → implemented; US-151):**

- With `role:assign` (`security-admin`, `system-admin`).
- ID (`^[a-z][a-z0-9-]{1,62}$`), name, optional description, an explicit permission list (possibly empty, at most 50) and `assignableToServiceAccounts`.
- Rejected:
  - an ID that is taken, built-in or custom: 409;
  - an unknown permission: 422;
  - `metamodel:write` with `visibility:configure` or `visibility-scope:assign` (SoD-2): 422;
  - an admin-class permission in a role assignable to service accounts (SoD-4): 422.
- Audited as `role.created`, with high severity for a privileged role.
- Four-eyes approval (`202`) is not part of this change; it follows with US-150, like for assignments today.

**Frontend:**

- **New page "Authorization roles" (SCR-039)** for `role:assign`: built-in and custom roles with their permissions and the markers built-in, privileged and override; a dialog "New role".
- **New page "Override roles" (SCR-040)** for `visibility:configure`: the list as a multi-select of all roles, a warning that every read through the override is audited, and the hint when the list is empty.
- Links to both pages in the header, shown by permission, like SCR-032.

**Manual:** a new page on authorization roles, the override section on the property visibility page, in English and German.

**Realigned sections:**

- **US-128 AC1 and REQ-128 AC1 "retrieves an entity":** the entity detail does not exist yet; the override applies to the existing read channels, the catalog and portal query. The entity detail follows the same rule later (as for US-126 and US-127).
- **US-128 AC2 and REQ-128 AC2 "an entry with user ID, entity ID and property name":** one event per query with all properties and entity IDs (owner decision).
- **REQ-128 AC8 (raw analytics):** raw analytics do not exist yet; nothing to implement.
- **US-151 is new** (UC-23 A5 had no story).

## Out of Scope

- Changing and deleting custom roles (`updateAuthorizationRole`, `deleteAuthorizationRole`) and changing role permissions.
- Four-eyes approval (US-150).
- A user interface for role assignments.
- The entity detail and raw analytics.

## Capabilities

### New Capabilities

- `identity/authorization-roles`: custom authorization roles, their validation and the page "Authorization roles".
- `metamodel/visibility-override`: the override role list, its governance and audit, the audit of reads through the override, and the page "Override roles".

### Modified Capabilities

- `catalog/catalog-query`: a column is readable for a holder of an override role ("Visibility of the query result").
- `portal/portal-catalogs`: the same in the portal ("Visibility in the portal").

## Impact

- **`api/openapi.yaml`:** `createAuthorizationRole`, `getPropertyVisibilityOverride` and `updatePropertyVisibilityOverride` implemented; descriptions of `queryCatalog` and `queryPortalCatalog` name the override.
- **`backend/`:**
  - no migration (the tables exist since V3);
  - `AuthorizationRoles` (create, validation, audit);
  - `VisibilityOverride` (list, audit);
  - `PropertyAccess` with the override;
  - `CatalogQueries` writes `property-override-access`;
  - Cucumber features.
- **`frontend/`:** pages SCR-039 and SCR-040 with stories and images, API clients, header links, i18n in English and German.
- **`manual/`**, in English and German.
- **Documentation:**
  - US-128, US-151, REQ-128, REQ-157, REQ-160, REQ-161;
  - UC-23 (A5 story link);
  - the audit event catalog (`role.created`, `visibility.override-changed`, `property-override-access`);
  - `docs/data-model.puml` and `business-objects/authorization-role.md` (notes only);
  - `docs/screens/SCREENS.md` (SCR-039, SCR-040);
  - the sprint plan.
