---
title: Catalogs
covers:
  screens: [SCR-020, SCR-028]
  stories: [US-046, US-049]
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

- As long as no columns are configured, the table shows the columns *Name* and – if the entity type has the property *Status* – *Status*.
- Click a column header to sort: ascending, descending, and back to the default order.
- The table shows 50 entries per page; use the pagination below the table to move between pages.
- Sort and page are part of the address of the page. Back navigation, a reload or a shared link restore the same view; invalid values in the address are ignored and the first page is shown in the default order.
- If the entity type has no entities yet, the table shows *No entries found*.
- Another person's personal catalog cannot be opened; it behaves like a catalog that does not exist (*Catalog not found*).

Through the API, `GET /catalogs/{id}/query` returns the same rows with the parameters `page` (from 0), `pageSize` (1–200, default 50) and `sort` (`{field}:{asc|desc}`).
