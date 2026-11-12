---
identifier: solution
name_de: Lösung / Solution Architecture
name_en: Solution
version: 0.6.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Solution Architecture
  - Architecture Initiative
  - Lösungsarchitektur
  - Projekt (KMU)
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept/40-extensibility/14-extensibility.md
---

# Business Object: Solution

## Definition

A `Solution` is a named architecture initiative that describes which entities are created, change, or go out of service as part of a project or a transition. It is the primary workspace of a solution architect.

## Description

A solution supports two **operating modes**, chosen depending on the organization's maturity:

---

### Mode A: Plateau Mode (Enterprise)

`fromPlateauId` and `toPlateauId` are set. The solution describes the **delta** from a baseline [plateau](./plateau.md) to a target plateau. Entities of the baseline plateau are inherited; the solution describes only the changes.

Suitable for: organizations that manage formal architecture states (baseline / target) and need governance over plateau transitions (TOGAF maturity).

```text
Baseline P0 (currently productive)
  │
  └── Solution "Salesforce Introduction" (plateau mode)
        fromPlateau: P0 → toPlateau: P1 (Target 2027)
        deltas:
          CRM-Legacy:  retiring
          Salesforce:  new
```

---

### Mode B: Project Mode (SME)

`fromPlateauId` and `toPlateauId` are both `null`. The solution is a free project workspace. There is no formal baseline plateau; the baseline is the current catalog, that is the entities that are not deleted. The go-live of a solution (S14) applies its deltas to the catalog, so the catalog always holds the current overall state (owner decision 2026-11-12). The solution models entities and their changes directly, without a formal plateau framework.

Suitable for: SMEs and organizations that do not work with the plateau principle but carry out projects and continuously evolve the landscape.

```text
Solution "Salesforce Introduction" (project mode, no plateau)
  deltas:
    CRM-Legacy:  retiring
    Salesforce:  new
    [after implemented: the go-live applies the deltas to the catalog]
```

---

### Comparison of Modes

| Characteristic | Plateau Mode | Project Mode |
|---|---|---|
| Plateau references | `fromPlateauId` + `toPlateauId` set | both `null` |
| Baseline inheritance | Entities from `fromPlateau` | none; standalone workspace |
| Effective overall state | Baseline ∪ solution deltas | The current catalog, updated by the go-live of each solution |
| Suitable for | Enterprise, TOGAF governance | SME, project-based way of working |
| Plateau required | yes | no |

---

**Meaning of status**:

| Status | Meaning |
|---|---|
| `draft` | Idea, still incomplete |
| `proposed` | Fully described, submitted for review |
| `approved` | Approved for implementation |
| `in-progress` | Implementation under way |
| `implemented` | Go-live done; delta is part of the realized state |
| `archived` | Solution abandoned or replaced |

**Metamodel extension**: A solution can have its own [`MetamodelConfiguration`](./metamodel-configuration.md) with `scope=solution`. This allows the solution architect to try out new entity types without changing the locked instance default metamodel (REQ-037). These types are visible exclusively within this solution.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, globally unique | Internal primary key |
| name | string | required | | max. 255 characters; unique within the instance | Readable name (e.g. "Cloud Migration Infrastructure 2027") |
| description | string | optional | | max. 2000 characters | Goal and scope of the solution |
| status | enum | required | `draft` | `[draft, proposed, approved, in-progress, implemented, archived]` | Current progress |
| fromPlateauId | reference | **optional** | null | target: Plateau (status=baseline or transition); null = project mode | Starting state (plateau mode); null = no formal baseline plateau |
| toPlateauId | reference | **optional** | null | target: Plateau (status=target or transition); null = project mode | Target state (plateau mode); null = no formal target plateau |
| ownerId | reference | required | | target: person | Responsible person (solution architect) |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| startsFrom | [plateau](./plateau.md) | n:0..1 | yes | Starting state (plateau mode only) |
| targetsTo | [plateau](./plateau.md) | n:0..1 | yes | Target state (plateau mode only) |
| ownedBy | [person](./person.md) | n:1 | no | Responsible person |
| hasMetamodelExtension | [metamodel-configuration](./metamodel-configuration.md) | 1:0..1 | yes | Optional metamodel extension; the extension names its solution (`solutionId`), the solution keeps no pointer of its own |
| containsDeltas | EntityDelta[] | 1:n | yes | Set of entity changes (new / modified / retiring) |

### EntityDelta (value object, no BO of its own)

Stored in `entity_deltas` and `entity_delta_property_values` (V26). A delta is removed physically; the audit events `solution.delta-added` and `solution.delta-removed` keep the record.

