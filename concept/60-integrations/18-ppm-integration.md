## 18. Project Portfolio Integration (PPM)

The EA repository describes *what* is to change architecturally. Project portfolio management (PPM) describes *which projects implement this with which resources by when*. A natural bridge exists between the two worlds – the **work package** – which is already laid out in the metamodel. This chapter details the integration.

### 18.1 Division of Roles EA ↔ PPM

| EA repository answers | PPM answers |
|---|---|
| What should change architecturally? | Which projects implement it? |
| Why (goals, outcomes, capabilities)? | With which budget, which resources? |
| Which target states by when (plateaus)? | Which milestones, which dependencies? |
| Which gaps need to be closed? | Who is the project sponsor, manager, team? |
| Technical/architectural risks? | Project/resource/schedule risks? |

EA thinks in **capabilities, gaps, and plateaus**. PPM thinks in **projects, milestones, and budgets**. The connection arises via work packages (EA side) and projects (PPM side).

### 18.2 New EntityTypes

```yaml
EntityType: Project
  properties:
    - name, description
    - status: proposed | approved | active | on-hold | completed | cancelled
    - dates: plannedStart, plannedEnd, actualStart, actualEnd
    - budget: { planned, committed, spent }
    - methodology: waterfall | agile | hybrid
    - planningConfidence: committed | planned | proposed | directional
    - externalReferences[] (to PPM tool IDs)
  relations:
    - realizes: WorkPackage[]
    - dependsOn: Project[]
    - sponsoredBy: OrganizationUnit
    - managedBy: Role
    - contributesTo: Goal[] | Outcome[]

EntityType: Program
  basedOn: Project (structural similarity)
  additionalRelations:
    - contains: Project[]
    - realizesStrategy: Goal | Outcome

EntityType: Portfolio
  properties:
    - portfolioType: strategic | operational | mandatory | innovation
  relations:
    - contains: Program[] | Project[]
    - structuredBy: Capability | Domain
    - ownedBy: OrganizationUnit

EntityType: Demand
  properties:
    - status: submitted | evaluated | approved | rejected | deferred
    - businessJustification
    - estimatedBudget, estimatedEffort
    - priority
  relations:
    - proposedFor: Capability | Domain
    - evaluatedAgainst: ArchitecturePrinciple[]
    - convertedTo: Project (if approved)

EntityType: BusinessCase
  relations:
    - forProject: Project
    - expectedBenefits: Benefit[]
    - contributesToGoal: Goal[]
  properties:
    - roi, npv, paybackPeriod

EntityType: Benefit
  properties:
    - type: cost-saving | revenue | quality | compliance | capability-uplift
    - plannedValue, realizedValue
    - realizationDate (planned / actual)
  relations:
    - measuredThrough: KPI
    - contributesToGoal: Goal
    - realizedThrough: Project

EntityType: Risk
  properties:
    - type: technical | financial | schedule | compliance | strategic | obsolescence | competence
    - probability, impact, status
    - mitigation
  relations:
    - affects: Project | Capability | ApplicationComponent | Interface
    - ownedBy: Role
```

### 18.3 Extensions of Existing Entities

**WorkPackage** gains:
- `realizedByProject: Project[]` – which projects implement it
- `requiredSkills: Skill[]` – competence demand
- `estimatedEffort, estimatedCost` – for portfolio planning

**PhysicalApplicationComponent** optionally gains:
- `tco: { licenseCost, operationalCost, developmentCost }` per year
- `costCenter`
- `financialLifecycle: invest | sustain | rationalize | retire`
- `activeProjects: Project[]` (currently working on this application)

**Capability** optionally gains:
- `investmentLevel: heavy | moderate | minimal | divest`
- `strategicPriority: high | medium | low`
- `timeCategorization: tolerate | invest | migrate | eliminate` (TIME model, Gartner)

### 18.4 Architecture Dependencies as Project Dependencies

A central strength of the integration: **project dependencies can be derived automatically from architecture dependencies.**

Rule: If component A depends on component B and project X changes component B while project Y changes component A, then project Y has a dependency on project X.

This derivation is impossible in pure PPM tools because they lack the architecture information. In the integrated repository, it becomes available as a view or automatic dependency suggestion.

### 18.5 Mastership per Concept

**EA is master for:**
- Work package, gap, plateau
- Strategic goal, capability, driver, outcome
- Architecture-specific risks (obsolescence, technical debt)
- Architecture principles, standards

