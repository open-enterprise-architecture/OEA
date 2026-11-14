---
title: Change history
covers:
  screens: [SCR-026]
  stories: [US-098, US-099, US-100]
---

# Change history

Every change to an entity creates a new version. The page *Change history* shows who changed an entity when, which fields each version changed, and the values before and after. You need the permission `entity:read`; the roles `viewer`, `analyst` and `enterprise-architect` have it.

## Open the change history

In a [catalog](./catalogs) or in a catalog of the [portal](./portal), choose *History* in the row of an entity. The page opens with the versions of the entity, newest first. *Back to the catalog* returns to the catalog you came from, on the same page and with the same sort.

## Read the entries

Each entry shows:

- the **version number**, with *Created* for the first version;
- the **time** of the change in your local time zone;
- the **person** who made the change;
- the **changed fields**: system fields such as *Name* or *Description*, and the properties by their label.

The entries name the changed fields. Their values appear when you open a version.

An entity that was never changed after its creation shows *No changes since creation. Created on {date} by {person}.*

## See the values of a version

Choose *Show changes* in an entry. It lists each changed field with its old value in red and struck through, and its new value in green; a field without value shows *—*. Only fields whose value differs are listed. *Created* has no changes to show.

*Show complete state vN* lists every field of the entity as it was at that version: the system fields and the properties of its entity type, including empty ones. The properties are those assigned to the entity type at the time of the version. If a package import later removed an assignment, an empty property of that assignment is missing from older states; a property that had a value then is always listed.

Versions cannot be changed or deleted.

## Compare two versions

Choose *Compare*, then select two versions. A panel above the timeline shows the fields whose value differs between the earlier and the later version. A field that was changed and later changed back does not appear. Selecting a third version replaces the version you selected first. *End comparison* closes the panel.

## Restricted fields

A property you may not read under the [property visibility](../administration/property-visibility) never shows its value, neither in the changes nor in the complete state. Depending on the settings of your roles for the entity type, the field appears with the badge *Restricted*, or it is left out. If all fields of a version are left out, the version stays in the list without fields, so that the numbering has no gaps.

## Filter and load more

- **Person:** choose a person to see only the versions they made. The list offers everyone who changed the entity.
- **Period:** choose a start and an end date. Both days count fully: a version on the start or the end date is shown.

The page shows 50 versions at first. *Load more* adds the next 50.

## Through the API

`GET /entities/{id}/history` returns the versions newest first, with `from` and `to` (inclusive timestamps), `actorId`, `page`, `pageSize` (1 to 200, default 50) and `cursor` (the `nextCursor` of the previous page, sent with `page` + 1). The answer names the creation in `created` and every person who made a version in `actors`, independent of the filters. A start after the end answers 400 naming `from`, an unknown entity 404.

`GET /entities/{id}/versions/{version}` returns the complete state of a version in `fields`, each with `field`, `label`, `dataType`, `value` and `restricted`. `GET /entities/{id}/diff?from=2&to=7` returns the fields whose value differs between two versions in `changes`, each with `oldValue`, `newValue` and `redacted`; the changes of version N are `from=N-1&to=N`. `from` must be at least 1 and before `to`, otherwise 400 naming `from`; an unknown version answers 404. `PUT` and `DELETE` on a version answer 405.
