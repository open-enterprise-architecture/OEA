---
title: Editing the metamodel
covers:
  screens: [SCR-030, SCR-031]
  stories: [US-075, US-076, US-032, US-057, US-034, US-035, US-036, US-037, US-135, US-090, US-091, US-092, US-093, US-137, US-140, US-154, US-156]
---

# Editing the metamodel

Besides importing packages (see [Metamodel packages](./metamodel-packages)), you maintain the entity types and properties of your own organization on the page *Metamodel* of the web portal. Everyone with read access (permission `entity:read`, for example the role `viewer`) sees the metamodel there; changes need `metamodel:write` (role `metamodel-admin`). Every change is recorded in the audit trail with your name.

## The metamodel page {#the-metamodel-page}

Choose *Metamodel* in the header. The page has these sections:

| Section | Content |
|---|---|
| Entity types | the list of all entity types with abbreviation and origin, and the selected entity type with its properties |
| Properties | all properties with data type, category, values and limits |
| Categories | the categories that group properties |
| Layers | the [architecture layers](#layers) that entity types belong to |
| Viewpoints | the [viewpoints](#viewpoints) with their notation |
| Translations | the names of all elements per language |
| Starter packages | the packages shipped with OEA and their [import](./metamodel-packages#starter-packages-section) (administrators only) |
| Changes | who changed the metamodel and when (administrators only) |
| Settings | the lock mode, and what happens to your own properties when a package is imported again (administrators only) |

The origin tells you what you may change: *Own* content belongs to your organization. *Built-in* content is part of OEA, and content of a package (*Package oea-starter-togaf-classic 1.1.0*) comes from an import. Built-in and imported content is read-only; you can still add your own properties to such entity types. On a narrow screen, the page shows the list and the selected entity type one after the other.

<Screenshot name="metamodel-entity-type" alt="Metamodel page with the list of entity types and the selected entity type Security Zone with its properties" />

## Entity types

1. In *Entity types*, choose *New*.
2. Enter the name, an optional abbreviation of up to 10 characters, the sort order, an optional base type and an optional [layer](#layers).
3. Choose *Save*. The entity type appears in the list and can be used for entities and catalogs at once.

<Screenshot name="metamodel-entity-type-new" alt="Dialog New entity type with name, abbreviation, sort order, base type and layer" />

- Names are unique across all entity types, regardless of upper and lower case: *application component* is rejected while *Application Component* exists.
- You change (*Edit*) and delete (*Delete*) only your own entity types.
- An entity type with entities – also deleted ones –, one used by a catalog or one that is the base type of another type cannot be deleted. The message names the number of entities.

### Connection types {#connection-types}

A connection type describes directed relationships that are objects of their own: a data flow between two applications with its protocol, a network connection, or "a security zone contains technology components". Its entities – connections – always have one source and one target.

1. In *Entity types*, choose *New* and switch on *Connection type*. This cannot change later.
2. Under *Allowed sources* and *Allowed targets*, choose the entity types a connection may start and end at. Leave a list empty to allow every entity type.
3. Choose *Save*. The list marks the type with *Connection*, and its detail names the allowed sources and targets.

- An allowed entity type also allows its subtypes.
- A connection type may itself be an allowed source or target, so that one connection can start or end at another (for example, a data flow branching off another one). To allow the type itself, edit it after creating it.
- A relation between entity types, such as *contains*, is a connection type. There are no cardinalities.
- The base type of a connection type is a connection type, the base type of another entity type is not.
- An entity type that is an allowed source or target of a connection type cannot be deleted; remove it from the connection type first.
- Connections are written through the API, see [Writing entities in batch](../usage/entities-batch#connections).

### Composition {#composition}

One connection type of the instance can be marked as *Composition*. When someone places a component under another one in the [browser](../usage/navigation#arrange-components), OEA then creates a connection of this type from the parent to the child, if the type allows both entity types (see above). Without a marked type, only the hierarchy is stored.

1. Open a connection type of your own and switch on *Composition* (also when creating it).
2. Choose *Save*. Changing the mark is a change of the type and raises its version.

At most one connection type of the instance is the composition; marking a second one, an entity type or a type of a solution extension is refused. The starter package brings the connection type *Composition*, already marked and without restriction of its endpoints. The built-in entity type *Structuring aid* is read-only and has no properties; it cannot be a base type or an allowed source or target.

### Base types

An entity type inherits all properties of its base type and of the base type's own base types. The detail names the base type, and inherited properties show *Inherited from* with the base type. A property may appear only once in a chain; base types cannot form a cycle. Packages can name a base type of the same package or a built-in one.

If you change the base type of your own entity type, it loses the properties of the old base type – with their values, see below – and gains those of the new one.

## Properties

1. In *Properties*, choose *New*.
2. Enter the name and choose the data type: *Text (short)*, *Text (long)*, *Whole number*, *Yes/no*, *Date*, *URL* or *Selection list*. Depending on the type, you set a maximum length, a minimum and maximum, or a pattern (a regular expression).
3. For a selection list, enter the values one by one and choose *Add*.
4. Choose *Save*.

<Screenshot name="metamodel-property-new" alt="Dialog New property of type selection list with the values low, medium and high" />

- OEA derives a storage name from the name, for example `trust_level`. It is used in reports and never changes, even if you rename the property.
- You cannot change the data type once entities have values for the property, also in their history.
- In an existing selection list, you add and delete values directly in the dialog. Entities that have a deleted value lose it.
- A property that is still assigned to an entity type cannot be deleted. A property that appears in the history of entities is retired instead: it disappears from the lists, and its name stays taken.
- *Categories* group properties; a category with properties cannot be deleted.
- A property whose values only some persons may see carries the badge *Restricted*; its dialog shows the visibility read-only. Security administration changes it on the page [Property visibility](./property-visibility).

## Assigning properties to entity types

1. Select the entity type and choose *Add property*.
2. Choose one of your own properties, the necessity, the date from which it applies and an optional default value.
3. Choose *Save*.

<Screenshot name="metamodel-mapping-new" alt="Dialog Add property with the property, the necessity Mandatory and the valid-from date" />

- You assign your own properties to any entity type, also to imported and built-in ones. The properties of a package stay unchanged.
- The necessity is *Mandatory*, *Warning* or *Optional*.

### A new mandatory property

Assigning a mandatory property always succeeds, even if entities exist. OEA marks every existing entity of the type and its subtypes without a value; the confirmation states their number. A marked entity keeps working until someone changes it: saving it without the value is rejected. Setting the value removes the mark.

Mandatory properties are checked only where the writer can change them. Properties with restricted visibility cannot be written yet, so they are never reported as missing.

### Changing the necessity by effective date

A metamodel evolves: a property that is optional today can become mandatory from a certain date. In the row of the property, choose *Add dated entry* and enter the new necessity and the date from which it applies.

- The earlier entries stay; the entry with the latest date not after today applies. Each entry shows its state: *active*, *future* or *past*. *Show past entries* lists the entries that no longer apply. A second entry with the same date is rejected.
- A date in the past applies at once. On a future date, OEA marks the entities without a value when the date is reached – at the latest about ten minutes after midnight (UTC). If a property is no longer mandatory, its marks disappear.
- This also works for the properties of a package, for example to make *Status* mandatory. Your entry is your own; the package stays unchanged. If the package itself brings an entry on the same date, yours wins.
- Entries are made on the entity type that holds the property, not on its subtypes.
- To correct an entry, remove it and add a new one. Removing one of several entries of a property deletes no values.

### Removing a property {#removing-a-property}

You can always remove your own entries of a property (*Remove entry*).

::: warning Values are deleted
If the entity type then no longer has the property and entities of the type or its subtypes have values for it, these values are deleted. Every affected entity gets a new version; the removed value remains visible in its history. The dialog warns you before. Deleting a value of a selection list works the same way for the entities that hold this value.
:::

If active [solutions](../usage/solutions) use the values, the dialog lists them after your first confirmation, and you decide for each one. This also applies when a changed base type takes properties away:

- **Apply to this solution:** the values are removed from the changes of the solution as well. A modification that changed nothing else is removed.
- **Keep as snapshot:** the solution keeps the property, or the value of the selection list, until its go-live. Its changes keep their values and can still set them. At the go-live, each such value is either discarded or becomes an [open issue](../usage/solutions#open-issues).

Solutions that work toward the same [plateau](../usage/plateaus) are decided together: the dialog asks once for the plateau and names its solutions. A solution without a plateau is still asked on its own.

A property that a solution keeps as a snapshot cannot be deleted until that solution went live. Changes inside the extension of a solution, and package imports, do not ask.

## Layers {#layers}

Architecture layers sort entity types by their kind of content, for example *Business*, *Data*, *Application* and *Technology*, and charts on dashboards group by them. The section *Layers* of the metamodel page lists the layers of the instance in their order, with their origin and the number of entity types that have each; everyone who may read the metamodel sees them. An entity type has at most one layer, or none; you set it in the dialog of the entity type (*Layer*), and the list and the detail of the entity type show it.

1. In *Layers*, choose *New* and enter the name.
2. Choose *Save*. The layer appears at the end of the list.
3. Move a layer with the arrows, rename it with the pencil or delete it with the bin.

- The name has 1 to 100 characters and is unique regardless of upper and lower case: *business* is rejected while *Business* exists.
- An instance has at most 20 layers. At the limit, *New* is switched off.
- You change only your own layers. Layers of a [package](./metamodel-packages#layers-in-packages) (for example those of the starter package) are read-only, and so are the entity types of a package: to give an imported entity type another layer, import a new version of the package.
- A layer that an entity type – of the instance or of a solution extension – still has cannot be deleted. The message names the number of entity types; remove the layer from them first.
- In a [locked](#lock-mode) metamodel, layers cannot be changed either; the section stays readable.
- Layers have translations like other elements: open [Translations](#translating-labels) and enter the name per language.

## Viewpoints {#viewpoints}

A viewpoint names the entity types and connection types that belong to a view, for example "Application Landscape" with applications, data objects and their data flows, together with its notation and the element each type is drawn with. The section *Viewpoints* of the metamodel page lists them in two groups; everyone who may read the metamodel sees them:

- **System-defined:** viewpoints installed by a [metamodel package](./metamodel-packages#viewpoints-in-packages), marked with a lock and their package. They are read-only; only a new version of their package changes them.
- **Organization:** the viewpoints of your organization.

You need the permission `viewpoint:manage` (for example the role `enterprise-architect`) to change viewpoints; the [lock mode](#lock-mode) of the metamodel does not apply to them.

- *New viewpoint* asks for a name (up to 255 characters, unique regardless of upper and lower case), an optional description (up to 1000 characters), at least one entity type and any number of connection types of the instance.
- *Notation* is *Generic*, *ArchiMate 3*, *UML* or *BPMN 2.0*. Viewpoints created before notations existed have *Generic*.
- *Notation mapping* is optional: for each selected type you choose an *Element* of the notation, for example *Application Component* of ArchiMate, and optionally a *Color*. The list offers only the elements of the chosen notation that fit the type (shapes for entity types, relationships for connection types) and the generic box or arrow. A type without an element is drawn as a generic box, a connection type as a generic arrow. Changing the notation clears the elements that no longer fit.
- *Ask 'New parent?'* decides whether the [diagram editor](../usage/diagrams#nested-shapes) asks when a shape is moved into a container or out of it: *Global setting* (the default), *Ask* or *Do not ask*. The global setting is the switch *Ask 'New parent?' when moving shapes* in the section *Diagram settings* above the list; it is on by default. A viewpoint overrides the global setting only with *Ask* or *Do not ask*. Everyone who may read the metamodel sees the global setting; changing it needs `viewpoint:manage`, and the switch is not shown to others. A system-defined viewpoint takes the value from its package.
- *Default sizes* are optional and independent of the notation mapping. For each entity type you can enter a *Width* and a *Height* in pt (1 to 2000, both or neither), for each connection type a *Length* in pt (1 to 2000), which is the distance at which a connected new element is placed from its source. A type without a size gets 90 × 30 pt, a connection type 90 pt, whatever its element; the empty fields show these values. Changing the notation keeps the sizes, and removing a type from the viewpoint removes its sizes.
- *Edit* changes them. If someone else changed the viewpoint meanwhile, the dialog shows the [current state](#concurrent-changes).
- *Delete* removes the viewpoint after a confirmation. If [diagrams](../usage/diagrams) use the viewpoint, the confirmation says how many *diagrams will be deleted with the viewpoint*; they are deleted for good, the deleted ones included, and the entities stay. A viewpoint that no diagram uses is deleted without this warning.
- When you remove an entity type or connection type from a viewpoint that diagrams use, the dialog warns *N diagrams affected – their shapes of removed types become conflicts*. N is the number of diagrams that show a shape or edge of a removed type; a diagram that shows none, or only shapes of deleted entities, is not counted, and without such diagrams there is no warning. The warning does not block saving. Their shapes and edges become *conflicts*: until they are removed from the diagram or the type is allowed again, the diagram cannot be saved (see [Change the viewpoint](../usage/diagrams#change-the-viewpoint)). Through the API, `POST /admin/viewpoints/{id}/affected-diagrams/query` (`viewpoint:manage`) counts them for the allowed types you propose in `metatypeIds` and `connectionTypeIds` (at most 500 each, 400 beyond); it only reads and answers `{"count": N}`.

When an entity type is deleted, it disappears from the viewpoints that named it, together with its mapping.

Through the API, `GET /admin/diagram-settings` reads the global setting `askNewParent` with its version as `ETag` (`entity:read` or `viewpoint:manage`), and `PUT /admin/diagram-settings` with `If-Match` changes it (`viewpoint:manage`; 412 for a stale version, 428 without one). A change is recorded as `diagram.settings-updated` with the old and the new value; writing the current value changes nothing. A viewpoint carries `askNewParent`: `true`, `false` or `null` for the global setting; a write replaces it like the other fields, so an omitted value stores `null`. A package may name it per viewpoint. `GET /admin/viewpoints/notations` lists the elements of all notations with key (for example `archimate:ApplicationComponent`), notation, label and kind. A viewpoint carries `diagramCount` (the number of diagrams that use it and are not deleted), `notation`, `mappings`, `sizes` (`metatypeId` with `width` and `height`, or `length`, in pt) and, per allowed type, the `effectiveMappings` with the element actually used and its effective size (`width`, `height` or `length`, and `sizeStored`, which is `false` for the default). A size of the wrong kind, for a type the viewpoint does not allow, or two sizes for one type are rejected with 422 naming `sizes`; values outside 1 to 2000 answer 400. A mapping that still carries `defaultWidth` or `defaultHeight` is accepted, and these fields are ignored. An element of another notation or of the wrong kind is rejected with 422 naming `mappings`; changing or deleting a system-defined viewpoint is rejected with 422 and the problem type `urn:oea:problem:read-only-viewpoint`.

## Translating labels {#translating-labels}

Users see the names of entity types, categories and properties and the values of selection lists in the language of their user interface (see [Your profile](../usage/login#your-profile)).

1. Open *Translations* and choose the language: German, English, French or Italian.
2. Enter the translations. The placeholder shows the translation of the package or the name.
3. Choose *Save*.

<Screenshot name="metamodel-translations" alt="Section Translations with the German name Sicherheitszone for the entity type Security Zone" />

- You can translate every element, also those of a package. Your translation wins over the one of the package; without either, users see the name in your corporate language. Clearing a field removes your translation.
- Texts have at most 200 characters.
- Your portal shows the new names at once; other open portals pick them up within five minutes, without a reload.

## Organization model {#organization-model}

The section *Organization model* says which entity types play which part in [assignments](../usage/diagrams#assignments): the element parts *organizational unit*, *role* and *person*, and the connection parts *belongs to*, *performs role* and *responsible*. Everyone who reads the metamodel sees them; holders of `metamodel:write` choose a type per part (or *Not set*) and choose *Save*, which writes all six parts with the version read. A locked metamodel offers no change.

- A part of the first kind needs an entity type, a part of the second kind a connection type; otherwise the page names the part (`422`). A connection part whose allowed targets exclude its element part and the base types of it is refused too, unless its target list is empty (it then reaches every type). The check applies whenever both parts are set.
- The import of the [organization package](./metamodel-packages#organization-package) fills the parts that are not set. Without an organizational unit, a role or a person no assignment is offered for it, and a missing connection part blocks assignments of that part.
- The **person** here is separate from the [entity type of persons](./property-visibility#person-metatype) of property visibility, which belongs to security administration; when both are set and differ, the section shows a hint. Account links use the one of property visibility.
- If the entity type of a part is deleted, the part is not set any more. Every change is audited as `metamodel.organization-model-updated` with the changed parts; `GET` and `PUT /metamodel/organization-model` read and set the setting (`If-Match` with the version; `412` and `428` as for other settings, `403` with `urn:oea:problem:metamodel-locked` in a locked metamodel).

## Lineage model {#lineage-model}

The section *Lineage model*, next to the organization model, says which types play which part in [data lineage](../usage/data-lineage): the entity type of the *data object* and the connection types of the *data flow*, *carries data*, *data input* and *data output*. Everyone who reads the metamodel sees them; holders of `metamodel:write` choose a type per part (or *Not set*) and choose *Save*, which writes all five parts with the version read. A locked metamodel offers no change.

- The data object needs an entity type, the other parts a connection type; otherwise the page names the part (`422`). *Carries data* must allow the data flow as source and the data object as target, *data input* the data object as source and *data output* the data object as target, each unless its list is empty (it then reaches every type); base types count. The check applies whenever both parts are set.
- The import of the TOGAF package 1.5.0 fills data object, data flow and carries data, the import of the BPMN package 1.2.0 data input and data output, in each case only parts that are not set. A part you set to an own type stays. The lineage query needs data object, data flow and carries data (otherwise `422 LINEAGE_MODEL_INCOMPLETE`); without data input or data output it returns no readers or no writers.
- If the entity type of a part is deleted, the part is not set any more. Every change is audited as `metamodel.lineage-model-updated` with the changed parts; `GET` and `PUT /metamodel/lineage-model` read and set the setting (`If-Match` with the version; `412` and `428` as for other settings, `403` with `urn:oea:problem:metamodel-locked` in a locked metamodel).

## Locking the metamodel {#lock-mode}

If your organization maintains its metamodel in a versioned repository and imports it as a package, lock the metamodel, so that nobody changes it by hand:

1. In *Settings*, switch on *Lock metamodel (changes only through package import)*.
2. While the metamodel is locked, every section shows the notice *Metamodel locked*, and nothing can be created, changed or deleted: entity types, assignments, properties, values, categories and translations. Through the API, such a change is rejected with `403` (`metamodel-locked`), whatever your permissions.
3. Package imports keep working (see [Metamodel packages](./metamodel-packages)), and so do the re-import settings and the setting of parallel changes.

To unlock, switch the setting off again. Only metamodel administrators (`metamodel:write`) switch the lock mode, and every switch is recorded in the audit trail (`metamodel.edit-mode-changed`).

## Organization properties at a package re-import

When a package is imported again, it replaces its own properties of its entity types. Whether your own properties and entries on these types stay depends on the way you work, so you configure it:

- **Globally** in *Settings*: *Keep own properties* (default) or *Remove own properties*.
- **Per entity type** in the detail under *Re-import of packages*: *Like the base type* (default), *Keep own properties* or *Remove own properties*. *Like the base type* takes the setting of the base type; at the top of the chain the global setting applies. *Applies* shows the setting in effect.

Removing at a re-import deletes the values as described above.

## Parallel changes in the baseline {#parallel-changes}

When several solutions change the same entity, the solutions warn their architects (see [Solutions](../usage/solutions#parallel-changes)). In addition, the baseline of a solution can mark entities that another active solution changes, before the architect captures anything. You switch this marker on per entity type: in its detail under *Parallel changes*, switch on *Show parallel changes in the baseline*.

- It is off for every entity type until you switch it on, also for new and imported ones.
- It works for built-in and imported entity types as well, and while the metamodel is locked. A re-import of the package keeps it.
- A subtype has its own setting; it does not follow its base type.
- It affects only the marker in the baseline; the warnings on the changes themselves always show.

## Tracing changes {#tracing-changes}

Every change of the metamodel is recorded in the audit trail together with the change itself: if the record cannot be written, the change is not made either. Metamodel administrators read these records in the section *Changes*:

- Each row shows the time, the person, the kind of change (for example *Entity type deleted*), the element and details such as the changed fields or the imported package. *System* marks changes OEA made itself, for example an assignment taking effect on its date.
- Filter by kind, by person, and with *From* and *To* by period.
- The changes of one package import belong to one import batch. Choose *Import batch* in a row to list the whole import; remove the filter to see all changes again.
- *Load more* shows older changes.

The section lists only metamodel changes. The whole audit log needs the permission `audit:read`.

## When someone else changed it {#concurrent-changes}

Two administrators may open the same element. If someone else saved it after you opened it, OEA does not overwrite their change silently: the dialog warns *Changed by someone else meanwhile* and shows the current state. You decide:

- *Overwrite with my input* saves your input over the other change.
- *Discard my input* closes the dialog; you see the current state.

The same applies when you delete an element that was changed meanwhile.

## Scripting the metamodel {#scripting}

Everything on the page is also available through the API, for example for scripts that set up an instance:

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "SecurityZone", "abbreviation": "SZ", "configId": "00000000-0000-0000-0000-000000000001"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions" -H 'Content-Type: application/json' \
  -d '{"name": "Trust Level", "dataType": "enum"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions/$PROPERTY/enum-values" \
  -H 'Content-Type: application/json' -d '{"value": "high"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes/$TYPE/property-mappings" -H 'Content-Type: application/json' \
  -d '{"propertyDefId": "'"$PROPERTY"'", "necessity": "mandatory", "validFrom": "2027-01-01"}'
curl "${auth[@]}" -X PUT "$OEA/metamodel/labels/de" -H 'Content-Type: application/json' \
  -d '{"labels": {"metatype.'"$TYPE"'.name": "Sicherheitszone"}}'
```

- Entity types, properties and categories carry a `version`, also returned as `ETag`. Send it back with `If-Match: "<version>"` when you change or delete an element, an assignment, a value or a re-import setting: if someone changed the element meanwhile, the request is rejected with `412` (`stale-version`, with `currentVersion`) and nothing changes. Without `If-Match` the change is made anyway, so scripts should send it.
- Rejections use the problem types `name-taken`, `read-only-metatype`, `in-use` (with the number of entities in `count`), `conflict` and `validation-failed` (with `invalidFields`).
- A label key names the element by its identifier: `metatype.{id}.name`, `property-category.{id}.name`, `property-def.{id}.label`, `enum-value.{id}.label` or `architecture-layer.{id}.name`; an empty text removes your label, and an unknown key rejects the whole change.
- Connection types are created with `"isConnection": true` and the lists `allowedSourceMetatypeIds` and `allowedTargetMetatypeIds`; `GET /metamodel/allowed-connections?sourceMetatypeId=…&targetMetatypeId=…` answers which connection types may connect two entity types, in which direction.
- `GET /metamodel/metatypes/{id}/property-mappings?includeHistory=true` lists all entries of the assignments with their `state`. The re-import settings are `PUT /metamodel/settings/reimport-policy` and `PUT /metamodel/metatypes/{id}/reimport-policy`. `PUT /metamodel/metatypes/{id}/parallel-changes` with `{"showInBaseline": true}` and `If-Match` sets the marker of parallel changes; the entity type shows it as `showParallelChanges`, and the change is recorded as `metamodel.metatype-parallel-changes-changed`.
- Layers: `GET /metamodel/architecture-layers` lists them in their order with `metatypeCount` and `version` (permission `entity:read` or `metamodel:write`); `POST /metamodel/architecture-layers` with `{"name": "Business"}` creates one at the end, and `PUT /metamodel/architecture-layers/{id}` with `{"name": "Business", "sortOrder": 0}` renames and moves it. `PUT` and `DELETE` need `If-Match` (`428` without, `412` for an old version) and answer `409` with `read-only-metatype` for a layer of a package, `in-use` (with the number of entity types in `count`) for a layer that entity types have and `name-taken` for a taken name. A name with more than 100 characters, a blank name and the 21st layer give `422`. An entity type carries its layer as `architectureLayerId` (`null` for none) and, read-only, `architectureLayerName`; an unknown ID gives `422` naming `architectureLayerId`. Audit events: `metamodel.layer-created`, `metamodel.layer-updated` and `metamodel.layer-deleted`, listed by `GET /metamodel/changes?kind=layer`.
- Every change writes an audit event, for example `metamodel.metatype-updated`, `metamodel.mapping-deleted` with the number of affected entities, `metamodel.mapping-effective` when an entry takes effect, or `metamodel.labels-changed`.
- `GET /metamodel/configurations` lists the instance configuration and the extensions with their `editMode`. `PUT /metamodel/configurations/{id}` with `{"name": "Instance metamodel", "editMode": "import-only"}` and `If-Match` locks a configuration; `gui-and-import` unlocks it.

### Extensions of solutions {#extensions}

An extension lets architects try out new entity types for one solution without changing the instance metamodel. Extensions are prepared for the solutions of a later release and have no page in the portal yet. They need `solution-metamodel:write`, held by the roles `enterprise-architect` and `metamodel-admin`:

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/configurations" -H 'Content-Type: application/json' \
  -d '{"solutionId": "'"$SOLUTION"'", "name": "Cloud Migration 2027"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "CloudService", "configId": "'"$EXTENSION"'", "baseMetatypeId": "'"$COMPONENT"'"}'
curl "${auth[@]}" "$OEA/metamodel/metatypes?configId=$EXTENSION"
```

- `GET /metamodel/metatypes?configId=<extension>` returns the effective metamodel: the entity types of the instance and those of the extension, each with its `configId`. Without `configId`, the list contains only the entity types of the instance.
- An extension cannot use a name of the instance, nor one name twice (`409`, `name-taken`). An entity type of the instance cannot take a name used in an extension. Different extensions may use the same name.
- Base types and allowed sources and targets come from the instance or the same extension. An entity type keeps its configuration.
- Properties, categories and translations belong to the instance. You assign existing properties to the entity types of an extension.
- An extension has its own lock mode, so it stays editable while the instance is locked. You can delete an extension while it has no entity types.
- The entity types of an extension are not used for entities yet. The batch rejects such an entity with `METATYPE_NOT_FOUND`.
- Audit events: `metamodel.configuration-created`, `metamodel.configuration-updated` and `metamodel.configuration-deleted`.

`GET /metamodel/changes` lists the changes of the section *Changes*, newest first, with the filters `kind` (for example `metatype`, `mapping` or `package`), `actorRef`, `from`, `to` and `batchId` and the paging parameters `page` and `pageSize`. `actors` names every person who changed the metamodel. It needs `metamodel:write`.
