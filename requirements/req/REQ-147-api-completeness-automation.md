---
id: REQ-147
title: API-Vollständigkeit und Automations-Tauglichkeit (API-First)
type: interface
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-05
    - UC-06
    - UC-11
    - UC-14
    - UC-17
    - UC-18
  business_objects:
    - architecture-element
    - catalog
    - diagram
    - solution
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
    - SH-09
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-013
supersedes: []
superseded_by: []
---

# REQ-147: API-Vollständigkeit und Automations-Tauglichkeit (API-First)

## Aussage

Jede modellverändernde Operation, die über die OEA-Benutzeroberfläche ausführbar ist, MUSS gleichwertig über einen dokumentierten, versionierten REST-Endpoint erreichbar sein. Der Endpoint MUSS so gestaltet sein, dass er von externen Systemen (CI/CD-Pipelines, Migrationsskripte, ITSM-Konnektoren, andere EA-Werkzeuge) ohne Kenntnis der UI konsumierbar ist. Das System MUSS Bulk-Operationen für alle zentralen Entitätstypen anbieten (Erstellen, Aktualisieren, Löschen im Batch). Alle zustandsverändernden Endpoints MÜSSEN idempotent ausgelegt sein oder explizit dokumentieren, warum Idempotenz nicht möglich ist. Das System MUSS ein Webhook-Mechanismus anbieten, über den externe Systeme bei Modelländerungen proaktiv benachrichtigt werden können, ohne polling zu benötigen.

## Begründung

OEA ist API-first (ADR-013, Leitprinzip `concept/README.md`). Dieses Prinzip bleibt ohne prüfbare Anforderung abstrakt. In der Praxis entstehen häufig "UI-only"-Operationen, die technisch HTTP nutzen, aber keine stabilen, externen API-Contracts darstellen — z.B. Session-gebundene Endpoints, UI-spezifische Aggregations-Antworten oder fehlende Batch-Endpunkte. Diese REQ stellt sicher, dass das API-first-Prinzip bei der Entwicklung jedes neuen Features operativ durchgesetzt wird.

Konkreter Anwendungsfall: Ein Konzern-Architect (SH-05) möchte 2 000 Applikationskomponenten aus dem CMDB per Skript in OEA importieren. Ein ITSM-System (SH-06) soll automatisch benachrichtigt werden, wenn eine Entität als `deprecated` markiert wird. Ein CI-Pipeline (SH-09) soll nach jedem Release-Branch-Merge automatisch den Plateau-Status umschalten. Alle drei Szenarien scheitern ohne diese Anforderung.

## Kontext

Diese REQ ergänzt ADR-013 (welche Technologie) um die qualitative Dimension (was muss erreichbar sein):

| Aspekt | ADR-013 (Wie) | REQ-147 (Was) |
|---|---|---|
| API-Stil und Technologie | REST + OpenAPI 3.x | — |
| Vollständigkeit | implizit erwähnt | explizit prüfbar (AC1) |
| Bulk-Operationen | nicht spezifiziert | MUSS vorhanden sein (AC2) |
| Idempotenz | nicht spezifiziert | MUSS oder dokumentierte Ausnahme (AC3) |
| Externe Consumer | als Ziel erwähnt | keine UI-spezifischen Annahmen (AC4) |
| Webhooks / Push | SSE erwähnt | vollständiger Event-Mechanismus (AC5) |
| Deprecation-Policy | Versionierungsstrategie | Consumer-Schutz 6 Monate (AC6) |

## Typ-spezifische Felder

### Bei type=interface

**Schnittstellen-Kontrakt**:

| Eigenschaft | Anforderung |
|---|---|
| Protokoll | HTTP/1.1, HTTP/2 |
| Stil | REST, ressourcenorientiert |
| Spezifikation | OpenAPI 3.x (maschinenlesbar, CI-Artefakt) |
| Authentifizierung | Bearer JWT (ADR-006), API-Key (REQ-002) |
| Basis-Pfad | `/api/v1/` |
| Content-Type | `application/json`; `application/json-patch+json` für Partial-Updates |
| Fehlerformat | RFC 9457 Problem Details (`type`, `title`, `status`, `detail`, `instance`) |

**Vollständigkeits-Regel**:

Jede der folgenden Operationen MUSS als eigenständiger Endpoint existieren, bevor das zugehörige UI-Feature als *done* gilt:

