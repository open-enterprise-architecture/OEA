# Proposal

## Why

Since S10 change 1, security administration can restrict a property to roles, but nothing honors the restriction yet:

- the catalog and the portal leave out every non-public property;
- the entity write rejects every value of such a property.

So a restricted property can neither be maintained nor read by the people it is meant for ([US-126](../../../requirements/user-stories/US-126-role-restricted-property-enforcement.md), [#68](https://github.com/open-enterprise-architecture/OEA/issues/68)). This change enforces `role-restricted` on the read and write channels that exist today. This is S10 change 2.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-21: Configure Property Visibility](../../../requirements/use-cases/UC-21-configure-property-visibility.md) |
| User Stories | [US-126: Role-restricted property enforcement](../../../requirements/user-stories/US-126-role-restricted-property-enforcement.md) |
| Requirements | [REQ-126: Enforcement of role-restricted visibility](../../../requirements/req/REQ-126-property-visibility-enforcement-role.md), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md) (AC2, AC4), [REQ-188: Secrets and key management](../../../requirements/req/REQ-188-secrets-key-management.md) |
| Issues | [#68: role-restricted and connection-scoped properties in catalog rows](https://github.com/open-enterprise-architecture/OEA/issues/68) |
| ADRs | [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md) (§3, encrypted restricted values), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (§5), [ADR-013: API Style](../../../adrs/ADR-013-api-style.md) |
| Sprint / Milestone | S10 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-08:**

- **Reading and writing:**
  - Holders of a write role write the values of a `role-restricted` property.
  - Their old and new values are stored encrypted in the audit trail (REQ-160 AC4, ADR-033 §3).
- **A column stays visible but empty:**
  - A person without a read role sees the column of a restricted property with empty cells and cannot sort or filter by it (US-126 AC4).
  - All readers keep the same column layout.

**Reading:**

- **Who may read:** a property is readable for a principal if it is `public`, or `role-restricted` with one of the principal's effective roles among its read roles. `connection-scoped` stays unreadable for everyone until change 3.
- **Catalog and portal query:**
  - a column of a property the caller cannot read is returned with `readable: false` and `sortable: false`;
  - its values and the values of join columns on such a property are `null`;
  - a sort or a filter on it answers 400 naming `sort` or `filter`, also through a saved view;
  - the totals are those without that property.
- **Column configuration:** every mapped property that is not retired can be configured as a column, not only `public` ones (#68). The default columns (name, status) stay as they are and use only a public `Status`.

**Writing:**

- **Who may write:** a value of a `role-restricted` property can be written or removed by a principal holding one of its write roles.
- **Without a write role:** a batch or a `createConnection` that names such a property answers 403. The whole request is rejected, as for scoping connections, and the denial is audited.
- **`connection-scoped`:** stays rejected until change 3, with the existing code `RESTRICTED_PROPERTY_NOT_SUPPORTED`.
- **Mandatory properties:** a mandatory property is checked only for writers who may write it, as today.

**Audit values (ADR-033 §3):**

- **Storage:** a change of a property whose visibility is not `public` at the time of the event is stored as AES-256-GCM ciphertext (`value_protection = encrypted`, `key_id`).
- **Key:** the audit value key is a data key, wrapped by the key-encryption key of REQ-188 and stored in a new table `audit.value_keys`.
- **Rotation:** `oea secrets-reencrypt` re-wraps the data keys with the current key-encryption key.
- **No reading yet:** no channel decrypts them yet; reading audit changes (`getAuditEventChanges`) is still planned.

**Frontend (SCR-020, SCR-050):**

- a column with `readable: false` shows empty cells, offers no sort and is not offered in the filter bar;
- a lock icon in its header explains why.

**Realigned sections:**

- **US-126 AC1 and AC2** (entity read API and entity form) wait for the entity detail, which does not exist yet; US-126 stays `in-progress`.
- **#68** is resolved for `role-restricted`. `connection-scoped` follows in change 3.
- **The catalog specs** that say "only `public` properties appear as columns" change to the rule above.

## Out of Scope

- `connection-scoped` reading and writing, and the account link to a Person entity (change 3).
- The override role list (change 4).
- Decrypting audit values for `audit:read` holders: no endpoint returns change values yet.
- Entity detail, history, exports and the other read channels of REQ-160 that do not exist yet. Each will apply the same rule when it is built.

## Capabilities

### New Capabilities

- `platform/audit-value-encryption`: encrypted audit values of restricted properties, the wrapped audit value keys and their rotation.

### Modified Capabilities

- `catalog/catalog-query`: the requirement "Visibility of the query result" (restricted columns) changes.
- `catalog/catalog-columns`: the requirement "All attributes as columns" (restricted properties are offered) changes.
- `catalog/catalog-filters`: no filter on a column the caller cannot read.
- `portal/portal-catalogs`: the requirement "Visibility in the portal" changes.
- `repository/entity-write`:
  - "Restricted properties not writable yet" becomes "Writing restricted properties";
  - "Necessity of properties" names who may write.

## Impact

- **`api/openapi.yaml`:**
  - `CatalogColumn.readable`;
  - the 400 descriptions of `queryCatalog` and `queryPortalCatalog`;
  - the 403 of `batchWriteEntities` and `createConnection` for restricted properties.
- **`backend/`:**
  - a readability and writability check from the principal's effective roles (`io.oea.app.metamodel`);
  - `CatalogColumns` and `CatalogQueries` (redacted columns);
  - `EntityValidator` and `MetatypeRules` (write roles);
  - `AuditWriter` and a new `AuditValueCipher` with `audit.value_keys` (migration-audit `V5`);
  - `ReencryptCommand`.
- **`frontend/`:** `CatalogTable` and `CatalogFilterBar`.
- **`manual/`:** the catalogs page (empty restricted columns) and the property visibility page (enforcement), in English and German.
- **Documentation:**
  - US-126, REQ-126, REQ-160;
  - `docs/data-model.puml` and `docs/audit-hash-chain.md` if affected;
  - the audit event catalog;
  - the sprint plan.
