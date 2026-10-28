# Complete Chapter Overview

Chronological list of all chapters with a short description.

## 00 – Overview

1. [Positioning and Objectives](00-overview/01-positioning.md) – Scope, guiding principles
2. [Meta-Metamodel: The Three Levels](00-overview/02-meta-metamodel.md) – M2/M1/M0 concept, reification

## 10 – Foundations

3. [Relationship to Arc42 and TOGAF](10-foundations/03-framework-relationship.md) – Friction points and resolution
4. [Enterprise Continuum and Technical Reference Model](10-foundations/04-enterprise-continuum-trm.md) – ABBs, SBBs, patterns, TRM
5. [Architecture Principles, Standards, and Decisions](10-foundations/05-principles-standards-adrs.md) – Principle vs. standard, waivers, ADRs

## 20 – Entities

6. [Core Entity Types](20-entities/06-core-entity-types.md) – TOGAF mapping, Arc42 mapping, BPMN integration
7. [Motivation, Stakeholders, and Goals](20-entities/07-motivation-stakeholders-goals.md) – Vision, driver, goal, stakeholder, glossary
8. [Organization, Roles, and Persons](20-entities/08-organization-roles-persons.md) – Org hierarchy, role types, skills, RACI, governance bodies
9. [Process Architecture](20-entities/09-process-architecture.md) – Process landscape (abstract), BPMN 2.0 editor at Descriptive conformance level, CRUD matrix, value streams
10. [Additional Cross-Cutting Concepts](20-entities/10-cross-cutting.md) – Location, security, sustainability, contracts, deliverables, protection need, requirements repository

## 30 – Dynamics

11. [Temporal Model](30-dynamics/11-temporal-model.md) – Plateaus, lifecycle, gaps, work packages, target states
12. [Domain Concept and Views](30-dynamics/12-domain-views.md) – n:m domains, landscape maps (Bebauungspläne), stored views
13. [Linking the Business and Technology Levels](30-dynamics/13-business-technology-linking.md) – The traceability chain

## 40 – Extensibility

14. [Extensibility](40-extensibility/14-extensibility.md) – Custom EntityTypes, stereotypes, constraints
15. [Schema Evolution](40-extensibility/15-schema-evolution.md) – Subjective scopes, patterns, versioning, data quality

## 50 – Walkthrough

16. [Example Walkthrough](50-walkthrough/16-example-walkthrough.md) – CRM migration as an end-to-end scenario

## 60 – Integrations

17. [ITSM Integration (CMDB and Service Catalog)](60-integrations/17-itsm-integration.md) – Mastership, services, ITIL 4 practices
18. [Project Portfolio Integration (PPM)](60-integrations/18-ppm-integration.md) – Project, program, portfolio, benefits, ARB
19. [Agile Scaling Frameworks (SAFe, LeSS)](60-integrations/19-agile-scaling.md) – Framework-specific mapping and schema profiles
20. [GRC, GDPR, and ISMS Integration](60-integrations/20-grc-gdpr-isms-integration.md) – Asset export, Art. 30, third-country transfers, ISO 27005, audit trail

## 70 – Platform

21. [API Architecture and Modularity](70-platform/21-api-architecture.md) – Layers, modules (incl. BPMN), events, SDK, property-level authZ, real-time collaboration
22. [Analyzability and Query Architecture](70-platform/22-analyzability.md) – Graph traversal, secured SQL analytics on reporting views, persistence decision (ADR-034)

## 90 – Backlog

23. [Open Issues / Next Decisions](90-backlog/23-open-issues.md) – ADR candidates
24. [Next Steps](90-backlog/24-next-steps.md) – Review, use case catalog, walking skeleton

---

## Reading Paths

Depending on focus, different orders make sense:

**For decision makers** (What is the concept?):
README → 01 → 03 → 11 → 12 → 16

**For metamodel designers** (How is it structured?):
02 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11 → 14 → 15

**For integration architects** (How does it dock onto existing systems?):
17 → 18 → 19 → 20 → 21

**For implementers** (What is to be built?):
21 → 22 → 23 → 24

**For business unit stakeholders** (What does this mean for us?):
01 → 07 → 08 → 09 → 12 → 16

**For compliance/data protection/security roles** (What is supported from a regulatory perspective?):
01 → 10 → 17 → 20 → 21
