# Business Analysis

Stakeholder profiles, concerns, and pain points. Foundation for the requirements phase.

## Contents

```text
business-analysis/
├── README.md                    ← this document
├── vision.md                    ← a vision in one sentence, supplemented by context
├── stakeholders/                ← concrete persons or roles
│   ├── SH-01-...md
│   └── ...
├── concerns/                    ← cross-stakeholder concerns
│   └── ...
└── glossary.md                  ← project-specific term definitions
```

## Approach

### Step 1: Capture the Vision

One statement in one sentence: What should the tool achieve for whom? Example:

> "The tool gives an enterprise architect full control over the landscape, target states, and governance via a Git-based repository, without tying them to proprietary formats."

This vision decides every later trade-off. It is recorded in `vision.md`.

### Step 2: Identify Stakeholders

Three to five concrete persons (with real names from your environment, if possible) as primary stakeholders. One profile per person based on `../templates/stakeholder.template.md`.

**Avoid the anti-pattern**: "Enterprise architect with medium experience" is meaningless. "Stefan, solution architect at a Swiss insurer, frustrated by Sparx EA license costs" is gold.

### Step 3: Collect Pain Points

Per stakeholder, the biggest problems today. What costs time? What does not work? What is annoying? Pain points are more valuable than wishes because they generate concrete use cases.

### Step 4: Derive Concerns

Cross-stakeholder worries that the tool must address. Examples:

- "Architecture documentation becomes outdated quickly"
- "EA tools are expensive and proprietary"
- "Compliance evidence is manual and error-prone"

Concerns are later translated into use cases.

## Connection to Other Folders

- Stakeholders → use cases in `../requirements/use-cases/`
- Concerns → requirements in `../requirements/`
- Vision → guiding principles in `../concept/README.md`

## Slash Commands

The following should be available in `.opencode/commands/`:

- `/new-stakeholder` – creates a new stakeholder profile
- `/list-stakeholders` – shows all stakeholders with use case reference
