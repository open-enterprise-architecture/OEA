# Requirements Engineering Templates

Seven Markdown templates for the requirements phase: **Stakeholder**, **Business Object**, **Use Cases**, **Requirements** (all types), **User Stories**, **NFRs**, and the ADR template (separate in `adrs/`). They build on each other and reference the concept paper as well as the ADR directory.

## Overview

| Template | Purpose | When | File Name Scheme |
|---|---|---|---|
| `stakeholder.template.md` | Concrete persons/roles with concerns | First – without stakeholders no prioritization | `SH-NN-shortname.md` |
| `business-object.template.md` | Business domain modeling with attributes, relationships, lifecycle | **Before** use cases – domain model first | `<english-kebab-case>.md` |
| `use-case.template.md` | Goal-oriented user scenarios with pre/post conditions, flows, data flow | After stakeholders and business objects | `UC-NN-shortname.md` |
| `requirement.template.md` | Atomic requirements of all types (functional, NFR, constraint, business-rule, data, interface, compliance) | **After** use cases | `REQ-NNN-shortname.md` |
| `user-story.template.md` | Granular backlog items, implementable in 1-3 days | During implementation planning | `US-NNN-shortname.md` |
| `nfr.template.md` | Non-functional requirements with target values (legacy – can be replaced by `requirement.template.md` with `type: non-functional`) | In parallel with use cases | `NFR-NN-shortname.md` |

The ADR template is located separately in `adrs/adr.template.md` (closer to the ADR documents).

## How the Templates Relate

```
Stakeholder (SH)
    ↓ has concern → addressed by
Use Case (UC)
    ↓ is broken down into
User Stories (US)
    ↓ implemented by
Code + Tests

NFR
    ↓ applies to
Use Cases and Stories
    ↓ is verified in
Tests / Monitoring
```

**Most important cross-references**:

- Every user story → use case → stakeholder (trace)
- Every use case → at least one concept chapter
- Every NFR → at least one stakeholder
- Major architecture decisions → ADR

## Recommended Repo Structure

```
requirements/
├── README.md                   ← entry point, MoSCoW overview, trace matrix
├── glossary.md                 ← project-specific term definitions
├── stakeholders/
│   ├── SH-01-stefan-solution-architect.md
│   ├── SH-02-anna-enterprise-architect.md
│   └── ...
├── use-cases/
│   ├── UC-01-capture-application.md
│   ├── UC-02-export-landscape-map.md
│   └── ...
├── user-stories/
│   ├── US-001-create-entity-cli.md
│   ├── US-002-show-validation-error.md
│   └── ...
├── nfr/
│   ├── NFR-01-response-time-cli.md
│   ├── NFR-02-repository-scaling.md
│   └── ...
└── traceability.md             ← trace matrix use case × concept × ADR
```

## Slash Commands for OpenCode

Suggestion for your `.opencode/commands/`:

### `/new-stakeholder`

```markdown
Create a new stakeholder file in requirements/stakeholders/ based on
templates/stakeholder.template.md.

Procedure:
1. Ask for name/role for ID generation (SH-NN-shortname).
2. Determine the next free SH-NN number.
3. Copy the template, replace placeholders.
4. Update the stakeholder list in requirements/README.md.
```

### `/new-usecase`

```markdown
Create a new use case in requirements/use-cases/ based on
templates/use-case.template.md.

Procedure:
1. Ask for title and primary stakeholder.
2. Validate that the stakeholder exists (requirements/stakeholders/).
3. Determine the next free UC-NN number.
4. Copy the template, replace placeholders.
5. Concept reference: Ask which chapters are affected, or suggest concept/ chapters based on
   title/description.
6. Update the use case list in requirements/README.md.
7. Add the use case reference in stakeholders/SH-NN.md.
```

### `/new-story`

```markdown
Create a new user story in requirements/user-stories/ based on
templates/user-story.template.md.

Procedure:
1. Ask for title and parent use case.
2. Validate that the use case exists.
3. Determine the next free US-NN number (3 digits, because there are more stories than use cases).
4. Copy the template, replace placeholders.
5. Add the story in the use case under "Realizing Components".
```

### `/new-nfr`

```markdown
Create a new NFR in requirements/nfr/ based on templates/nfr.template.md.

Procedure:
1. Ask for title and category.
2. Determine the next free NFR-NN number.
3. Remind: NFRs without measurable target values are wishful thinking – fill in the target value table.
4. Copy the template, replace placeholders.
```

### `/trace-check`

```markdown
Check the requirements/ directory for completeness:

1. Does every use case have a stakeholder reference? (mandatory)
2. Does every use case have at least one concept reference? (mandatory)
3. Does every user story have a use case reference? (mandatory)
4. Does every NFR have a measurable target value? (mandatory)
5. Does every stakeholder have at least one use case? (Otherwise: why model them?)
6. Generate/update traceability.md with the matrix use case × concept × ADR.
```

## Practical Tips

### When Writing Stakeholders

- **Real names from your environment** beat abstract personas. If you do not name anyone, the persona is often too generic.
- Start with 3-5 personas, not 15. You can expand later.
- Pain points are more valuable than wishes. "Sparx EA is too expensive" is clearer than "want a cheap tool".

### When Writing Use Cases

- **Goal-oriented, not functional**: "Export landscape map (Bebauungsplan)" is good, "ApplicationComponent CRUD" is bad.
- Basic flow in at most 7-10 steps. If longer, it is a process, not a use case.
- Acceptance criteria must be verifiable. "Should be fast" is not verifiable. "Responds in <2s" is verifiable.

### When Writing NFRs

- **Always measurable**, otherwise it is not an NFR.
- Target values refer to a specific scope (e.g. "with 1000 entities"). Without scope the number is worthless.
- Document trade-offs explicitly – NFRs often compete with each other.

### When Writing User Stories

- Observe the INVEST criteria (Independent, Negotiable, Valuable, Estimable, Small, Testable)
- Story points estimated relative to other stories, not in hours
- Acceptance criteria as Given-When-Then for clarity

## Anti-Patterns

**1. Use case = data model listing**
Bad: "Use case: Capture Application Component with name, description, owner"
Good: "As a solution architect, I want to capture a new application so that it appears in the landscape view"

**2. NFR without a number**
Bad: "The tool should be performant"
Good: "CLI validation of a repo with 1000 entities completes in <30s (p95)"

**3. Stakeholder without concerns**
Bad: "Stakeholder: Enterprise Architect"
Good: "Stakeholder: Anna, EA at bank X, frustrated by 6 months of Sparx EA modeling without visible added value, wants a Git-based workflow"

**4. User story too large**
Bad: "As an architect, I want to use the complete OEA"
Good: "As an architect, I want to create an application via CLI so that it is available in subsequent tests"

## Recommended Start Sequence

1. **Day 1**: Write README, create folders, copy templates
2. **Day 2-3**: 3-5 stakeholder profiles (real persons, concrete pain points)
3. **Day 4-5**: 5-10 use cases for the most important pain points
4. **Day 6**: Derive NFRs from the use cases, make target values concrete
5. **Day 7**: Prioritization (MoSCoW across all UCs and NFRs)
6. **Day 8**: Generate traceability.md, find gaps
7. **Day 9-10**: Break down user stories for MUST use cases (walking skeleton backlog)
8. **Day 11-12**: First ADRs for blocking decisions (see `concept/90-backlog/`)

After that, implementation of the walking skeleton can begin.
