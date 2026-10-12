---
id: REQ-051
title: Dashboard anlegen
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-051: Dashboard anlegen

## Aussage

Das System MUSS es berechtigten Nutzern ermöglichen, ein neues [Dashboard](../../business-objects/dashboard.md)-Objekt mit Name, optionaler Beschreibung und Scope (`instance` oder `personal`) anzulegen. Dashboards mit `scope=instance` DÜRFEN nur von Nutzern angelegt werden, die die Rolle „Enterprise Architekt" oder eine äquivalente Dashboard-Schreibberechtigung besitzen. Ein neu angelegtes Dashboard enthält keine Widgets und ist sofort für berechtigte Betrachter sichtbar.

## Begründung

Dashboards sind der Einstiegspunkt für C-Level-Reporting. Die Anlage muss einfach und validiert sein; insbesondere muss scope=instance durch eine Berechtigung geschützt werden, damit keine unkontrollierten öffentlichen Dashboards entstehen.

## Kontext

Dashboard-Management-Aktionen erfolgen ausschliesslich in der **Client App** (ADR-008). Betrachten (read-only) ist im **Web Portal** möglich. Dieses REQ betrifft nur die Erstellung; Widget-Konfiguration folgt in REQ-052.

## Typ-spezifische Felder

### API-Endpunkte

**Anlegen**:
- `POST /api/v1/dashboards`
- Request Body:
  ```json
  {
    "name": "IT-Investitionsplanung 2026–2030",
    "description": "C-Level-Reporting für CIO",
    "scope": "instance"
  }
  ```
- Response 201: `{ "id": "uuid", "name": "...", "scope": "instance", "createdBy": "person-uuid", "widgets": [] }`
- Response 409: Name bereits vergeben im Scope
- Response 403: Scope=instance ohne Berechtigung

**Bearbeiten (Metadaten)**:
- `PUT /api/v1/dashboards/{id}` – Name, Beschreibung, Scope aktualisieren
- Scope-Upgrade (`personal` → `instance`) erfordert ebenfalls Dashboard-Schreibberechtigung

**Auflisten**:
- `GET /api/v1/dashboards` – gibt `scope=instance`-Dashboards + eigene `personal`-Dashboards zurück; nie fremde `personal`-Dashboards

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | `name` muss eindeutig sein: für `scope=instance` global; für `scope=personal` pro Ersteller (case-insensitive, trimmed) | onCreate, onUpdate |
| BR-02 | `scope=instance` erfordert Dashboard-Schreibberechtigung; fehlt diese → 403 | onCreate, onUpdate |
| BR-03 | Der Ersteller kann jederzeit seinen eigenen Dashboard bearbeiten und löschen; Admins ebenfalls | onUpdate, onDelete |
| BR-04 | Dashboard-`name` darf nach dem Anlegen geändert werden, solange die Eindeutigkeits-Bedingung (BR-01) gewahrt bleibt | onUpdate |

## Akzeptanzkriterien

**AC1** (Anlegen mit scope=instance):
- Gegeben: Kurt (Rolle Enterprise Architekt) ist eingeloggt
- Wenn: `POST /api/v1/dashboards` mit name="IT-Investitionsplanung" und scope=instance
- Dann: HTTP 201; Dashboard-Objekt in DB; für alle Nutzer im GET /api/v1/dashboards sichtbar

**AC2** (Duplikat-Name):
- Gegeben: Dashboard „IT-Investitionsplanung" (scope=instance) existiert bereits
- Wenn: zweites POST mit demselben Namen
- Dann: HTTP 409 mit Fehlermeldung „Name bereits vergeben"

**AC3** (Fehlende Berechtigung für scope=instance):
- Gegeben: Franz (Rolle Junior Domain Architekt, keine Dashboard-Schreibberechtigung) ist eingeloggt
- Wenn: POST mit scope=instance
- Dann: HTTP 403; kein Dashboard angelegt

**AC4** (Personal-Dashboard für beliebigen Nutzer):
- Gegeben: Franz (ohne Schreibberechtigung) ist eingeloggt
- Wenn: POST mit scope=personal, name="Meine Analyse"
- Dann: HTTP 201; Dashboard ist nur für Franz sichtbar (in GET /api/v1/dashboards für andere Nutzer nicht enthalten)

**AC5** (Scope-Upgrade):
- Gegeben: Franz hat ein personal-Dashboard; später erhält er die Dashboard-Berechtigung
- Wenn: PUT mit scope=instance
- Dann: HTTP 200; Dashboard ab sofort für alle sichtbar

## Abhängigkeiten

- Setzt voraus: UC-01 (Login, REQ-001–007)
- Ermöglicht: REQ-052 (Widgets hinzufügen), REQ-055 (Daten abrufen), REQ-056 (Zugriff steuern)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
