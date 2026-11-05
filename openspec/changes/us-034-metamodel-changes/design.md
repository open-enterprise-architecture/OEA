# Design

## Context

- **Audit events.** Every metamodel change appends an event to `audit.events` in the transaction of the change (`MetamodelAudit`, `PackageImporter`, ADR-033).
  - Target types: `metatype` (also for its mappings), `property-definition` (also for its enum values), `property-category` and `metamodel-configuration` (labels, re-import setting, configurations).
  - Actors are stored pseudonymously (`actor_person_id`, `actor_admin_id`).
  - The details carry `name` on creation and deletion, `changedFields` on updates, `before` and `after` for settings, and `batchId` and `packageId` for imports. See `docs/audit-event-catalog.md`.
- **Access.** The runtime role `oea_app` may `SELECT` on `audit.events`. `audit.events` has no index besides the primary key and `chain_seq`.
- **Reading.** The general audit log operations (`listAuditEvents`, `getAuditEventChanges`) are planned and need `audit:read`. Names are resolved at read time only for `audit:read` (ADR-033 §3, `business-objects/person.md`).

## Goals / Non-Goals

**Goals:**

- A read of the `metamodel.*` events for metamodel administrators, with names, filters and paging.
- A section "Changes" in SCR-030.

**Non-Goals:**

- The general audit log and its operations.
- Field-level before/after values that the events do not carry today.
- Writing audit events differently: the atomic write of ADR-033 stays.

## Decisions

### D1 – Operation `listMetamodelChanges`

`GET /metamodel/changes`, `x-oea-authorization: permissions [metamodel:write]`.

**Parameters** (all optional):

- `kind`: one of `metatype`, `property`, `category`, `enum-value`, `mapping`, `labels`, `reimport`, `package`, `configuration`. Each maps to a set of event types (D2).
- `actorRef`: `persons.id` as a decimal string, or the UUID of a system admin account.
- `from` and `to`: dates in UTC, both inclusive.
- `batchId`: the UUID of an import batch.
- `page` and `pageSize`: the shared parameters, at most 100 per page.

**Answer `MetamodelChangePage`:**

- `items`, ordered by `occurredAt` and `id`, newest first;
- `page` (`PageMeta`);
- `actors`: the distinct actors of all metamodel events with their names, for the person filter.

**Schema `MetamodelChange`:**

- `id`, `occurredAt`, `eventType` and `kind`;
- `actorType`, `actorRef` and `actorName`;
- `targetType`, `targetId` and `elementName`;
- `batchId`;
- `details`: the stored details of the event, which contain no secrets for metamodel events.

**Errors.** Invalid parameters give 400.

### D2 – Which events and which names

**Events.** The reader selects `event_category = 'change'` and `event_type LIKE 'metamodel.%'`. `metamodel.package-rejected` is included: it shows a failed import with its reason.

**Kinds.** The kinds map by prefix of the event type:

| Kind | Event types |
|---|---|
| `metatype` | `metamodel.metatype-*`, except `metamodel.metatype-imported` |
| `property` | `metamodel.property-*` |
| `category` | `metamodel.category-*` |
| `enum-value` | `metamodel.enum-value-*` |
| `mapping` | `metamodel.mapping-*` |
| `labels` | `metamodel.labels-changed` |
| `reimport` | `metamodel.reimport-policy-changed` |
| `package` | `metamodel.package-*` and `metamodel.metatype-imported` |
| `configuration` | `metamodel.configuration-*` and `metamodel.edit-mode-changed` |

**Actor name.**

- A person or service account: `persons.name`. For an anonymized person this is already the pseudonym (REQ-189).
- A system admin account: its `login_name`.
- `system`: no name. The portal shows "System", for example when a dated mapping took effect.

**Element name.**

- `details.name` or `details.value`, written on creation and deletion;
- otherwise the current name of the target, so a mapping shows its metatype and an enum value its property;
- otherwise `null`; the portal then shows the ID.

The reader never decrypts or reads entity content.

**Why a separate operation rather than `listAuditEvents` with a permission condition.** The general operation returns chain fields and every event family, and it needs `audit:read` semantics. A narrow operation keeps `audit:read` unchanged, gives metamodel administrators exactly the metamodel history (owner decision), and can ship before SCR-054.

### D3 – Index for the metamodel events

The audit migration `V4__metamodel_events_index.sql` creates the partial index `events_metamodel_occurred ON audit.events (occurred_at DESC, id DESC) WHERE event_type LIKE 'metamodel.%'`. The reader uses the same predicate, so the newest-first page does not scan the whole trail. The audit schema migrations run as the audit owner; the index changes neither the hash chain nor the grants.

### D4 – Atomicity

Nothing changes in the write path: `AuditWriter.append` runs in the transaction of the change, and a failure rolls the change back (ADR-033, fail closed). A test makes the audit insert fail for one change and checks that the metatype was not created. US-034 AC3 and REQ-034 AC4 are reworded accordingly.

### D5 – Permission

`metamodel:write` already allows changing the whole metamodel and is admin-class. Reading who changed it adds the names of fellow administrators and of the importing persons, but no other personal data.

- **ADR-032** gets an amendment of the permission description.
- **The contract catalog** gets the same description, and `check_openapi_authorization.py` checks it.
- **`business-objects/person.md`** names the second reader of resolved names.

`solution-metamodel:write` alone does not read the changes. Extensions show in the list like other configurations.

### D6 – SCR-030 section "Changes"

- **Tab.** It is shown with `metamodel:write`, after "Translations", at the route `/metamodel/changes`. It is not part of the store reload: it loads its own page on opening and when a filter changes.
- **Table.** Time in the local time zone, person, kind as text (for example "Entity type created"), element, details:
  - changed fields;
  - before → after for settings;
  - the package and version for imports.
- **Filters.** Kind (a select), person (a select from `actors`), from and to (dates).
- **Import batches.** An import shows a badge "Import batch"; selecting it sets the batch filter, which shows as a removable chip.
- **Paging.** "Load more" appends the next page.
- **States.** An empty list shows "No changes", and a failed load shows an error.
- **Story.** "Changes" with an import batch and two persons. The screen image stays the first story.
- **Messages.** One message per event type in English and German; an unknown type shows the type itself.

## Risks / Trade-offs

- **[Names to metamodel administrators]** → Limited to metamodel events. It is owner-decided and recorded in ADR-032; `audit:read` still covers everything else.
- **[Partial index predicate must match the query]** → The reader uses the literal predicate of the index, kept as one constant.
- **[Details are free-form JSON]** → The reader passes details through for metamodel events only. Their content is defined in the event catalog and contains no secrets.

## Migration Plan

The audit migration V4 only adds an index. A rollback drops the index. The new operation is additive.
