# Design

## Context

- **Configurations.** `metamodel_configurations` (V5) already has `scope` (`instance` or `solution`), `parent_id` (required for `solution`), `solution_id`, `edit_mode` (`gui-and-import` or `import-only`) and `last_modified_*`. A unique index keeps a single instance configuration, `00000000-0000-0000-0000-000000000001`. Nothing reads or enforces `edit_mode` yet.
- **Metatypes.** Every metatype has a `config_id`, and `UNIQUE (config_id, name)`. `MetatypeCatalog` lists only the instance configuration. `MetatypeEditing.validate` accepts only the instance configuration. `CatalogStore` already checks that a primary metatype belongs to the instance.
- **Other content.** Property definitions, categories, enum values and labels have no configuration; they are instance-wide.
- **Metamodel lock.** Every metamodel write starts with `MetamodelEffects.lock()`, which reads the instance configuration row `FOR UPDATE`. The package import takes the same lock.
- **Authorization.** Permissions come from the catalog of ADR-032, seeded in V3 (`permissions`, `role_permissions`). The contract declares them per operation in `x-oea-authorization`. Conditions that depend on the request are enforced by the handler and marked with `@ConditionsEnforcedByHandler`, together with a probe that the startup check runs.

## Goals / Non-Goals

**Goals:**

- Enforce the edit mode for the instance and for extensions, and switch it with the right permission and with an audit event.
- Create, list, change and delete extensions; maintain metatypes and mappings in them; read the effective metamodel.
- Add the permission `solution-metamodel:write` with its role grants.

**Non-Goals:**

- Solutions, entities in extensions, imports into extensions and a user interface for extensions.
- Stereotypes and constraint rules.

## Decisions

### D1 – Configurations as a resource

**Operations.** There are four new operations:

| Operation | Endpoint | Permissions | Answer |
|---|---|---|---|
| `listMetamodelConfigurations` | `GET /metamodel/configurations` | `entity:read`, `metamodel:write` or `solution-metamodel:write` | the instance configuration and all extensions |
| `createMetamodelConfiguration` | `POST /metamodel/configurations` | `solution-metamodel:write` | 201; creates an extension only |
| `updateMetamodelConfiguration` | `PUT /metamodel/configurations/{id}` | `metamodel:write` for the instance, `solution-metamodel:write` for an extension | the changed configuration |
| `deleteMetamodelConfiguration` | `DELETE /metamodel/configurations/{id}` | `solution-metamodel:write` | 204; 409 `in-use` while metatypes exist, and the instance cannot be deleted |

**Schema `MetamodelConfiguration`.** It has:

- `id` and `scope`;
- `parentId`, which is null for the instance;
- `solutionId`, a UUID without a foreign key until solutions exist;
- `name`, which is unique, compared case-insensitively;
- `editMode`;
- `version`, `lastModifiedAt` and `metatypeCount`.

`PUT` changes `name` and `editMode`; `version` is checked with `If-Match`, as in scr-030.

**Migration V20.** It adds `name` (instance: "Instance metamodel") and `version` to `metamodel_configurations`, together with a check that `solution_id` is set exactly for `scope=solution`.

**Audit.** The events are `metamodel.configuration-created`, `metamodel.configuration-updated` and `metamodel.configuration-deleted`. A change of the edit mode additionally gives `metamodel.edit-mode-changed`, with `configId`, `before` and `after`.

### D2 – One check for every content write

A new component `MetamodelConfigurations` offers `requireWritable(principal, configId)`. Every content write calls it after `MetamodelEffects.lock()`:

1. It loads the configuration (404 if it does not exist).
2. If the configuration is `import-only`, it throws `MetamodelChangeException` with the reason `LOCKED` (403 `metamodel-locked`). This comes before the permission check, because the lock applies "regardless of the permission".
3. It checks the permission: `metamodel:write` for the instance and `solution-metamodel:write` for an extension. Otherwise it throws `PermissionDeniedException` (403, audited like other denials).

The configuration of a write follows from its target:

- metatype writes, mappings and the re-import setting of a metatype: the `config_id` of the metatype, or `MetatypeWrite.configId` when creating;
- property definitions, enum values, categories and labels: the instance.

The re-import settings, which are configuration settings and not content, and `updateMetamodelConfiguration` do not check the edit mode. The package import never calls the check, so imports keep working in `import-only` (US-035 AC2).

