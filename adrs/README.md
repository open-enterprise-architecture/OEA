# Architectural Decision Records (ADRs)

Documented architecture decisions with context, options, choice, and consequences.

## Convention

- File name: `ADR-NNN-shortname.md` (3-digit numbering)
- Template: `adr.template.md`
- Status workflow: `proposed → accepted | rejected → superseded`
- An ADR is never deleted, but at most marked as `superseded` and replaced by a new one

## First Pending ADRs (Group A – Pre-ADRs)

These decisions should be clarified before or in parallel with requirements engineering, because they shape the possible solution space:

| ID | Title | Status | Priority |
|---|---|---|---|
| ADR-001 | URN Schema and Stability Guarantees | **accepted** | high |
| ADR-002 | Enterprise Continuum: One Repo or Two? | **accepted** | high |
| ADR-003 | Product vs. Project: Coexistence or Separation? | **accepted** | high |
| ADR-004 | Reification Details (Max Depth, UI Representation) | **accepted** | medium |
| ADR-005 | Application-vs-Technology Classification Principle | **accepted** | medium |

## Further ADR Candidates

See [§23 Open Issues](../concept/90-backlog/23-open-issues.md) – currently 47 candidates.

## Slash Command

In `.opencode/commands/`:

- `/new-adr` – creates a new ADR with the next free number

## Overview of All ADRs

<!-- After creating the first ADRs, enter them here or generate via script -->

