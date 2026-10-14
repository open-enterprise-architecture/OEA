# Proposal

## Why

<!-- 1-2 sentences: What problem does the change solve? Why now? Reference the motivation from UC/US instead of repeating it. -->

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-NN: Title](../../../requirements/use-cases/UC-NN-short-name.md) |
| User Stories | [US-NNN: Title](../../../requirements/user-stories/US-NNN-short-name.md) |
| Requirements | [REQ-NNN: Title](../../../requirements/req/REQ-NNN-short-name.md) |
| ADRs | [ADR-NNN: Title](../../../adrs/ADR-NNN-short-name.md) |
| Sprint / Milestone | SNN / Milestone N ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

<!-- Concrete changes as a list. Mark breaking changes with **BREAKING**. -->

## Out of Scope

<!-- What is deliberately not part of this change (e.g. an AC of a US that follows in a later change). -->

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Name each capability for a cohesive system
     behavior that can own related requirements as the system evolves. Do not name
     implementation tasks or proposal sections. Avoid broad catch-all names. Use
     kebab-case for path segments you introduce (e.g., user-auth or identity/user-auth)
     that follow the project's existing spec organization. Each creates
     specs/<capability-path>/spec.md. -->
- `<capability-path>`: <brief description of what this capability covers>

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use the exact existing path under openspec/specs/. Leave empty if no requirement
     changes. A change with no capabilities at all (pure refactor, tooling, docs)
     must set `skip_specs: true` in its .openspec.yaml - openspec validate rejects
     a zero-delta change without that marker. Do not invent a requirement just to
     satisfy validation. -->
- `<existing-capability-path>`: <what requirement is changing>

## Impact

<!-- Affected areas: backend/ (packages api/app/core), frontend/, api/openapi.yaml,
     Flyway migrations, docs/data-model.puml, business-objects/, docs/screens/ -->
