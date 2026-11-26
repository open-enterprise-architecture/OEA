---
title: Metamodel packages
covers:
  screens: []
  stories: [US-033, US-057]
---

# Metamodel packages

A new instance has no entity types. Before anyone can capture entities, you import a metamodel: the shipped starter package or a package of your own.

Importing packages requires the permission `metamodel:write`, which only the role `metamodel-admin` holds – not the local system admin account. Map an IdP group to `metamodel-admin` (see [Roles and permissions](./roles-and-permissions)); as a privileged role, it needs a second factor at the identity provider.

## Import the starter configuration

OEA ships the starter package `oea-starter-togaf-classic` with the entity types Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software and Network Node, each with the property *Status*. Version 1.4.0 adds the four [layers](./metamodel-editing#layers) *Business*, *Data*, *Application* and *Technology* with German names and assigns them to the entity types: *Application Component* and *Application Service* to Application, *Data Object* to Data, and *Technology Component*, *Technology Service*, *System Software* and *Network Node* to Technology (*Composition* has none). Importing it again into an instance that has an own layer of the same name, for example *Business*, is refused as a conflict; rename or delete that layer first. Version 1.3.0 adds the connection type *Composition*, marked as the [composition](./metamodel-editing#composition) of the hierarchy of components. Version 1.2.0 adds the two system-defined viewpoints *Application Landscape* and *Technology Landscape* in ArchiMate (see [Viewpoints in packages](#viewpoints-in-packages)). Version 1.1.0 brought German labels for its entity types, *Status* and its values, and English labels for the values (see [Translating labels](./metamodel-editing#translating-labels)). An instance that imported an earlier version receives the labels and viewpoints by importing the package again.

While the instance has no entity type, the start page shows *Import starter configuration* to a person with `metamodel:write`. Choose *Import* next to the package; the page confirms how many entity types are now available.

<Screenshot name="starter-import" alt="Start page with the starter configuration ready for import" />

Through the API:

```bash
curl "${auth[@]}" "$OEA/metamodel/starter-packages"                    # list with summary
curl "${auth[@]}" -X POST "$OEA/metamodel/starter-packages/oea-starter-togaf-classic/import"
curl "${auth[@]}" "$OEA/metamodel/metatypes"                                       # imported entity types
```

## Import your own package

Packages of your own use the same JSON format (`MetamodelPackage`, with `packageId`, `packageVersion`, `publisher` and stable UUIDs per element). Import them with `POST /metamodel/import`:

- A package is imported completely or not at all. Imported content gets the scope `imported` and the package as origin.
- Importing the same package again updates its own content. Content of another package or of your organization is never overwritten; the answer is `409` with the problem type `urn:oea:problem:package-conflict` and the list of conflicts in `conflictList`, and nothing of the package is stored.
- A package can name a base type for an entity type (`baseMetatypeId`): an entity type of the same package or a built-in one. Your own properties on its entity types are kept or removed according to the re-import setting (see [Editing the metamodel](./metamodel-editing)).
- Packages larger than `oea.metamodel.import.max-size` (default 10 MB) are rejected with `413`; faulty JSON or content that does not match the format with `422` and the location of the problem.
- A package can bring labels per language in `i18nBundles`, keyed as in [Translating labels](./metamodel-editing#translating-labels), but only for its own elements. A re-import replaces them; your own labels stay.
- The strategy `replace` and shape assignments are not supported yet (`422`).

Every import writes the audit events `metamodel.package-imported` and `metamodel.metatype-imported` with one batch ID.

## Composition in packages {#composition-in-packages}

A connection type of a package may carry `isComposition: true`; a package has at most one. The import is refused when the instance already has another marked connection type. A re-import of the same package moves or removes the mark with the package.

## Layers in packages {#layers-in-packages}

A package can bring architecture layers in `architectureLayers`: each with a stable `id`, a `name` of up to 100 characters and an optional `sortOrder`. The entity types of the package refer to them by `architectureLayerId`. The import installs them as read-only layers with the package as origin (see [Layers](./metamodel-editing#layers)).

- A package has at most 20 layers, and together with the layers of your organization the instance has at most 20 (`422` otherwise). Names are unique within the package regardless of case; an entity type that refers to a layer outside the package is rejected with `422`.
- A layer whose ID or name belongs to a layer of your organization or of another package answers `409` with `package-conflict`, and nothing is imported.
- New layers are placed behind the existing ones in the order of `sortOrder`; layers that are already there keep their position.
- Importing the same package again updates its layers. A layer the new version no longer has is deleted, and every entity type that had it – also those of your organization – loses its layer. Layers of your organization are kept.
- The import result counts the created, updated and deleted layers (`layersCreated`, `layersUpdated`, `layersDeleted`) and the entity types that lost a layer (`layerAssignmentsCleared`). Labels for a layer go in `i18nBundles` under the key `architecture-layer.{id}.name`.

## Viewpoints in packages {#viewpoints-in-packages}

A package can bring viewpoints in `viewpoints`: each with a stable `id`, `name`, optional `description`, `notation`, the allowed `metatypeIds` and `connectionTypeIds` (types of the package or of the instance), optional `mappings` and optional default `sizes` in pt (`metatypeId` with `width` and `height`, or `length`; see [Viewpoints](./metamodel-editing#viewpoints)) and the optional `askNewParent` (`true` or `false`; without it the global setting applies). The import installs them as system-defined, read-only viewpoints with the package as origin (see [Viewpoints](./metamodel-editing#viewpoints)).

- A viewpoint has the same limits as one of your own: name up to 255 characters, description up to 1000, at most 500 entity types, 500 connection types, 1000 mappings and 1000 sizes, sizes of 1 to 2000 pt and colors as `#rrggbb`. A violation or an unknown type is rejected with `422` naming the viewpoint, a faulty size also `sizes`. `defaultWidth` and `defaultHeight` in a mapping are ignored.
- Importing the same package again updates its viewpoints, also when one was renamed, and deletes those the package no longer contains, together with their [diagrams](../usage/diagrams), the deleted ones included; the entities stay. Viewpoints of your organization or of another package are never overwritten: the same ID or name answers `409`.
- The import result counts the created, updated and deleted viewpoints and, as `diagramsDeleted`, the diagrams that were deleted with them. A rejected import deletes nothing.
