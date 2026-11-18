# Design

## Context

- **Metatypes:** `metatypes.architecture_layer_id TEXT` exists since V5. The read schema exposes `architectureLayer` and the write schema `architectureLayerId`; `MetatypeEditing` stores the write value unchecked, the screen never sends one, and the package importer never writes it. The package format reuses `MetatypeDetail` for its metatypes.
- **ADR-017:** layers are operator-defined, with no system enum; this change implements its layer definitions.
- **Metamodel content:** metatypes, property definitions and viewpoints carry a scope `built-in`, `imported` or `organization`, with read-only imported content (409 `read-only-metatype`).
- **Labels:** labels live in `i18n_entries` (V17, entity types `metatype`, `property_category`, `property_def` and `enum_value`), keyed `{prefix}.{id}.{field}` (`LabelKey`). Administrators set organization labels through `updateMetamodelLabels` (section "Translations"); packages bring theirs in `i18nBundles`. Element writes carry no labels.
- **Lock mode, versions, changes:** writes run under `MetamodelEffects.lock()`, are rejected in `import-only` (403 `metamodel-locked`), check `If-Match` through `MetamodelVersions`, and are listed by `MetamodelChanges` by kind.
- **Packages:** they are validated by `PackageValidator` and imported by `PackageImporter`; viewpoints were the last section added (US-091).

## Goals / Non-Goals

**Goals:** the layer list, the layer of a metatype, and layers in packages, as specified in `specs/metamodel/architecture-layers`.

**Non-goals:**

- grouping charts by layer (change 2);
- architecture domains;
- layers of solution extensions: their metatypes use the instance layers (BR-15 of the business object);
- solution snapshots (US-154): they keep property and enum values; a layer changes no entity data, so it needs none;
- the package export, still `planned` (501).

## Decisions

### D1 – Migration `V44__architecture_layers.sql`

```sql
architecture_layers (id UUID PK, config_id UUID REFERENCES metamodel_configurations (always the instance),
                     name TEXT 1–100, sort_order INT ≥ 0, version INT ≥ 1,
                     scope TEXT IN ('imported','organization'), source_package_id, source_package_version,
                     created_at, created_by, updated_at, updated_by)
UNIQUE (config_id, lower(btrim(name)))
```

- **`metatypes.architecture_layer_id`:** dropped as `TEXT` and re-added as `UUID REFERENCES architecture_layers (id)`, with an index. A free-text value an API client may have written referenced no layer; it is set to null before the conversion.
- **Labels:** the `i18n_entries.entity_type` check gets `architecture_layer`.
- **Limit:** the 20-layer limit is checked by the service, not the database.
- **Grants:** like metatypes.

### D2 – Contract

- **Operations:**
  - `listArchitectureLayers`: `GET /metamodel/architecture-layers`, `entity:read`;
  - `createArchitectureLayer`: `POST /metamodel/architecture-layers`, `metamodel:write`;
  - `updateArchitectureLayer`: `PUT /metamodel/architecture-layers/{id}`, `metamodel:write`, `If-Match`;
  - `deleteArchitectureLayer`: `DELETE /metamodel/architecture-layers/{id}`, `metamodel:write`, `If-Match`.
- **Schemas:**
  - `ArchitectureLayer {id, name, sortOrder, scope, sourcePackageId?, metatypeCount, version}`, with `ETag`;
  - write `ArchitectureLayerWrite {name, sortOrder?}`; labels go through `updateMetamodelLabels` like those of every other element;
  - `sortOrder` 0–19, placed and clamped like the tree positions.
- **Authorization:** the list `anyOfPermissions: [entity:read, metamodel:write]` like `listMetatypes`; the writes `metamodel:write`, 403 `MetamodelForbidden` (also `metamodel-locked`).
- **Metatype:** `MetatypeDetail` replaces `architectureLayer` with `architectureLayerId` (uuid or null) and adds the read-only `architectureLayerName`; `MetatypeWrite.architectureLayerId` becomes uuid or null. Since packages reuse `MetatypeDetail`, package metatypes get `architectureLayerId` with it.
- **Package:** `architectureLayers: [PackageArchitectureLayer {id, name, sortOrder}]`, at most 20.
- **Enumerations:** `MetamodelChangeKind` gains `layer`; `MetamodelLabel.elementType` gains `architecture-layer`.
- **Errors (existing problem types of `MetamodelConflict`):** 409 `name-taken`; 409 `read-only-metatype` for an imported layer; 409 `in-use` with `count` (the number of metatypes of all configurations); 422 `validation-failed` for the name, the 21st layer and an unknown `architectureLayerId`.

**Paths:** the layers sit under `/metamodel` with tag `Metamodel`, next to metatypes, categories and labels, which are all there today. Change 5 decides #262 API-7 for the whole metamodel root; if it moves to `/admin/metamodel`, the layer operations move with the rest.

