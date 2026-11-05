---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-047, US-049]
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
