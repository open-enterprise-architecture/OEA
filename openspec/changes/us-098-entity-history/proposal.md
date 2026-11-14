# Proposal

## Why

Architects and auditors must be able to trace who changed an entity when, for reviews and compliance ([UC-14](../../../requirements/use-cases/UC-14-view-change-history.md)). Every entity write already records a version since V6 (ADR-022), but nothing shows it: `getEntityHistory` is still `planned`. [US-098](../../../requirements/user-stories/US-098-version-history-timeline.md) makes the history visible as a timeline. It is S12 change 1; the values and the diff of two versions follow in change 2 (`us-099-version-diff`).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-14: View Change History](../../../requirements/use-cases/UC-14-view-change-history.md) |
| User Stories | [US-098: View an Entity's Change History as a Timeline](../../../requirements/user-stories/US-098-version-history-timeline.md) (AC1–AC3, realigned) |
| Requirements | [REQ-098: Version history timeline](../../../requirements/req/REQ-098-version-history-timeline.md) (statement and implementation notes, realigned), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-127: Enforcement of connection-scoped visibility](../../../requirements/req/REQ-127-property-visibility-enforcement-connection-scoped.md) |
| ADRs | [ADR-022: Structured property model and delta versioning](../../../adrs/ADR-022-structured-property-model.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S12 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-14:**

- **Entry point:** a page of its own, `/entities/:id/history` (SCR-026). The row action "History" in the catalog (SCR-020) and in the portal catalog opens it. No entity detail page is built.
- **No change reason:** no write path records a reason, so the timeline shows none. REQ-098 notes it as a later extension.
- **Fields the reader may not read:** a field is unreadable when the property is `role-restricted` without a read role of the reader, or `connection-scoped` outside the reader's scope. The timeline never shows the value of such a field (values follow in US-099 anyway). Whether it names the field as "Restricted" or leaves it out entirely is configurable per combination of authorization role and metatype.
  - With several roles, the more open setting applies: the field is hidden only when every role of the reader is set to "hide" for the metatype.
  - A combination that nobody configured shows the field as "Restricted".

**History read (`repository`):**

- `getEntityHistory` (`GET /entities/{id}/history`, `entity:read`) is implemented; `x-oea-status: planned` is removed.
- It returns the versions newest first. Each version has its number, the time (UTC), the person who made it (ID and name) and the changed fields.
  - Version 1 is the creation and has no changed fields.
  - A changed field has its key, its label, whether it is a system field (name, description, logical flag, source, target), and `restricted`.
- The filters `from` and `to` (inclusive, ISO 8601 timestamps) and `actorId` narrow the versions; `from` after `to` is answered with 400.
- Paging uses `page`, `pageSize` (default 50, at most 200) and `cursor`, like the delta list. The response names the actors of all versions, for the actor filter, and the creation (time and person).
- An unknown entity is answered with 404.
- **Response schema:** the contract's `FieldDiff` with `oldValue` and `newValue` is replaced in `EntityVersionSummary` by a new schema `EntityVersionChange` without values. `FieldDiff` stays for the diff of US-099.

**Visibility of restricted changes (`metamodel`):**

- `GET` and `PUT /admin/history-visibility` with `visibility:configure` read and change the settings: one entry per role and metatype, with the mode `show-restricted` or `hide`.
  - A change is atomic and names invalid entries in `invalidFields`.
  - Setting an entry to `show-restricted` removes it, since that is the default.
  - An unknown role or metatype is answered with 422.
- Each changed entry is audited as `visibility.history-changed`, with the role, the metatype and the old and new mode.
- A subtype has its own setting; there is no inheritance along base types.
- A package re-import keeps the settings. Deleting a role removes its entries.

**Frontend:**

- **SCR-026 "Change history":**
  - the entity name and a timeline of version cards, newest first, with number, local time, person and changed fields; a restricted field carries the badge "Restricted";
  - filters for the period and the person;
  - "Load more" above 50 versions;
  - the empty state "No changes since creation. Created on {date} by {person}.";
  - a back link to the catalog it was opened from.
- **SCR-020 and portal catalog:** the row action "History" for each entity row.
- **SCR-032 "Property visibility":** a new section "Change history" with the matrix of roles and metatypes. The administrator picks a metatype and sets "Restricted" or "Hidden" per role.
- **Screen inventory:** SCR-026 moves from `legacy-mockup` to `prototype`, then to `implemented`. The legacy SVGs are replaced by the generated PNGs.

**Realigned sections:**

- **US-098 AC3:** "the tab shows" becomes "the page shows".
- **REQ-098:**
  - the statement drops the change reason (noted as a later extension);
  - the implementation notes name the cursor paging and the configurable visibility of restricted changes.

## Out of Scope

- Values of changed fields, the field-by-field diff and the complete state of a version (US-099, US-100; change 2).
- A change reason on entity writes.
- Restoring a version (UC-15, UC-16; S13).
- The read-only change history of the web portal as a screen of its own (SCR-054, priority should). The portal catalog opens SCR-026, which is read-only anyway.
- History of soft-deleted entities: `deleteEntity` is not implemented yet. The history read applies to every entity row that exists.
- Audit events for reading the history. A read through an override role is already audited per query (visibility-override); the timeline shows no values, so it is not such a read.

## Capabilities

### New Capabilities

- `repository/entity-history`: reading the version history of an entity, its filters, paging and visibility rules, and the page SCR-026 with its entry points.
- `metamodel/history-visibility`: the configuration of how changes to unreadable fields appear in the history, per role and metatype, with its governance, audit and the section on SCR-032.

### Modified Capabilities

- None. The row actions and the section on SCR-032 are specified in the new capabilities, as they exist only for them.

## Impact

- **api/openapi.yaml:**
  - `getEntityHistory` implemented, with the parameters `from`, `to`, `actorId` and `cursor`;
  - `EntityHistoryResponse` with `actors`, `created` and `nextCursor`;
  - the new schema `EntityVersionChange`;
  - the new operations `getHistoryVisibility` and `updateHistoryVisibility`.
- **backend:**
  - `core/entity`: history read;
  - `app/history` (new feature package): timeline and visibility rule;
  - `app/metamodel`: settings;
  - `api`: controllers;
  - the Flyway migration V29 with the table `history_visibility_settings`.
- **frontend:** the page SCR-026 and its API module, the row action in `CatalogTable.vue`, and the section on SCR-032; a Storybook story for SCR-026.
- **Documentation:**
  - `docs/data-model.puml` and `business-objects/authorization-role.md` (the new settings);
  - `docs/audit-event-catalog.md` (`visibility.history-changed`);
  - `docs/screens/SCREENS.md`;
  - the user manual (English and German).
