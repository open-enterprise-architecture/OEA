## 11. Temporal Model

This is the conceptual centerpiece of the tool.

### 11.1 Plateau as a Temporal Anchor

A **plateau** (term from ArchiMate) represents a consistent architecture state at a point in time or for a period.

```
Plateau {
  id: "baseline-2026-q1"
  name: "Current State Q1 2026"
  type: baseline | transition | target
  validFrom: 2026-01-01
  validTo: 2026-03-31
  description: "..."
}
```

Typical plateau structure of an enterprise:

- **1 baseline** ("current state today")
- **1–n transition plateaus** (intermediate states, e.g., "after migration phase 1")
- **1–n target plateaus** (target states, e.g., "target 2028", "long-term vision 2030")

### 11.2 Entity Lifecycle per Plateau

Every entity has a state **per plateau**:

| Lifecycle State | Meaning |
|---|---|
| `planned` | Planned, not yet implemented |
| `under-construction` | Under implementation |
| `active` | In production / in use |
| `sunset` | To be replaced, still active |
| `retired` | Decommissioned |
| `not-applicable` | Does not exist in this plateau |

Entities are **not deleted** when they are replaced – they get a lifecycle state per plateau. This preserves history and enables diffs.

### 11.3 Gaps and Work Packages

A **gap** is the difference of an entity (or group) between two plateaus. Gaps are:

- **computable** from lifecycle states and property differences, or
- **explicitly modeled** if they have their own business meaning

A **work package** closes one or more gaps:

```
WorkPackage {
  id: "wp-crm-migration"
  name: "CRM replacement Salesforce → HubSpot"
  closesGaps: [gap-001, gap-002]
  dependsOn: [wp-identity-migration]
  plannedStart: 2026-06-01
  plannedEnd: 2027-03-31
  deliverables: [...]
}
```

### 11.4 Target States – Structure

A target state is **not a new entity type** but a **view on entities with a state in the target plateau**, enriched with motivation entities:

- **Goal** (strategic goal)
- **Outcome** (measurable result)
- **Principle** (guideline)
- **Driver** (external/internal driver)
- **Assessment** (evaluation of the current state)

The connection: Driver → Assessment → Goal → Outcome → realized by → Capability → supported by → ApplicationComponent (in the target plateau).

### 11.5 Plateau Diff: Structured Comparison between Plateaus

The comparison between two plateaus is one of the most important views in the tool – it answers "what changes between current state and target?". The result is a structured difference that serves as the basis for migration planning, stakeholder communication, and gap analysis.

**Diff dimensions**: A plateau diff considers three kinds of changes:

```yaml
PlateauDiff:
  - sourcePlateau: Plateau              # e.g. baseline-2026
  - targetPlateau: Plateau              # e.g. target-2028
  - generatedAt: timestamp
  - changes:
      entitiesAdded: Entity[]             # new in target
      entitiesRetired: Entity[]           # no longer active in target
      entitiesModified: PropertyDiff[]    # same URN, changed properties
      lifecycleTransitions:               # same entity, different lifecycle state
        - entity: Entity
          stateInSource: active
          stateInTarget: sunset
      relationsAdded: Relation[]
      relationsRetired: Relation[]
      relationsModified: PropertyDiff[]
```

**Calculation rules**:

- **Added**: Entity exists only in `target`, or in `source` with lifecycle `not-applicable` and in `target` with an active state
- **Removed**: Entity active in `source`, `retired` or `not-applicable` in `target`
- **Modified**: Same URN, at least one property value different
- **Lifecycle transition**: Same URN, both plateaus active, different lifecycle state

**Filtering and aggregation**: A complete plateau diff over a large repository is overwhelming. Users can filter:

- By domain (finance changes only)
- By EntityType (application changes only)
- By significance (major changes only)
- By capability (only changes affecting capability X)
- Restricted to a sub-hierarchy (all modules of a suite)

**Diff as a basis for landscape map (Bebauungsplan) visualization**: The §12 landscape map views consume plateau diffs to display lifecycle transitions in color (e.g., green=new, red=retired, yellow=modified).

**Diff persistence**: Diffs are not stored persistently but calculated on demand. Exception: If a diff becomes part of a RepositoryRelease (see [§22.11 (Analyzability & Query Architecture)](../70-platform/22-analyzability.md)), it is preserved as a snapshot at the release tag – so that it remains traceable later what the state of the difference was at the time of the release tag.

**API endpoint**:

```
POST /api/v1/plateaus/{a}/diff/{b}
Body: { filter: { domain: "...", entityTypes: [...] }, aggregateBy: "domain" | "capability" | ... }
Response: PlateauDiff (structured)
```

**Views and reports on diffs**:

- **Migration backlog**: all lifecycle transitions `active → sunset` with assigned work packages
- **Innovations report**: all `entitiesAdded` with their goal/capability reference
- **Retirement report**: all `entitiesRetired` with rationale (typically via ADR reference)
- **Cross-plateau conflicts**: inconsistencies, e.g., entity is `retired` in plateau A, but plateau B still references it

---

← [Cross-Cutting](../20-entities/10-cross-cutting.md) · [🏠 Overview](../README.md) · [Domain Views](12-domain-views.md) →
