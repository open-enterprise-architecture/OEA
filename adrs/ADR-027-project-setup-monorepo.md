# ADR-027: Projekt-Setup — Mono-Repo, Maven-Struktur, Package-Naming, Docker Compose

**Status**: accepted
**Datum**: 2026-06-29
**Entscheider**: [Lukas – Repository-Inhaber](../business-analysis/stakeholders/SH-01-lukas-enterprise-architect.md)

---

## Kontext

Vor dem ersten Code-Commit müssen vier strukturelle Fragen entschieden werden, die
nachträglich nur mit hohem Aufwand zu ändern sind:

1. **Repository-Strategie**: Ein Repo für alles oder separate Repos pro Schicht?
2. **Backend-Modul-Struktur**: Ein Maven-Modul oder mehrere?
3. **Java-Package-Naming**: Welches Basis-Package für alle Java-Klassen?
4. **Lokale Entwicklungsumgebung**: Wie startet man OEA auf einem Entwickler-Laptop?

---

## Entscheidungen

### 1. Mono-Repo

Alle Schichten — Backend, Frontend (Client-App + Web Portal), API-Spec,
Dokumentation und Requirements — leben in einem einzigen Git-Repository.

```
oea/
├── backend/              ← Java 21 / Spring Boot (ADR-012)
│   ├── src/
│   └── pom.xml
├── frontend/             ← Vue 3 + TypeScript (ADR-011)
│   ├── src/              ← Shared Code: Komponenten, Stores, Router
│   ├── electron/         ← Electron Main Process + Preload (ADR-008)
│   └── portal/           ← Web-Portal Entry Point (kein Electron)
├── api/                  ← openapi.yaml (Vertrag zwischen Backend + Frontend)
├── adrs/                 ← Architectural Decision Records
├── requirements/         ← Use Cases, REQs, User Stories
├── business-analysis/    ← Stakeholder, Vision
├── business-objects/     ← Domain Model
├── docs/                 ← Datenmodell, Walking Skeleton, Screens
├── scripts/              ← Hilfsskripte
├── docker-compose.yml    ← Lokale Entwicklungsumgebung
└── AGENTS.md
```

**Begründung**: Backend-API und Frontend sind tight-coupled (OpenAPI-Vertrag).
Eine Breaking-Change in der API erfordert simultane Anpassung im Frontend — das
gehört in einen einzigen PR. Contributors klonen ein Repo. Der Walking Skeleton
läuft als ein `docker compose up`.

### 2. Backend: Einzelnes Maven-Modul für v1

Das Backend ist ein **einziges Maven-Modul** (`backend/pom.xml`). Interne
Paketstruktur ersetzt Modul-Grenzen:

```
backend/src/main/java/io/oea/
├── api/          ← REST-Controller, Request/Response DTOs, OpenAPI (ADR-028)
├── app/          ← Application Services: stabile Interfaces + Commands/Queries (ADR-028)
├── core/         ← Domain Model, Repositories, Domain-Services
├── audit/        ← Audit-Schema-Handling (ADR-024)
├── integration/  ← Externer Schreibzugriff / Service-Accounts (ADR-026)
└── config/       ← Spring-Konfiguration, Security, Flyway
```

Die Kommunikationsregeln zwischen den drei Schichten (`api → app → core`)
sind in ADR-028 beschrieben.

**Warum nicht Multi-Modul?** Maven-Multi-Module-Projekte (mehrere `pom.xml`-Dateien
in einer Eltern-Kind-Hierarchie) bringen erst ab mehreren Teams oder wiederverwendbaren
Libraries echten Nutzen. Für v1 mit einem Entwickler bedeutet es nur Overhead:
separate Build-Schritte, Inter-Modul-Abhängigkeiten in XML pflegen, längere
Konfigurationszeit.

**Nachrüstbar**: Wenn OEA wächst und z.B. ein `oea-client-lib` für externe
API-Clients sinnvoll wird, kann das Modul ohne Breaking Changes herausgelöst werden.

### 3. Java-Package-Naming: `io.oea`

Alle Java-Klassen verwenden `io.oea` als Basis-Package:

```java
io.oea.api.v1.controller.EntityController
io.oea.app.entity.EntityAppService
io.oea.app.entity.command.UpsertEntityCommand
io.oea.core.domain.entity.Entity
io.oea.core.repository.EntityRepository
io.oea.audit.service.AuditEventWriter
io.oea.integration.service.BatchWriteService
```

