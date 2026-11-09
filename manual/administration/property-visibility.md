---
title: Property visibility
covers:
  screens: [SCR-032]
  stories: [US-124, US-125, US-126]
---

# Property visibility

Some properties should not be visible to everyone who can read an entity, such as investment costs or a security classification. Security administration decides for each property who may see and write its values. You need the role `security-admin` (permission `visibility:configure`); metamodel administrators cannot change the visibility (see [Roles and permissions](./roles-and-permissions)).

## What the visibility does

- **Role-based** properties are enforced in catalogs, in the portal and when writing entities:
  - Without a read role, a column on the property shows a lock and empty cells and cannot be sorted or filtered.
  - Without a write role, a batch or connection that sets or removes its value is rejected with `403`.
  - The old and new values of such a property are stored encrypted in the audit log.
- **Connection-based** properties stay hidden and cannot be written until their enforcement follows in a later version.

## The page

Open *Property visibility* in the header. The page lists every property with the entity types that use it, its visibility and its restriction; restricted properties carry the badge *Restricted*. Filter the list by name.

<Screenshot name="property-visibility" alt="Dialog for the visibility of a property with read and write roles" />

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

Both need `visibility:configure`.
