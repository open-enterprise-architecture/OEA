---
title: Metamodel packages
covers:
  screens: []
  stories: [US-033]
---

# Metamodel packages

A new instance has no entity types. Before anyone can capture entities, you import a metamodel: the shipped starter package or a package of your own.

Importing packages requires the permission `metamodel:write`, which only the role `metamodel-admin` holds – not the local system admin account. Map an IdP group to `metamodel-admin` (see [Roles and permissions](./roles-and-permissions)); as a privileged role, it needs a second factor at the identity provider.

## Import the starter configuration

OEA ships the starter package `oea-starter-togaf-classic` with the entity types Application Component, Application Service, Data Object, Technology Component, Technology Service, System Software and Network Node, each with the property *Status*.

While the instance has no entity type, the start page shows *Import starter configuration* to a person with `metamodel:write`. Choose *Import* next to the package; the page confirms how many entity types are now available.

Through the API:

```bash
curl "${auth[@]}" "$OEA/admin/metamodel/starter-packages"                          # list with summary
curl "${auth[@]}" -X POST "$OEA/admin/metamodel/starter-packages/oea-starter-togaf-classic/import"
curl "${auth[@]}" "$OEA/metamodel/metatypes"                                       # imported entity types
```

## Import your own package

Packages of your own use the same JSON format (`MetamodelPackage`, with `packageId`, `packageVersion`, `publisher` and stable UUIDs per element). Import them with `POST /admin/metamodel/import`:

- A package is imported completely or not at all. Imported content gets the scope `imported` and the package as origin.
- Importing the same package again updates its own content. Content of another package or of your organization is never overwritten; the answer is `409` with the list of conflicts.
- Packages larger than `oea.metamodel.import.max-size` (default 10 MB) are rejected with `413`; faulty JSON or content that does not match the format with `422` and the location of the problem.
- The strategy `replace`, viewpoints, shape assignments and translation bundles are not supported yet (`422`).

Every import writes the audit events `metamodel.package-imported` and `metamodel.metatype-imported` with one batch ID.