**Begründung**: `io.oea` ist kurz, technisch korrekt (Reverse-Domain-Konvention)
und eindeutig. Alternativen wie `com.openea` oder `org.openea` würden eine
registrierte Domain voraussetzen, die zum Projektzeitpunkt nicht existiert.

### 4. Lokale Entwicklung: Docker Compose

```
docker compose up
```

startet alle Dienste, die für den Walking Skeleton nötig sind:

```yaml
services:
  db:        # PostgreSQL 15 (primäre Entwicklungs-DB)
  api:       # Spring Boot Backend (Profil: dev)
  frontend:  # Vite Dev-Server mit HMR
```

Zusätzliche Compose-Dateien für andere DBs (REQ-150):
- `docker-compose.mysql.yml`    — MySQL 8
- `docker-compose.sqlserver.yml` — SQL Server 2019

**Kein Keycloak im Compose für den Walking Skeleton**: Auth via OIDC (ADR-006)
wird im Dev-Modus mit einem vereinfachten JWT-Stub überbrückt, der lokal keinen
externen OIDC-Provider erfordert. Für Integrationstests: Keycloak als optionaler
Compose-Service (`--profile keycloak`).

### 5. Frontend-Package-Manager: npm (kein Yarn, kein pnpm)

Vite und Vue 3 unterstützen alle drei. npm wird gewählt weil:
- Kein zusätzliches Tool nötig (npm kommt mit Node.js)
- GitHub Actions Caching für npm out-of-the-box verfügbar
- Keine `.npmrc`-Konfiguration für private Registries nötig (OSS)

---

## Verzeichnis-Konventionen

| Pfad | Inhalt |
|---|---|
| `backend/src/main/java/io/oea/` | Java-Quellcode |
| `backend/src/main/resources/` | `application.yml`, Flyway-Migrations (`db/migration/`) |
| `backend/src/test/java/io/oea/` | Unit- und Integration-Tests |
| `frontend/src/components/` | Vue-Komponenten (shared) |
| `frontend/src/stores/` | Pinia-Stores |
| `frontend/src/api/` | Generierter OpenAPI-Client (nicht manuell editieren) |
| `frontend/electron/main.ts` | Electron Main Process |
| `frontend/portal/main.ts` | Web-Portal Entry Point |

---

## Verworfene Alternativen

### Multi-Repo (Backend + Frontend getrennt)

- **Pro**: Unabhängige CI-Pipelines; Electron-Build isoliert
- **Contra**: Cross-Repo-Koordination bei API-Änderungen; zwei Repos für Contributors;
  Walking Skeleton über Repo-Grenzen hinweg unhandlich
- **Verworfen**: Overhead ohne Nutzen für v1

### Maven-Multi-Modul von Anfang an

- **Pro**: Erzwingt saubere Modul-Grenzen früh
- **Contra**: Konfigurationsaufwand (3–5 `pom.xml`); inter-Modul-Builds langsamer;
  für einen Entwickler kein Mehrwert
- **Verworfen**: YAGNI — nachrüstbar wenn nötig

### Gradle statt Maven

- **Pro**: Kürzere Build-Files (Kotlin DSL); inkrementelle Builds schneller
- **Contra**: Spring Boot Initializr generiert Maven; Team-Kontext kennt Maven;
  ADR-012 impliziert Maven (`./mvnw`)
- **Verworfen**: Konsistenz mit ADR-012

---

## Konsequenzen

**Positiv:**
- Erster `git clone` + `docker compose up` = lauffähige Entwicklungsumgebung
- Kein Cross-Repo-Versioning nötig
- API-Spec und generierter Client immer synchron
- CI kann Backend und Frontend in einer Pipeline koordinieren

**Negativ / Kompromisse:**
- Git-Repository wächst mit Code (akzeptabel; kein grosser Binary-Content)
- Electron-Build (Code-Signing, plattformspezifische Releases) läuft in derselben
  Pipeline — Komplexität in CI, nicht im Repo
- Einzelnes Maven-Modul: Paket-Disziplin erfordert Code-Review statt Compiler-Enforcement

---

## Betroffene Konzept-Kapitel

- §21 (Tech-Stack), §6 (Repository-Struktur)

## Verwandte ADRs

- ADR-011: Frontend Vue 3
- ADR-012: Backend Java/Spring Boot
- ADR-015: Flyway Migrations
- ADR-023: Multi-DB (docker-compose.mysql.yml)
- ADR-026: Externe Integration (backend/integration/)
- ADR-028: Backend-Schichtenarchitektur api/app/core (Kommunikationsregeln)
