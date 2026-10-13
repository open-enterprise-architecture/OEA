## 19. Agile Scaling Frameworks (SAFe, LeSS)

Large organizations today often work with scaled agile methods. The two most widespread – SAFe (Scaled Agile Framework) and LeSS (Large-Scale Scrum) – have **fundamentally different philosophies** and place different demands on EA integration.

### 19.1 SAFe – Framework-Rich and EA-Friendly

SAFe is a comprehensive framework with explicit EA concepts. Integration with an EA repository is comparatively straightforward because SAFe **names and structures** many concepts that exist in an OEA anyway.

**Relevant SAFe concepts and their EA counterparts:**

| SAFe concept | EA counterpart | Remark |
|---|---|---|
| Strategic Theme | Goal / Outcome | 3–5 year horizon, portfolio-wide |
| Portfolio Canvas | Portfolio + Capability Map | Strategic framework |
| Portfolio Epic | Work Package (large) | Moves between Portfolio Kanban stages |
| Enabler Epic | Work Package (technical) | Infrastructure, compliance, research work |
| Solution | LogicalApplicationComponent (suite) | System of systems |
| Capability (SAFe) | Its own type! Not EA capability! | Confusing: same term, different meaning |
| Feature | Feature / large user story | Implementable within one PI |
| Architectural Runway | Technical enabler + roadmap | "Upfront investment in architecture" |
| Intentional Architecture | Steered architecture decisions | Counterpart to "emergent design" |
| System Team | OrganizationUnit | Cross-cutting for integration |
| ART (Agile Release Train) | OrganizationUnit + solution assignment | 50–125 people around a solution |

**The capability trap**: SAFe uses "capability" for something different than TOGAF. In SAFe, a capability is a **large feature unit spanning several PIs**. In TOGAF, a capability is a **long-term enterprise ability**. In the metamodel, I recommend:

```yaml
Stereotype: SAFeCapability
basedOn: WorkPackage
properties:
  - safeState: funnel | analyzing | portfolio-backlog | implementing | done
  - targetPI: string  # e.g. "PI-2026-Q2"
```

The TOGAF capability remains the primary capability term; the SAFe capability is mapped as a stereotype on WorkPackage. This avoids semantic collisions.

**Portfolio epic as a first-class bridge**: In SAFe, portfolio epics are the explicit place where strategy meets implementation. They have a **lean business case**, pass through a **portfolio Kanban**, and are implemented in **ARTs**. This is already a complete PPM structure that can be cleanly integrated with the EA repository:

```
Strategic Theme (EA: Goal)
  ↓ realizedBy
Portfolio Epic (EA: Work Package, Stereotype: SAFePortfolioEpic)
  ↓ decomposedInto
Capability (SAFe) / Feature
  ↓ implementedIn
Program Increment (PI) via ART
```

**Architectural runway** – an EA concept with a new name: SAFe denotes the upfront investment in architecture ("runway") that allows future features to be delivered quickly. This is nothing other than what classic EA knows as "technical infrastructure target states". In the metamodel:

```yaml
Stereotype: ArchitecturalRunway
basedOn: WorkPackage
additionalProperties:
  - enablesCapabilities: Capability[]  (which future capabilities are enabled)
  - runwayType: infrastructure | compliance | performance | usability
```

**Intentional architecture vs. emergent design**: SAFe demands a balance. The EA repository represents the *intentional architecture* – the deliberately planned structure. Emergent design happens in teams and should be **documented retroactively** when it becomes significant. The repository allows both, but the distinction can be marked as a property:

```yaml
ArchitectureDecision gains:
  - origin: intentional | emergent
  - decidedIn: ARB | ART-SystemTeam | Team
```

### 19.2 LeSS – Framework-Poor and EA-Critical

LeSS is the opposite of SAFe: minimal structure, radical focus on product instead of project, deliberately **skeptical of enterprise architecture** as a discipline.

LeSS basic principles with EA relevance:
- **One product, one product backlog, one product owner** – even with 8 teams
- **No additional roles** beyond Scrum (no ARB, no enterprise architect as a separate role)
- **Architecture emerges from the code** (in the sense of DDD, continuous architecture)
- **Avoidance of "architects without code"** – architects are ideally also developers

