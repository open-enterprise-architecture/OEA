---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-048, US-049, US-050]
---

# Catalogs

A catalog is a reusable table view of the repository. It shows the entities of one entity type as rows. You need the permission `entity:read` (for example the role `viewer`) to see catalogs and create personal ones; shared catalogs need `catalog:manage` (role `enterprise-architect`).

## Create a catalog

1. Choose *Catalogs* in the header, then *New catalog*.
2. Fill in the dialog *New catalog*:

   | Field | Meaning |
   |---|---|
   | Name | required; unique within the instance, regardless of upper and lower case |
   | Description | optional, up to 1000 characters |
   | Primary entity type | required; its entities become the rows of the table |
   | Visibility | *Shared*: visible to all users of the instance (needs the permission to manage catalogs); *Personal*: visible only to you |
   | Default join mode | *Aggregate*: one row per entity, linked entities in one cell; *Rows*: one row per linked entity. Applies to joins added later |

<Screenshot name="catalog-create" alt="Dialog New catalog with name, description, entity type and visibility" />

3. Choose *Create catalog*. The new catalog opens.

If the name is already taken, the dialog says so; choose another name. Without the permission to manage catalogs, *Shared* is not selectable.

## Browse a catalog

The list *Catalogs* shows every catalog you may see, marked *Shared* or *Personal*. Open one to see its table:

<Screenshot name="catalogs" alt="List of catalogs" />

<Screenshot name="catalog-table" alt="Table of a catalog with application components" />

- Names of entity types, column headers and values of enum properties appear in your language where your organization has translated them (see [Your profile](./login#your-profile)).
- As long as no columns are configured (see [Configure columns](#configure-columns)), the table shows the columns *Name* and – if the entity type has the property *Status* – *Status*.
- Values of an enum property, such as *Status*, are sorted in the order of the values, not alphabetically; entries without a value come last.
- Click a column header to sort: ascending, descending, and back to the default order.
- The table shows 50 entries per page; use the pagination below the table to move between pages.
- Sort and page are part of the address of the page. Back navigation, a reload or a shared link restore the same view; invalid values in the address are ignored and the first page is shown in the default order.
- If the entity type has no entities yet, the table shows *No entries found*.
- Another person's personal catalog cannot be opened; it behaves like a catalog that does not exist (*Catalog not found*).

Through the API, `GET /catalogs/{id}/query` returns the same rows with the parameters `page` (from 0), `pageSize` (1–200, default 50) and `sort` (`{field}:{asc|desc}`).

## Configure columns {#configure-columns}

You decide which attributes a catalog shows, in which order and under which header. A shared catalog needs the permission to manage catalogs (`catalog:manage`, role `enterprise-architect`); your own personal catalog you configure yourself.

1. Open the catalog and choose *Configure columns*.
2. The dialog lists every attribute of the entity type: the fields *Name*, *Description*, *External ID*, *Created*, *Updated* and *Version*, and every property assigned to the entity type, also the inherited ones.
3. Per row:
   - *Visible* shows or hides the column. A hidden column stays in the list, so you can show it again later.
   - *Header* replaces the name of the attribute in the table, for example *Operational Status* for *Status*.
   - *Sortable* decides whether readers may sort by the column.
4. Change the order by dragging a row, or with the arrow buttons of a row.
5. Under *Default sort*, choose the column and direction the table is sorted by when nobody chose a sort.
6. Choose *Save*. At least one column must be visible.

A property that is no longer assigned to the entity type shows as *Not available* and is removed when you save. Every change is recorded in the audit trail.

Through the API, `PUT /catalogs/{id}/columns` replaces the list. Each column names a field (`"field": "name"`, `description`, `externalId`, `createdAt`, `updatedAt`, `entityVersion`) or a property (`"propertyDefId": "…"`), with `displayLabel`, `visible`, `sortable` and at most one `defaultSortDirection` (`asc` or `desc`). An empty list restores the default columns.

## Joins {#joins}

A join shows related entities next to each entry of the catalog, for example all interfaces of an application. It follows a connection type of the metamodel, such as *Data Flow*. Joins are configured like columns: a shared catalog needs the permission to manage catalogs, your own personal catalog you configure yourself.

1. Open the catalog and choose *Joins*. The dialog lists the joins of the catalog.
2. Choose *Add join* and go through the four steps:
   1. *Connection type*: only connection types that connect the entity type of the catalog are offered.
   2. *Direction*: *Outgoing* follows connections that start at the entry, *Incoming* those that end at it, *Both directions* both; an entity reached in both directions is listed once.
   3. *Target type*: only entity types the connection type allows at its other end.
   4. *Name*, *Default mode* and the columns of the target type, chosen like in [Configure columns](#configure-columns). The name becomes the header above the columns of the join.
3. Choose *Save*. The table shows the join as a group of columns.

*Edit* changes a join, *Remove* removes it. A join whose connection type or target type no longer exists or no longer connects the entity types shows as *Not available* and is left out of the table. A catalog has at most 10 joins. Every change is recorded in the audit trail.

### Compact or rows

Every join has the switch *Compact / Rows* in its header:

- *Compact* shows one row per entry; a cell lists all linked entities, one per line. An entry without linked entities keeps its row with an empty cell.
- *Rows* shows one row per linked entity; the entry repeats. In this mode you can also sort by a column of the join, and the page counts rows.

The switch changes only your view, not the catalog. It is part of the address of the page, like sort and page. With several joins in *Rows*, each combination of their linked entities gets a row of its own. The *Default mode* of a join, or else the default join mode of the catalog, applies when you open the catalog.

Through the API, `POST /catalogs/{id}/joins` adds a join with `name`, `connectionMetatypeId`, `joinDirection` (`outbound`, `inbound`, `both`), `targetMetatypeId`, `targetColumns` (like the columns, without default sort) and `defaultJoinMode` (`rows`, `aggregate` or `null`). `PUT` and `DELETE /catalogs/{id}/joins/{joinId}` change and remove it. The query returns the results of every join in `joinResults` under the ID of the join; `joinMode[{joinId}]=rows` switches a join for one query, and `sort={joinId}.{key}:asc` sorts by a join column in rows mode.