### D3 – Backend

- **Store and service:** `core/metamodel/ArchitectureLayerStore` and `app/metamodel/ArchitectureLayers` (ADR-051). Writes take `MetamodelEffects.lock()`, reject `import-only` with `locked()`, check `If-Match` through a new `MetamodelVersions.Element.ARCHITECTURE_LAYER`, raise `version` and audit through `MetamodelAudit` in the same transaction.
- **Delete:** removes the layer's organization labels (`removeOrphanLabels` learns `architecture_layer`).
- **Labels:** `LabelKey.Element.ARCHITECTURE_LAYER("architecture-layer", "architecture_layer", "name")`; `MetamodelLabels` lists layers in the administrators' list and in the bundle.
- **Changes:** `MetamodelChanges.KINDS` gains `layer` → `metamodel.layer-%`.
- **Reordering:** dense positions, as `NavigationTreeStore` does.
- **`MetatypeEditing` and `MetatypeCatalog`:** the layer ID becomes a `UUID`; `MetatypeEditing` validates it against the instance layers, also for metatypes of an extension, and gives 422 naming `architectureLayerId`; built-in metatypes stay read-only. `MetatypeCatalog` returns ID and name.
- **Packages:**
  - `PackageValidator`: unique names in the package, references only to package layers, at most 20;
  - `MetamodelPackages`: maps the section and the metatype field into `PackageDefinition`;
  - `PackageImporter`: upserts its own layers, then the metatype references; a re-import removes its own layers no longer in the package, after clearing the assignments of every metatype to them (also organization metatypes; the import result counts them). No per-layer audit event; the package event names the layer counts.
  - A layer whose ID or name belongs to an organization layer or another package is a conflict: 409 `package-conflict` with the layer in `conflictList`, as for metatypes.
- **Labels:** imported through `i18nBundles` with the key `architecture-layer.{id}.name`.
- **Starter package:** version 1.4.0 with the layer IDs as name-based UUIDs like the other package IDs, and the German labels Geschäft, Daten, Anwendung and Technologie.

### D4 – Frontend

- **SCR-030:**
  - a new section "Layers" (component `metamodel/ArchitectureLayersSection.vue`): a list with the label, origin and number of metatypes;
  - "New layer", "Rename", "Move up" and "Move down", and "Delete" with the 409 message;
  - imported layers have no actions; without `metamodel:write` or in `import-only` the section is read-only, like the other sections;
  - "Translations" shows layers (type label `architecture-layer`), "Changes" offers the kind `layer`.
- **Metatype dialog** (`metamodel/MetatypeDialog.vue`): a select "Layer" with "None".
- **Metatype list:** a "Layer" column.
- **Supporting files:** `api/metamodel.ts`, `i18n/labels.ts` (`labelKeys.architectureLayer`), the fake `src/test/metamodel.ts` and the SCR-030 stories.

### D5 – Documentation

- **Manual:** "Metamodel editing" gets the section "Layers", and "Metamodel packages" gets `architectureLayers`, in English and German.
- **Business object and data model:** `business-objects/metamodel-configuration.md` (`ArchitectureLayerDefinition`: UUID instead of kebab-case ID, `sortOrder`, scope; `description` and colors not yet; BR-15 and BR-18 aligned) and `docs/data-model.puml` (table `architecture_layers`, the FK instead of the soft reference).
- **Audit catalog:** `docs/audit-event-catalog.md` gets the `metamodel.layer-*` events.
- **`SCREENS.md`:** SCR-030.
- **Sprint plan:** the S17 row with the cut and the owner decisions.

### D6 – Security

- **Authorization:** reads need `entity:read`; writes need `metamodel:write` (admin class, ADR-032).
- **Integrity:** imported layers are read-only; a layer in use cannot be deleted; `If-Match` applies to update and delete; `import-only` blocks all layer writes except the package import.
- **Input limits, both sides tested:**
  - the name: 100/101 and blank;
  - layers: 20/21, both through the API and in a package;
  - `sortOrder` 0/19 (larger values are clamped);
  - a package section of more than 20 entries gives 422.
- **Output encoding:** names are rendered as text.
- **Audit:** every write; none on a rejection.

## Risks / Trade-offs

- **Column type change:** the conversion of `architecture_layer_id` from text to uuid drops free-text values. No layer ever existed, so no value referenced one, and neither the screen nor packages wrote the column.
- **Re-import clears assignments:** removing a dropped package layer also clears its assignment on organization metatypes. The alternative, rejecting the re-import, would block package updates on a cosmetic attribute.

## Migration Plan

V44 is additive except for that column. The starter package 1.4.0 is offered as an update.

## Open Questions

None.
