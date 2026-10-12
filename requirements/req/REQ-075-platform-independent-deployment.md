---
id: REQ-075
title: Plattformunabhängigkeit und Cloud-Betreibbarkeit
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-075: Plattformunabhängigkeit und Cloud-Betreibbarkeit

## Aussage

Das System MUSS als containerisierte Anwendung (Docker) auslieferbar sein, die ohne Anpassung auf Linux, macOS und Windows (Docker Desktop) sowie auf jeder CNCF-zertifizierten Kubernetes-Distribution betrieben werden kann. Das System DARF KEINE harten Abhängigkeiten auf proprietäre Cloud-Dienste eines einzelnen Anbieters haben — alle externen Dienste (Datenbank, Objektspeicher, Identity-Provider) MÜSSEN durch offene Standards oder anbieterübergreifende APIs austauschbar sein.

## Begründung

OEA richtet sich an KMUs (SH-06: self-hosted on-premise oder managed cloud) und Konzerne (SH-05: eigene Cloud-Infrastruktur, oft Multi-Cloud oder regulated cloud). Vendor-Lock-in auf einen Cloud-Anbieter würde grosse Teile der Zielgruppe ausschliessen. Gleichzeitig muss ein Cloud-Betrieb möglich sein — nicht als Pflicht, sondern als Option — damit Betreiber frei wählen können: on-premise, private cloud, public cloud (AWS, Azure, GCP) oder managed SaaS.

Die Container-Basis ist Voraussetzung für Reproduzierbarkeit, einfache Updates und Skalierbarkeit (REQ-074).

## Kontext

OEA besteht aus mehreren Diensten, die alle plattformunabhängig betreibbar sein müssen:

| Dienst | Austauschbare Basis |
|---|---|
| API-Server | Docker-Container; Standard-JVM oder Node-Runtime |
| Datenbank | PostgreSQL (Standard-SQL; keine proprietären Extensions) |
| Identity-Provider | OIDC/OAuth2-kompatibler IdP (Keycloak, Entra ID, Okta, Auth0, …) |
| Objektspeicher (Dateien, Arc42-Content-Blobs) | S3-kompatible API (AWS S3, MinIO, GCS mit S3-Gateway, Azure Blob via Adapter) |
| PlantUML-Server | optional; Docker-Image `plantuml/plantuml-server` |

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: portability

**Messbare Zielwerte**:

| Metrik | Zielwert | Verifikation |
|---|---|---|
| Deployment via Docker Compose | Lauffähig auf Linux, macOS, Windows ohne Codeänderung | manueller Test auf allen drei Plattformen |
| Deployment via Helm Chart | Lauffähig auf mind. 2 verschiedenen K8s-Distributionen (z.B. vanilla K8s + K3s) | CI-Test in Kind-Cluster |
| Vendor-Lock-in-Freiheit: Datenbank | Betrieb mit vanilla PostgreSQL ≥ 15 ohne proprietäre Extensions | Integrationstest gegen Standard-Postgres |
| Vendor-Lock-in-Freiheit: Objektspeicher | Betrieb mit MinIO (S3-kompatibel) als Ersatz für AWS S3 | Integrationstest gegen MinIO |
| Vendor-Lock-in-Freiheit: Auth | Betrieb mit Keycloak als Ersatz für Entra ID oder Okta | Integrationstest gegen Keycloak |
| Cloud-Deployment: AWS | Dokumentiertes Referenz-Deployment (EKS + RDS Postgres + S3) | Deployment-Guide, kein automatisierter Test |
| Cloud-Deployment: Azure | Dokumentiertes Referenz-Deployment (AKS + Azure Database for PostgreSQL + Blob Storage S3-Gateway) | Deployment-Guide |
| Image-Grösse API-Server | ≤ 500 MB (komprimiert) | CI-Build |

## Akzeptanzkriterien

**AC1** (Docker Compose auf Linux):
- Wenn: `docker compose up` auf einem Linux-System (Ubuntu 22.04 LTS) ohne vorherige Konfiguration ausgeführt wird
- Dann: OEA-API, Datenbank und Auth-Service starten; `GET /health` antwortet HTTP 200 innerhalb von 60 Sekunden

**AC2** (Docker Compose auf macOS und Windows):
- Wie AC1, auf macOS 14+ (Docker Desktop) und Windows 11 (Docker Desktop WSL2)
- Dann: identisches Ergebnis wie AC1; keine plattformspezifischen Anpassungen nötig

