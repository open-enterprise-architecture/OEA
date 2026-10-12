# ADR-013: API-Stil – REST + OpenAPI 3.x

**Status**: accepted
**Datum**: 2026-06-26
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Requirements Engineer
**Informiert**: –
**Aktualisiert**: 2026-06-28 — NestJS-Referenzen durch SpringDoc OpenAPI (Spring Boot 3) und Gradle-Workflow ersetzt; ADR-012 hat Java 21 + Spring Boot 3 gewählt, nicht NestJS
**Aktualisiert**: 2026-10-04 — Umstellung von code-first auf **spec-first**: `api/openapi.yaml` ist der verbindliche Vertrag (ADR-027), Backend-Interfaces und Frontend-Typen werden daraus generiert; Build mit Maven statt Gradle (ADR-027)

## Kontext und Problem

OEA ist API-first (Leitprinzip in `concept/README.md`): alle Funktionalität ist über eine stabile, versionierte API zugänglich. UI, CLI, CI-Pipelines, Module und externe Integrationen nutzen dieselbe API. Es muss entschieden werden, welches API-Paradigma verwendet wird und wie die Spezifikation gepflegt wird.

Alle bisherigen REQs spezifizieren bereits REST-Endpunkte (`GET /api/v1/entities/...`, `POST /api/v1/catalogs/...`). Diese Entscheidung bestätigt und formalisiert diesen Ansatz.

## Entscheidungstreiber

- **Universelle Konsumierbarkeit**: CLI-Tool, CI-Pipelines, externe Integrationen (ITSM, PPM) müssen die API ohne TypeScript-Bindung nutzen können
- **Selbst-dokumentierend**: API-Spezifikation soll aus dem Code generiert werden (kein manuelles Pflegeproblem)
- **Typ-Sharing**: Frontend (Vue 3, ADR-011) soll typsichere API-Clients aus der Spezifikation generieren können
- **Standard-Konformität**: etablierter Standard, den Enterprise-Integrationsteams kennen
- **REQ-075**: keine proprietären Protokolle; API muss von Standard-HTTP-Clients konsumierbar sein

## Betrachtete Optionen

### Option 1: REST + OpenAPI 3.x ✓

- **Pro**:
  - Universell: jeder HTTP-Client (curl, Postman, fetch, Python requests) kann die API nutzen
  - Spec-first: der Vertrag `api/openapi.yaml` entsteht vor der Implementierung und ist für Backend, Frontend und externe Konsumenten gleichermaßen verbindlich
  - `openapi-generator-maven-plugin` (Generator `spring`, `interfaceOnly`) erzeugt Controller-Interfaces und DTOs für Spring Boot ([ADR-012](./ADR-012-backend-stack.md))
  - `openapi-typescript`: generiert typsichere Fetch-Clients für Vue 3-Frontend aus der Spec
  - Swagger UI ist out-of-the-box verfügbar (`/api/docs`)
  - Standard in EA-Tool-Integrationen (ITSM, PPM, CMDB alle sprechen REST)
  - Alle bestehenden REQ-Endpunkte sind bereits als REST spezifiziert
