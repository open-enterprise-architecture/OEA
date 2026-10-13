# Business Objects

Structured business domain model of OEA. One file per business object with standardized frontmatter, attributes, relationships, lifecycle, and business rules.

## Structure

```
business-objects/
├── README.md             ← this document, overview and capability map
├── <object-id>.md        ← one file per business object (english-kebab-case)
├── capabilities/
│   └── <capability>.md   ← business capabilities with relationships
└── rules/
    └── BR-NN-rule.md     ← cross-object business rules
```

## Responsible

The Business Engineer (see `.opencode/agents/business-engineer.md`) models here. Works **before** use-case engineering, following the domain-model-first approach.

## Procedure

1. Clarify with the PO: which business terms are in scope?
2. Create first business object sketches (one file per object) using `templates/business-object.template.md`
3. Clarify relationships between objects
4. Obtain PO review
5. Iterate, then hand over to use-case engineering

## Overview of Modeled Business Objects

### Identity & Access

| ID | Name (DE) | Name (EN) | Status | Version |
|---|---|---|---|---|
| [person](./person.md) | Person | Person | draft | 0.1.0 |
| [role](./role.md) | Rolle | Role | draft | 0.1.0 |
| [system-admin-account](./system-admin-account.md) | System-Admin-Account | System Admin Account | draft | 0.1.0 |
| [local-credential](./local-credential.md) | Lokaler Credential | Local Credential | draft | 0.1.0 |

### Architecture Repository

| ID | Name (DE) | Name (EN) | Status | Version |
|---|---|---|---|---|
| [architecture](./architecture.md) | Architektur-Repository | Architecture | draft | 0.3.0 |
| [entity](./entity.md) | Architektur-Entität | Architecture Entity | draft | 0.1.0 |
| [entity-version](./entity-version.md) | Entitäts-Versionsschnappschuss | Entity Version | draft | 0.1.0 |
| [plateau](./plateau.md) | Plateau | Plateau | draft | 0.1.0 |
| [solution](./solution.md) | Solution / Änderungsinitiative | Solution | draft | 0.1.0 |

### Metamodel & Configuration

| ID | Name (DE) | Name (EN) | Status | Version |
|---|---|---|---|---|
| [metamodel-configuration](./metamodel-configuration.md) | Metamodell-Konfiguration | Metamodel Configuration | draft | 1.0.0 |
| [viewpoint](./viewpoint.md) | Viewpoint / Architektursicht | Viewpoint | draft | 0.2.0 |
| [document-collection](./document-collection.md) | Strukturierte Dokumentation | Document Collection | draft | 0.2.0 |

### Enterprise Continuum (TOGAF)

| ID | Name (DE) | Name (EN) | Status | Version |
|---|---|---|---|---|
| [architecture-building-block](./architecture-building-block.md) | Architektur-Baustein (ABB) | Architecture Building Block | draft | 0.1.0 |
| [solution-building-block](./solution-building-block.md) | Lösungs-Baustein (SBB) | Solution Building Block | draft | 0.1.0 |
| [architecture-pattern](./architecture-pattern.md) | Architektur-Muster | Architecture Pattern | draft | 0.1.0 |
| [reference-architecture](./reference-architecture.md) | Referenzarchitektur | Reference Architecture | draft | 0.1.0 |
| [trm-category](./trm-category.md) | TRM-Kategorie | TRM Category | draft | 0.1.0 |

### Presentation & Navigation

| ID | Name (DE) | Name (EN) | Status | Version |
|---|---|---|---|---|
| [catalog](./catalog.md) | Katalog / Tabellenansicht | Catalog | draft | 0.1.0 |
| [tree-node](./tree-node.md) | Navigationsbaum-Knoten | Tree Node | draft | 0.1.0 |
| [dashboard](./dashboard.md) | Dashboard / C-Level-Auswertung | Dashboard | draft | 0.1.0 |

## Capability Map

<!-- Overview diagram of the business capabilities and their relationships. Initially empty. -->

## Slash Command

`/new-business-object` creates a new business object from the template.
