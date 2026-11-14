---
title: Change history
covers:
  screens: [SCR-026]
  stories: [US-098]
---

# Change history

Every change to an entity creates a new version. The page *Change history* shows who changed an entity when, and which fields each version changed. You need the permission `entity:read`; the roles `viewer`, `analyst` and `enterprise-architect` have it.

## Open the change history

In a [catalog](./catalogs) or in a catalog of the [portal](./portal), choose *History* in the row of an entity. The page opens with the versions of the entity, newest first. *Back to the catalog* returns to the catalog you came from, on the same page and with the same sort.

## Read the entries

Each entry shows:

- the **version number**, with *Created* for the first version;
- the **time** of the change in your local time zone;
- the **person** who made the change;
- the **changed fields**: system fields such as *Name* or *Description*, and the properties by their label.

The entries name the changed fields, not their values. The values and the comparison of two versions follow in a later release.

An entity that was never changed after its creation shows *No changes since creation. Created on {date} by {person}.*

## Restricted fields

A property you may not read under the [property visibility](../administration/property-visibility) never shows its value. Depending on the settings of your roles for the entity type, the field appears with the badge *Restricted*, or it is left out. If all fields of a version are left out, the version stays in the list without fields, so that the numbering has no gaps.

## Filter and load more

- **Person:** choose a person to see only the versions they made. The list offers everyone who changed the entity.
- **Period:** choose a start and an end date. Both days count fully: a version on the start or the end date is shown.

The page shows 50 versions at first. *Load more* adds the next 50.

## Through the API

`GET /entities/{id}/history` returns the versions newest first, with `from` and `to` (inclusive timestamps), `actorId`, `page`, `pageSize` (1 to 200, default 50) and `cursor` (the `nextCursor` of the previous page, sent with `page` + 1). The answer names the creation in `created` and every person who made a version in `actors`, independent of the filters. A start after the end answers 400 naming `from`, an unknown entity 404.
