# Design

## Context

- **Visibility data (S10 change 1):**
  - `property_definitions.visibility_mode` and `scoping_connection_type_id`;
  - `property_visibility_roles (property_def_id, role_id, access)`;
  - the service `io.oea.app.metamodel.PropertyVisibility`, whose `all()` returns the visibility per property.
- **Effective roles:** `PermissionResolver.roles(principal)` gives the effective role IDs of a principal (IdP mappings and local assignments; privileged roles only with a second factor), cached for 60 s.
- **Catalog query:**
  - `CatalogColumns` resolves the configured and default columns. The available properties come from `CatalogQueryStore.availableProperties` and `endpointProperties`, which keep only `visibility_mode = 'public'`.
  - `CatalogQueries.read` checks sort and filters against the resolved columns, and `CatalogQueryStore.page` selects the values.
  - The portal query is the same method with channel `portal`.
- **Entity write:**
  - `EntityValidator.writable` accepts only `public` properties and rejects others with `RESTRICTED_PROPERTY_NOT_SUPPORTED`.
  - Omitted properties keep their values: the validator starts from the stored values.
  - Mandatory properties the writer cannot change are not checked.
- **Audit:**
  - `AuditWriter` inserts `audit.event_changes` with plaintext `old_value` and `new_value`.
  - The table already has `value_protection`, `old_value_enc`, `new_value_enc` and `key_id`, with a check that keeps the plaintext empty when encrypted.
  - The hash chain covers all of these fields (`docs/audit-hash-chain.md`).
- **Keys:**
  - `KeyRing` holds the key-encryption keys (current and previous, REQ-188).
  - `SecretCipher` encrypts with AES-256-GCM and AAD.
  - `ReencryptCommand` re-encrypts a fixed list of columns with the current key.

## Goals / Non-Goals

**Goals:**