| Operation | Minimum-Endpoint |
|---|---|
| Entität anlegen | `POST /api/v1/entities` |
| Entität lesen | `GET /api/v1/entities/{id}` |
| Entität aktualisieren | `PUT /api/v1/entities/{id}` / `PATCH /api/v1/entities/{id}` |
| Entität löschen (soft) | `DELETE /api/v1/entities/{id}` (ADR-019) |
| Entitäten abfragen | `GET /api/v1/entities?type=…&q=…` |
| Bulk-Erstellen | `POST /api/v1/entities/bulk` |
| Bulk-Aktualisieren | `PATCH /api/v1/entities/bulk` |
| Diagramm-Shape einfügen | `POST /api/v1/diagrams/{id}/shapes` |
| Diagramm-Shape entfernen | `DELETE /api/v1/diagrams/{id}/shapes/{shapeId}` |
| Katalog-Abfrage | `GET /api/v1/catalogs/{id}/query` |
| Plateau umschalten | `PUT /api/v1/solutions/{id}/plateau` |

**Webhook-Mechanismus**:

| Aspekt | Anforderung |
|---|---|
| Registrierung | `POST /api/v1/webhooks` mit `{ url, events[], secret }` |
| Ereignistypen | `entity.created`, `entity.updated`, `entity.deleted`, `diagram.updated`, `plateau.changed` (mind.) |
| Payload | `{ event, timestamp, entityId, entityType, changedBy, diff }` |
| Sicherheit | HMAC-SHA256-Signatur im Header `X-OEA-Signature` |
| Retry | mind. 3 Versuche mit exponentiellem Backoff bei HTTP-Fehlerantwort |

## Akzeptanzkriterien

**AC1** (API-Vollständigkeit):
- Gegeben: Ein neues UI-Feature wird als *done* markiert
- Dann: Für jede zustandsverändernde Aktion des Features existiert ein dokumentierter Endpoint in der OpenAPI-Spec (`/api/docs`)
- Verifikation: Code-Review-Checkliste; CI schlägt fehl wenn neue Controller-Methode keine OpenAPI-Annotation hat

**AC2** (Bulk-Import):
- Wenn: `POST /api/v1/entities/bulk` mit 500 ApplicationComponent-Objekten aufgerufen wird
- Dann: Alle 500 Objekte werden angelegt oder eine atomare Rollback-Antwort zurückgegeben; Antwortzeit < 10 s; kein Timeout
- Scope: Testdatenbank mit 10 000 bestehenden Entities

**AC3** (Idempotenz):
- Wenn: `PUT /api/v1/entities/{id}` mit identischem Body zweimal aufgerufen wird
- Dann: Zweiter Aufruf gibt 200 zurück ohne doppelten Eintrag im Audit-Log
- Wenn: Ein Endpoint nicht idempotent sein kann: er ist in der OpenAPI-Spec als `x-idempotent: false` dokumentiert

**AC4** (Keine UI-Kopplung):
- Wenn: Ein externer Client alle Entitäten eines Katalogs abruft und modifiziert, ohne je die UI zu laden
- Dann: Alle Operationen (lesen, anlegen, aktualisieren, löschen, abfragen) sind vollständig via API durchführbar; kein Endpoint erfordert einen Browser-Session-Cookie
- Verifikation: Integration-Test mit reinem `curl` / Python `requests`

**AC5** (Webhook-Notification):
- Wenn: Eine Entität via `PUT /api/v1/entities/{id}` aktualisiert wird
- Dann: Alle registrierten Webhooks für Event `entity.updated` erhalten innerhalb von 5 s einen HTTP-POST mit korrekter HMAC-Signatur

**AC6** (Deprecation-Policy):
- Wenn: Ein Endpoint in einer neuen API-Version entfernt oder inkompatibel geändert wird
- Dann: Der alte Endpoint ist mind. 6 Monate nach Ankündigung noch erreichbar und antwortet mit `Deprecation`- und `Sunset`-Headern (RFC 8594)

## Abhängigkeiten

- Basiert auf: ADR-013 (REST + OpenAPI 3.x — Technologieentscheid)
- Zusammenhang: REQ-002 (API-Key-Authentifizierung), REQ-046 (Katalog-Abfrage-Endpoint), REQ-047 (SavedFilter)
- Betrifft: alle zukünftigen Feature-REQs — jedes neue Feature muss AC1 erfüllen
- Blockiert: Walking Skeleton API-Design (UC-06)

## Realisierungs-Hinweise

- **Spec-first für neue Endpoints**: OpenAPI-Annotation (`@Operation`, `@ApiResponse`) MUSS vor der Implementierung reviewed sein
- **Bulk-Endpoint-Design**: Transaktional (all-or-nothing) mit max. Batch-Grösse 1 000; Fehler-Response enthält Index der fehlgeschlagenen Items
- **Webhook-Service**: separater Spring-`@EventListener` auf Domain Events; entkoppelt von HTTP-Request-Thread; Webhook-Deliveries in separater Tabelle (für Retry und Audit)
- **CI-Gate**: `api/openapi.yaml` muss valide sein und `./mvnw verify` (Interface-Generierung aus der Spec, ADR-013) grün; API-Breaking-Change-Detector (z.B. `openapi-diff`) schlägt fehl wenn keine neue API-Version deklariert wurde

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft; erster REQ vom Typ `interface` im Projekt |
