---
title: Portal
covers:
  screens: [SCR-050]
  stories: []
---

# Portal

The portal shows the shared catalogs of the repository read-only. It is meant for people who read the architecture but do not work on it, for example with the role `portal-reader`. Everybody with the permission `portal:read` can open it; the roles `viewer`, `analyst` and `enterprise-architect` have it too.

## Open the portal

- With only `portal:read` (for example the role `portal-reader`), you land on the portal after login. The header shows *Portal* as its only entry; the start page and the catalogs lead to the portal as well.
- With `entity:read`, the header shows *Portal* next to *Catalogs*.

The page *Portal* lists the **shared catalogs** by name, each with its entity type. Personal catalogs never appear in the portal, not even your own.

## Read a catalog

Choose a catalog in the list. It opens with the columns, the default sort and the default join mode its owner configured.

- **Sort:** choose a column header. The first click sorts ascending, the second descending, the third returns to the default order.
- **Page:** the table shows 50 rows per page; the page numbers below the table move between pages.
- **Back:** *Back to the portal* returns to the list.

Sort and page are part of the address, so you can bookmark a sorted page or send it to someone. The portal offers no action that changes the catalog: no columns, joins, filters, views or navigation tree. If a catalog is no longer shared or does not exist, the page says *Catalog not found*.

The portal shows only properties with the visibility *public*, like every catalog.

## Through the API

- `GET /portal/catalogs` lists the shared catalogs, ordered by name.
- `GET /portal/catalogs/{id}/query` returns one page of a shared catalog with `page`, `pageSize` (at most 200) and `sort` (`{column}:{asc|desc}`), the same result as `GET /catalogs/{id}/query` without filters, join mode overrides or views.
- The next page can be read with `cursor` (the `nextCursor` of the previous page) and `page` + 1, and a query above the time limit answers 503 `query-timeout`, as in the catalog query (see [Catalogs](./catalogs#browse)). A cursor of the catalog query is not valid in the portal. Columns on properties you may not see are empty, as in the catalog query.

Both need `portal:read`. A personal catalog answers 404, an invalid parameter 400 with the parameter in `invalidFields`.
