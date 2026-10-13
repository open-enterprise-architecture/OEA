# Stakeholder: Rigobert – Product Owner and Repository Owner

**ID**: SH-09
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Rigobert is the product owner and repository owner of OEA. He makes strategic architecture and product decisions (ADRs), is responsible for the scope of the requirements phase, and has full administration rights (`system-admin`) when operating his own OEA instance. He is technically versed, knows the domain model completely, and acts as the primary decision maker for conflicting requirements.

## Role and Responsibility

- **Current role**: product owner, repository owner, system administrator (own instance)
- **Decision level**: strategic (scope, tech stack, ADRs), tactical (priorities, release cuts)
- **Main daily tasks**:
  - Make and document strategic ADR decisions
  - Prioritize requirements (MoSCoW)
  - Maintain product scope and vision
  - System administration of the own OEA instance (bootstrapping, roles, backup)
  - Check quality gates (trace check, link validation)

## Relation to OEA

- **Usage frequency**: daily (active development phase)
- **Main usage**: make decisions, maintain requirements, administer the instance
- **Technical level**: very high (domain model, tech stack, API, DevOps)
- **Preferred interface**: direct editing of Markdown artifacts, CLI, admin UI

## Concerns

| Concern | Category | Severity |
|---|---|---|
| All data and decisions transparently traceable (ADR, audit log) | compliance | blocking |
| Full access to all properties and entities (admin override) | functional | blocking |
| System integrity ensured during error analysis and data migration | reliability | blocking |
| Product scope clearly delimited and consistently documented | functional | worrying |
| Open-source-suitable license and structures (Apache 2.0) | compliance | worrying |

## Connections to Other Stakeholders

- **All personas**: Rigobert decides which concerns are addressed in which order
- **SH-06 (Max)**: overlapping admin role in operations; Max for SME operations, Rigobert for the own development instance

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
