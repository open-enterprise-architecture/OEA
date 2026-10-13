# ADR-017: Architecture Layer Strategy – Fully Open in v1.0

**Status**: accepted
**Date**: 2026-06-27
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Business Engineer
**Informed**: ADR-016 (persistence), ADR-008 (GUI architecture)
**Supersedes**: –

## Context and Problem

OEA models enterprise architecture as a network of `ArchitectureEntity` instances (elements + connections). Each instance belongs to a meta type (`EntityTypeDefinition`) that is configured in the metamodel. So far, `EntityTypeDefinition` contains a field `architectureLayerId` that indicates membership in an architecture layer.

What was open: **Should these layers be predefined and enforced by the system – or does the operator determine which layers (if any) exist in their model?**

This question arose in the context of plateau/go-live modeling, because layer-specific go-live semantics (e.g. "technology layer deployed, application layer not yet") are only possible if layers are known to the system.

## Decision Drivers

- **Organizational diversity**: EA frameworks differ greatly — TOGAF, ArchiMate, Zachman, domain-driven models, pure capability maps. A fixed layer specification excludes organizations that think differently.
- **Refactoring risk**: Hard system-side layer IDs are a promise to all operators. Later changes (e.g. introducing a 4th data layer retroactively) break existing installations.
- **Consistency with the metamodel concept**: The metamodel is deliberately configurable (EntityTypes, domains, stereotypes, properties). Layers as the only hard system concept would be an inconsistent break.
- **Template approach as alternative**: Opinionated starter configurations can ship ArchiMate 3.0 layers, standard EntityTypes, and standard rules — without forcing the system.

## Considered Options

### Option 1: Fully Open ✓

Layers are not system concepts. The operator defines in the metamodel which layers (if any) exist — as free categorization, not as an enforced construct. OEA ships templates that pre-populate common layer models.

- **Pro**: Maximum flexibility; no migration risks; consistent with the rest of the metamodel approach; smaller SMEs without formal EA discipline can use OEA without layer overhead
- **Con**: System cannot offer layer-aware validations (e.g. "warn about connections across layer boundaries"); plateau go-live cannot provide layer-specific progress display; model quality depends more on operator discipline

### Option 2: Soft Layers

Canonical layers (`business`, `application`, `technology`) are defined system-side, but optional. Operator can ignore or extend them, but the IDs are stable.

- **Pro**: Stable reference IDs for viewpoints and business rules; standard viewpoints out of the box; plateau can work layer-sensitively
- **Con**: Implicit promise that these three layers always exist; organization-specific layer models must be set up "on top"; potential confusion in 4-layer organizations (data as 4th layer is common in ArchiMate)

### Option 3: Hard Layers

Three canonical layers are mandatory, not deletable; operator can rename and add more.

- **Pro**: Highest predictability for tool-side features; impact analysis and plateau diff can work layer-sensitively
- **Con**: Greatest refactoring risk; forces even SME operators into a framework they do not know; contradicts the open-source promise of maximum adaptability

### Option 4: Strict Layers

Hard layers + system-enforced cross-layer relationship rules.

- **Pro**: Model integrity without configuration effort
- **Con**: OEA would become a prescriptive ArchiMate tool; ruled out for v1.0

## Decision

**Option 1: Fully Open.**

Layers are not a system concept in v1.0. `EntityTypeDefinition.architectureLayerId` remains as an optional free-text field (or reference to operator-defined layer definitions in the metamodel), but the system knows no canonical layer IDs and enforces no layer structure.

### Consequence for the Data Model

`EntityTypeDefinition` retains `architectureLayerId` as an optional reference — but the reference points to operator-defined layer entries in the `MetamodelConfiguration`, not to a system enum:

```
MetamodelConfiguration
  └── layerDefinitions[]          ← operator-defined (e.g. "business", "application", "technology")
        id: string                ← free ID, no system enum
        name: string
        description: string

EntityTypeDefinition
  └── architectureLayerId: string | null  ← optional; reference to layerDefinitions[].id
```

Entities whose meta type has no `architectureLayerId` are layer-independent. Valid.

### Template Strategy

OEA ships preconfigured starter templates:

| Template | Layer model | Target group |
|---|---|---|
| **ArchiMate 3.0** | Business / Application / Technology | Enterprises with TOGAF/ArchiMate governance |
| **Capability Map** | (no layer, domains only) | Strategic EA, SMEs without tech detail |
| **Blank** | (no presets) | Maximum freedom |

Templates contain: layer definitions, standard EntityTypes, standard stereotypes, optional business rules for cross-layer constraints.

### Consequence for Plateau and Go-Live

A plateau transition (go-live) is **layer-agnostic**: It describes the transition of the entire architecture (all entities, all layers as defined by the operator) from target to baseline. Layer-specific go-live progress displays are **not a v1.0 feature**; they could be implemented in v2.0 as a template-specific extension, provided the operator has defined layers.

## Consequences

### Positive Consequences

- No refactoring risk with future layer model changes
- SMEs can use OEA without EA framework knowledge
- Consistency: the entire metamodel is operator-controlled
- Templates create an entry point without decision pressure

### Negative Consequences / Trade-offs

- No system-side protection against layer violations (e.g. connecting a technology entity directly to a business entity without an application intermediate layer); must be defined as an optional business rule in the template
- Standard viewpoints in the template cannot be generated by the system — they must be preconfigured in the template
- Plateau progress display per layer not implementable without operator layer configuration

### Follow-up Decisions / Open Questions

- **Template format**: How are templates distributed and imported? (own ADR when the Walking Skeleton starts)
- **Business rule syntax**: How does an operator express "connection must not skip layer boundaries" as a rule? (part of the metamodel configuration, not yet specified)
- **`architectureLayerId` in entity.md**: Adjust section — field is an optional reference to operator-defined `layerDefinitions`, not a system enum

## References

**Related ADRs**: [ADR-004](./ADR-004-reification-details.md) (reification), [ADR-016](./ADR-016-persistence-strategy.md) (persistence)

**Business Objects**: [architecture.md](../business-objects/architecture.md) (operating modes), [plateau.md](../business-objects/plateau.md), [metamodel-configuration.md](../business-objects/metamodel-configuration.md)

**Concept**: §14 Extensibility, §23 Open Issues
