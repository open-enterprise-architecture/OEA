# OEA

**Open Enterprise Architecture** – Open-Source-Werkzeug für versionierte EA-Disziplin

[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Enterprise License](https://img.shields.io/badge/enterprise-license%20available-green.svg)](LICENSES/LICENSE-ENTERPRISE.md)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange.svg)](#status)
[![Conventional Commits](https://img.shields.io/badge/conventional%20commits-1.0.0-yellow.svg)](https://www.conventionalcommits.org/)

---

OEA macht die Architektur einer Organisation zur gemeinsamen, versionierten Wahrheit – für Architekten als Pflegewerkzeug, für Fachbereiche als Inventar, für Compliance als Asset-Quelle, für automatisierte Pipelines als prüfbares Artefakt.

## Vision

> Enterprise Architecture als versionierte, modulare und werkzeugoffene Open-Source-Disziplin – konzeptionell so rigoros wie nötig, pragmatisch so niederschwellig wie möglich.

Drei bewusste Designprinzipien prägen OEA:

- **Offene, textbasierte Repräsentation statt proprietärer Formate**, weil Architektur-Wissen den Hersteller überleben muss
- **Progressive Disclosure statt erzwungener Modellierungs-Tiefe**, weil unterschiedliche Rollen unterschiedliche Sichten brauchen
- **Modulare, schnittstellen-orientierte Architektur statt Monolith**, weil das Tool nicht alles selbst können muss

Mehr Details: [business-analysis/vision.md](business-analysis/vision.md)

## Status

**Pre-Alpha** – Requirements Engineering abgeschlossen, Walking Skeleton in Vorbereitung.

<!-- AUTO-GENERATED: stats -->
| Artefakt | Anzahl |
|---|---|
| Stakeholder-Profile | 9 |
| Use Cases | 21 |
| Requirements | 155 |
| User Stories | 142 |
| ADRs | 29 |
| Business Objects | 22 |

_Konzept: v0.17 · Letzter Update: 2026-10-12_
<!-- /AUTO-GENERATED: stats -->

**Nächster Schritt**: Walking Skeleton auf Basis von UC-06 (Katalog-Browser).

- Konzept: vollständig (24 Kapitel + Changelog, v0.17.1), siehe [`concept/`](concept/)
- Requirements: abgeschlossen – alle 21 UCs, 142 REQs, 136 USs, 21 ADRs definiert
- Tech-Stack: entschieden (Java 21 / Vue 3 / PostgreSQL 15 / Tauri)
- Code: noch nicht begonnen
- v1.0-Release: TBD

OEA ist **noch nicht produktiv einsetzbar**. Wenn du mitwirken willst: [CONTRIBUTING.md](CONTRIBUTING.md).

## Schnellüberblick

| Was | Wo |
|---|---|
| Konzeptpapier | [`concept/README.md`](concept/README.md) |
| Vision und Stakeholder | [`business-analysis/`](business-analysis/) |
| Business Objects / Datenmodell | [`business-objects/`](business-objects/) · [`docs/data-model.puml`](docs/data-model.puml) |
| Anforderungen | [`requirements/`](requirements/) |
| Architekturentscheidungen | [`adrs/`](adrs/) |
| Wireframes / Screens | [`docs/screens/`](docs/screens/) |
| Workflow-Beispiel | [`docs/workflow-example.md`](docs/workflow-example.md) |
| Quick Reference | [`docs/quick-reference.md`](docs/quick-reference.md) |
| Anti-Patterns | [`docs/anti-patterns.md`](docs/anti-patterns.md) |
| Templates | [`templates/`](templates/) |
| Beitragen | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Sicherheit | [SECURITY.md](SECURITY.md) |

## Wer profitiert von OEA

<!-- AUTO-GENERATED: stakeholders -->
9 Persona-Profile sind ausgearbeitet (siehe [`business-analysis/stakeholders/`](business-analysis/stakeholders/)):

- **SH-01** Franz – Junior Domain Architekt im Konzern
- **SH-02** Lukas – Senior Data Architekt im Mittelstand
- **SH-03** Kurt – Lead Enterprise Architekt im KMU
- **SH-04** Michael – Solution Architekt im Mittelstand
- **SH-05** CIO – Konzern mit gemischtem OEA-Stack
- **SH-06** Max – Operator im regulierten KMU
- **SH-07** Sabine – Senior Business Engineer im Globalkonzern
- **SH-08** Anna – Business Analyst im Mittelstand
- **SH-09** Rigobert – Produkt Owner und Repository-Inhaber
<!-- /AUTO-GENERATED: stakeholders -->

## Technologie-Entscheidungen

<!-- AUTO-GENERATED: adrs -->
| ADR | Entscheidung | Status |
|---|---|---|
| [ADR-001](adrs/ADR-001-urn-schema.md) | URN-Schema und Stabilitäts-Garantien | accepted |
| [ADR-002](adrs/ADR-002-continuum-repository.md) | Enterprise Continuum – Ein Repository oder zwei? | accepted |
| [ADR-003](adrs/ADR-003-product-vs-project.md) | Product vs. Project – Koexistenz oder Trennung? | accepted |
| [ADR-004](adrs/ADR-004-reification-details.md) | Reifikations-Details (Max-Tiefe, Adressierung, UI) | accepted |
| [ADR-005](adrs/ADR-005-app-vs-tech-default.md) | Application-vs-Technology-Klassifikations-Prinzip (Default) | accepted |
| [ADR-006](adrs/ADR-006-auth-stack-choice.md) | Auth-Stack-Wahl (Identity-Provider-Integrationen) | accepted |
| [ADR-007](adrs/ADR-007-canvas-library.md) | Canvas-Bibliothek für interaktive Diagramm-Editierung | accepted |
| [ADR-008](adrs/ADR-008-gui-architecture-dual-track.md) | GUI-Architektur – Client App + Web Portal (Dual-Track-Delivery) | accepted |
| [ADR-009](adrs/ADR-009-client-app-framework.md) | Client-App-Framework – Electron vs. Tauri | accepted |
| [ADR-010](adrs/ADR-010-n-connection-data-lineage.md) | Modellierung der DataFlow↔DataObject-Beziehung (n-Connection vs. Property-String) | accepted |
| [ADR-011](adrs/ADR-011-frontend-framework.md) | Frontend-Framework – Vue 3 + TypeScript | accepted |
| [ADR-012](adrs/ADR-012-backend-stack.md) | Backend-Stack – Java 21 + Spring Boot 3 + Hibernate | accepted |
| [ADR-013](adrs/ADR-013-api-style.md) | API-Stil – REST + OpenAPI 3.x | accepted |
| [ADR-014](adrs/ADR-014-frontend-component-library.md) | Frontend-Komponentenbibliothek und WYSIWYG-Editor | accepted |
| [ADR-015](adrs/ADR-015-db-migration.md) | Datenbank-Migrations-Tool – Flyway | accepted |
| [ADR-016](adrs/ADR-016-persistence-strategy.md) | Persistenz-Strategie – PostgreSQL 15 + JSONB | accepted |
| [ADR-017](adrs/ADR-017-architecture-layer-strategy.md) | Architektur-Layer-Strategie – Fully Open in v1.0 | accepted |
| [ADR-018](adrs/ADR-018-business-rule-engine.md) | Business-Rule-Engine – CEL mit GUI-Abstraktionsschicht | accepted |
| [ADR-019](adrs/ADR-019-soft-delete-strategy.md) | Soft-Delete-Strategie für Entities und Connections | accepted |
| [ADR-020](adrs/ADR-020-explorer-navigation-model.md) | Explorer-Browser-Navigationsmodell im Native Client | proposed |
| [ADR-021](adrs/ADR-021-implicit-parent-child-connection.md) | Implizite Parent-Child-Verbindung bei Component-Verschachtelung | proposed |
| [ADR-022](adrs/ADR-022-structured-property-model.md) | Strukturiertes Property-Modell mit Kategorie, temporalem Mapping und Delta-Versionierung | accepted |
| [ADR-023](adrs/ADR-023-multi-db-strategy.md) | Multi-DB-Strategie — Datenbankabstraktion via JPA/Hibernate | accepted |
| [ADR-024](adrs/ADR-024-audit-separation.md) | Audit-Datenhaltung — separates Schema, konfigurierbar als externe Datenbank | accepted |
| [ADR-025](adrs/ADR-025-i18n-strategy.md) | I18N-Strategie — Zweischichtig mit ETag-Caching und SSE-Invalidierung | accepted |
| [ADR-026](adrs/ADR-026-external-system-integration.md) | Externe System-Integration — Schreibzugriff via API (optional) | accepted |
| [ADR-027](adrs/ADR-027-project-setup-monorepo.md) | Projekt-Setup — Mono-Repo, Maven-Struktur, Package-Naming, Docker Compose | accepted |
| [ADR-028](adrs/ADR-028-backend-layered-architecture.md) | Backend-Schichtenarchitektur — API / Application / Core | accepted |
| [ADR-029](adrs/ADR-029-cucumber-test-framework.md) | Testframework für BDD-Systemtests — Cucumber JVM | accepted |
<!-- /AUTO-GENERATED: adrs -->

## Mitwirken

Wir freuen uns über Beiträge jeder Größe:

- **Bug Reports**: Issue mit Template [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md)
- **Feature Requests**: Issue mit Template [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md)
- **Code, Doku, Konzept**: Pull Request, siehe [CONTRIBUTING.md](CONTRIBUTING.md)
- **Sicherheitslücken**: Bitte **NICHT öffentlich**, siehe [SECURITY.md](SECURITY.md)

Erste Schritte: Suche nach Issues mit Label `good first issue`.

## Wenn du OEA evaluierst

Drei realistische Fragen vor einem Test:

1. **Bist du in einer regulierten Branche?** Dann lies [SECURITY.md](SECURITY.md) und die [GRC/DSGVO/ISMS-Integration](concept/60-integrations/20-grc-gdpr-isms-integration.md). Audit-Trail und Compliance sind Pflicht-Features, aber v1.0 wird sie erstmalig vollständig liefern.
2. **Migrierst du aus Sparx, Avolution, LeanIX?** Migration-Adapter sind in Planung, aber pre-v1.0 noch nicht produktiv. Lies die [Persona-Profile](business-analysis/stakeholders/) für realistische Erwartungen.
3. **Brauchst du Vendor-Support?** OEA ist OSS ohne kommerzielle Anbietersicherung. Community-Support ist da, SLAs nicht.

## License

OEA is dual-licensed:

| Edition | License | Use |
|---|---|---|
| Community | [AGPL-3.0](LICENSE) | free of charge, source disclosure obligation |
| Enterprise | [proprietary](LICENSES/LICENSE-ENTERPRISE.md) | paid, no disclosure obligation |

Contributions are subject to the [Contributor License Agreement (CLA)](LICENSES/CLA.md).
Copyright 2026 Lukas Mathis – see [NOTICE](NOTICE). The copyright holder and licensor is Lukas Mathis as a private individual; there is currently no organization behind the project.

## Quellenverzeichnis

OEA baut auf etablierten Standards auf:
- TOGAF Content Metamodel (The Open Group)
- ArchiMate (The Open Group)
- BPMN 2.0 (OMG)
- arc42 (Gernot Starke, Peter Hruschka)
- ISO 42010 (Architecture Description)
- ISO 27005 (Risk Management)
- BIAN (für Business Object Modeling)

## Kontakt

- **Konzeptionelle Fragen**: GitHub Discussions
- **Sicherheits-Meldungen**: siehe [SECURITY.md](SECURITY.md)
- **Verhaltenskodex-Verstöße**: `conduct@oea.org` (TBD)