**PPM is master for:**
- Project, program, portfolio (operational units)
- Demand / initiative (preliminary stages)
- Resources, budgets, concrete milestones
- Project-specific risks
- Benefits tracking (realization side)
- Business cases

**Jointly (property mastership):**
- Work package ↔ project: EA master for *what* is implemented, PPM master for *when/who/how much*
- Roadmap dates: the EA plan is influenced by project dates, PPM dates must fit the plateaus
- Benefits: EA master for strategic assignment, PPM master for realization status

### 18.6 Planning Horizons and Confidence

EA and PPM work with different planning horizons that must be synchronized:

- EA plateaus: typically 2–5 years
- Budget planning: often only 1–2 years
- Concrete projects: 6 months to 2 years
- Agile PIs/sprints: weeks to quarters

The `planningConfidence` property on projects allows roadmaps to be visualized in tiers:
- `committed`: budget approved, team allocated
- `planned`: in current portfolio planning
- `proposed`: in the backlog, not yet prioritized
- `directional`: strategically desired, no plan yet

### 18.7 New Views

- **Investment heatmap**: capabilities × investment level, colored by aggregated project budget
- **Portfolio-architecture alignment**: projects without a work package (architecturally blind) and work packages without a project (without implementation)
- **Resource demand over time**: skill demand aggregated from work packages, distributed over project periods
- **Benefit attribution**: which capabilities/goals benefit from which active projects
- **Technical debt portfolio**: applications with sunset pressure and a missing replacement project
- **Strategic alignment check**: active projects without a contribution to a strategic goal
- **Compliance risk view**: compliance requirements with an approaching deadline and not addressed by projects
- **TIME matrix** (Gartner): applications categorized as tolerate/invest/migrate/eliminate with associated investments

### 18.8 New Constraints

```yaml
constraint: work-package-has-project
  appliesTo: WorkPackage
  rule: "entity.lifecycleState == 'active' → entity.realizedByProject.length >= 1"
  severity: warning

constraint: target-plateau-coverage
  appliesTo: Plateau (type: target)
  rule: "all gaps to this plateau are addressed by active or planned projects"
  severity: error
  message: "Target plateau not covered by the portfolio"

constraint: sunset-has-replacement-project
  appliesTo: PhysicalApplicationComponent
  rule: "entity.lifecycleState == 'sunset' → exists Project that addresses retirement"
  severity: warning

constraint: no-invest-in-sunset
  appliesTo: Project
  rule: "project.affects.applications must not include applications with lifecycleState in [sunset, retired]"
  severity: warning
  message: "Project invests in an application that is being replaced"
```

### 18.9 Architecture Review Board as an Organizational Bridge

An **Architecture Review Board (ARB)** is the organizational docking point between EA and PPM. It reviews:

- Demands against architecture principles and target states
- Project business cases for strategic fit
- Deviations from standards (waivers)
- Major changes against architecture implications

In the metamodel:

```yaml
EntityType: ArchitectureReview
  properties:
    - reviewDate, outcome: approved | conditional | rejected
    - conditions[]
    - waiverGranted: boolean
  relations:
    - reviews: Project | Demand | Change
    - assessedAgainst: ArchitecturePrinciple[] | Plateau
    - decidedBy: Role (ARB members)
    - resultsInADR: ADR (for fundamental decisions)
```

### 18.10 PPM Tool Integration

Relevant PPM tools:
- **Enterprise**: ServiceNow SPM, Planview, Clarity PPM, Jira Align
- **Mid-sized company / open source**: OpenProject, Jira + portfolio plugins
- **Agile-native**: Jira Advanced Roadmaps, AzureDevOps, Linear

Pragmatic approach: **generic REST connector with mapping configuration** instead of deep tool specifics. A standardized import/export structure (based on PMBOK vocabulary) that every tool can serve. Tool-specific adapters as plugins.

### 18.11 Practical Principles

- **EA assesses demands *before* the portfolio decision** – not retroactively, otherwise the budget is already committed
- **Work package as a contract between EA and PPM** – changes to the work package require coordination between both sides
- **Link benefits to goals** – otherwise benefits realization trickles away and strategy success remains unmeasurable
- **Do not maintain separate roadmaps** – the integrated roadmap from repository data is the single source of truth

---

← [ITSM Integration](17-itsm-integration.md) · [🏠 Overview](../README.md) · [Agile Scaling](19-agile-scaling.md) →