| ID | Title | Status | Date | Supersedes |
|---|---|---|---|---|
| [ADR-001](./ADR-001-urn-schema.md) | Internal integer ID + external URN `urn:oea:{instance}:{id}` | accepted | 2026-06-26 | – |
| [ADR-002](./ADR-002-continuum-repository.md) | One repository + import mechanism (scope: built-in/imported/organization) | accepted | 2026-06-26 | – |
| [ADR-003](./ADR-003-product-vs-project.md) | work-initiative as built-in base; product/project as metamodel subtypes | accepted | 2026-06-26 | – |
| [ADR-004](./ADR-004-reification-details.md) | Max depth 1 (v1.0); all connections have integer IDs; 3-dot-circle UI | accepted | 2026-06-26 | – |
| [ADR-005](./ADR-005-app-vs-tech-default.md) | No enforced default; two optional starter packages during bootstrapping | accepted | 2026-06-26 | – |
| [ADR-006](./ADR-006-auth-stack-choice.md) | Auth stack choice (Entra ID + Authentik mandatory, local auth optional) | accepted | 2026-06-24 | – |
| [ADR-007](./ADR-007-canvas-library.md) | Canvas library: Vue Flow (`@vue-flow/core`) — updated from React Flow following ADR-011 | accepted | 2026-06-26 | – |
| [ADR-008](./ADR-008-gui-architecture-dual-track.md) | GUI architecture: client app + web portal (dual-track delivery) | accepted | 2026-06-26 | – |
| [ADR-009](./ADR-009-client-app-framework.md) | Client app framework: Electron (vs. Tauri v2) | accepted | 2026-06-26 | – |
| [ADR-010](./ADR-010-n-connection-data-lineage.md) | Modeling DataFlow↔DataObject: n-connection vs. property string | accepted | 2026-06-26 | – |
| [ADR-011](./ADR-011-frontend-framework.md) | Frontend framework: Vue 3 + TypeScript (Composition API) | accepted | 2026-06-26 | – |
| [ADR-012](./ADR-012-backend-stack.md) | Backend stack: Java 21 + Spring Boot 3 + Hibernate; Maven | accepted | 2026-06-27 | – |
| [ADR-013](./ADR-013-api-style.md) | API style: REST + OpenAPI 3.x (spec-first, contract `api/openapi.yaml`) | accepted | 2026-06-26 | – |
| [ADR-014](./ADR-014-frontend-component-library.md) | Frontend component library: PrimeVue 4 + TipTap 2.x | accepted | 2026-06-26 | – |
| [ADR-015](./ADR-015-db-migration.md) | DB migration: Flyway (Spring-native, all 5 target DBs) | accepted | 2026-06-26 | – |
| [ADR-016](./ADR-016-persistence-strategy.md) | Persistence: PostgreSQL 15 + JSONB; versioning via entity_versions; no AGE in v1.0 | accepted | 2026-06-27 | – |
| [ADR-017](./ADR-017-architecture-layer-strategy.md) | Architecture Layer Strategy – Fully Open in v1.0 | accepted | 2026-06-27 | – |
| [ADR-018](./ADR-018-business-rule-engine.md) | Business Rule Engine – CEL with GUI Abstraction Layer | accepted | 2026-06-27 | – |
| [ADR-019](./ADR-019-soft-delete-strategy.md) | Soft-Delete Strategy for Entities and Connections | accepted | 2026-06-28 | – |
| [ADR-020](./ADR-020-explorer-navigation-model.md) | Explorer Browser Navigation Model in the Native Client | proposed | 2026-06-28 | – |
| [ADR-021](./ADR-021-implicit-parent-child-connection.md) | Implicit Parent-Child Connection on Component Nesting | proposed | 2026-06-28 | – |
| [ADR-022](./ADR-022-structured-property-model.md) | Structured Property Model with Category, Temporal Mapping, and Delta Versioning | accepted | 2026-06-29 | – |
| [ADR-023](./ADR-023-multi-db-strategy.md) | Multi-DB Strategy — Database Abstraction via JPA/Hibernate | superseded by ADR-030 | 2026-06-29 | – |
| [ADR-024](./ADR-024-audit-separation.md) | Audit Data Storage — Separate Schema, Configurable as External Database | accepted | 2026-06-29 | – |
| [ADR-025](./ADR-025-i18n-strategy.md) | I18N Strategy — Two-Layer with ETag Caching and SSE Invalidation | accepted | 2026-06-29 | – |
| [ADR-026](./ADR-026-external-system-integration.md) | External System Integration — Write Access via API (optional) | accepted | 2026-06-29 | – |
| [ADR-027](./ADR-027-project-setup-monorepo.md) | Project Setup — Mono-Repo, Maven Structure, Package Naming, Docker Compose | accepted | 2026-06-29 | – |
| [ADR-028](./ADR-028-backend-layered-architecture.md) | Backend Layered Architecture — API / Application / Core | accepted | 2026-06-29 | – |
| [ADR-029](./ADR-029-cucumber-test-framework.md) | Test Framework for BDD System Tests — Cucumber JVM | accepted | 2026-06-30 | – |
| [ADR-030](./ADR-030-postgresql-only.md) | PostgreSQL as the Only Supported Database | accepted | 2026-10-13 | ADR-023 |
| [ADR-031](./ADR-031-ui-prototyping-storybook.md) | UI Prototyping with Clickable Vue Prototypes in Storybook | accepted | 2026-10-13 | – |
| [ADR-032](./ADR-032-authorization-model.md) | Authorization model: RBAC with a fixed permission catalog and configurable roles | accepted | 2026-10-13 | – (partially: ADR-006, ADR-008, ADR-026) |
| [ADR-033](./ADR-033-audit-trail-hardening.md) | Audit Trail Hardening – Owner/Runtime Role Separation, Hash Chain in All Editions, Retention Floor and Pseudonymous Actors | accepted | 2026-10-13 | – (partially ADR-024, ADR-015, ADR-026) |
| [ADR-034](./ADR-034-secured-raw-analytics.md) | Secured Raw SQL Analytics on a Masked Reporting Schema; Cypher Deferred | accepted | 2026-10-13 | – (partially ADR-013) |
| [ADR-035](./ADR-035-electron-security-baseline.md) | Electron Security Baseline – Hardened Runtime, Local Data Protection and Release Integrity | accepted | 2026-10-13 | – (partially ADR-009) |
| [ADR-036](./ADR-036-dev-test-identity-provider.md) | Development and Test Identity Provider Without Authentication Stub | accepted | 2026-10-13 | – (partially ADR-027) |
| [ADR-037](./ADR-037-database-roles-provisioning.md) | Database Roles and Provisioning | accepted | 2026-10-13 | – (partially ADR-033, ADR-034) |
| [ADR-038](./ADR-038-spring-boot-4.md) | Spring Boot 4.1 Instead of Spring Boot 3 | accepted | 2026-10-14 | – (partially ADR-012) |
| [ADR-039](./ADR-039-java-25.md) | Java 25 LTS Instead of Java 21 | accepted | 2026-10-15 | – (partially ADR-012, ADR-038) |
| [ADR-040](./ADR-040-ui-component-library-after-primevue-license-change.md) | UI Component Library After the PrimeVue License Change | accepted | 2026-10-15 | – (partially ADR-014, ADR-031) |
| [ADR-041](./ADR-041-root-aggregator-pom.md) | Root Aggregator POM for the Dependency Graph | accepted | 2026-10-15 | – (partially ADR-027) |
| [ADR-042](./ADR-042-setup-phase-without-second-factor.md) | Setup Phase – System Admin Login Without Second Factor Until Other Identities Get Access | accepted | 2026-10-15 | – (partially ADR-032 SoD-8) |
| [ADR-043](./ADR-043-third-party-license-policy.md) | License Policy for Third-Party Components | accepted | 2026-10-15 | – |
| [ADR-044](./ADR-044-view-state-in-url.md) | View State of List and Table Views in the URL | accepted | 2026-10-22 | – |
| [ADR-045](./ADR-045-user-manual.md) | Multilingual User Manual with VitePress on GitHub Pages | accepted | 2026-10-23 | – (partially ADR-031 Pages) |
| [ADR-046](./ADR-046-manual-coverage-story-statuses.md) | User Story Statuses Covered by the User Manual Check | accepted | 2026-10-26 | – |
| [ADR-047](./ADR-047-real-time-collaboration.md) | Architecture of Real-Time Collaboration | proposed | 2026-10-28 | – (revises concept issue #26) |
