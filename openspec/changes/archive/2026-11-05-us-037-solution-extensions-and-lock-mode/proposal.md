# Proposal

## Why

Two needs of UC-04 rest on one mechanism, the edit mode of a metamodel configuration.

- **Lock mode.** An organization that manages its metamodel from a versioned repository wants to prevent manual changes in the instance. US-035 and REQ-035 call for an `import-only` mode in which only package imports change the metamodel.
- **Solution extensions.** A solution architect wants to try out new entity types for one initiative without touching that locked instance metamodel. US-037 and REQ-037 call for an extension configuration per solution with its own edit mode, which may only add types.

Solutions themselves follow from S10. This change prepares extensions in the metamodel and the API, so that S10 only has to connect them to solutions. This is S8 change 2 of UC-04.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure Metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) |
| User Stories | [US-035: Metamodel Lock Mode](../../../requirements/user-stories/US-035-metamodel-lock-mode.md), [US-037: Try Out an Architecture Metamodel](../../../requirements/user-stories/US-037-try-out-architecture-metamodel.md) |
| Requirements | [REQ-035: Metamodel Lock Mode](../../../requirements/req/REQ-035-metamodel-lock-mode.md), [REQ-037: Solution-Specific Metamodel Extension](../../../requirements/req/REQ-037-architecture-metamodel-extension.md) |
| ADRs | [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (amended: new permission), [ADR-002](../../../adrs/ADR-002-continuum-repository.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Sprint / Milestone | S8 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-04:

- **Solution extensions without a user interface:** a new resource `/metamodel/configurations` lists the instance configuration and the solution extensions.
  - A new extension names its solution by ID, has a name and its own edit mode, and inherits from the instance configuration.
  - The ID of the solution stays a reference without a foreign key until solutions exist (S10).
  - Metatypes are created in an extension with its `configId`.
  - `listMetatypes?configId=<extension>` returns the effective metamodel: the instance metatypes plus those of the extension, each with its `configId`.
  - A name already used in the instance is rejected, as is one used twice in the same extension (AC3, BR-09).
- **US-035 in the same change:**
  - The edit mode of the instance configuration (`gui-and-import` or `import-only`) is enforced and switched by metamodel administrators in SCR-030 under "Settings".
  - In `import-only`, every write on metamodel content of the instance gives 403 with the problem type `metamodel-locked`. Package imports keep working (US-035 AC2).
  - SCR-030 shows a notice and no changing actions (AC1, AC3).
  - An extension has its own edit mode, which applies only to its metatypes.
- **New permission `solution-metamodel:write`:**
  - It allows creating extensions and maintaining their metatypes and mappings.
  - It is not admin-class.
  - The built-in roles `enterprise-architect` and `metamodel-admin` get it.
  - The instance metamodel still needs `metamodel:write`.
  - ADR-032 is amended with the new permission.
- **Manual:** the lock mode is described in "Editing the metamodel", the extensions in its scripting section, and the new permission in "Roles and permissions", each in English and German.

Assumptions, open for the owner's review:

- **What is metamodel content:** the lock mode covers all content writes of the instance: metatypes, mappings, property definitions, enum values, categories and labels. The re-import settings and the edit mode itself stay changeable, so that administrators can unlock.
- **Extensions hold only metatypes and their mappings:** property definitions, categories and labels stay instance-wide and keep needing `metamodel:write`. An extension maps existing properties to its metatypes.
- **Base types in an extension:** the base type of a metatype in an extension is an instance metatype or one of the same extension. Instance metatypes never get a base type from an extension.
- **No entities or catalogs of extension metatypes:** until solutions exist (S10), the batch rejects them with `METATYPE_NOT_FOUND`, and catalogs reject them as before.
- **Deleting an extension:** an extension can be deleted while it has no metatypes.
- **Instance names and extensions:** an instance metatype may not take a name used in any extension, so that an extension never shadows an instance metatype.

## Out of Scope

- Solutions, plateaus, and connecting extensions to them (S10 and later), and a user interface for extensions.
- Stereotypes and constraint rules (UC-04 A2, A3).
- Importing packages into an extension (`POST /solutions/{id}/metamodel/import` of US-037).
- The section "Changes" of SCR-030 (US-034, S8 change 3).

## Capabilities

### New Capabilities

- `metamodel/solution-extensions`: extension configurations per solution, their metatypes, names and base types, the effective metamodel and the permission `solution-metamodel:write`.
- `metamodel/lock-mode`: the edit mode of a configuration, what `import-only` rejects and what stays possible, and who switches it.

### Modified Capabilities

- `metamodel/configuration-screen`: SCR-030 shows and switches the lock mode and hides changing actions while it is active (new requirement).

## Impact

- `api/openapi.yaml`:
  - new operations `listMetamodelConfigurations`, `createMetamodelConfiguration`, `updateMetamodelConfiguration` and `deleteMetamodelConfiguration`;
  - `MetatypeDetail.configId`;
  - the authorization of the metatype and mapping writes changes to `anyOfPermissions` with a handler condition for extensions;
  - the problem type `metamodel-locked`;
  - `solution-metamodel:write` in the permission catalog and the built-in roles.
- `backend/`:
  - Flyway V20 with the permission, the role grants, and `name` and `version` on `metamodel_configurations`;
  - `io.oea.app.metamodel` for configurations, the edit mode check in every content write, and names and base types in extensions;
  - the effective metamodel in `MetatypeCatalog`;
  - `MetatypeRules` without extension metatypes.
- `frontend/`: the lock mode in the SCR-030 settings, a notice and hidden actions.
- `manual/`: "Editing the metamodel" and "Roles and permissions" in English and German.
- `adrs/ADR-032` (amendment), `docs/data-model.puml`, `business-objects/metamodel-configuration.md`, US-035, US-037, REQ-035, REQ-037 and the sprint plan.
