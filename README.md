# OEA

**Open Enterprise Architecture** – open-source tool for versioned EA discipline

[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Enterprise License](https://img.shields.io/badge/enterprise-license%20available-green.svg)](LICENSES/LICENSE-ENTERPRISE.md)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange.svg)](#status)
[![Conventional Commits](https://img.shields.io/badge/conventional%20commits-1.0.0-yellow.svg)](https://www.conventionalcommits.org/)

---

OEA turns an organization's architecture into a shared, versioned source of truth – a maintenance tool for architects, an inventory for business units, an asset source for compliance, a verifiable artifact for automated pipelines.

## Vision

> Enterprise architecture as a versioned, modular and tool-agnostic open-source discipline – as conceptually rigorous as necessary, as pragmatically low-threshold as possible.

Three deliberate design principles shape OEA:

- **Open, text-based representation instead of proprietary formats**, because architecture knowledge must outlive the vendor
- **Progressive disclosure instead of enforced modeling depth**, because different roles need different views
- **Modular, interface-oriented architecture instead of a monolith**, because the tool does not have to do everything itself

More details: [business-analysis/vision.md](business-analysis/vision.md)

## Status

**Pre-Alpha** – requirements engineering completed, Walking Skeleton in preparation.

<!-- AUTO-GENERATED: stats -->
| Artifact | Count |
|---|---|
| Stakeholder profiles | 8 |
| Use Cases | 22 |
| Requirements | 193 |
| User Stories | 142 |
| ADRs | 31 |
| Business Objects | 22 |

_Concept: v0.18 · Last update: 2026-10-13._
<!-- /AUTO-GENERATED: stats -->

**Next step**: Walking Skeleton based on UC-06 (Catalog Browser).

- Concept: complete (24 chapters + changelog, v0.18.1), see [`concept/`](concept/)
- Requirements: completed – all 21 UCs, 155 REQs, 142 USs, 29 ADRs defined
- Tech stack: decided (Java 21 / Vue 3 / PostgreSQL 15 / Electron)
- Code: not started yet
- v1.0-Release: TBD

OEA is **not yet ready for production use**. If you want to contribute: [CONTRIBUTING.md](CONTRIBUTING.md).

## Quick Overview

| What | Where |
|---|---|
| Concept paper | [`concept/README.md`](concept/README.md) |
| Vision and stakeholders | [`business-analysis/`](business-analysis/) |
| Business objects / data model | [`business-objects/`](business-objects/) · [`docs/data-model.puml`](docs/data-model.puml) |
| Requirements | [`requirements/`](requirements/) |
| Architecture decisions | [`adrs/`](adrs/) |
| Wireframes / screens | [`docs/screens/`](docs/screens/) |
| Workflow example | [`docs/workflow-example.md`](docs/workflow-example.md) |
| Quick Reference | [`docs/quick-reference.md`](docs/quick-reference.md) |
| Anti-Patterns | [`docs/anti-patterns.md`](docs/anti-patterns.md) |
| Templates | [`templates/`](templates/) |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Security | [SECURITY.md](SECURITY.md) |

## Who Benefits from OEA

<!-- AUTO-GENERATED: stakeholders -->
8 persona profiles have been worked out (see [`business-analysis/stakeholders/`](business-analysis/stakeholders/)):

- **SH-01** Franz – Junior Domain Architect in a Corporate Group
- **SH-02** Lukas – Senior Data Architect in a Mid-Sized Company
- **SH-03** Kurt – Lead Enterprise Architect in an SME
- **SH-04** Michael – Solution Architect in a Mid-Sized Company
- **SH-05** CIO – Corporate Group with a Mixed OEA Stack
- **SH-06** Max – Operator in a Regulated SME
- **SH-07** Sabine – Senior Business Engineer in a Global Corporate Group
- **SH-08** Anna – Business Analyst in a Mid-Sized Company
<!-- /AUTO-GENERATED: stakeholders -->

## Technology Decisions

<!-- AUTO-GENERATED: adrs -->
| ADR | Decision | Status |
|---|---|---|
| [ADR-001](adrs/ADR-001-urn-schema.md) | URN Schema and Stability Guarantees | accepted |
| [ADR-002](adrs/ADR-002-continuum-repository.md) | Enterprise Continuum – One Repository or Two? | accepted |
| [ADR-003](adrs/ADR-003-product-vs-project.md) | Product vs. Project – Coexistence or Separation? | accepted |
| [ADR-004](adrs/ADR-004-reification-details.md) | Reification Details (Max Depth, Addressing, UI) | accepted |
| [ADR-005](adrs/ADR-005-app-vs-tech-default.md) | Application-vs-Technology Classification Principle (Default) | accepted |
| [ADR-006](adrs/ADR-006-auth-stack-choice.md) | Auth Stack Choice (Identity Provider Integrations) | accepted |
| [ADR-007](adrs/ADR-007-canvas-library.md) | Canvas Library for Interactive Diagram Editing | accepted |
| [ADR-008](adrs/ADR-008-gui-architecture-dual-track.md) | GUI Architecture – Client App + Web Portal (Dual-Track Delivery) | accepted |
| [ADR-009](adrs/ADR-009-client-app-framework.md) | Client App Framework – Electron vs. Tauri | accepted |
| [ADR-010](adrs/ADR-010-n-connection-data-lineage.md) | Modeling the DataFlow↔DataObject Relationship (n-Connection vs. Property String) | accepted |
| [ADR-011](adrs/ADR-011-frontend-framework.md) | Frontend Framework – Vue 3 + TypeScript | accepted |
| [ADR-012](adrs/ADR-012-backend-stack.md) | Backend Stack – Java 21 + Spring Boot 3 + Hibernate | accepted |
| [ADR-013](adrs/ADR-013-api-style.md) | API Style – REST + OpenAPI 3.x | accepted |
| [ADR-014](adrs/ADR-014-frontend-component-library.md) | Frontend Component Library and WYSIWYG Editor | accepted |
| [ADR-015](adrs/ADR-015-db-migration.md) | Database Migration Tool – Flyway | accepted |
| [ADR-016](adrs/ADR-016-persistence-strategy.md) | Persistence Strategy – PostgreSQL 15 + JSONB | accepted |
| [ADR-017](adrs/ADR-017-architecture-layer-strategy.md) | Architecture Layer Strategy – Fully Open in v1.0 | accepted |
| [ADR-018](adrs/ADR-018-business-rule-engine.md) | Business Rule Engine – CEL with GUI Abstraction Layer | accepted |
| [ADR-019](adrs/ADR-019-soft-delete-strategy.md) | Soft-Delete Strategy for Entities and Connections | accepted |
| [ADR-020](adrs/ADR-020-explorer-navigation-model.md) | Explorer Browser Navigation Model in the Native Client | proposed |
| [ADR-021](adrs/ADR-021-implicit-parent-child-connection.md) | Implicit Parent-Child Connection on Component Nesting | proposed |
| [ADR-022](adrs/ADR-022-structured-property-model.md) | Structured Property Model with Category, Temporal Mapping, and Delta Versioning | accepted |
| [ADR-023](adrs/ADR-023-multi-db-strategy.md) | Multi-DB Strategy — Database Abstraction via JPA/Hibernate | superseded |
| [ADR-024](adrs/ADR-024-audit-separation.md) | Audit Data Storage — Separate Schema, Configurable as External Database | accepted |
| [ADR-025](adrs/ADR-025-i18n-strategy.md) | I18N Strategy — Two-Layer with ETag Caching and SSE Invalidation | accepted |
| [ADR-026](adrs/ADR-026-external-system-integration.md) | External System Integration — Write Access via API (optional) | accepted |
| [ADR-027](adrs/ADR-027-project-setup-monorepo.md) | Project Setup — Mono-Repo, Maven Structure, Package Naming, Docker Compose | accepted |
| [ADR-028](adrs/ADR-028-backend-layered-architecture.md) | Backend Layered Architecture — API / Application / Core | accepted |
| [ADR-029](adrs/ADR-029-cucumber-test-framework.md) | Test Framework for BDD System Tests — Cucumber JVM | accepted |
| [ADR-030](adrs/ADR-030-postgresql-only.md) | PostgreSQL as the Only Supported Database | accepted |
| [ADR-031](adrs/ADR-031-ui-prototyping-storybook.md) | UI Prototyping with Clickable Vue Prototypes in Storybook | accepted |
<!-- /AUTO-GENERATED: adrs -->

## Contributing

Contributions of any size are welcome:

- **Bug Reports**: issue with template [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md)
- **Feature Requests**: issue with template [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md)
- **Code, docs, concept**: pull request, see [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security vulnerabilities**: please **do NOT report publicly**, see [SECURITY.md](SECURITY.md)

First steps: look for issues with the label `good first issue`.

## If You Are Evaluating OEA

Three realistic questions before a trial:

1. **Are you in a regulated industry?** Then read [SECURITY.md](SECURITY.md) and the [GRC/GDPR/ISMS integration](concept/60-integrations/20-grc-gdpr-isms-integration.md). Audit trail and compliance are mandatory features, but v1.0 will deliver them completely for the first time.
2. **Are you migrating from Sparx, Avolution, LeanIX?** Migration adapters are planned, but not yet productive before v1.0. Read the [persona profiles](business-analysis/stakeholders/) for realistic expectations.
3. **Do you need vendor support?** OEA is OSS without commercial vendor backing. Community support is available, SLAs are not.

## License

OEA is dual-licensed:

| Edition | License | Use |
|---|---|---|
| Community | [AGPL-3.0](LICENSE) | free of charge, source disclosure obligation |
| Enterprise | [proprietary](LICENSES/LICENSE-ENTERPRISE.md) | paid, no disclosure obligation |

Contributions are subject to the [Contributor License Agreement (CLA)](LICENSES/CLA.md).
Copyright 2026 Lukas Mathis – see [NOTICE](NOTICE). The copyright holder and licensor is Lukas Mathis as a private individual; there is currently no organization behind the project.

## References

OEA builds on established standards:

- TOGAF Content Metamodel (The Open Group)
- ArchiMate (The Open Group)
- BPMN 2.0 (OMG)
- arc42 (Gernot Starke, Peter Hruschka)
- ISO 42010 (Architecture Description)
- ISO 27005 (Risk Management)
- BIAN (for business object modeling)

## Contact

- **Conceptual questions**: GitHub Discussions
- **Security reports**: see [SECURITY.md](SECURITY.md)
- **Code of conduct violations**: `conduct@oea.org` (TBD)
