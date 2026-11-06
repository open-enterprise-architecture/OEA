---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-048, US-049, US-050, US-051, US-052, US-080]
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

## Filters {#filters}

Filters narrow the table to the entries you need, for example the active applications only. Every reader of a catalog can set filters; they change only your view, not the catalog.

1. Choose *Add filter* above the table.
2. Choose the *Column*: every column of the table, including the columns of the joins (shown as *Join · Column*) and, in a [connection catalog](#connection-catalogs), the columns of the source and the target.
3. Choose the *Operator*. Only the operators that fit the values of the column are offered:

   | Values of the column | Operators |
   |---|---|
   | Text (name, description, text properties) | *equals*, *not equals*, *contains*, *starts with*, *is one of*, *is none of*, *is empty*, *is not empty* |
   | Enum values, such as *Status* | *equals*, *not equals*, *is one of*, *is none of*, *is empty*, *is not empty* |
   | Yes or no | *equals*, *not equals*, *is empty*, *is not empty* |
   | Numbers and dates (also *Created*, *Updated* and *Version*) | *equals*, *not equals*, *greater than*, *less than*, *at least*, *at most*, *is empty*, *is not empty* |

4. Enter or choose the *Value* and choose *Apply*. For *is one of* and *is none of*, choose several enum values, or separate several text values with commas.

Each filter appears as a chip above the table, for example *Status equals active*; the *×* of a chip removes it. The total below the table counts only the entries that pass, and the table starts again at the first page.

- Text comparisons ignore upper and lower case. *not equals* and *is none of* also keep entries without a value.
- Dates compare by day; *Created* and *Updated* use the day in UTC.
- **Join columns:** a filter on a column of a join keeps only the entries with at least one matching linked entity, and the join shows only the matching ones. A join without a filter stays complete.
- **AND or OR:** from two filters on, choose *All filters (AND)* or *Any filter (OR)*. With OR, an entry passes if any filter holds.
- A table has at most 20 filters.
- **The address:** filters are part of the address of the page, like sort and page. Back navigation, a reload or a shared link show the same filtered view. A filter the catalog cannot apply, for example on a column that was removed, is dropped, and the table shows all entries.

### Saved filters

A catalog keeps named filters for quick selection, for example *Active systems only*:

- **Save:** with the filters set, choose *Save filter*, enter a name and, optionally, a description, and choose *Save*. The name must be unique in the catalog, regardless of upper and lower case. A catalog has at most 50 saved filters.
- **Use:** *Saved filters* next to the chips lists them for every reader of the catalog. Choosing one replaces the filters of the table with its conditions and its AND or OR.
- **Not available:** a saved filter on a column the catalog no longer shows is marked *not available* and cannot be chosen. Removing a join also removes the conditions of saved filters on its columns, and a saved filter left without conditions.

Saving filters follows the rules for configuring a catalog. A shared catalog needs the permission to manage catalogs (`catalog:manage`); in your own personal catalog you save them yourself. Without that right, *Save filter* is not offered; create a personal catalog to keep your own filters. Saved filters are recorded in the audit trail.

Through the API, `GET /catalogs/{id}/query` takes one `filter={key}:{operator}[:{value}]` per filter, for example `filter=status:eq:active`, `filter=source.name:contains:CRM` or `filter={joinId}.protocol:in:REST,SOAP`, and `filterMode=or`. The key is the column key of the result, the operator one of `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte` and `lte`; an invalid filter answers 400. `POST /catalogs/{id}/filters` saves a filter with `name`, `description`, `logicalOperator` and `expressions` (`attributePath`, `operator`, `value`), and `DELETE /catalogs/{id}/filters/{filterId}` removes it.

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

## Connection catalogs {#connection-catalogs}

A catalog can also list connections, for example all data flows between applications. Choose a connection type as *Primary entity type* when you create the catalog; the dialog lists the entity types under *Elements* and the connection types under *Connections*.

- Each connection becomes a row. As long as no columns are configured, the table shows *Source*, *Name* and *Target*: the entity the connection starts at, the connection itself and the entity it ends at.
- *Configure columns* also lists the attributes of the source and the target, for example *Source: Vendor*. Offered are the fields and the properties of the entity types the connection type allows at that end, with their subtypes; a connection type without such a restriction offers every property. An entity at that end without the property shows an empty cell.
- These columns sort like any other column. If the source or target has been deleted, its cells stay empty.
- A join starts at the connection itself, for example from a data flow to the data objects it carries. If no connection type starts or ends at the connection type of the catalog, the dialog *Joins* says so.

Through the API, a column of the source or target carries `"endpoint": "source"` or `"target"`, and its key in the query is `source.{key}` or `target.{key}`, for example `sort=source.name:asc`.
