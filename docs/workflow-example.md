# Example Workflow: From PO Request to Merged Feature

Concrete end-to-end example for a first feature in the walking skeleton. The document shows every step with example prompts to the agents and realistic responses.

**Scenario**: You want Franz (SH-01, junior domain architect) to be able to capture an Application Component in the tool. This is one of the first walking skeleton features.

**Duration**: Realistically 1-2 weeks, depending on the depth of the reviews.

> **Note**: This example predates the implementation phase. Implementation now runs through OpenSpec changes (see `openspec/README.md`) instead of `tasks/` files, and the tech stack is decided in ADR-011 (Vue 3) and ADR-012 (Java 21 + Spring Boot 3). The phases before implementation still apply.

---

## Phase 0 – Check Prerequisites

Before you start, make sure:

- [ ] Vision in `business-analysis/vision.md` is formulated
- [ ] At least Franz (SH-01) exists as a stakeholder
- [ ] AGENTS.md is in the repo
- [ ] Seven agent files are in `.opencode/agents/`
- [ ] Templates are in `templates/`

If not: fix that first, otherwise the agents work in a vacuum.

---

## Phase 1 – Initial Clarification with Solution Architect

**You** (in the OpenCode chat):

> I want to start with the walking skeleton. As the first feature, Franz (SH-01) should be able to capture an Application Component. Please act as Solution Architect (`.opencode/agents/solution-architect.md`) and clarify the approach with me.

**Solution Architect** reads the concept and persona, responds roughly:

> Before we start, three clarifying questions:
> 
> 1. **The business object does not exist yet**. Application Component is described conceptually in §6, but we have no `business-objects/application-component.md`. Should the Business Engineer model it first?
> 
> 2. **Open ADRs are relevant**: ADR-001 (URN schema) and the still-missing ADR on persistence. Do we want to decide these first or clarify iteratively?
> 
> 3. **Tech stack still open**: Backend and frontend engineers have not yet made a proposal. Should that happen before or in parallel?

**You** decide:

> Business object first – that is the basis. ADRs iteratively – I decide when a concrete conflict arises. Tech stack in parallel – ask the two engineering agents for proposals today.

**Solution Architect** creates a `tasks/uc-01-capture-application-component/` folder with an `architect-notes.md` documenting the plan. It contains:

```markdown
# Plan: UC-01 Capture Application Component

1. Business Engineer models Application Component (business-objects/application-component.md)
2. Backend Engineer makes stack proposal
3. Frontend Engineer makes stack proposal
4. PO decides stack (ADR-006 backend, ADR-007 frontend)
5. Formulate use case
6. Derive requirements
7. UI Designer builds clickable prototypes (Storybook stories)
8. Backend implements
9. Frontend implements
10. Security reviews
11. Integration and PO acceptance
```

---

## Phase 2 – Model Business Object

**You**:

> Please act as Business Engineer now and model "Application Component" as a business object. Use `templates/business-object.template.md`.

**Business Engineer** reads the template, §6.1.1 (Application vs. Technology) and §6.1, asks:

> Clarifying questions:
> 1. Logical/physical separation: Should Application Component be physical only for the walking skeleton, or both layers? §6.1.1 says logical is optional.
> 2. Owner: Should this be free text or a reference to stakeholder/role?
> 3. Lifecycle: Which states make sense initially? Proposal: `proposed → active → sunset → retired`. Does that fit?
> 4. Relationship to capability: 1:n (an application belongs to one capability) or n:m?

**You** answer:

> 1. Walking skeleton physical only. Logical comes later.
> 2. Owner is a reference to Role.
> 3. Four states are fine – plus `planned` before them.
> 4. n:m. An application can serve several capabilities.

**Business Engineer** produces `business-objects/application-component.md`:

```markdown
---
identifier: application-component
name_de: Anwendungskomponente
name_en: Application Component
version: 0.1.0
status: draft
owner_role: domain-architect
references:
  concept:
    - concept/20-entities/06-core-entity-types.md
---

# Business Object: Application Component

## Definition
An independently deployable software unit that encapsulates application functionality 
(TOGAF Content Metamodel).

## Attributes
| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| name | string | required | - | 3-255 characters | Unique name |
| owner | reference→Role | required | - | - | Responsible role |
| description | string | optional | - | max 2000 | Business description |
...
```

