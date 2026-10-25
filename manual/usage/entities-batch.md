---
title: Writing entities in batch
covers:
  screens: []
  stories: [US-141]
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

Not supported yet: connections, `dryRun=true`, `parentId`, `lifecycle` and properties with restricted visibility (`422` or an error for the item).

Every written entity creates a new version with the previous values of the changed fields and the audit event `entity.created` or `entity.updated`.
