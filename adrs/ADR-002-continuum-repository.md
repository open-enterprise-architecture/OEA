# ADR-002: Enterprise Continuum – One Repository or Two?

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Requirements Engineer (metamodel-configuration.md, ADR-005)
**Informed**: all stakeholders

---

## Context and Problem

The TOGAF Enterprise Continuum distinguishes between generally reusable building blocks (Foundation/Industry/Common Systems Architecture) and organization-specific content. The open question: Are these kept in one repository with scope marking, or in two separate repositories?

**Prior decision already made** (metamodel-configuration.md): The metamodel already has the attribute `isBuiltIn: boolean` on EntityTypeDefinition. Built-in types (`bpmn-user-task`, `arc42-meta-object`, `organizational-unit`, etc.) are part of the software delivery. This concept is the starting point for the ADR decision.

## Decision Drivers

- **Simplicity**: No second repository for first-time users
- **Reusability**: Continuum content should be shareable between OEA instances
- **Update path**: New continuum versions (e.g. TOGAF TRM 2025) must be importable without losing local customizations
- **OSS distribution**: Continuum packages publishable as independent packages

## Considered Options

### Option 1 (chosen): One repository + import mechanism

All content (built-in, imported, organization-specific) in one OEA instance. Differentiation via a `scope` property on EntityTypeDefinition:

- `built-in`: part of the software delivery; immutable; update via OEA release
- `imported`: imported from a continuum package; immutable except for an explicit "fork"
- `organization`: organization-specific; freely modifiable

Continuum packages are JSON/YAML files with EntityTypeDefinitions and connection types, imported via `POST /api/v1/metamodel/import`.

**Pro**: simple architecture; uniform API; no sync problems; no second repository to operate
**Con**: Separation of continuum and org content is only possible via `scope` filter, not by physical separation

### Option 2: Two separate repositories

- Pro: Continuum as its own OSS package; clear separation
- Con: two APIs, two repositories, version management, reference resolution between repos — considerable complexity for v1.0

### Option 3: Sub-repository mechanism

- Pro: Continuum embedded, versioned separately
- Con: Git submodules or package mechanism increase deployment complexity; overkill for v1.0

## Decision

**Option 1 is the decision.**

**Scope model** on EntityTypeDefinition (complements metamodel-configuration.md):

| Scope | Description | Modifiable? | Origin |
|---|---|---|---|
| `built-in` | Part of the OEA software; always present | no (read-only) | OEA release |
| `imported` | Imported from continuum package | no; "fork" creates org copy | Package import |
| `organization` | Organization-specific | yes | Admin |

**Package format** (continuum library):

- JSON or YAML; contains EntityTypeDefinitions, connection types, optional example entities
- Identified by `packageId` (e.g. `oea-togaf-trm:1.0`) and `publisher`
- Import via API: `POST /api/v1/metamodel/packages/import`
- After import: types are `scope=imported`; immutable; organization fork via "Adopt as own copy" action

**Delivery packages** (v1.0):

- `oea-bpmn-2.0` — BPMN 2.0 types (already built-in in process.md)
- `oea-arc42` — Arc42 types (already built-in in arc42.md)
- `oea-togaf-starter` — basic TOGAF types (ApplicationComponent, TechnologyComponent, DataObject, etc.)

## Consequences

### Positive Consequences

- Uniform API for all EntityType operations (no second repository)
- Continuum packages are publishable and versionable as standalone OSS artifacts
- `scope=imported` protects package content from unintended modification
- Update path: import new package → existing `imported` types updated; org forks remain unchanged

### Negative Consequences / Trade-offs

- Physical separation of continuum and org content only via filter, not via repository
- Package import mechanism must be implemented for v1.0 (additional scope)

### Follow-up Decisions

- metamodel-configuration.md is extended with the `scope` property on EntityTypeDefinition
- REQ-004 (configure metamodel, UC-04) receives a new REQ for package import
- Clarification (owner decisions 2026-10-30 and 2026-10-31, OpenSpec change `us-032-metatype-editing`): mapping an organization property to an imported or built-in metatype extends it without changing it – name, abbreviation, base type and the mappings of the package stay read-only. Whether a re-import keeps or removes these organization mappings is configurable globally and per metatype along the base type (default: keep)

## References

**Concept Chapters**:

- [§4 Enterprise Continuum & TRM](../concept/10-foundations/04-enterprise-continuum-trm.md)

**Dependent ADRs**: ADR-005 (starter configuration uses import packages)

**Related Business Objects**: metamodel-configuration.md (EntityTypeDefinition + scope), process.md (BPMN built-in), arc42.md (Arc42 built-in)