**What does this mean for your OEA?**

The philosophical tension: LeSS does not say "EA is wrong" but "EA as a separate discipline with separate roles is harmful". Instead:
- Architecture is the result of continuous decisions in the team
- Cross-team coordination takes place in design workshops, not in ARBs
- Documentation is as lean as possible (LeSS demands "whisper" documentation)

**Where does it still dock onto EA?**

LeSS organizations also have:
- **Multi-team products** with shared technical infrastructure
- **Cross-system interfaces** to other products / SaaS / legacy
- **Compliance requirements** that must be documented
- **Portfolio level**, at which products are funded and delimited

For a LeSS-compatible tool design, this means:

**The primary entity is "Product", not "Project"**:

```yaml
EntityType: Product
  properties:
    - vision, productGoal
    - lifecycleState: explore | exploit | sunset
  relations:
    - backlog: Epic[] | Feature[]
    - ownedBy: Role (Product Owner)
    - supportsCapability: Capability
    - realizedBy: LogicalApplicationComponent[]
```

A product is longer-lived than a project – it exists as long as the product exists. Projects are not needed in LeSS contexts, or only as temporary cost containers.

**Lean documentation policy**: The tool should not make every entity mandatory. A **minimal schema set** for LeSS contexts would be helpful:

```yaml
minimalSchema: less-core
includes:
  - Product
  - ApplicationComponent (hierarchical)
  - Interface
  - Capability (only at product level)
  - ADR
excludes:
  - Program (does not exist)
  - Portfolio-Epic (does not exist)
  - ARB (does not exist)
```

**Team-centric views**: While SAFe views are often ART-centric, LeSS views are team-centric:
- Which team maintains which components? (ownership)
- Which cross-team dependencies exist?
- Which interfaces are "contracts" between teams?

### 19.3 The Practical Consequence for Your Tool

A tool that wants to serve *both* worlds (and it should, as an open-source standard) must:

**1. Offer entities for SAFe AND product-centric work:**
- Project, Program, Portfolio → for SAFe/classic PPM
- Product → for LeSS/product-centric orgs
- Both can coexist, depending on schema configuration

**2. Allow different schema profiles:**

```yaml
profiles:
  - enterprise-safe:
      includes: [togaf-core, safe-portfolio, ppm-full, itsm-integration]
  - enterprise-classical:
      includes: [togaf-core, ppm-classical, itsm-integration]
  - product-less:
      includes: [togaf-core-slim, less-core, ddd-extensions]
  - solo-architect:
      includes: [togaf-core-slim, arc42, adr-only]
```

Organizations choose their profile at setup. The meta-metamodel stays the same, but schema activation varies.

**3. Keep vocabulary neutral**: The tool should not permanently say "Project" in its UI when an organization works on products. Configurable labels per entity type help to speak the respective organization's language.

**4. Governance flexibility**: An ARB is optional in SAFe and deliberately rejected in LeSS. The tool should *support* governance processes where they exist but not *enforce* them. Approval workflows as a plugin, not as core.

### 19.4 Summary: Metamodel Extensions for Agile Scaling

**New EntityTypes:**
- `Product` (for product-centric organizations)
- `ProgramIncrement` (SAFe PI as a time-period entity)
- `AgileReleaseTrain` (SAFe ART as an OrganizationUnit stereotype)

**New stereotypes:**
- `SAFeCapability` on WorkPackage
- `SAFePortfolioEpic` on WorkPackage
- `ArchitecturalRunway` on WorkPackage
- `EnablerEpic` on WorkPackage

**New properties on existing types:**
- `ArchitectureDecision.origin: intentional | emergent`
- `Project.methodology: waterfall | agile-scaled | agile-product | hybrid`
- `WorkPackage.safeState` (optional, only with SAFe profile)

**Schema profiles** as organizational configuration, as outlined in [§19.3 (Agile Scaling)](19-agile-scaling.md).

---

← [PPM Integration](18-ppm-integration.md) · [🏠 Overview](../README.md) · [GRC, GDPR, and ISMS Integration](20-grc-gdpr-isms-integration.md) →
