## 6. Core Entity Types

### 6.1 TOGAF Content Metamodel Mapping

The TOGAF architecture domains are mapped 1:1 as layers:

- **Business Architecture**: Actor, Role, OrganizationUnit, BusinessService, BusinessFunction, BusinessProcess, Capability, ValueStream, InformationObject
- **Data Architecture**: DataEntity, LogicalDataComponent, PhysicalDataComponent
- **Application Architecture**: ApplicationComponent, ApplicationService, ApplicationFunction, Interface
- **Technology Architecture**: TechnologyComponent, Platform, Node, CommunicationPath

### 6.1.1 Distinguishing Application from Technology in Practice

The TOGAF definitions are **structural**: ApplicationComponent encapsulates application functionality, TechnologyComponent encapsulates technology infrastructure. In practice, the distinction is nevertheless disputed for many objects – modern architectures (cloud-native, serverless, SaaS, platform products) blur the boundaries. The tool must be able to handle this fuzziness without escalating every borderline debate to the ARB.

**Why the distinction is difficult**:

- Causal interdependence: every technology ultimately exists to enable applications
- Perspective dependence: the same object can be an application for the operating team and a technology for the consuming team
- Platform products (Kafka, Kubernetes, Keycloak, Control-M) play both roles at the same time

**Four decision principles** as practical support:

1. **Business purpose test**: Does the object exist for an independent business reason, regardless of which other application needs it? CRM has the business purpose "manage customer relationships" → Application. A DB has no independent purpose, it exists for others → Technology.

2. **Replaceability test**: Would a replacement be purely technical or also business-related? Oracle to PostgreSQL → technical only → Technology. SAP to Oracle Financials → business changes as well → Application.

3. **Ownership test**: Is there a product owner with business responsibility who prioritizes features? Yes → Application. No, only operational responsibility → Technology.

4. **Cataloging test**: Would the object appear in a business application catalog that management reads? Yes → Application. No → Technology.

These principles are **heuristics, not algorithms** – in borderline cases they will give different answers. What matters is not that each principle is unambiguous on its own, but that the organization commits to one guiding principle and applies it consistently.

**Organizational principle as a requirement**: Every organization that uses the tool formulates an **architecture principle** (in the sense of [§5 (Principles & Standards)](../10-foundations/05-principles-standards-adrs.md)) that defines the distinction in a binding way. Two legitimate variants:

- **Variant A – "Platform services are technology"**: Products that primarily provide technical capabilities to other applications (messaging, DB, identity, API gateway) are modeled as TechnologyComponents – even with their own operating team. Kafka, PostgreSQL, Keycloak, Kong are technology.

- **Variant B – "Products with a product owner are applications"**: Every product with a business product owner is modeled as an ApplicationComponent – even if technical in nature. An internal identity system with a product owner is an application.

Both variants are legitimate. Which one an organization chooses is a governance decision documented as an ADR.

**Dual modeling for borderline cases**: If an object plays both roles in business terms (e.g., Control-M as a scheduler product *and* as a runtime platform for jobs of other applications), the metamodel supports three approaches:

- **Both entities**: ApplicationComponent and TechnologyComponent are modeled simultaneously, connected via a `realizes` relation. Maximally explicit, but maintenance-intensive.
- **Primary classification plus stereotype**: Modeled primarily as an application, with the stereotype `PlatformProduct` marking the technology role. Pragmatic.
- **ApplicationService as a bridge**: The application exposes an ApplicationService that other applications consume. The technology nature stays in the background, the interface is explicit.

Which approach is used should also be regulated in the organizational principle.

**Consequence for the tool**: The repository does not force either variant – the metamodel supports all. But it supports enforcement of the organization-wide decision via constraints:

```yaml
constraint: platform-service-classification
  appliesTo: PhysicalApplicationComponent
  when: "entity has stereotype 'PlatformProduct'"
  rule: "organization.classificationPrinciple == 'platform-as-application'
         OR entity also modeled as TechnologyComponent"
  severity: warning
  message: "Platform product classified contrary to the organizational principle"
```

