# Design

## Context

See proposal.md – Why. The current state:

- **Tables:** `authorization_roles` (with `overrides_property_visibility`, false for every built-in role) and `role_permissions` (with `approval_status`) exist since V3. Only the nine built-in roles exist; `AccessCatalog` reads both tables for `listAuthorizationRoles`, `getAuthorizationRole` and `listPermissions`, which are implemented.
- **Reading restricted properties:** `PropertyAccess.Snapshot` decides per request:
  - read roles for `role-restricted`;
  - the anchor and scope for `connection-scoped` (change 3).
  
  `CatalogQueries.readable` turns a column the caller cannot read into a redacted one, and wraps a scoped column in its scope. The portal uses the same code (`Channel`).
- **Effective roles:** `PermissionResolver.roles(principal)` returns them, with local assignments and IdP group mappings, without a cache.
- **Audit:** events are written through the audit writer of ADR-033 in the request's transaction; `visibility.changed` already carries `selfBenefiting` (change 1).

## Goals / Non-Goals

**Goals:**

- The override list and the custom roles reuse the existing tables; no migration.
- The override changes only the read decision of `PropertyAccess`, so the catalog and the portal follow without their own rule.
- The override audit costs at most one insert per query and one scope lookup per scoped column and page.

**Non-Goals:**

- No cache of the override list or of effective roles.
- No change of the write decision.

## Decisions

### D1 – Override list in `authorization_roles.overrides_property_visibility`

- **Read:** `GET` returns the IDs with the flag set, sorted.
- **Write:** `PUT` replaces the list in one transaction:
  1. lock the roles (`SELECT … FOR UPDATE`);
  2. check that every ID exists (else 422 `UNKNOWN_ROLE` naming the IDs);
  3. set the flag to `id = ANY(?)` for all roles;
  4. write `visibility.override-changed` if the set changed.
- **Validation:** the contract checks `maxItems: 20` and `uniqueItems` (400).
- **Self-benefit:** `selfBenefiting` is true if an added role is among the actor's effective roles (`PermissionResolver.roles`).
- **New service** `VisibilityOverride` in `app/metamodel`, next to `PropertyVisibility` and `PersonMetatypes`; the controller is `AdminController`.
- **Alternative considered:** a separate table `visibility_override_roles`. Rejected, because the column exists, is documented in the data model and is already returned as `overridesPropertyVisibility`.

### D2 – The override in `PropertyAccess`

- **Snapshot:** `Snapshot` gets `override`: true if an effective role of the principal has the flag. `PropertyAccess.of(principal)` reads the flagged IDs in the same query as the visibilities.
- **Reading:**
  - `readable(id)` is true with `override`;
  - `scope(id)` stays as before, so the catalog knows which rows the scope covers;
  - new `throughOverride(id)` is true if the property is readable only through the override: `override` is set, the mode is not `public`, the read roles do not grant it, and for `connection-scoped` no anchor exists. A scoped column with an anchor is decided per row (D3).
- **Writing:** `writable` and `writableFor` are unchanged.
- **`of(Set<String> roles)`** (system writes, tests) has no override.

### D3 – Catalog and portal

**Column handling** in `CatalogQueries.readable`:

- **Overriding caller, `connection-scoped` column:** the column is returned unwrapped, so it shows every value and sorts and filters on all values. It is marked `overridden`, with the scope if the caller has an anchor.
- **Overriding caller, `role-restricted` column without a read role:** the column is marked `overridden`.

**After the store returned the page,** `OverrideReads` collects per marked column (primary columns and join columns):

- the entity IDs of rows or join results with a non-empty value;
- for a column with a scope, minus the entities in scope. `ScopeStore.inScope(anchor, type, ids)` gets an overload for a set of IDs, one query per column and page.

**Event:**

- If anything remains, one `property-override-access` event is written:
  - target `catalog`, the catalog ID;
  - details: `channel` (`catalog`/`portal`), `page`, and `properties`, a list of `{propertyDefId, property, entityIds}`.
- A query that answers 400 writes nothing.
- The event is written in a separate transaction after the query (`REQUIRES_NEW`), because the catalog query itself runs read-only.

**Alternatives considered:**

- One event per entity and property (REQ-128 AC2 as written). The owner chose one per query.
- Computing the scope flag in SQL as an extra column. Rejected: it widens every scoped query for a rare case, while one lookup per page is cheap.

### D4 – Custom roles (`AuthorizationRoles`)

