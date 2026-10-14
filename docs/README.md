# Documentation for PO and Contributors

Three onboarding documents plus this overview.

## Order

1. **Today**: skim the [Quick Reference](quick-reference.md) once (approx. 15 min)
2. **Before the first feature**: read the [Workflow Example](workflow-example.md) (approx. 30 min)
3. **After the first feature**: read the [Anti-Patterns](anti-patterns.md) (approx. 45 min)

Reading the anti-patterns before the first experience is abstract – after the first experience, a lot suddenly becomes clear.

## Contents

| File | Purpose |
|---|---|
| [quick-reference.md](quick-reference.md) | Tabular overview: which agent for what, ID conventions, slash commands, quality gates. Cheat sheet for lookup. |
| [workflow-example.md](workflow-example.md) | End-to-end example "Franz captures an Application Component" in 10 phases, with example prompts and realistic agent responses. |
| [anti-patterns.md](anti-patterns.md) | 29 typical failure patterns in 8 categories (PO role, agent usage, pipeline, spec discipline, security, documentation, scaling, human-machine). Prophylaxis. |
| [operations.md](operations.md) | Database provisioning, migration step and application start for operators. |

## Relationship to the Concept

These documents are **working documentation**, not concept. The actual concept lives in [`../concept/`](../concept/).

- Concept = what OEA is and does in terms of content
- docs/ = how we develop and maintain it
