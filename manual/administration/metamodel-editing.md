---
title: Editing the metamodel
covers:
  screens: [SCR-030]
  stories: [US-032, US-137, US-140]
---

# Editing the metamodel

Besides importing packages (see [Metamodel packages](./metamodel-packages)), you maintain the entity types and properties of your own organization on the page *Metamodel* of the web portal. Everyone with read access (permission `entity:read`, for example the role `viewer`) sees the metamodel there; changes need `metamodel:write` (role `metamodel-admin`). Every change is recorded in the audit trail with your name.

## The metamodel page {#the-metamodel-page}

Choose *Metamodel* in the header. The page has five sections:

| Section | Content |
|---|---|
| Entity types | the list of all entity types with abbreviation and origin, and the selected entity type with its properties |
| Properties | all properties with data type, category, values and limits |
| Categories | the categories that group properties |
| Translations | the names of all elements per language |
| Settings | what happens to your own properties when a package is imported again (administrators only) |

The origin tells you what you may change: *Own* content belongs to your organization. *Built-in* content is part of OEA, and content of a package (*Package oea-starter-togaf-classic 1.1.0*) comes from an import. Built-in and imported content is read-only; you can still add your own properties to such entity types. On a narrow screen, the page shows the list and the selected entity type one after the other.

<Screenshot name="metamodel-entity-type" alt="Metamodel page with the list of entity types and the selected entity type Security Zone with its properties" />

## Entity types

1. In *Entity types*, choose *New*.
2. Enter the name, an optional abbreviation of up to 10 characters, the sort order and an optional base type.
3. Choose *Save*. The entity type appears in the list and can be used for entities and catalogs at once.

<Screenshot name="metamodel-entity-type-new" alt="Dialog New entity type with name, abbreviation, sort order and base type" />

- Names are unique across all entity types, regardless of upper and lower case: *application component* is rejected while *Application Component* exists.
- You change (*Edit*) and delete (*Delete*) only your own entity types.
- An entity type with entities – also deleted ones –, one used by a catalog or one that is the base type of another type cannot be deleted. The message names the number of entities.
- Connection types follow in a later release.

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

### Removing a property

You can always remove your own entries of a property (*Remove entry*).

::: warning Values are deleted
If the entity type then no longer has the property and entities of the type or its subtypes have values for it, these values are deleted. Every affected entity gets a new version; the removed value remains visible in its history. The dialog warns you before. Deleting a value of a selection list works the same way for the entities that hold this value.
:::

## Translating labels {#translating-labels}

Users see the names of entity types, categories and properties and the values of selection lists in the language of their user interface (see [Your profile](../usage/login#your-profile)).

1. Open *Translations* and choose the language: German, English, French or Italian.
2. Enter the translations. The placeholder shows the translation of the package or the name.
3. Choose *Save*.

<Screenshot name="metamodel-translations" alt="Section Translations with the German name Sicherheitszone for the entity type Security Zone" />

- You can translate every element, also those of a package. Your translation wins over the one of the package; without either, users see the name in your corporate language. Clearing a field removes your translation.
- Texts have at most 200 characters.
- Your portal shows the new names at once; other open portals pick them up within five minutes, without a reload.

## Organization properties at a package re-import

When a package is imported again, it replaces its own properties of its entity types. Whether your own properties and entries on these types stay depends on the way you work, so you configure it:

- **Globally** in *Settings*: *Keep own properties* (default) or *Remove own properties*.
- **Per entity type** in the detail under *Re-import of packages*: *Like the base type* (default), *Keep own properties* or *Remove own properties*. *Like the base type* takes the setting of the base type; at the top of the chain the global setting applies. *Applies* shows the setting in effect.

Removing at a re-import deletes the values as described above.

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
- A label key names the element by its identifier: `metatype.{id}.name`, `property-category.{id}.name`, `property-def.{id}.label` or `enum-value.{id}.label`; an empty text removes your label, and an unknown key rejects the whole change.
- `GET /metamodel/metatypes/{id}/property-mappings?includeHistory=true` lists all entries of the assignments with their `state`. The re-import settings are `PUT /metamodel/settings/reimport-policy` and `PUT /metamodel/metatypes/{id}/reimport-policy`.
- Every change writes an audit event, for example `metamodel.metatype-updated`, `metamodel.mapping-deleted` with the number of affected entities, `metamodel.mapping-effective` when an entry takes effect, or `metamodel.labels-changed`.
