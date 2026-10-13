# Requirements

Use cases, user stories, and non-functional requirements for OEA.

## Contents

```text
requirements/
├── README.md                    ← this document
├── traceability.md              ← trace matrix use case × concept × ADR
├── use-cases/                   ← goal-oriented scenarios
│   ├── README.md
│   ├── UC-01-...md
│   └── ...
├── user-stories/                ← granular backlog items
│   ├── README.md
│   ├── US-001-...md
│   └── ...
└── nfr/                         ← non-functional requirements
    ├── README.md
    ├── NFR-01-...md
    └── ...
```

## Templates

Templates are located in `../templates/`:

- `use-case.template.md`
- `user-story.template.md`
- `nfr.template.md`

## Approach

1. **Stakeholders first** (in `../business-analysis/stakeholders/`), do not start directly with use cases
2. **Use cases** derived from stakeholder concerns and pain points
3. **NFRs** in parallel with use cases, with measurable target values
4. **Prioritization** by MoSCoW (must/should/could/wont)
5. **User stories** only once use cases are prioritized – as a breakdown of the MUST use cases for the walking skeleton
6. **Traceability** checked regularly with `/trace-check`

## MoSCoW Overview

<!-- Keep manually up to date after prioritization or generate via script -->

| Priority | Use Cases | NFRs |
|---|---|---|
| MUST (Walking Skeleton) | | |
| SHOULD (v1.0) | | |
| COULD (v1.x) | | |
| WONT (for this phase) | | |

## Slash Commands

In `.opencode/commands/`:

- `/new-usecase` – creates a new use case
- `/new-story` – creates a new user story
- `/new-nfr` – creates a new NFR
- `/trace-check` – checks link consistency