- **Contra**: Kein natives Subscription-Protokoll (→ SSE separat, §23 #26)

### Option 2: GraphQL

- **Pro**: Flexible Queries, Subscriptions nativ, kein Over-/Under-Fetching
- **Contra**: Zusätzliche Lernkurve; Caching komplexer (kein HTTP-Caching); CLI-Tools brauchen GraphQL-Client; Schema-Versionierung schwieriger; für OEA kein wesentlicher Vorteil gegenüber REST + OpenAPI
- Scheidet für v1.0 aus; kann als optionale Query-Schicht in v2.0 ergänzt werden

### Option 3: tRPC

- **Pro**: Maximale TypeScript-Type-Safety end-to-end (Frontend ↔ Backend)
- **Contra**: Nur für TypeScript-Clients sinnvoll — CLI-Tool (Node.js oder andere Sprachen), CI-Pipelines, ITSM-Konnektoren können tRPC nicht einfach konsumieren; bricht das API-first-Prinzip
- Scheidet aus

## Entscheidung

Wir wählen **Option 1: REST + OpenAPI 3.x**.

**Umsetzung:**

| Aspekt | Entscheidung |
|---|---|
| Paradigma | REST (ressourcenorientiert) |
| Spezifikation | OpenAPI 3.x, **spec-first**: handgepflegter Vertrag `api/openapi.yaml` |
| Code-Generierung Backend | `openapi-generator-maven-plugin` (Generator `spring`, `interfaceOnly=true`) erzeugt Controller-Interfaces + DTOs beim Maven-Build |
| API-Basis-Pfad | `/api/v1/` (versioniert) |
| Dokumentation | Swagger UI unter `/api/docs` (nur non-production), ausgeliefert aus `api/openapi.yaml` |
| Typ-Generierung Frontend | `openapi-typescript` (generiert `types.d.ts` aus Spec) |
| Echtzeit-Events | SSE unter `GET /api/v1/events` (§23 #26); kein WebSocket in v1.0 |
| Graph-Traversal | `POST /api/v1/entities/{id}/impact` (Recursive CTE, Tiefe konfigurierbar; ADR-016) |
| Analytics | `POST /api/v1/analytics/query` (SQL-Analytics, §22) |
| Authentifizierung | Bearer-Token (JWT) im `Authorization`-Header (ADR-006) |

**Versionierungsstrategie**: URL-basiert (`/api/v1/`, `/api/v2/`). Breaking Changes → neue Version; alte Version mind. 6 Monate parallel betreiben.

**OpenAPI-Spec als CI-Artefakt**: `api/openapi.yaml` wird in CI validiert (Lint) und gegen den Stand von `main` auf Breaking Changes geprüft (kein Bruch ohne Version-Bump). `./mvnw verify` generiert die Backend-Interfaces aus der Spec; weicht die Implementierung ab, schlägt der Build fehl. Die Spec wird als Release-Artefakt published.

**Workflow-Reihenfolge**: API-Änderung zuerst in `api/openapi.yaml` (Solution Architect, Teil des OpenSpec-Changes), danach Backend- und Frontend-Implementierung parallel gegen denselben Vertrag.

## Konsequenzen

### Positive Konsequenzen

- Alle bestehenden REQ-Endpunkte (`GET /api/v1/entities`, `POST /api/v1/catalogs`, ...) sind 1:1 umsetzbar
- Swagger UI ermöglicht interaktives Testen ohne separaten Client
- `openapi-typescript` (Frontend) und `openapi-generator-maven-plugin` (Backend) halten beide Seiten automatisch synchron mit dem Vertrag
- Backend und Frontend können parallel gegen denselben Vertrag entwickeln; API-Design wird vor der Implementierung reviewt
- Enterprise-Integrationen (ITSM, PPM, CI-Tools) können mit Standard-REST-Clients angebunden werden

### Negative Konsequenzen / Trade-offs

- SSE für Echtzeit-Updates ist nicht so elegant wie WebSocket/GraphQL Subscriptions; ausreichend für v1.0
- Kein automatisches Type-Safety für CLI-Tool wenn es nicht in TypeScript geschrieben wird (→ OpenAPI Spec als Quelle)
- Spec-first erfordert Disziplin: YAML wird von Hand gepflegt; Code-Generierung im Build und CI-Prüfung verhindern Drift zwischen Vertrag und Implementierung

## Bezüge

**Verwandte ADRs**: [ADR-012](./ADR-012-backend-stack.md) (Java 21 + Spring Boot 3), [ADR-006](./ADR-006-auth-stack-wahl.md) (Auth)

**Konzept**: [§21 API-Architektur](../concept/70-platform/21-api-architektur.md), [§22 Auswertbarkeit](../concept/70-platform/22-auswertbarkeit.md)