- **New service** `app/access/AuthorizationRoles.create(principal, request)`:
  1. **ID:** `INSERT … ON CONFLICT DO NOTHING`; no row means 409 `ROLE_EXISTS`.
  2. **Permissions:** validated against `AccessCatalog`:
     - unknown: 422 `UNKNOWN_PERMISSION`;
     - `metamodel:write` with `visibility:configure` or `visibility-scope:assign`: 422 `SOD_VIOLATION` (SoD-2);
     - an admin-class permission with `assignableToServiceAccounts`: 422 `SERVICE_ACCOUNT_ROLE_PRIVILEGED` (SoD-4).
  3. **Storage:** rows in `role_permissions` with `approval_status = 'approved'`; `created_by_person_id` or `created_by_admin_id` from the principal.
  4. **Audit:** `role.created`, category `security`, severity `high` for a privileged role, otherwise `info`; target `authorization-role` with the ID; details `name`, `permissions`, `assignableToServiceAccounts` and `privileged`.
- **Response:** 201 with the role as `getAuthorizationRole` returns it, and `Location`.
- **Validation:** the ID pattern, the name length, `maxItems: 50` and `uniqueItems` are checked by the contract (400).
- **Four-eyes:** `privilegedAssignmentApproval` is not evaluated, as for assignments today; US-150 adds the 202 path.
- **Alternative considered:** reusing `RoleAssignments`. Rejected, because roles and assignments are separate business objects with different audit events.

### D5 – Frontend pages

- **SCR-039 "Authorization roles"** (`/admin/roles`, `AuthorizationRolesPage.vue`), for `role:assign`:
  - a `UTable` with ID, name, permissions (badges) and the markers built-in, privileged and override;
  - a `UModal` "New role" with:
    - ID, name and description;
    - a multi-select of `listPermissions`, with admin-class permissions marked;
    - "assignable to service accounts".
  - A rejection (400/409/422) is shown in the dialog with the problem's detail. A success reloads the list.
- **SCR-040 "Override roles"** (`/admin/override-roles`, `OverrideRolesPage.vue`), for `visibility:configure`:
  - a `USelectMenu` (multiple) of all roles, with a save button;
  - a warning `UAlert` that every read through the override is audited, and an info alert while the list is empty;
  - a rejected save restores the previous list and shows the error.
- **Navigation:** links in `AppHeader.vue` by permission, like SCR-032. SCR-032 gets a link to SCR-040 in its enforcement note.
- **API clients:** `src/api/roles.ts` and the override functions in `src/api/visibility.ts`.
- **i18n:** keys in `en.json` and `de.json`.
- **Screens:** stories `SCR-039-authorization-roles.stories.ts` and `SCR-040-override-roles.stories.ts` with play functions, images rendered, and entries in `SCREENS.md` as `implemented`.

## API (delta to `api/openapi.yaml`)

- **`createAuthorizationRole`:** `x-oea-status: planned` is removed, and the description names the 409/422 cases and that `202` follows with US-150. The 501 response is removed.
- **`getPropertyVisibilityOverride`, `updatePropertyVisibilityOverride`:** implemented (501 removed); `422` names `UNKNOWN_ROLE`.
- **`queryCatalog`, `queryPortalCatalog`:** a sentence on the override and its audit.
- **`AuthorizationRole.overridesPropertyVisibility`:** unchanged.

## Data model

- **No schema change.**
- **`docs/data-model.puml`:**
  - the note on `overrides_property_visibility` names the operations of D1;
  - custom roles are named at `authorization_roles` (`is_builtin = false`, created through `createAuthorizationRole`).
- **`business-objects/authorization-role.md`:** custom roles and the override list as implemented.

## Risks / Trade-offs

- **[A large page with many overridden values makes a large event]** → the page size is at most 200 (catalog limit), so an event names at most 200 entities per property and join results of that page; acceptable.
- **[The override event is written outside the query transaction; a crash in between loses it]** → it is written before the response is returned, and a failure to write it fails the request with 500, so no value leaves without its event.
- **[Custom roles cannot be changed or deleted yet]** → documented in the manual; the planned operations stay planned.
- **[No four-eyes approval for privileged custom roles]** → high-severity audit, as for privileged assignments until US-150.

## Migration Plan

No migration. Deploying the change keeps the override list empty (Q14); rolling back leaves created custom roles in the tables, where the previous version lists them and allows their assignment.