| Attribute | Type | Description |
|---|---|---|
| deltaType | enum `[new, modified, retiring]` | Kind of change |
| entityId | integer, nullable | FK → [ArchitectureEntity](./entity.md).id (sequential integer ≥ 1) for `modified` and `retiring`: an entity of the baseline. Null for `new` until the go-live (S14) creates the entity (owner decision 2026-11-12) |
| metatypeId | reference, `new` only | The element metatype of the proposed entity |
| name | string 1–255, `new` only | The name of the proposed entity; unique per solution and metatype, case-insensitively |
| description | string ≤ 2000, `new` only | The description of the proposed entity |
| properties | PropertyValue[], `new` only | The values of the proposed entity, checked against the metamodel and the property visibility rules like the write of a new entity |
| changes | Change[], `modified` only | The changed fields – name, description or a property – each with the before value the server records from the catalog at capture and the after value; no value on either side means none (owner decision 2026-11-12) |
| createdAt | datetime | Time of capture |
| createdBy | reference → person | The capturing person (`solution:write`) |
| updatedAt | datetime, nullable | Last edit of a delta `modified` |
| updatedBy | reference → person, nullable | The person of the last edit |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `fromPlateauId` and `toPlateauId` MUST either **both be set** or **both be null** (no mixed state) | onCreate, onUpdate | – |
| BR-02 | If both plateau IDs are set: `fromPlateauId` ≠ `toPlateauId`; `toPlateauId.status` must be `target` or `transition` | onCreate, onUpdate | – |
| BR-03 | A solution with `status=implemented` or `status=archived` is read-only, including its deltas (409) | onUpdate | – |
| BR-04 | A `MetamodelConfiguration` naming a solution (`solutionId`) has `scope=solution` | onCreate, onUpdate | – |
| BR-05 | Entities of a solution are visible to other solutions (basis for collision detection on parallel changes to the same entity) | onRead | – |
| BR-06 | A solution holds at most one delta per entity, at most one `new` delta per metatype and name (case-insensitive), and at most 1000 deltas | onCreate | – |

## Lifecycle

```text
draft → proposed → approved → in-progress → implemented
              ↘ archived (abandoned)
```

**Plateau mode after `implemented`**: The go-live process sets `toPlateauId.status` from `target` to `realized`; the old baseline plateau becomes `realized`; the realized plateau becomes the new baseline.

**Project mode after `implemented`**: The solution is completed; its deltas are a permanent part of the realized overall state (no plateau transitions needed).

## Examples

**Project mode (SME, no plateau)**:

```yaml
Solution: "Salesforce Introduction" (project mode)
  fromPlateau: null
  toPlateau:   null
  status: draft → implemented
  deltas:
    - CRM-Legacy:  deltaType=retiring
    - Salesforce:  deltaType=new, type=ApplicationComponent
    - Integration: deltaType=new, type=Interface
```

After `implemented`, the go-live has applied the deltas to the catalog, which is the realized overall state of the instance and the baseline of the next solution.

**Plateau mode (Enterprise)**:

```text
Baseline P0: CRM-Legacy [active], ERP-Core [active]

Solution: "Salesforce Introduction" (plateau mode)
  fromPlateau: P0  →  toPlateau: P1 (Target 2027)
  deltas:
    - CRM-Legacy:  deltaType=retiring
    - Salesforce:  deltaType=new, type=ApplicationComponent
    - Integration: deltaType=new, type=Interface

Target P1 (after go-live):
  - ERP-Core    [active]    ← inherited from P0
  - Salesforce  [active]    ← new through solution
  - CRM-Legacy  [retiring]  ← being taken out of service
```

## Delimitation

- **NOT** a [Plateau](./plateau.md): A plateau is a stable state; a solution is the change process. In project mode, solutions exist without any plateau.
- **NOT** a project**: A solution is an architecture description; the project (budget, timeline, teams) is external (PPM tool integration is noted in §23 as an open question).
- **NOT** an ADR: Architecture decisions accompany a solution but are separate artifacts.

## Usage in Use Cases

- [UC-05](../requirements/use-cases/UC-05-describe-architecture-vision.md) (create a solution and describe its architecture vision, US-038)
- [UC-04](../requirements/use-cases/UC-04-configure-metamodel.md) (metamodel extension for solution, REQ-037)

## Notes

TOGAF reference (plateau mode): "Solution Architecture" in phase E/F. The `EntityDelta` structure corresponds to TOGAF Architecture Change Request + Gap Analysis. The questions on referential integrity across plateaus (§23 item 6) concern plateau mode exclusively; in project mode there are no plateau boundaries.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | Business Engineer | Initial draft |
| 0.2.0 | 2026-06-25 | Business Engineer | Made `fromPlateauId` + `toPlateauId` optional; introduced project mode (SME); adapted BR-01 (either both set or both null); comparison table of the modes |
| 0.3.0 | 2026-11-12 | Change us-038-create-solution | Project mode implemented (table `solutions`, V25): name unique case-insensitively after trimming, owner and creator the creating person, BR-01 as a database check; `metamodelExtensionId` removed in favor of the extension's `solutionId`; plateau references without foreign keys until S14 |
| 0.4.0 | 2026-11-12 | Change us-039-solution-baseline | Baseline in project mode realigned: the current catalog instead of the sum of all `implemented` solutions |
| 0.5.0 | 2026-11-12 | Change us-040-entity-deltas | EntityDelta stored (V26): `entityId` null for `new`, which carries metatype, name, description and property values; `createdAt`, `createdBy`; BR-03 extended to `archived`; BR-06 (one delta per entity, at most 1000) |
| 0.6.0 | 2026-11-13 | Change us-041-modified-delta | `changes` of a delta `modified`: name, description and properties with recorded before values; `updatedAt`, `updatedBy` |
