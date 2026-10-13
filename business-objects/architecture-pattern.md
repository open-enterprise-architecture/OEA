---
identifier: architecture-pattern
name_de: Architektur-Muster
name_en: Architecture Pattern
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Pattern
  - Architektur-Pattern
  - Muster
references:
  - concept: concept/10-foundations/04-enterprise-continuum-trm.md
  - adr: adrs/ADR-002-continuum-repository.md
---

# Business Object: ArchitecturePattern

## Definition

An `ArchitecturePattern` is a proven, reusable solution to a frequently occurring architecture problem. It describes the problem, the solution approach, applicability prerequisites, and consequences — in a format modeled on the Gang of Four description structure, but at the enterprise architecture level. Patterns are part of the Architecture Continuum and complement ABBs with structural and behavioral solution approaches.

## Description

Where ABBs specify *building blocks*, patterns describe *structures and interactions* between building blocks. Examples:

| Pattern | Problem | Solution |
|---|---|---|
| Strangler Fig | Gradually replace a legacy system | Proxy in front of the legacy system → gradually move functions into the new system |
| Event-Driven Architecture | Loose coupling between domains | Domains communicate only via events, no direct calls |
| API Gateway | Central entry point for clients | One gateway in front of all backend services; auth, rate limiting, routing there |
| Sidecar | Cross-cutting concerns (logging, proxy) without code changes | Companion container per service |

Patterns can be imported from general sources (e.g. TOGAF patterns, cloud design patterns) as well as developed in an organization-specific way.

When a pattern is applied in a concrete solution or entity, an `appliedIn` link is created: this makes it traceable where which pattern was actually implemented — and deviations from it can be identified.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | UUID | required | | v4, globally unique | Primary key |
| name | string | required | | max. 255 characters; unique per `scope` | Pattern name (e.g. "Strangler Fig", "CQRS") |
| problem | string | required | | max. 3000 characters; Markdown | Description of the problem this pattern solves |
| solution | string | required | | max. 5000 characters; Markdown | Solution approach, structure, and mechanism |
| applicability | string | optional | null | max. 2000 characters; Markdown | When is this pattern suitable? Prerequisites and restrictions |
| consequences | string | optional | null | max. 2000 characters; Markdown | Trade-offs, benefits, and known drawbacks of applying it |
| maturityLevel | enum | required | `experimental` | `[experimental, emerging, established, industry-standard]` | Maturity and adoption of the pattern |
| scope | enum | required | `organization` | `[built-in, imported, organization]` | Origin, analogous to ABB/SBB |
| sourcePackage | string | optional | null | max. 255 characters; only if `scope=imported` | Source package (e.g. "TOGAF 10", "Microsoft Azure Architecture Patterns") |
| tags | string[] | optional | [] | max. 20 tags, max. 50 characters each | Classification tags (e.g. "integration", "cloud", "security", "data") |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| appliedIn | [entity](./entity.md) | n:m | yes | ArchitectureEntities in which this pattern was explicitly applied |
| usedIn | [reference-architecture](./reference-architecture.md) | n:m | yes | Reference architectures built on this pattern |
| relatedPatterns | [architecture-pattern](./architecture-pattern.md) | n:m | yes | Related or complementary patterns (e.g. "CQRS" and "Event Sourcing" are complementary) |

## Business Rules

| Rule ID | Statement | Trigger |
|---|---|---|
| BR-01 | Patterns with `scope=imported` or `scope=built-in` are read-only | onUpdate, onDelete |
| BR-02 | `problem` and `solution` MUST both be set; a pattern without a problem description is not a pattern | onCreate, onUpdate |

## Lifecycle

Patterns have no formal governance lifecycle like ABBs or SBBs. They are maintained and can be introduced as `maturityLevel=experimental` and promoted to `established` through experience. Obsolete patterns are not deleted but marked as "deprecated" via `tags` (no dedicated enum field, since patterns are rarely formally withdrawn).

## Analyses

- **Pattern adoption**: How many entities apply a pattern? Low adoption → pattern possibly too complex or poorly documented
- **Pattern conflicts**: Two incompatible patterns on the same entity (`relatedPatterns` with "conflicts-with" semantics as a future feature)
- **Pattern coverage in reference architectures**: Which patterns are used in which reference architectures?

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Business Engineer | Initial draft; based on Enterprise Continuum §4 (concept) |
