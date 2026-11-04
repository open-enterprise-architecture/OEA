---
title: Editing the metamodel
covers:
  screens: []
  stories: [US-032, US-137, US-140]
---

# Editing the metamodel

Besides importing packages (see [Metamodel packages](./metamodel-packages)), you maintain entity types and properties of your own organization. The form in the web portal follows; until then you edit the metamodel through the API. All operations require `metamodel:write` (role `metamodel-admin`) and are recorded in the audit trail with your name.

## Entity types

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes" -H 'Content-Type: application/json' \
  -d '{"name": "SecurityZone", "abbreviation": "SZ", "configId": "00000000-0000-0000-0000-000000000001"}'
```

- Entity types you create get the scope `organization` and can be used for entities at once.
- Names are unique across all entity types, regardless of upper and lower case: `application component` is rejected with `409` (`name-taken`) while *Application Component* exists.
- You change and delete only your own entity types. Built-in and imported ones are read-only (`409`, `read-only-metatype`); you can still add your own properties to them.
- An entity type with entities – also deleted ones –, one used by a catalog or one that is the base type of another type cannot be deleted (`409`, `in-use`, with the number of entities in `count`).
- Connection types follow in a later release; `isConnection: true` is rejected.

### Base types

With `baseMetatypeId`, an entity type inherits all properties of its base type and of the base type's own base types. `GET /metamodel/metatypes/{id}` lists the inherited properties with `inheritedFrom`. A property may appear only once in a chain; base types cannot form a cycle. Packages can name a base type of the same package or a built-in one.

If you change the base type of your own entity type, it loses the properties of the old base type – with their values, see below – and gains those of the new one.

## Properties

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions" -H 'Content-Type: application/json' \
  -d '{"name": "Trust Level", "dataType": "enum"}'
curl "${auth[@]}" -X POST "$OEA/metamodel/property-definitions/$PROPERTY/enum-values" \
  -H 'Content-Type: application/json' -d '{"value": "high"}'
```

- Data types: `string`, `text`, `integer`, `boolean`, `date`, `url` and `enum`. Reference properties follow with the connection types.
- OEA derives the storage name `sqlColumnName` from the name, for example `trust_level`. It is used in reports and never changes, even if you rename the property.
- You cannot change the data type once entities have values for the property, also in their history.
- A property that is still assigned to an entity type cannot be deleted. A property that appears in the history of entities is retired instead: it disappears from the lists, and its name stays taken.
- Property categories (`/metamodel/property-categories`) group properties; a category with properties cannot be deleted.

## Assigning properties to entity types

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes/$TYPE/property-mappings" -H 'Content-Type: application/json' \
  -d '{"propertyDefId": "'"$PROPERTY"'", "necessity": "mandatory"}'
```

- You assign your own properties to any entity type, also to imported and built-in ones. The properties of a package stay unchanged.
- The necessity is `mandatory`, `warning` or `optional`; `validFrom` defaults to today.

### A new mandatory property

Assigning a mandatory property always succeeds, even if entities exist. OEA marks every existing entity of the type and its subtypes without a value; the response states the number in `markedEntityCount`. A marked entity keeps working until someone changes it: saving it without the value is rejected with `MANDATORY_MISSING`. Setting the value removes the mark.

Mandatory properties are checked only where the writer can change them. Properties with restricted visibility cannot be written yet, so they are never reported as missing.

### Changing the necessity by effective date

A metamodel evolves: a property that is optional today can become mandatory from a certain date. Assign the property again with the new necessity and the date from which it applies:

```bash
curl "${auth[@]}" -X POST "$OEA/metamodel/metatypes/$TYPE/property-mappings" -H 'Content-Type: application/json' \
  -d '{"propertyDefId": "'"$PROPERTY"'", "necessity": "mandatory", "validFrom": "2027-01-01"}'
```

- The earlier entries stay; the entry with the latest date not after today applies. A second entry with the same date is rejected with `409` (`conflict`).
- A date in the past applies at once. On a future date, OEA marks the entities without a value when the date is reached – at the latest about ten minutes after midnight (UTC) – and writes the audit event `metamodel.mapping-effective`. If a property is no longer mandatory, its marks disappear.
- This also works for the properties of a package, for example to make *Status* mandatory. Your entry has the scope `organization`; the package stays unchanged. If the package itself brings an entry on the same date, yours wins.
- Entries are made on the entity type that holds the property, not on its subtypes.
- `GET /metamodel/metatypes/{id}/property-mappings` shows the entries in effect today; with `?includeHistory=true` you get all entries with their `state` (`past`, `active`, `future`).
- To correct an entry, remove it and add a new one. Removing one of several entries of a property deletes no values.

### Removing a property

Removing an assignment of your own property – or one of your entries – is always possible:

```bash
curl "${auth[@]}" -X DELETE "$OEA/metamodel/metatypes/$TYPE/property-mappings/$MAPPING"
```

::: warning Values are deleted
If the entity type then no longer has the property and entities of the type or its subtypes have values for it, these values are deleted. Every affected entity gets a new version; the removed value remains visible in its history. The audit event `metamodel.mapping-deleted` states the number of affected entities. Deleting a value of an enum property works the same way for the entities that hold this value.
:::

## Translating labels {#translating-labels}

Users see the names of entity types, categories and properties and the values of enum properties in the language of their user interface (see [Your profile](../usage/login#your-profile)). You maintain the translations per language – `de`, `en`, `fr` or `it` – through the API until the form in the web portal follows:

```bash
curl "${auth[@]}" "$OEA/metamodel/labels/de"     # every element with its name, package label and your label
curl "${auth[@]}" -X PUT "$OEA/metamodel/labels/de" -H 'Content-Type: application/json' \
  -d '{"labels": {"metatype.'"$TYPE"'.name": "Anwendungskomponente", "enum-value.'"$VALUE"'.label": ""}}'
```

- A label key names the element by its identifier: `metatype.{id}.name`, `property-category.{id}.name`, `property-def.{id}.label` or `enum-value.{id}.label`. Keys you do not name stay unchanged; an empty text removes your label. Texts have at most 200 characters.
- You can translate every element, also those of a package. Your label wins over the label of the package; without either, users see the name in your corporate language.
- An unknown key rejects the whole change with `422` (`validation-failed`) naming the keys. Every change writes the audit event `metamodel.labels-changed`.
- Open web portals pick up a change within five minutes, without a reload.

## Organization properties at a package re-import

When a package is imported again, it replaces its own properties of its entity types. Whether your own properties and entries on these types stay depends on the way you work, so you configure it:

- **Globally** with `PUT /metamodel/settings/reimport-policy` and `{"organizationMappings": "keep"}` (default) or `"remove"`.
- **Per entity type** with `PUT /metamodel/metatypes/{id}/reimport-policy` and `inherit` (default), `keep` or `remove`. `inherit` takes the setting of the base type; at the top of the chain the global setting applies. `GET /metamodel/metatypes/{id}` shows the own value in `reimportPolicy` and the applying one in `effectiveReimportPolicy`.

Removing at a re-import deletes the values as described above.
