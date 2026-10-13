## 12. Domain Concept and Views

### 12.1 Domain as an n:m Classification

```yaml
Domain {
  id: "domain-finance"
  name: "Finance"
  parent: "domain-corporate" (optional, hierarchical)
  owner: OrganizationUnit
}
```

Entities are **assigned** to domains, not embedded in them:

```text
belongsTo-Relation {
  source: ApplicationComponent "SAP S/4HANA"
  target: Domain "Finance"
  primary: true
}
belongsTo-Relation {
  source: ApplicationComponent "SAP S/4HANA"
  target: Domain "Controlling"
  primary: false
}
```

One primary assignment + any number of secondary ones. This solves the cross-domain problem cleanly.

### 12.2 Landscape Map (Bebauungsplan) – Structure and Variants

A landscape map is a **view**, defined by:

- Scope: domain(s) / layer / capability subtree
- Time horizon: list of plateaus (e.g., current state → T+1 → T+2 → target)
- Dimension: Y axis (capability / domain / OrgUnit), X axis (time)
- Entities to display: ApplicationComponent, Interface, Technology, etc.

Typical landscape map variants:

1. **Domain landscape map** – e.g., "Finance landscape 2026–2030"
2. **Capability landscape map** – applications per capability over time
3. **Layer landscape map** – e.g., application layer only, enterprise-wide
4. **Program landscape map** – filtered to the work packages of a program
5. **Consolidated overall map** – for management reporting

All of them are **defined and stored views**, not separate data stores.

### 12.3 View Definition

```yaml
View {
  id: "view-finance-roadmap"
  type: roadmap | landscape | heatmap | matrix | diagram
  scope: {
    domains: [finance, controlling]
    layers: [application]
    entityTypes: [ApplicationComponent, Interface]
  }
  timeline: [baseline-2026-q1, transition-2027, target-2028]
  groupBy: capability
  render: [plantuml, mermaid, archimate-xml]
}
```

A view is described declaratively and rendered on demand – this is the basis for pipeline integration.

### 12.4 Progressive Disclosure – Three Views for Different Maturity Levels

The cognitive reality described in [§6.1.1 (Core Entity Types)](../20-entities/06-core-entity-types.md) – that people think in concrete solutions, not in layers – finds its concrete implementation in three predefined views, used depending on the maturity of the modeling and the needs of stakeholders.

#### View: Inventory (default for beginners and business units)

```yaml
View "inventory":
  type: list
  scope:
    entityTypes: [PhysicalApplicationComponent, PhysicalTechnologyComponent]
  groupBy: none           # flat list, no layer structuring
  display:
    visualMarkers:        # color differentiation
      PhysicalApplicationComponent: blue
      PhysicalTechnologyComponent: grey
    columns: [name, type, owner, status, domain]
  filters: [search, owner, domain, status]
  detailView: layer-view        # click opens view 2
```

The inventory view is the **entry view** into the tool: flat, searchable, usable without conceptual prior knowledge. Application and technology are side by side, visually distinguishable but equally findable. A business unit representative finds all systems they know here without having to learn what "Logical Application Component" means.

**Important**: This view also works when no logical layer is maintained – it shows only what actually exists.

#### View: Layers (for architects)

```yaml
View "layered-detail":
  type: structured
  scope:
    entityTypes: [Logical*, Physical*, ApplicationService, PlatformService]
  groupBy: realization-chain    # Logical realized by Physical, runs on Technology
  display:
    showLayers: true
    expandableHierarchy: true
  detailView: landscape-map-view
```

Here the structural embedding becomes visible: Which logical application stands behind a physical application, which technology components run beneath it, which services are exposed. This view is requested when analytical depth is needed.

If the logical layer is not maintained, the view shows this transparently: "This physical application has no logical reference. [Generate logical?]" – with the option of automatic stub generation.

#### View: Landscape Map (for strategic decisions)

The landscape maps (Bebauungspläne) described in [§12.2 (Domain & Views)](12-domain-views.md) deliberately use the logical/physical separation: logical stays stable across plateaus, physical changes. This view requires mature modeling with a maintained logical layer.

#### Progressive Build-up in the Repository

The three views are not an exclusive hierarchy – they support a **step-by-step build-up** of the model:

| Maturity level | What is modeled | Which views are usable |
|---|---|---|
| 1 – Initial | Only physical applications and technologies | Inventory ✓, Layers ⚠️ (with gaps), Landscape map ✗ |
| 2 – Structured | + Logical applications, ApplicationServices | Inventory ✓, Layers ✓, Landscape map ⚠️ (without plateaus) |
| 3 – Temporal | + Plateaus, lifecycle assignment per plateau | All views ✓ |
| 4 – Complete | + Capabilities, goals, landscape map variants | All views ✓ with full power |

An organization can stay at any level and work with the tool meaningfully. Moving up to higher levels happens when the concrete need arises – not because the tool enforces it.

#### Implementation: Views as Stored Queries

All three views are implemented as stored views (see [§22.8 (Analyzability & Query Architecture)](../70-platform/22-analyzability.md)) and are shipped with the tool. Organizations can adopt them, adapt them, or define their own variants.

---

← [Temporal Model](11-temporal-model.md) · [🏠 Overview](../README.md) · [Business–Technology Linking](13-business-technology-linking.md) →
