---
id: entity-version
title: EntityVersion – Entity Version Snapshot
version: 0.1.1
status: draft
created: 2026-06-27
last_updated: 2026-10-13
author: business-engineer
references:
  business_objects:
    - entity
    - person
  adrs:
    - adrs/ADR-016-persistence-strategy.md
  use_cases:
    - UC-14
    - UC-15
    - UC-16
---

# EntityVersion – Entity Version Snapshot

## Purpose

`EntityVersion` is the immutable snapshot of an `ArchitectureEntity` at a specific version state. Before every change to an entity, the current state is saved as an `EntityVersion` record — together with metadata about the change (who, when, why, which fields). This keeps the complete change history traceable at any time, and earlier states can be restored in UC-15.

`EntityVersion` records are **never editable or deletable**. They form the audit foundation of the repository.

## Attributes

### EntityVersion

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| entityId | integer | required | | FK → ArchitectureEntity.id; immutable | Entity to which this snapshot belongs |
| versionNumber | integer | required | | ≥ 1; unique per entityId | Version number represented by this snapshot; corresponds to `ArchitectureEntity.version` at the time of saving |
| snapshot | JSON | required | | immutable after creation | Complete entity state at `versionNumber`; contains all restorable fields (see below) |
| appliedAt | datetime | required | | ISO 8601, UTC | Time at which the entity transitioned from `versionNumber` to `versionNumber+1` |
| appliedBy | reference | required | | target: person | Person who made the change |
| changeReason | string | optional | null | max. 1000 characters | Optional reason given at the time of the update |
| changedFields | string[] | required | [] | dot notation; e.g. `["name", "properties.owner"]` | Fields that changed in the transition from `versionNumber` to `versionNumber+1` |
| restoredFromVersion | integer | optional | null | FK → EntityVersion.versionNumber of the same entityId | Set if this transition was a restore (UC-15/UC-16); indicates from which earlier state it was restored |
| restoredFields | string[] | optional | null | dot notation; e.g. `["description", "properties.owner"]`; only for UC-16 | For partial restore: exact list of the restored fields; null for full restore (UC-15) or regular updates |

### Snapshot Content

The `snapshot` JSON contains **all** fields of the entity at the time `versionNumber` — including fields that are immutable on the system side. The snapshot is a complete, self-contained image of the entity state: an auditor or a restore process should be able to fully reconstruct the state at any point in time without having to look up further data.

| Field in Snapshot | Type | Description |
|---|---|---|
| `id` | integer | Primary key of the entity |
| `entityTypeId` | string | Meta-type at the time of this version |
| `name` | string | Display name |
| `description` | string | Business description |
| `isLogical` | boolean | Logical/physical flag |
| `stereotypeIds` | string[] | Assigned stereotypes |
| `properties` | object | Complete property map: **all** properties configured for this EntityType at the time of the snapshot, including those with an empty or null value; no property is omitted, even if it has no value set |
| `propertyDefinitions` | object[] | Copy of the EntityType's `PropertyDefinition` entries from the MetamodelConfiguration at the time of the snapshot (name, dataType, validationMode, category); enables interpretation of the snapshot even after later metamodel changes |
| `sourceEntityId` | integer \| null | Source entity (connections only) |
| `targetEntityId` | integer \| null | Target entity (connections only) |
| `createdAt` | datetime | Creation timestamp |
| `createdBy` | string | Creating person (ID) |
| `updatedAt` | datetime \| null | Time of the last change before this snapshot |
| `updatedBy` | string \| null | Person of the last change before this snapshot |
| `version` | integer | Version number at the time of saving (identical to `versionNumber`) |

On restore (UC-15), despite the complete snapshot, only the **restorable** fields are applied to the entity (`name`, `description`, `isLogical`, `stereotypeIds`, `properties`). The immutable fields and `propertyDefinitions` in the snapshot serve exclusively audit purposes.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| versionOf | [entity](./entity.md) | n:1 | no | Each snapshot belongs to exactly one entity |
| appliedByPerson | [person](./person.md) | n:1 | no | Person who triggered the transition |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Before **every** update to an `ArchitectureEntity`, an `EntityVersion` record of the current state MUST be written before the change is applied | onUpdate (entity) | ADR-016 |
| BR-02 | `EntityVersion` records are **immutable and not deletable** after creation — neither via API nor via GUI; the runtime database role holds only INSERT and SELECT on the history tables; rows are removed only by the purge of soft-deleted entities (REQ-190) | always | ADR-016, ADR-033 §10 |
| BR-03 | `versionNumber` + `entityId` together form a unique key; no second snapshot may exist for the same entity and the same version number | onCreate | – |
| BR-04 | Writing the `EntityVersion` record and applying the entity change MUST happen in the **same DB transaction**; if the transaction fails, neither snapshot nor change is persisted | onUpdate (entity) | ADR-016 |
| BR-05 | `changedFields` MUST contain at least one element; an update that changes no field value must not create an `EntityVersion` and MUST be rejected with HTTP 304 | onCreate | – |
| BR-06 | If `restoredFromVersion` is set, the referenced `versionNumber` value MUST exist in `entity_versions` for the same `entityId` | onCreate | UC-15 |
| BR-07 | The `snapshot` contains ALL fields of the entity as well as all configured properties (including empty ones) and a copy of the associated `PropertyDefinitions`; it is not validated or transformed — it is a complete, self-contained copy of the entity state at the time of saving, even if later metamodel changes make individual values or definitions obsolete | onCreate | – |

## Lifecycle

`EntityVersion` records have no lifecycle. They are created on every entity update and exist permanently. There is no `deleted` state.

Exception: if an `ArchitectureEntity` is hard-deleted (no UC for this yet), it must be decided whether the associated `EntityVersion` records are deleted in cascade or retained as "orphaned" audit records. Until the decision: no deletion. ADR-033 §10 states that history rows are removed only by the purge of soft-deleted entities (REQ-190); the [audit events](./audit-event.md) remain independently.

Note (2026-10-13): `docs/data-model.puml` stores the history delta-based (`entity_version_headers`, `entity_property_value_history`, `entity_system_field_history`) instead of the JSON snapshot described here; aligning this business object with the delta model is open.

## Example

*Entity "SAP ERP" (id=42) is edited three times by Kurt:*

| EntityVersion | versionNumber | appliedAt | appliedBy | changedFields | restoredFromVersion |
|---|---|---|---|---|---|
| Snapshot 1 | 1 | 2026-03-01T09:00Z | Kurt | `["description"]` | null |
| Snapshot 2 | 2 | 2026-05-15T14:30Z | Michael | `["properties.owner", "properties.criticality"]` | null |
| Snapshot 3 | 3 | 2026-06-27T10:00Z | Kurt | `["name", "description", "properties.owner"]` | 1 |

Snapshot 3 shows `restoredFromVersion=1`: Kurt reset the entity to the state of version 1 (UC-15). Snapshot 3 itself contains the state the entity had shortly before the restore (= state of v3), so that this state is also preserved in the history.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | Business Engineer | Initial draft; basis for UC-14 (change history) and UC-15 (restore); snapshot content, BRs, and lifecycle defined |
| 0.1.1 | 2026-10-13 | Business Engineer | BR-02: append-only for the runtime role, deletion only by the REQ-190 purge (ADR-033 §10, DM-A9); the entity history is not the audit trail (see [audit-event](./audit-event.md)) |