- `role-restricted` enforced on the catalog and portal query and on the entity write.
- Restricted audit values encrypted as ADR-033 §3 describes.
- Restricted properties configurable as catalog columns (#68).

**Non-Goals:**

- `connection-scoped` (change 3) and the override list (change 4).
- Decrypting audit values for readers (no endpoint yet).
- Read channels that do not exist yet.

## Architecture Constraints

- **ADR-033 §3:**
  - restricted values are encrypted with an audit value key, a data key wrapped by the key-encryption key;
  - append-only records are never re-encrypted, only the data keys are re-wrapped;
  - old data keys stay as long as records need them.
- **ADR-032 §5:** visibility uses authorization roles. A role counts only while it is effective (SoD-8).
- **ADR-028:**
  - the visibility rule lives in `io.oea.app.metamodel` (`PropertyAccess`);
  - the query and write services call it;
  - the store keeps SQL only.
- **REQ-158:** a denial answers the generic 403 and never names the property.

## Decisions

### D1 Who may read and write

`PropertyAccess` (app layer) answers, for a principal, which properties it may read and which it may write:

- **`public`:** readable and writable.
- **`role-restricted`:**
  - readable if an effective role is among the read roles;
  - writable if an effective role is among the write roles.
- **`connection-scoped`:** neither, until change 3.

**Snapshot:** it builds a snapshot per request from `PropertyVisibility.all()` and `PermissionResolver.roles(principal)`, so a query and a write see one consistent state.

**Caching:** there is no further cache. A changed visibility applies from the next request, and a changed role assignment within the 60 s of the role cache.

### D2 Catalog columns

- **Available properties:** `availableProperties` and `endpointProperties` drop the filter on `public`, but keep the filter on retired properties.
  - The configuration offers every mapped property (#68).
  - The default column `Status` still needs a public `Status`, so that no reader gets an empty default column.
- **Per query:** `CatalogColumns` marks each resolved column with `readable` from the snapshot of D1. This applies to primary, endpoint and join target columns; system fields are always readable.
- **An unreadable column:**
  - is `sortable: false` in the result and `readable: false`;
  - selects `NULL` instead of its value. The store gets the column without its property, so no value is read at all.
- **Rejections:**
  - a sort on it adds `sort`, a filter on it adds `filter` to the invalid parameters;
  - this also covers join columns and views, which pass their filters as parameters.
- **Totals:** unchanged, because no unreadable filter or sort can be applied.
- **Cursor fingerprint:** it already contains the resolved column keys. A reader without the read role gets other sortable columns, but the fingerprint does not depend on readability. A cursor never carries values of an unreadable column, because such a column cannot be sorted.

### D3 Writes

- **Validator:** `EntityValidator` gets the writable properties of the principal (D1).
  - A property the principal cannot write that the request names, with a value or empty, makes the request a denial.
  - The batch and `createConnection` answer it with `PermissionDeniedException` (403, audited as `authz.denied`) before anything is written, for the whole request.
  - This matches the scoping rule of change 1 and does not reveal per item which property is restricted.
- **`connection-scoped`:** a value keeps the item error `RESTRICTED_PROPERTY_NOT_SUPPORTED`, so that its behavior stays as today until change 3.
- **Mandatory check:** it uses the same writable set.
- **Change records:** the writer marks each change of a property whose visibility is not `public` as protected (`AuditEvent.Change.protectedValue`).

### D4 Encrypted audit values

- **Key table:** migration `db/migration-audit/V5__audit_value_keys.sql` creates `audit.value_keys`:
  - `key_id TEXT PRIMARY KEY`, `wrapped_key TEXT NOT NULL`, `kek_id TEXT NOT NULL`, `created_at`;
  - `oea_app` gets `SELECT`, `INSERT` and `UPDATE (wrapped_key, kek_id)`.
  - The table is not part of the hash chain: the keys are not audit records, and re-wrapping must not touch the chain.
- **`AuditValueCipher` (package `io.oea.audit`):**
  - **Data key:** on the first protected change it creates a random 256-bit data key with ID `avk-<date>-<random>`, wraps it with `SecretCipher` (AAD `audit.value_keys:<key_id>`) and stores it. Later changes use the newest data key, unwrapped once and held in memory.
  - **Values:** each value is encrypted with AES-256-GCM under the data key, with AAD `audit.event_changes:<event_id>:<property_def_id>:old|new`, and stored as nonce plus ciphertext in `old_value_enc` and `new_value_enc`.
  - **Empty values:** an empty old or new value stays `NULL`.
- **`AuditWriter`:** for a protected change it writes `value_protection = 'encrypted'`, the two ciphertexts and `key_id`, and no plaintext. Other changes stay `plain`.
- **Rotation:** `ReencryptCommand` adds `audit.value_keys` (`key_id`, `wrapped_key`, `kek_id`) to its targets, so `oea secrets-reencrypt` re-wraps every data key with the current key-encryption key. Data keys themselves are never replaced, so records need no re-encryption.
- **Decryption:** `AuditValueCipher.decrypt` exists for the tests and the later reader of audit changes. No endpoint uses it in this change.

### D5 Contract

- **`CatalogColumn.readable`:** boolean, default `true`. `false` means that the caller may not read the column's property; its values are `null`, and it cannot be sorted or filtered.
- **`queryCatalog` and `queryPortalCatalog`:** the 400 names `sort` and `filter` also for unreadable columns.
- **`batchWriteEntities` and `createConnection`:** the 403 names writing a restricted property without a write role. The `x-oea-authorization` conditions gain "a value of a role-restricted property is written" → the write roles, checked by the handler. The condition is not a permission, so it is described in the operation text instead.

### D6 Frontend

- **`CatalogTable`:** a column with `readable: false` shows a lock icon with the tooltip "You may not see the values of this column", empty cells and no sort control.
- **`CatalogFilterBar`:** offers only readable columns.
- **Saved views:** a saved view whose filter names an unreadable column gets 400 `filter` and falls back to the view without filters, as for any invalid filter today.

## Data Model

- **`audit.value_keys`** (new, migration-audit V5): `key_id`, `wrapped_key`, `kek_id`, `created_at`. Added to `docs/data-model.puml` in the audit package, with the note on the key wrapping.
- **`audit.event_changes`:** unchanged. The note "encrypted iff visibility_mode <> 'public' at the time of the event" becomes implemented.
- **`business-objects/`:** no change; the audit trail is not a business object.

## API

Delta to `api/openapi.yaml`:

- `CatalogColumn.readable`, with its description;
- the descriptions of `queryCatalog`, `queryPortalCatalog`, `batchWriteEntities` and `createConnection` (D5).

## Risks / Trade-offs

- **Configured restricted columns are empty for most readers.** → The lock icon explains it. Catalog managers decide which columns a shared catalog shows (owner decision: same layout for all).
- **Loss of the key-encryption key makes the audit values unreadable.** → Like client secrets (REQ-188), the key is kept outside the database. Re-wrapping keeps rotation safe; destroying a data key is the intended crypto-shredding of ADR-033.
- **The role cache delays a revoked role up to 60 s.** → It is the existing bound of ADR-032 §7 for every permission.
- **A whole batch fails for one restricted value.** → This is consistent with the scoping rule and reveals nothing per item. Integrations writing restricted properties need a write role.
