# Proposal

## Why

Dashboards in S17 group charts by architecture layer (US-057 AC4, REQ-053 BR-04, e.g. "entities per layer"). The metatype schema has carried an `architecture_layer_id` since V5, but no layer exists: no definition, no editing, no package support, and the starter package assigns none.

The owner decided on 2026-11-18 to introduce layers now, as a configurable list in the metamodel, rather than a fixed set.

This is S17 change 1 ([sprint plan](../../../docs/sprint-plan.md)). It prepares change 2 (`us-056-kpi-and-chart-widgets`), which groups charts by layer, and takes migration V44.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md) (metamodel), [UC-07](../../../requirements/use-cases/UC-07-create-and-use-dashboard.md) (grouping by layer) |
| User Story | [US-057](../../../requirements/user-stories/US-057-configure-chart-widget.md) AC4 (prerequisite; the grouping itself is change 2) |
| Requirements | [REQ-053](../../../requirements/req/REQ-053-property-aggregation-datasource.md) BR-04 |
| ADRs | [ADR-017](../../../adrs/ADR-017-architecture-layer-strategy.md) (operator-defined layers), [ADR-002](../../../adrs/ADR-002-continuum-repository.md) (packages), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`metamodel:write`), [ADR-049](../../../adrs/ADR-049-organization-extensions-of-packages.md) |
| Business object | [metamodel-configuration](../../../business-objects/metamodel-configuration.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-18:**

- **Layers now:** charts group by architecture layer, so layers are introduced in S17.
- **Configurable list:** layers are a list maintained in the metamodel, not a fixed set. Packages can bring layers.

**Decisions and assumptions of this change (open for review):**

- **Layer:**
  - A layer has a name of 1–100 characters, unique within the instance metamodel and compared case-insensitively (409 `name-taken`), and an order.
  - Layers are maintained by holders of `metamodel:write` and read with `entity:read` or `metamodel:write`, like metatypes.
  - An instance holds at most 20 layers.
  - While the instance metamodel is `import-only`, layer writes are rejected with 403 `metamodel-locked` (lock mode, US-035).
- **Metatype:** a metatype has at most one layer of the instance metamodel, which is optional. Element and connection metatypes may both carry one, and so may metatypes of solution extensions.
- **Origin:** like metatypes, a layer is either `organization` (editable) or `imported` (read-only, from a package).
- **Labels:** a layer is a new element of the labels capability (key `architecture-layer.{id}.name`): packages bring them in `i18nBundles`, administrators set organization labels in the section "Translations", and the label bundle carries them.
- **Deleting:** a layer still assigned to a metatype of any configuration cannot be deleted (409 `in-use` with the number of metatypes in `count`). Unassigning comes first.
- **Packages:**
  - the new section `architectureLayers` (`id`, `name`, `sortOrder`);
  - metatypes reference a layer of the package by `architectureLayerId`;
  - a re-import updates the package's own layers in place and removes those the new version no longer has, clearing the assignments to them;
  - a layer whose ID or name belongs to an organization layer or another package is a conflict (409 `package-conflict`).
- **Starter package 1.4.0:** the layers Business, Data, Application and Technology, in TOGAF order:
  - Application Component and Application Service → Application;
  - Data Object → Data;
  - Technology Component, Technology Service, System Software and Network Node → Technology;
  - Composition → none.
- **Screens:**
  - the metamodel page SCR-030 gets the section "Layers" (list, create, rename, reorder, delete);
  - the metatype dialog gets the field "Layer";
  - the metatype list shows the layer.
- **Audit:** `metamodel.layer-created`, `metamodel.layer-updated` and `metamodel.layer-deleted`, listed in the metamodel changes under the new kind `layer`; the layer of a metatype is a field of the existing metatype change event.

**Contract (spec-first):**

- `listArchitectureLayers` (`GET /metamodel/architecture-layers`, `entity:read` or `metamodel:write`);
- `createArchitectureLayer`, `updateArchitectureLayer` and `deleteArchitectureLayer` (`/metamodel/architecture-layers[/{id}]`, `metamodel:write`, `If-Match` on update and delete);
- `architectureLayerId` on the metatype read and write schemas, replacing the unused `architectureLayer` text;
- `architectureLayers` in the package schema;
- the value `layer` of `MetamodelChangeKind` and `architecture-layer` of `MetamodelLabel.elementType`.

**Not in this change:**

- Grouping charts by layer (change 2).
- Architecture domains (REQ-053 BR-04, `architectureDomainId`) follow when domains are modelled.
- Layers of solution extensions: layers live in the instance metamodel only; metatypes of an extension use the instance layers.
- Solution snapshots (US-154) keep property values and enum values only; layers do not change entity data and need no snapshot.
- The package export (`exportMetamodelPackage`) is still `planned`; it gets the layers when it is implemented.

## Capabilities

### New Capabilities

- `metamodel/architecture-layers`: the layer list, its maintenance, the layer of a metatype, and the layers in packages.

### Modified Capabilities

None. The package import requirement gains layers through the new capability, which keeps `metamodel/package-import` unchanged.

## Impact

- **Contract:** the four operations, the metatype field and the package section.
- **Migration:** `V44__architecture_layers.sql`. It adds the table `architecture_layers`, converts `metatypes.architecture_layer_id` to a UUID foreign key (clearing any free-text value, which referenced no layer), and extends the i18n entity types by `architecture_layer`.
- **Backend:** the layer service and store, metatype editing, labels, metamodel versions and changes, the package validator and importer, the starter package 1.4.0.
- **Frontend:** the section "Layers" on SCR-030, the field in the metatype dialog, and the layer kind in the sections "Translations" and "Changes".
- **Documentation:**
  - the manual "Metamodel editing" and "Metamodel packages" (en/de);
  - `business-objects/metamodel-configuration.md` and `docs/data-model.puml`;
  - the audit catalog, SCREENS.md and the sprint plan.