**AC3** (Kubernetes / Kind-Cluster):
- Gegeben: Helm Chart für OEA; lokaler Kind-Cluster
- Wenn: `helm install oea ./chart` ausgeführt
- Dann: alle Pods `Running` innerhalb von 120 Sekunden; `GET /health` HTTP 200

**AC4** (PostgreSQL ohne proprietäre Extensions):
- Gegeben: vanilla PostgreSQL 15 (kein AWS Aurora, kein Azure Flexible Server, kein Cloud SQL)
- Wenn: OEA mit dieser Datenbank verbunden
- Dann: alle Funktionen arbeiten korrekt; keine Extension ausser `uuid-ossp` (falls benötigt) oder keine

**AC5** (MinIO statt AWS S3):
- Gegeben: MinIO-Instanz als Objektspeicher konfiguriert (S3-kompatible API)
- Wenn: OEA Dateien/Blobs schreibt und liest
- Dann: korrekte Funktion; kein AWS-SDK-spezifischer Code-Pfad

**AC6** (Keycloak statt kommerziellem IdP):
- Gegeben: Keycloak als OIDC-Provider konfiguriert
- Wenn: Nutzer Login via OIDC
- Dann: Login erfolgreich; Rollen korrekt übertragen; kein Entra-ID-spezifisches API verwendet

**AC7** (Keine proprietären Umgebungsvariablen als Pflicht):
- Wenn: OEA ohne AWS_ACCESS_KEY, AZURE_CLIENT_ID o.ä. Cloud-spezifische Env-Variablen gestartet
- Dann: System startet (mit S3-kompatibler Alternative konfiguriert); keine Exception

## Verifikationsmethode

- [x] Methode: test (automatisiert für AC1, AC3–AC6) + manuell (AC2, AC7)
- [x] Test-Setup: CI-Pipeline mit Docker-Build + Kind-Cluster + MinIO + Keycloak + Postgres 15
- [x] Bestanden-Kriterium: alle ACs grün; kein proprietäres SDK als transitive Pflicht-Abhängigkeit
- [ ] In CI integriert: AC1, AC3–AC6 als CI-Job; AC2 manuell bei Release

## Abhängigkeiten

- **Voraussetzungen**: Deployment-ADR (Gruppe-A-ADR noch offen; dieses REQ ist Input für den ADR)
- **Folgewirkungen**: REQ-073 (Verfügbarkeit) gilt für alle Deployment-Varianten; REQ-074 (Skalierbarkeit) setzt zustandslose API voraus — dies ist hier als Designvorgabe verankert
- **Beeinflusst**: ADR-006 (Auth-Stack) — OIDC-Wahl muss mit Keycloak, Entra ID und Okta kompatibel sein

## Risiken bei Nichterfüllung

- KMU-Operator (SH-06) kann OEA nicht on-premise betreiben
- Konzern (SH-05) mit Azure-only-Policy kann nicht wechseln wenn AWS-spezifisches SDK verwendet wird
- Open-Source-Glaubwürdigkeit leidet wenn Betrieb nur mit einem Cloud-Anbieter funktioniert

## Trade-offs

- S3-kompatible API als Standard bedeutet: kein direkter Einsatz von AWS S3 Transfer Acceleration oder Azure CDN-Funktionen; akzeptierter Trade-off für Portabilität
- OIDC als gemeinsamer Nenner bedeutet: kein direkter Einsatz von Entra-ID-spezifischen Gruppen-APIs; Rollen-Mapping muss über Standard-Claims erfolgen (REQ-001/003)
- Keine proprietäre DB-Extension: z.B. kein `pg_vector` für Embedding-Features (könnte für künftige KI-Features relevant werden — dann separater ADR nötig)

## Realisierungs-Hinweise

- Docker-Images: Multi-Stage-Build; kein Root-User; distroless oder alpine base
- Helm Chart: alle externen Dienste als `externalSecret` oder Env-Variable konfigurierbar; keine hardcodierten Endpunkte
- S3-SDK: AWS SDK v3 mit custom Endpoint-URL konfigurierbar (kompatibel mit MinIO und GCS)
- PostgreSQL: Flyway oder Liquibase für DB-Migration; keine DB-spezifischen Stored Procedures

## Realisierung

- ADR(s): Deployment-ADR (ausstehend, Gruppe A)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
