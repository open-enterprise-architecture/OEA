---
identifier: architecture
name_de: Architektur-Modell (Übersicht)
name_en: Architecture Model Overview
version: 0.3.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Architecture
  - EA-Modell
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/40-extensibility/14-extensibility.md
  - concept: concept/90-backlog/23-open-issues.md
  - adr: adrs/ADR-017-architecture-layer-strategy.md
---

# Architecture Model: Overview

> **Note**: This file was originally modeled as a generic "scope container" (v0.1.0). With v0.2.0 it becomes the overview file of the overall model. The concrete BOs are:
>
> - [Plateau](./plateau.md) — stable architecture state (baseline, target, transition)
> - [Solution](./solution.md) — initiative that moves entities from one plateau to another

---

## Operating Modes

OEA supports two operating modes that can be chosen depending on the organization's maturity. Both are based on the [Solution](./solution.md) concept; the [Plateau](./plateau.md) is optional.

| Mode | Plateau required | Suitable for |
|---|---|---|
| **Project mode** | no | SME, project-based way of working without a formal baseline-target scheme |
| **Plateau mode** | yes | Enterprise, TOGAF governance, formal baseline/target transitions |

In **project mode**, solutions are free workspaces. After go-live, the overall state of the landscape results from the sum of all `implemented` solutions. No plateau needed.

In **plateau mode**, solutions are transitions between plateaus. The overall state at a point in time is a plateau; solutions describe the delta between two plateaus.

---

## Conceptual Model (Plateau Mode)

In OEA, an enterprise architecture is described in two complementary dimensions:

### Dimension 1: States (Plateaus)

A **[Plateau](./plateau.md)** is a stable, named state of the overall architecture at a point in time. The OEA instance always has exactly one **baseline plateau** (current production state) and any number of **target plateaus** (planned target states).

```text
Plateau P0 (Baseline, productive)
  ├── ApplicationComponent: CRM-Legacy        [active]
  ├── ApplicationComponent: ERP-Core          [active]
  └── Interface: Legacy-ERP-Interface         [active]

Plateau P1 (Target 2027, not yet productive)
  ├── ApplicationComponent: ERP-Core          [active]    ← inherited from P0
  ├── ApplicationComponent: Salesforce        [active]    ← new (through solution)
  ├── ApplicationComponent: CRM-Legacy        [retiring]  ← being replaced
  └── Interface: Salesforce-API               [active]    ← new (through solution)
```

### Dimension 2: Transitions (Solutions)

A **[Solution](./solution.md)** describes the **delta** between two plateaus: which entities are created, change, or are decommissioned. It is the workspace of the solution architect.

```yaml
Solution: "Salesforce Introduction"
  fromPlateau: P0 (Baseline)
  toPlateau:   P1 (Target 2027)
  deltas:
    - CRM-Legacy:             retiring
    - Salesforce:             new, type=ApplicationComponent
    - Salesforce-API:         new, type=Interface
    - Legacy-Interface:       retiring
```

### Interplay

This corresponds to the **plateau principle** from TOGAF / ArchiMate 3:

```text
Plateau P0 (Baseline)
      │
      ├── Solution A ──────────────── Plateau P1 (Target A)
      ├── Solution B ──────────────── Plateau P2 (Target B)
      │   [multiple solutions run in parallel]
      │
      ▼ (after go-live of solution A)
Plateau P1 is realized → new baseline
Plateau P0 → realized (read-only)
```

### Metamodel Scoping

The instance metamodel ([MetamodelConfiguration](./metamodel-configuration.md) `scope=instance`) applies instance-wide. A solution can have its own metamodel extension (`scope=solution`) to try out new entity types without modifying the locked instance default metamodel (REQ-037).

```text
MetamodelConfiguration (scope=instance, editMode=import-only)
└── Solution extension (scope=solution, editMode=gui-and-import)
      └── own custom types, visible only within this solution
```

---

## Term Delimitation

| Term | What it is | BO file |
|---|---|---|
| **Plateau** | Stable architecture state (baseline / target) | [plateau.md](./plateau.md) |
| **Solution** | Initiative with entity deltas between plateaus | [solution.md](./solution.md) |
| **MetamodelConfiguration** | Instance metamodel + optional solution extension | [metamodel-configuration.md](./metamodel-configuration.md) |
| **ArchitectureEntity** | Concrete instance of an entity type (UUID + meta-type reference) | [entity.md](./entity.md) |
| **EntityDelta** | Value object: single change of an entity in a solution | defined in solution.md |

---

## TOGAF Reference

| OEA Term | TOGAF Equivalent | ArchiMate 3 |
|---|---|---|
| Plateau | Plateau | `Plateau` (migration layer) |
| Solution | Solution Architecture (Phase E/F) | `Deliverable`, `Gap` |
| fromPlateau / toPlateau | Baseline / Target Architecture | Plateau with roles |
| EntityDelta | Architecture Change Request / Gap Analysis | `Gap` |

---

## Conceptual Model (Project Mode / SME)

```text
OEA instance (no plateau)
  │
  ├── Solution "Salesforce Introduction" [implemented]
  │     deltas: CRM-Legacy retiring, Salesforce new
  │
  ├── Solution "API Gateway Introduction" [implemented]
  │     deltas: API-GW new
  │
  └── Solution "ERP Upgrade" [in-progress]
        deltas: ERP-Core v4 → v5 (modified)

Realized overall state = sum of all implemented solutions:
  - Salesforce        [active]
  - API-Gateway       [active]
  - ERP-Core v5       [active]  ← from running solution (after go-live)
  - CRM-Legacy        [retired]
```

---

## Open Questions (§23 Open Issues)

- **Item 3 – Bitemporality**: validity time (plateau time window) vs. recording time. The plateau model abstracts at first; the answer affects the DB schema.
- **Item 4 – Plateau diffs**: How are transitions computed and visualized? The solution/EntityDelta model provides the basis.
- **Item 6 – Referential integrity across plateaus**: `retiring` entities remain visible in the target plateau until go-live; afterwards `archived`, no longer usable as a relation target.

These questions are intended for future ADRs (group A or B).

---

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | Business Engineer | Initial draft as generic scope container |
| 0.2.0 | 2026-06-25 | Business Engineer | Converted to model overview; plateau + solution introduced as separate BOs |
| 0.3.0 | 2026-06-25 | Business Engineer | Project mode (SME) introduced: solutions without plateau; operating modes table; SME example |