This keeps the distinction a consistent organizational element – no endless fundamental debate per object.

#### Cognitive Reality: People Think in Solutions, Not in Layers

The logical/physical schema is analytically valuable but cognitively unnatural. People think in concrete solutions: "We have Salesforce." "Confluence runs there." These statements automatically mix business role and concrete implementation – this is not sloppiness but how perception works.

A successful OEA must acknowledge this reality and **must not enforce** analytical cleanliness. A central design decision follows from this:

**The logical layer is optional, not mandatory.**

- A `PhysicalApplicationComponent` can exist without an associated `LogicalApplicationComponent` – the tool does not block this
- An organization can start with pure physical modeling ("Which systems do we have?") and add the logical layer later as needed ("What business role does each system have?")
- Schema constraints that require a logical reference are designed as `severity: hint` or `warning`, not as `error`

This is consistent with the schema evolution approach from [§15 (Schema Evolution)](../40-extensibility/15-schema-evolution.md): requirements can be introduced scope-based instead of enforcing a global obligation.

#### Progressive Disclosure as a UX Strategy

The tool supports a step-by-step build-up across three views:

**View 1 – Inventory view (default for beginners and business units)**: Flat list of all products/systems, without an explicit level distinction. Application and technology side by side, visually marked (e.g., by color), but both entries of the same list in terms of content. Newcomers find everything they know without having to learn what "Logical Application Component" means.

**View 2 – Layer view (for architects)**: Clicking an entry reveals the structural embedding – which logical application stands behind it, which technology components run beneath it, which business roles are fulfilled. This view is requested when analytical depth is needed.

**View 3 – Landscape map view (Bebauungsplan) (for strategic decisions)**: Here the logical/physical separation becomes essential because migrations become visible (logical stays stable, physical changes).

The three views work on the same repository but show different levels of detail. Details on the view definition in [§12 (Domains & Views)](../30-dynamics/12-domain-views.md).

#### Auto-Generation of Logical from Physical

To support the step-by-step build-up, the tool can optionally offer **automatic logical stub generation**: If a PhysicalApplicationComponent is created without an explicit logical reference, the tool generates in the background a stub logical component ("`<Name> Logical`") that can later be enriched by the user or merged with other logicals.

This auto-generation is:

- Optional (can be enabled/disabled via schema configuration)
- Visibly marked as "auto-generated, pending review"
- Not semantically loaded – the stub inherits no business meaning, only the name

This creates a complete layer structure from the start without the modeler having to actively maintain it. If someone later takes the logical layer seriously, the stubs are starting points, not blockers.

### 6.2 Arc42 Mapping

Arc42 is primarily a **document structure for solution design**, not a modeling language of its own. The mapping is done via:

- **Building block view** → `ApplicationComponent` (hierarchical via `contains`)
- **Runtime view** → `Scenario` with references to building blocks
- **Deployment view** → `DeploymentNode` with `hosts` relations
- **Cross-cutting concepts** → `ArchitecturalDecision` / `Concept` entities
- **Architecture decisions** → `ADR` entity (with references to affected entities)

Arc42 documents are **generated from the repository**, not maintained separately.

### 6.3 BPMN 2.0 Integration

BPMN elements are full-fledged entities in the repository with explicit cross-layer references:

| BPMN element | References EA entity |
|---|---|
| `Pool` | `OrganizationUnit` |
| `Lane` | `OrganizationUnit` or `Role` |
| `Task` (User/Service/Manual) | `BusinessService` or `ApplicationService` |
| `DataObject` / `DataStore` | `InformationObject` or `DataEntity` |
| `MessageFlow` | `Interface` (business information flow) |
| `Event` (Message) | `Interface` |

This creates **end-to-end traceability**: a BPMN task knows which BusinessService it calls; the service knows its realizing application; the application knows its technology. This traversability is the basis for impact analyses.

---

← [Principles, Standards & ADRs](../10-foundations/05-principles-standards-adrs.md) · [🏠 Overview](../README.md) · [Motivation, Stakeholders & Goals](07-motivation-stakeholders-goals.md) →
