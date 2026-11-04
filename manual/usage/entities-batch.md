---
title: Writing entities in batch
covers:
  screens: []
  stories: [US-141, US-036]
---

# Writing entities in batch

Source systems – a repository scanner, a CMDB export, a script – create and update entities through `POST /api/v1/entities/batch`. In this version, the caller is a person with the permission `entity:write` (role `enterprise-architect`) and a session (see [Log in for administration through the API](../administration/#log-in-for-administration-through-the-api)); service accounts with client credentials follow in a later version.

```bash
curl "${auth[@]}" -X POST "$OEA/entities/batch" \
  -H 'X-Agent-Id: repo-scanner' -H 'X-Agent-Run: 2026-10-16-nightly' \
  -d '{"strategy": "upsert", "entities": [{"name": "CRM System", "metatypeId": "<id>", "externalId": "git:org/crm",
       "properties": [{"propertyDefId": "<id of Status>", "value": "active"}]}]}'
```

`GET /metamodel/metatypes` returns the IDs of the entity types and their properties.

## How items are written

- `externalId` identifies an entity from the source system. With the strategy `upsert`, an unknown ID creates the entity and a known one updates it; `create-only` and `skip-existing` behave as named.
- On update, fields and properties missing in the item stay unchanged; an empty value removes a property value. An unchanged item is reported as `unchanged` and creates no version.
- With `atomic=false` (default), every item is written on its own and the answer reports a status per item. With `atomic=true`, all items are written or none (`422` with the errors per item).
- A request contains at most `oea.entities.batch.max-items` entities (default and maximum 500) and at most 10 MB.
- `X-Agent-Id` and `X-Agent-Run` are stored only as informational fields of the audit events.

Not supported yet: `dryRun=true`, `parentId`, `lifecycle` and properties with restricted visibility (`422` or an error for the item).

Every written entity creates a new version with the previous values of the changed fields and the audit event `entity.created` or `entity.updated`.

## Connections {#connections}

Connections are entities of a [connection type](../administration/metamodel-editing#connection-types) with one source and one target. Send them in `connections` of the same batch; they are written after the entities, so that they can name entities of the batch by their external ID:

```bash
curl "${auth[@]}" -X POST "$OEA/entities/batch" -d '{"strategy": "upsert", "atomic": true,
  "entities": [{"name": "CRM", "metatypeId": "<id>", "externalId": "cmdb:crm"},
               {"name": "ERP", "metatypeId": "<id>", "externalId": "cmdb:erp"}],
  "connections": [{"connectionMetatypeId": "<id of Data Flow>", "externalId": "flow:crm-erp",
                   "sourceExternalId": "cmdb:crm", "targetExternalId": "cmdb:erp",
                   "properties": [{"propertyDefId": "<id of Protocol>", "value": "sftp"}]}]}'
```

- Name the source and the target either by ID (`sourceId`, `targetId`) or by external ID (`sourceExternalId`, `targetExternalId`), not both. A connection may start or end at another connection if the connection type allows it.
- Without `name`, a new connection is named "source name → target name".
- Source and target cannot change. Sending a known connection with another source or target is an error; send it as a new connection instead. Deleting connections follows in a later version.
- Strategies, atomicity and the limit of 500 apply to connections as to entities. The answer reports connections with `itemType` `connection` and their index in `connections`.
- Error codes of a connection: `SOURCE_REQUIRED`, `SOURCE_AMBIGUOUS`, `SOURCE_NOT_FOUND` and `SOURCE_NOT_ALLOWED` (the same with `TARGET_`), `METATYPE_NOT_CONNECTION` and `ENDPOINT_CHANGE_NOT_SUPPORTED`. An entity of a connection type sent in `entities` is rejected with `METATYPE_IS_CONNECTION`.

A single connection is created with `POST /api/v1/connections` and the same fields; the answer (`201`) contains the connection with its source and target. A missing source or target gives `404`, other errors `422`.

`GET /metamodel/allowed-connections?sourceMetatypeId=…&targetMetatypeId=…` tells you in advance which connection types may connect two entity types.
