---
title: Deleted entities
covers:
  screens: [SCR-068]
  stories: [US-074]
---

# Deleted entities

When you delete an entity from the repository (for example *Delete from repository* on the canvas of a [diagram](./diagrams#the-canvas)), OEA does not remove it: the entity, its children and their connections are marked as deleted and can be restored. The page *Deleted entities* lists them. It needs the permission `entity:restore` (role `enterprise-architect`); without it the header has no entry *Deleted entities*, and the page shows *No permission*.

## Find deleted entities

Choose *Deleted entities* in the header. The page lists the deleted entities and connections, the latest deletion first. Each entry shows:

- the name and, for a connection, its source and target (also when they are deleted themselves), the entity type, and the person and time of the deletion;
- *Deleted with …* and the name of an entity, if the entry was taken along when that entity was deleted, for example a child or a connection of it.

Type a part of a name into *Filter by name* (up to 255 characters; characters such as `%` and `_` are matched as typed) and choose an entity type to narrow the list. The list shows 50 entries at a time; choose *Load more* for the next ones. Without any deleted entity the page says *No deleted entities.*

## Restore an entity

1. Choose *Restore* on an entry.
2. Confirm the dialog. The entity comes back together with what its deletion took along: its children and their connections that were deleted with it.

Afterwards a notice names the entities that were restored and those that stayed deleted. A connection stays deleted if its other endpoint is still deleted; restore that entity as well, and then the connection. An element whose parent is still deleted comes back at the top level, and the notice says so.

- **A single entry:** an entry that was taken along, for example a child, can be restored on its own. The dialog names the entity it was deleted with.
- **A connection with a deleted endpoint:** restoring it alone is refused. The page says so; restore the endpoint first.
- **Someone was faster:** if the entity was restored or changed meanwhile, the page says so and reloads the list.

Restoring changes no version and is recorded in the audit trail as `entity.undeleted` for each restored entity.

## Deleted rows in a catalog

A catalog can show deleted rows as well, with the switch *Show deleted* (see [Catalogs](./catalogs#show-deleted)). *Restore* works there the same way.

## Through the API

- `GET /entities/deleted` lists the deleted entities and connections (`entity:restore`): the latest deletion first, then by ID. `q` is a part of the name (at most 255 characters), `metatypeId` an entity type, `pageSize` is 1 to 200 (default 50) and `page` starts at 0. The answer carries `nextCursor`; send it as `cursor` together with `page` increased by 1 to continue. A cursor of another query is rejected with 400 naming `cursor`; other invalid values answer 400.
- Each entry has `id`, `name`, `metatype`, `isConnection`, for a connection `source` and `target` (`id` and `name`), `version`, `deletedAt`, `deletedBy` and `deletedWith` (`id` and `name`, or `null` for an entity that was deleted on its own). It carries no property values.
- `POST /entities/{id}/undelete` with the header `If-Match` and the `version` of the entry restores the entity (see [Diagrams](./diagrams#through-the-api)). The list is read only and not audited.