**You review** and accept, or request changes.

---

## Phase 3 – Obtain Tech Stack Proposals in Parallel

**You** (two separate sessions or sub-agents):

> Backend Engineer: Please make a stack proposal for the backend. Take persona Max (SH-06) and the type safety requirement into account.

**Backend Engineer** responds with a comparison table and recommendation. Example:

> **Recommendation**: Java 21 + Spring Boot 3 + Hibernate + Flyway
> 
> Rationale:
> - Spec-first with `openapi-generator` from `api/openapi.yaml`
> - Static typing satisfies the type safety requirement
> - Single container image, mature operations tooling (suitable for Max)
> - Large enterprise OSS community for attracting contributors
> - Secondary choices: PostgreSQL + JSONB
> 
> Alternative: Kotlin + Ktor (smaller ecosystem in the target organizations)

**Frontend Engineer** in parallel:

> **Recommendation**: Vue 3 + TypeScript + Vite + Pinia + PrimeVue
> 
> Rationale:
> - First-class TypeScript support since Vue 3.3
> - Gentler learning curve than React (good for OSS contributors)
> - PrimeVue: A11y built in, design-token capable, dark mode
> - openapi-typescript for API client generation

**You decide**:

> Backend Java 21 + Spring Boot 3, frontend Vue 3. Please document as ADR-012 and ADR-011.

Solution Architect or you yourself create the ADR files.

---

## Phase 4 – Formulate Use Case

**You**:

> Solution Architect, let's formulate UC-01. Persona Franz, he should capture an Application Component.

**Solution Architect** proposes a structure:

> Proposal for UC-01:
> - Title: "Capture Application Component in the repository"
> - Basic flow: Wizard with three steps (name → owner → capability assignment)
> - Preconditions: Franz is logged in, has role "domain-architect"
> - Acceptance criteria: Application Component appears in the inventory view (§12.4)
> 
> Clarifying questions:
> 1. Assign multiple capabilities or only one in the MVP?
> 2. For a duplicate name: block hard or warn?
> 3. Should the wizard be saveable as a draft, or only "finish or cancel"?

**You answer**, Solution Architect produces `requirements/use-cases/UC-01-capture-application-component.md` according to the template.

---

## Phase 5 – Derive Requirements

**You**:

> Requirements Engineer, derive the requirements from UC-01.

**Requirements Engineer** reads UC-01 and the Application Component business object, thinks through the seven types:

> The following result from UC-01:
> 
> **Functional**:
> - REQ-001: System MUST be able to create Application Component
> - REQ-002: System MUST offer a wizard with step navigation
> - REQ-003: System MUST display the Application Component in the inventory view immediately after creation
> 
> **Data**:
> - REQ-004: Mandatory fields name, owner; constraints from the business object
> - REQ-005: URN is generated automatically (see ADR-001)
> 
> **Non-Functional**:
> - REQ-006: Wizard step transition p95 <300ms
> - REQ-007: Creation endpoint p95 <500ms with repository <1000 entities
> 
> **Interface**:
> - REQ-008: POST /api/v1/applications per OpenAPI 3.1
> - REQ-009: Response contains generated URN and audit ID
> 
> **Compliance**:
> - REQ-010: Audit log entry on every creation (mandatory per §21.8)
> 
> **Constraint**:
> - REQ-011: Only authenticated users with role "domain-architect" or higher may create
> 
> Should I create all as drafts or prioritize?

**You**:

> All as drafts. REQ-006 and REQ-007 are SHOULD, the rest MUST.

Requirements Engineer produces 11 files in `requirements/req/`.

---

## Phase 6 – UI Designer Creates Clickable Prototypes

**You**:

> UI Designer, I need a prototype for UC-01. Persona Franz (junior, low threshold).

**UI Designer** reads UC-01 and the Franz profile:

> Prototype concept:
> - Wizard with three steps
> - Inline validation with helpful hints instead of error messages
> - Progress indicator at the top
> - "Save and exit" as a secondary button next to "Next"
> 
> Questions:
> 1. Capability selection: multiselect dropdown or tag input?
> 2. If the owner role is empty: suggestion "Set yourself as owner"?
> 3. Success state: navigate directly to the inventory view or confirmation screen?

You answer, the UI Designer writes the screen story with real PrimeVue components against the mock API (ADR-031):