**Contract.** The metatype and mapping operations change their `x-oea-authorization` to `anyOfPermissions: [metamodel:write, solution-metamodel:write]` with a condition ("the instance metamodel requires `metamodel:write`, an extension `solution-metamodel:write`"). The handler enforces it, marked with `@ConditionsEnforcedByHandler`. The probe uses the role `enterprise-architect`, which holds only `solution-metamodel:write`, on the instance. For operations with a path ID, a new attribute `probePath` names a concrete path; an unknown metatype counts as one of the instance, so the probe gets 403 and metamodel administrators get 404. The other content operations keep `metamodel:write`.

### D3 – Names and base types in extensions

`MetatypeEditing.validate` accepts the instance or an existing extension as `configId`. A metatype keeps its configuration; `PUT` with another `configId` gives 422.

**Names.** All comparisons ignore upper and lower case:

- A name in an extension must not exist in the instance or in the same extension.
- A name in the instance must not exist in the instance or in any extension. This also holds for the package import, which reports such a metatype as a conflict with organization content (409).

Different extensions may use the same name; each tries out its own types.

**Base types.**

- The base type of a metatype in an extension is a metatype of the instance or of the same extension.
- An instance metatype may only have an instance base type.
- The kind rules of us-036 still apply.

**Mappings.** A mapping on a metatype of an extension maps an existing organization property definition. The rules of property-mappings apply unchanged.

**Connection constraints.** They may name metatypes of the instance and of the same extension.

### D4 – Effective metamodel and isolation

- **Metatype lists.** `MetatypeCatalog.list(configId)` loads the metatypes of the instance and, for an extension, also those of the extension. Base type chains and inherited mappings cross the boundary, because an extension type may inherit from an instance type.
- **`configId` in the answer.** `MetatypeDetail.configId` names the configuration of each metatype.
- **Without `configId`.** The list contains only the instance metatypes.
- **Single reads.** `GET /metamodel/metatypes/{id}` also answers for metatypes of extensions.
- **Entities.** `MetatypeRules` loads only instance metatypes, so the batch and `createConnection` answer `METATYPE_NOT_FOUND` for metatypes of extensions until solutions exist.
- **Catalogs.** They already restrict the primary metatype to the instance.
- **Labels.** They stay instance-wide; extension metatypes are not part of the label bundle yet.

### D5 – The new permission

V20 inserts `solution-metamodel:write` into `permissions` (not admin-class, after `metamodel:write`) and grants it to `enterprise-architect` and `metamodel-admin`.

- **Contract.** The permission catalog and the built-in roles get it too, and `check_openapi_authorization.py` compares them.
- **Separation of duties.** SoD-2 lists only `metamodel:write`, so the rule stays unchanged; an extension cannot change visibility.
- **ADR-032** gets an amendment: the new row in the permission table and in the role matrix, with the date and the reason.

### D6 – SCR-030

- **Settings.** The section shows a switch "Lock metamodel (changes only through package import)" with an explanation, saved with `updateMetamodelConfiguration` and `If-Match`.
- **Store.** It loads the instance configuration with the lists.
- **Locked state.** While the metamodel is locked:
  - every section shows a notice with a link to the manual chapter on packages;
  - `canWrite` for content is false, so all creating, changing and deleting actions disappear;
  - the re-import settings and the switch stay.
- **Story.** A "Locked" story shows the notice.

## Risks / Trade-offs

- **Preparing extensions before solutions** leaves an API that nobody uses through the portal yet. The owner accepted this for S8. The `solutionId` without a foreign key is a deliberate gap, closed when the solutions table exists (S10).
- **Instance names blocked by extension names** may surprise an administrator. The 409 names the conflict, and extensions are rare until solutions exist.
- **Lock before permission:** a person without permission learns that the metamodel is locked. This is not sensitive; `GET /metamodel/configurations` shows it to readers anyway.
- **Labels for extension metatypes** do not exist yet; their names show in the corporate language. This follows with solutions.

## Migration Plan

V20 adds the columns with defaults and the permission rows. Existing instances stay `gui-and-import`, the default of V5. Clients that never change the edit mode see no difference. A rollback removes the columns and the permission, after removing the role grants and any custom role assignments of it.
