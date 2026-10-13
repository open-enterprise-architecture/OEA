# Meta-Metamodel for an EA and Solution Architecture Tool

**Status:** Draft v0.19.0 – see [CHANGELOG.md](CHANGELOG.md) for the version history
**Scope:** Open-source OEA, TOGAF-compliant (incl. Enterprise Continuum and TRM), Arc42-capable, BPMN 2.0 as an optional add-on module, extensible metamodel with controlled schema evolution, pipeline-capable diagram generation, API-centric architecture with strong analyzability (graph traversal and repository-wide aggregations), ITSM/CMDB, PPM, GRC/GDPR, and ISMS integration as first-class concepts

## Guiding Principles

- **Standards as configuration, not as code**: TOGAF Content Metamodel, Arc42, BPMN 2.0, ArchiMate are mapped as schemas onto a generic meta-metamodel.
- **Git-friendly, pipeline-capable**: Every entity can be represented as a human-readable file that is diffable and PR-reviewable.
- **Time as a first-class citizen**: Current state, target states, and landscape maps (Bebauungspläne) are not special cases but basic concepts of the metamodel.
- **Views instead of silos**: Domains, layers, and landscape maps are filters over the repository, not separate data stores.
- **ITSM coexistence, not competition**: The EA repository complements the CMDB and service catalog with a planning and strategy view. Mastership is defined at property level, not at entity level.
- **EA as asset master data supplier**: For GRC, GDPR, and ISMS, EA provides the structural basis (assets, classifications, flows). Risk assessments and evidence remain in the specialized tools.
- **API-first**: All functionality is accessible through a stable, versioned API. UI, renderers, integrations, add-on modules – all use the same API. No privileged access paths.
- **Modular architecture**: The core is small and independent. Business extensions (BPMN, ITSM connectors, PPM connectors, GRC/ISMS adapters, renderers) are modules that dock on via the API.
- **Persistence neutrality**: The API defines query capabilities; the implementation underneath is interchangeable. Enables migration to other databases without an API breaking change.
- **Property-level authorization**: Not only entities but individual properties are subject to access control. Personal data and protection needs are sensitive and must not be visible to all users.
- **Complete audit trail**: Every change to repository data is logged immutably, exportable, and access-protected. A compliance requirement, not an additional feature.
- **Progressive disclosure instead of enforced depth**: Conceptual correctness (logical/physical separation, layer model) is supported but not enforced. The tool accepts pragmatic beginner models (e.g., only physical apps without a logical layer) and allows gradual deepening. The intuitive "inventory view" is the default; the structured view is available for those who need it.

## Navigation

The concept paper is organized thematically into subfolders. A complete, ordered overview can be found in [INDEX.md](INDEX.md).

### Quick Access by Topic

- **Introduction**: [Positioning and Objectives](00-overview/01-positioning.md), [Meta-Metamodel](00-overview/02-meta-metamodel.md)
- **Foundations**: [Framework Relationship](10-foundations/03-framework-relationship.md), [Enterprise Continuum & TRM](10-foundations/04-enterprise-continuum-trm.md), [Principles, Standards & ADRs](10-foundations/05-principles-standards-adrs.md)
- **Entities**: [Core](20-entities/06-core-entity-types.md), [Motivation](20-entities/07-motivation-stakeholders-goals.md), [Organization](20-entities/08-organization-roles-persons.md), [Processes](20-entities/09-process-architecture.md), [Cross-Cutting](20-entities/10-cross-cutting.md)
- **Dynamics**: [Temporal Model](30-dynamics/11-temporal-model.md), [Domains & Views](30-dynamics/12-domain-views.md), [Business–Technology Linking](30-dynamics/13-business-technology-linking.md)
- **Extensibility**: [Custom Entities](40-extensibility/14-extensibility.md), [Schema Evolution](40-extensibility/15-schema-evolution.md)
- **Example**: [Walkthrough](50-walkthrough/16-example-walkthrough.md)
- **Integrations**: [ITSM](60-integrations/17-itsm-integration.md), [PPM](60-integrations/18-ppm-integration.md), [Agile Scaling](60-integrations/19-agile-scaling.md), [GRC / GDPR / ISMS](60-integrations/20-grc-gdpr-isms-integration.md)
- **Platform**: [API Architecture](70-platform/21-api-architecture.md), [Analyzability](70-platform/22-analyzability.md)
- **Backlog**: [Open Issues](90-backlog/23-open-issues.md), [Next Steps](90-backlog/24-next-steps.md)

## Structure

```text
concept/
├── README.md                          ← this file (introduction, guiding principles)
├── INDEX.md                           ← complete chapter overview
│
├── 00-overview/                       ← Positioning and meta-metamodel
├── 10-foundations/                    ← Framework relationship, continuum, normative layer
├── 20-entities/                       ← Core entities and business modeling
├── 30-dynamics/                       ← Time, views, cross-layer
├── 40-extensibility/                  ← Extensibility and schema evolution
├── 50-walkthrough/                    ← Concrete example
├── 60-integrations/                   ← ITSM, PPM, agile, GRC/GDPR/ISMS
├── 70-platform/                       ← API and query architecture
└── 90-backlog/                        ← Open questions and next steps
```

## Definition of Done – Requirements Phase

As of 2026-06-28: Requirements phase completed.

- [x] Vision formulated (`business-analysis/vision.md`)
- [x] At least 3 stakeholder profiles with concerns (8 present)
- [x] At least 10 use cases, all prioritized by MoSCoW (16 UCs: 11 must, 5 should)
- [x] At least 5 NFRs with measurable target values (8 present: REQ-008, REQ-071–075, REQ-082–083)
- [x] Group A ADRs (ADR-001 to ADR-005) decided (all `accepted`)
- [x] Walking skeleton UC identified (UC-06 catalog; 31 SP; prerequisites UC-01, UC-02, UC-04; entities via batch API US-141)
- [x] Traceability matrix maintained (`requirements/traceability.md`; 106 REQs / 106 USs / 16 UCs)
- [x] Trace check without warnings (all 16 UCs covered with REQs and USs)

**Next phase**: Walking skeleton → details in `concept/90-backlog/24-next-steps.md`

---

## Working Method

This document is the **basis for discussion** for the metamodel. Changes are made via PR with an ADR reference. Every substantial change should:

1. Address specific open questions from [23-open-issues.md](90-backlog/23-open-issues.md) or
2. Document a new open item if ambiguity arises

The chapter structure is deliberately organized by topic, not by framework structure – this allows parallel work on independent topics and makes reviews more focused.
