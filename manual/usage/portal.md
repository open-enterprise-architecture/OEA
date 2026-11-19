---
title: Portal
covers:
  screens: [SCR-050, SCR-053]
  stories: [US-060, US-061, US-127, US-128]
---

# Portal

The portal shows the shared catalogs and the dashboards of the repository read-only. It is meant for people who read the architecture but do not work on it, for example with the role `portal-reader`. Everybody with the permission `portal:read` can open it; the roles `viewer`, `analyst` and `enterprise-architect` have it too.

## Open the portal

- With only `portal:read` (for example the role `portal-reader`), you land on the portal after login. The header shows *Portal* as its only entry; the start page and the catalogs lead to the portal as well.
- With `entity:read`, the header shows *Portal* next to *Catalogs*.

The page *Portal* has two sections. *Shared catalogs* lists the **shared catalogs** by name, each with its entity type; personal catalogs never appear in the portal, not even your own. *Dashboards* lists the **dashboards** (see [Dashboards in the portal](#dashboards)). If one list cannot be loaded, the page shows the error in its section and the other section stays usable.

## Read a catalog

Choose a catalog in the list. It opens with the columns, the default sort and the default join mode its owner configured.

- **Sort:** choose a column header. The first click sorts ascending, the second descending, the third returns to the default order.
- **Page:** the table shows 50 rows per page; the page numbers below the table move between pages.
- **Back:** *Back to the portal* returns to the list.

Sort and page are part of the address, so you can bookmark a sorted page or send it to someone. The portal offers no action that changes the catalog: no columns, joins, filters, views or navigation tree. If a catalog is no longer shared or does not exist, the page says *Catalog not found*.

The portal applies the [property visibility](../administration/property-visibility) like every catalog: a column on a property you may not see stays empty, and a column on a connection-based property shows the value only in the rows of entities connected to your element. With an [override role](../administration/property-visibility#override-roles) you see all values, and the query is recorded in the audit log.

## Dashboards

The section *Dashboards* lists, ordered by name, the **shared dashboards** and your **own personal dashboards**, each with its number of widgets; personal ones carry the badge *Personal*. A personal dashboard of another person never appears, not even for administrators; they read it in the work area (see [Dashboards](./dashboards)). Without any dashboard the section says *No dashboards yet.*

Choose a dashboard to open its read-only view:

- The widgets sit where their owner placed them in the 12-column grid. There is no *Edit*, *Add widget* or *Delete*, and no drag handle or resize handle; the portal changes nothing.
- The widgets show the same data as in the work area, computed with your permissions and the [property visibility](../administration/property-visibility): a KPI over a property you may not read for every entity says so instead of showing a value, and a widget that cannot be computed (for example because its catalog is gone) shows its message while the others render normally.
- A table widget pages through *Page x of y*. Its rows do not link to an entity; the portal has no entity detail yet.
- The page shows the time of the data (*Data as of …*). **Refresh** reads the data again; the page does not update on its own.
- *Back to the portal* returns to the list. If the dashboard does not exist or is not visible to you, the page says *Dashboard not found*.

If you hold only `portal:read` and open the address of a dashboard of the work area (`/dashboards` or `/dashboards/{id}`), you are taken to the portal, or to the same dashboard in the portal.

## Through the API

- `GET /portal/catalogs` lists the shared catalogs, ordered by name.
- `GET /portal/catalogs/{id}/query` returns one page of a shared catalog with `page`, `pageSize` (at most 200) and `sort` (`{column}:{asc|desc}`), the same result as `GET /catalogs/{id}/query` without filters, join mode overrides or views.
- The next page can be read with `cursor` (the `nextCursor` of the previous page) and `page` + 1, and a query above the time limit answers 503 `query-timeout`, as in the catalog query (see [Catalogs](./catalogs#browse)). A cursor of the catalog query is not valid in the portal. Columns on properties you may not see are empty, as in the catalog query.

Both need `portal:read`. A personal catalog answers 404, an invalid parameter 400 with the parameter in `invalidFields`.

The dashboards of the portal need `portal:read` only:

- `GET /portal/dashboards` lists the shared dashboards and your own personal ones, ordered by name, each with `canEdit: false`.
- `GET /portal/dashboards/{id}` returns the configuration (widgets, titles, settings and grid positions), `GET /portal/dashboards/{id}/data` the data of all widgets, and `GET /portal/dashboards/{id}/widgets/{widgetId}/data?page=` another page of a table widget (from 0; a negative page answers 400). The answers have the shape of the work-area reads (see [Dashboards](./dashboards#api)), the configuration always with `canEdit: false` and without an `ETag`. A table widget that exceeds the time limit of its catalog query answers 503 `query-timeout`.
- Another person's personal dashboard and an unknown dashboard or widget answer 404, also for administrators. Service accounts answer 403.
