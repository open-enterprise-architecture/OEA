---
identifier: plateau
name_de: Plateau
name_en: Architecture Plateau
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Architektur-Plateau
  - Architektur-Zustand
  - Baseline
  - Target Architecture
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/40-extensibility/15-schema-evolution.md
---

# Business Object: Plateau

## Definition

A `Plateau` is a relatively stable, named state of the enterprise architecture of an OEA instance at a specific point in time or period. It describes which entities and relationships exist in this state, and whether they are active or out of service.

## Description

The plateau concept originates from TOGAF and ArchiMate and forms the basis for time-structured EA work. At any point in time, an OEA instance has exactly one **baseline plateau** (the current production state) and any number of **target or transition plateaus** (planned states).

**Plateau types**:

| Status | Meaning | Editability |
|---|---|---|
| `baseline` | Current production state (AS-IS); the only active baseline plateau | Only via a controlled process; usually locked |
| `target` | Desired target state (TO-BE); may not yet be productive | Editable by solution architects |
| `transition` | Intermediate state on the way from baseline to target | Editable by solution architects |
| `realized` | Former target state that has become productive; the new baseline replaces the old baseline | Archived, read-only |

**Relationship to solutions**: Plateaus are stable states. [Solutions](./solution.md) are the work units that change entities *from one plateau to another*. A solution architect works in a solution that is assigned to a target plateau.

**Effective content of a plateau**: The set of all entities that are `active` or `retiring` in this plateau state. Entities from the baseline plateau are inherited into target plateaus (they exist there until they are explicitly changed or `retired` by a solution).

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, globally unique | Internal primary key |
| name | string | required | | max. 255 characters; unique within the instance | Readable name (e.g. "Baseline 2026-Q1", "Target Cloud Migration 2027") |
| description | string | optional | | max. 2000 characters | Purpose and scope of the plateau |
| status | enum | required | | `[baseline, transition, target, realized]` | Type of the plateau |
| validFrom | date | optional | | ISO 8601 | Valid from (for baseline: date of the last go-live; for target: planned go-live) |
| validTo | date | optional | | ISO 8601; only if status=realized or superseded baseline | Valid until |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| isTargetOf | [solution](./solution.md) | 1:0..n | yes | Solutions that work toward this plateau |
| succeeds | Plateau | 0..1:1 | yes | Predecessor plateau (for transition/target: the baseline from which work proceeds) |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | An OEA instance has exactly one plateau with `status=baseline` at any point in time | onStatusChange | – |
| BR-02 | A plateau with `status=baseline` can only be set to `status=realized` through a controlled go-live process; at the same time an existing target or transition plateau becomes the new baseline | onStatusChange | – |
| BR-03 | A plateau with `status=realized` is read-only; no entity changes possible | onUpdate | – |

## Lifecycle

```text
target / transition → realized → (becomes the new baseline)
                                  old baseline → realized
```

The transition `target → realized` corresponds to go-live: the planned architecture becomes the new reality.

## Delimitation

- **NOT** a [Solution](./solution.md): A plateau is the stable state; a solution is the change process between two plateaus.
- **NOT** a time series of individual entities: Bitemporality (validity time vs. recording time) is an open question (§23 Open Issues, item 3); the plateau concept abstracts from it.

## Usage in Use Cases

- Future: UC for plateau management (create, go-live process)
- [UC-04](../requirements/use-cases/UC-04-configure-metamodel.md) (metamodel is instance-wide; plateau-specific extensions run via solutions)

## Notes

TOGAF reference: "Plateau" in the migration layer (TOGAF 10, Part IV). ArchiMate 3.x: `Plateau` element in the motivation and migration layer. The open questions on bitemporality (§23 items 3–6) will further refine the plateau mechanism.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | Business Engineer | Initial draft |
