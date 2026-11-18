---
title: Dashboards
covers:
  screens: [SCR-024, SCR-056]
  stories: [US-055, US-058, US-059, US-060, US-062]
---

# Dashboards

A dashboard gives readers a structured view of the architecture. It arranges **widgets** in a grid of 12 columns: text widgets with formatted notes, and table widgets that show the rows of a [catalog](./catalogs). You need the permission `entity:read` (for example the role `viewer`) to see dashboards and to create personal ones; shared dashboards need `dashboard:manage` (role `enterprise-architect`).

There are two kinds of dashboards:

- **Shared** dashboards are visible to every reader of the instance. Every person with `dashboard:manage` can change and delete every shared dashboard.
- **Personal** dashboards are visible to you only. Nobody else sees them in a list, and their address answers *The dashboard does not exist or you may not see it*. The exception are administrators with the permission `settings:manage`: they see the personal dashboards of other persons in the list, marked with the owner, and can read them, but never change or delete them.

Service accounts have no access to dashboards.

## The page Dashboards

Choose *Dashboards* in the header. The page lists the dashboards you may see with their visibility (*Shared* or *Personal*), the owner and the number of widgets. Choose a dashboard to open it.

## Create a dashboard

1. Choose *Create dashboard* on the page *Dashboards*.
2. Fill in the dialog:

   | Field | Meaning |
   |---|---|
   | Name | required, 1 to 200 characters; unique among the shared dashboards and among your personal dashboards, regardless of upper and lower case |
   | Description | optional, up to 1000 characters |
   | Visibility | *Shared*: visible to every reader (needs the permission to manage dashboards); *Personal*: visible to you only |

3. Choose *Create dashboard*. The new dashboard opens with the notice "No widgets yet. Add widget."

If the name is already taken, the dialog says so. Without the permission to manage dashboards, *Shared* is not selectable.

## Read a dashboard

A dashboard shows its widgets at their positions. The data is computed each time you open it, with your own permissions: a table widget shows the catalog as you may see it, including locked columns (see [Property visibility](../administration/property-visibility)).

- A **text widget** shows its Markdown as formatted text.
- A **table widget** shows the rows of its catalog, with the columns of its saved view if it has one. Below the table, *Page 1 of 5 · 47 entries* and the pagination move between pages.
- A widget whose catalog is gone or not visible to you shows *Catalog not found*; a widget whose saved view is gone shows *Saved view not found*. The other widgets stay unaffected.
- Widgets that overlap are marked with a red border and the note *Overlaps another widget*.

## Change a dashboard

If you may change a dashboard, the page has the button *Edit*. In the edit mode you can:

- **Add widget**: choose the type *Text* or *Table*, enter a title of 1 to 150 characters and configure it (see below). New widgets go below the existing ones and can then be arranged. A dashboard has at most 50 widgets.
- **Edit** or **Remove** a widget with the buttons in its header. The type of a widget cannot change; remove it and add a new one.
- **Rename** the dashboard, change its description, or **Share with everyone**: a personal dashboard becomes shared, if you hold `dashboard:manage`, the name is free among the shared dashboards, and no widget shows a personal catalog. A shared dashboard does not become personal again.
- **Delete** the dashboard with all its widgets, after a confirmation.

Choose *Done* to leave the edit mode.

### Text widgets

A text widget holds Markdown of up to 10000 characters, for example methodological notes: bold and italic text, lists, quotes, headings, tables and links. Links work with `http`, `https` and `mailto` only. Raw HTML is not interpreted; it appears as text. Scripts, event handlers and other schemes such as `javascript:` never run.

### Table widgets

A table widget needs a catalog you may see. On a shared dashboard only shared catalogs can be chosen, so that every reader sees data; a personal dashboard can also show your personal catalogs. Optionally choose a saved view of that catalog for its columns, and the rows per page (5 to 100, default 10). The widget always reads current data through the catalog query, as the reader.

## Arrange the widgets

The grid has 12 columns. A widget has a column, a width, a row and a height:

| Property | Allowed values |
|---|---|
| Column | 1 to 12 |
| Width | 1 to 12; column + width is at most 13 |
| Row | 1 to 500 |
| Height | 1 to 6 rows |

In the edit mode:

- **Drag** a widget by the handle at the left of its title to move it, and drag the corner at the bottom right to resize it.
- Or use the **arrangement buttons** below a widget (move left, right, up, down; one column narrower or wider; one row shorter or taller), or the **arrow keys** on the handle: arrow keys move the widget, with Shift they resize it.

Every change is saved at once, as one request. If a widget would reach beyond column 12, the page says *Widget exceeds the grid width* and does not save the position. Overlaps are allowed; the editor marks them.

## Changes by several people

Every change refers to the version of the dashboard you have loaded. If someone else changed the dashboard in the meantime, the page reloads it and says so; repeat your change.

## API

The dashboards are available through `/dashboards` and `/views/dashboards` (see the [API reference](../api/)):

- `GET /dashboards` and `POST /dashboards` list and create dashboards; `PUT` and `DELETE /dashboards/{id}` change and delete one.
- `POST /dashboards/{id}/widgets`, `PUT` and `DELETE /dashboards/{id}/widgets/{widgetId}` change widgets, and `PUT /dashboards/{id}/layout` positions up to 50 widgets in one request. Every write needs the header `If-Match` with the version that was read (`"3"`); without it the answer is 428, with another version 412. The answers carry the new version as `ETag`.
- `GET /views/dashboards/{id}` returns the configuration, `GET /views/dashboards/{id}/data` the data of all widgets, and `GET /views/dashboards/{id}/widgets/{widgetId}/data?page=` another page of a table widget (from 0).
- A taken name answers 422 with the problem type `urn:oea:problem:dashboard-name-taken`; other invalid input answers 422 with `invalidFields`. The personal dashboard of another person answers 404.