```
frontend/src/screens/
└── SCR-NNN-capture-application.stories.ts   ← stories: step 1–3, validation error, success state
```

CI publishes the stories to GitHub Pages (clickable) and generates `docs/screens/SCR-NNN-light.png` / `-dark.png`; the UI Designer updates `docs/screens/SCREENS.md`.

---

## Phase 7 – Specs and Tickets

**Solution Architect** now creates the interface specs and tickets:

```
api/openapi/applications.yaml          ← POST /api/v1/applications
api/schemas/application-component.json ← data structure
tasks/uc-01-capture-application-component/
├── README.md
├── backend-task.md
├── frontend-task.md
└── security-review.md (preparation)
```

Each ticket references the specs, requirements, and acceptance criteria.

---

## Phase 8 – Implementation in Parallel

**You** invoke backend and frontend engineer (in parallel or sequentially – both have the spec as a common basis):

> Backend Engineer, implement `tasks/uc-01-capture-application-component/backend-task.md`.

**Backend Engineer** reads the spec, generates the Spring Boot setup with the OpenAPI-generated interfaces, writes:
- Flyway migration for the `application_components` table
- POST endpoint with validation, audit log, OIDC check
- Unit and contract tests against the OpenAPI spec

> Frontend Engineer, implement `tasks/uc-01-capture-application-component/frontend-task.md`.

**Frontend Engineer** reads the spec and the screen prototype, writes:
- Vue 3 components for the three wizard steps
- API client generated from OpenAPI
- Pinia store for wizard state
- Storybook stories for each new component
- E2E test with Playwright

---

## Phase 9 – Security Review

**You**:

> Security Engineer, please review the implementation in PR #1 for UC-01.

**Security Engineer** checks spec, backend code, frontend code, configuration:

> Security review UC-01:
> 
> **Critical**: none
> 
> **High**:
> - Audit log contains username in plain text – should be pseudonymized
> - POST endpoint has no rate limiting (see ADR on rate limits)
> 
> **Medium**:
> - Error messages leak internal paths in stack traces (adjust production logger)
> 
> **Low**:
> - CORS header too permissive for local setup
> 
> Sign-off possible once the high findings are addressed.

**You decide** what gets fixed before merge. Backend Engineer fixes the high findings. Security Engineer signs off.

---

## Phase 10 – Integration and Acceptance

**Solution Architect** checks consistency:

> Integration check UC-01:
> - Spec, backend, frontend consistent: ✓
> - Tests green: ✓
> - Audit log works: ✓
> - Security sign-off: ✓
> - Documentation updated: ✓
> 
> Ready for PO acceptance.

**You** as PO test it yourself (or mentally from Franz's perspective):
- Log in
- Click "New Application Component"
- Go through the wizard
- Check whether the component appears in the inventory view

If ok: merge to `main`. Set status of the artifacts to `realized`:
- UC-01: status `realized`
- REQ-001 to REQ-011: status `realized`
- Tasks: status `done`

Update the trace matrix in `requirements/traceability.md`.

---

## What You Learned in This Sequence

Once you have played this through, you know:

1. **Every agent asks instead of assuming** – that is a feature, not a bug
2. **You are never out of the loop** – but you do not have to micromanage every step either
3. **Specs are the truth** – backend and frontend can work in parallel without coordinating
4. **Reviews are quality gates** – the security engineer is not optional
5. **Traceability is mandatory** – in the end every artifact is linked to every other

## Next Iteration: Getting Faster

With the second use case, much becomes clearer automatically:

- Tech stack discussion is dropped (ADR-006/007 are made)
- The business object may already exist
- Templates are known
- You know which agents make sense when

Realistically: UC-02 takes half the time of UC-01. UC-10 a third.

## When Something Goes Wrong

**Agent makes an assumption that does not fit your world**: correct immediately, do not wave it through. The correction is valuable, the later refactoring is expensive.

**You lose the overview**: Invoke Solution Architect, "give me the status of all open tasks". He has the overview because he created the tasks.

**Security Engineer blocks because of "theater"**: review together whether the finding is realistic. If not: document the rationale, move on.

**Backend and frontend drift from the spec**: Call the Solution Architect, he must re-synchronize the spec.

**You are tired**: stop, document where you are. The agents forget nothing (the artifacts are in the repo). You can continue tomorrow.
