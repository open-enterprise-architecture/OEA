---
title: Property visibility
covers:
  screens: [SCR-032, SCR-040]
  stories: [US-124, US-125, US-126, US-127, US-128]
---

# Property visibility

Some properties should not be visible to everyone who can read an entity, such as investment costs or a security classification. Security administration decides for each property who may see and write its values. You need the role `security-admin` (permission `visibility:configure`); metamodel administrators cannot change the visibility (see [Roles and permissions](./roles-and-permissions)).

## What the visibility does

- **Role-based** properties are enforced in catalogs, in the portal and when writing entities:
  - Without a read role, a column on the property shows a lock and empty cells and cannot be sorted or filtered.
  - Without a write role, a batch or connection that sets or removes its value is rejected with `403`.
  - The old and new values of such a property are stored encrypted in the audit log.
- **Connection-based** properties are enforced per entity on the same channels:
  - A person sees and writes the value only for entities that are connected through the chosen connection type to the element linked to the person's account, in either direction and over one connection (see [The entity type of persons](#person-metatype)).
  - In a catalog the column shows the value in the rows of such entities and an empty cell in all others. Sort and filter treat the empty cells as entries without a value, so the result never depends on a hidden value.
  - Without a linked element, or for the local system admin account, the column behaves like a role-based column without a read role.
  - A batch or connection that sets or removes the value of an entity outside the scope, or of an entity created in the same batch, is rejected with `403`; create the scoping connection first.
  - A new or removed scoping connection applies to the next request.
- **Override roles** read every property, whatever its visibility (see [Override roles](#override-roles)). Nobody else does: neither `system-admin` nor `security-admin` reads restricted values without a read role.

## The page

Open *Property visibility* in the header. The page lists every property with the entity types that use it, its visibility and its restriction; restricted properties carry the badge *Restricted*. Filter the list by name.

<Screenshot name="property-visibility" alt="Dialog for the visibility of a property with read and write roles" />

## The entity type of persons {#person-metatype}

Connection-based visibility starts from the element that represents a person in the model, for example a *Business Actor*. Choose this entity type in *Entity type of persons* above the list; *None* switches connection-based visibility off for everyone. Only entity types that are not connection types can be chosen, and an entity type in use here cannot be deleted. The change is written to the audit log as `visibility.person-metatype-changed`.

As long as connection-based properties exist but no entity type is set, the page shows a warning: the values of these properties are visible to nobody.

The account of a person is linked to their element through the API (see [Identity providers and persons](./identity-providers#link-an-account-to-an-element)). A link counts only while the element exists and is of the chosen entity type or one of its subtypes; changing the entity type keeps the links, but links to elements of another type stop counting.

## Override roles {#override-roles}

Exceptional work, such as an error analysis or a data migration, may need someone to see every restricted value without changing the read roles of each property. For this, security administration puts a role on the override list: open *Override roles* in the header, or follow the link on the page *Property visibility*.

<Screenshot name="override-roles" alt="Override roles with the audit warning and the role selection" />

- Tick the roles and choose *Save*. The list is empty after the installation and holds at most 20 roles. Usually you use a [role of your own](./roles-and-permissions#custom-roles), such as `migration-support`, and assign it only for the duration of the work.
- Holders of an override role see every value in catalogs and in the portal and can sort and filter by it, but only of the entities they can read anyway: the override role grants no access to entities, and it does not allow writing restricted values.
- Every query that shows a value only because of an override role writes one entry `property-override-access` to the audit log, with the properties and the entities whose values were shown. A value the person could read through a read role or a connection does not count.
- Every change of the list is written to the audit log as `visibility.override-changed`, with the old and the new list; adding one of your own roles is marked as self-benefiting.
- A change applies to the next request.

## Change the visibility

Click the pencil of a property and choose the visibility:

- **Public:** everyone who can read the entity sees the value. This is the default of every property.
- **Role-based:** only holders of the chosen read roles see the value, and only holders of the chosen write roles may change it. Choose at least one read role; every write role must also be a read role. Without a write role nobody may change the value.
- **Connection-based:** only persons connected to the entity through the chosen connection type see and change the value, for example a domain architect connected through *Domain Assignment*. Only connection types can be chosen.

The dialog shows only the fields of the chosen visibility and points out a missing selection before it saves. Every change is written to the audit log as `visibility.changed`; a change that grants one of your own roles is marked as self-benefiting.

As long as a connection type is used for a connection-based property, creating connections of that type needs the permission `visibility-scope:assign` (role `security-admin`), so that nobody grants themselves access. Such a connection type cannot be deleted.

In the metamodel configuration, restricted properties show the badge *Restricted*, and the dialog of a property shows its visibility read-only (see [Editing the metamodel](./metamodel-editing)).

## Through the API

- `GET /admin/property-visibility` lists the visibility of every property with `visibilityMode`, `readRoles`, `writeRoles`, `scopingConnectionType` and the entity types in `metatypes`.
- `PUT /admin/property-visibility` changes the listed properties in one step, for example:

  ```bash
  jq -n --arg id "$PROPERTY_ID" '[{propertyDefId: $id, visibilityMode: "role-restricted",
    readRoles: ["enterprise-architect", "auditor"], writeRoles: ["enterprise-architect"]}]' |
    curl "${auth[@]}" -X PUT -d @- "$OEA/admin/property-visibility"
  ```

  An invalid entry answers `422` with the field in `invalidFields`, such as `[0].readRoles`, and nothing changes.

- `GET` and `PUT /admin/property-visibility/person-metatype` read and set the entity type of persons, for example `{"metatypeId": "<entity type ID>"}`, or `{"metatypeId": null}` for none. A connection type or an unknown entity type answers `422` with `metatypeId` in `invalidFields`.

- `GET` and `PUT /admin/property-visibility/override-roles` read and replace the override roles, for example `{"roles": ["migration-support"]}`. An unknown role answers `422` with `roles` in `invalidFields`; more than 20 roles answer `400`.

All need `visibility:configure`.
