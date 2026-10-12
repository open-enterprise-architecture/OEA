---
id: REQ-056
title: Dashboard-Zugriff und Sichtbarkeit (scope-Logik)
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
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-056: Dashboard-Zugriff und Sichtbarkeit (scope-Logik)

## Aussage

Das System MUSS die Sichtbarkeit von [Dashboards](../../business-objects/dashboard.md) anhand des `scope`-Attributs durchsetzen: `scope=instance`-Dashboards MÜSSEN für alle eingeloggten Nutzer der Instanz sichtbar und lesbar sein; `scope=personal`-Dashboards DÜRFEN ausschliesslich für den Ersteller sichtbar sein. Kein Nutzer darf `scope=personal`-Dashboards anderer Nutzer lesen, auch nicht per direktem API-Zugriff.

## Begründung

Dashboards können vertrauliche Unternehmenskennzahlen enthalten. Die scope-Kontrolle stellt sicher, dass persönliche Analysen nicht unbeabsichtigt öffentlich werden und dass C-Level-Dashboards konsistent für alle zugänglich sind.

## Typ-spezifische Felder

### API-Endpunkte und Sichtbarkeitsregeln

**Liste abrufen**:
- `GET /api/v1/dashboards`
- Liefert: alle `scope=instance`-Dashboards + eigene `scope=personal`-Dashboards
- Liefert NICHT: `scope=personal`-Dashboards anderer Nutzer

**Dashboard-Konfiguration abrufen**:
- `GET /api/v1/dashboards/{id}`
- `scope=instance`: HTTP 200 für alle authentifizierten Nutzer
- `scope=personal` + Requester ≠ Ersteller: HTTP 403

**Dashboard-Daten abrufen**:
- `GET /api/v1/dashboards/{id}/data`
- Gleiche Sichtbarkeitsregel wie bei Konfiguration

**Web Portal (öffentliche Instanz)**:
- Wenn die OEA-Instanz als öffentlich konfiguriert ist: `scope=instance`-Dashboards ohne Login lesbar
- Wenn die Instanz Login erfordert: alle Endpunkte erfordern Authentication (UC-01)

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | `scope=personal`-Dashboards sind ausschliesslich für den Ersteller und Admins sichtbar | onRead, onList |
| BR-02 | `scope=instance`-Dashboards sind für alle authentifizierten Nutzer der Instanz lesbar | onRead |
| BR-03 | Schreib-Zugriff (create, update, delete) ist ausschliesslich dem Ersteller des Dashboards und Admins erlaubt; plus: Dashboard-Schreibberechtigung für scope=instance (REQ-051 BR-02) | onWrite |
| BR-04 | Ein Dashboard kann von `personal` zu `instance` hochgestuft werden, wenn der Ersteller die Dashboard-Schreibberechtigung hat; Downgrade von `instance` zu `personal` ist nicht möglich, wenn andere Nutzer das Dashboard bereits verwenden | onUpdate |
| BR-05 | Direkte URL-Kenntnis einer `scope=personal`-Dashboard-ID umgeht die Sichtbarkeitsprüfung nicht; die API prüft immer den Ersteller gegen den Requester | onRead |

## Akzeptanzkriterien

**AC1** (instance-Dashboard: alle sehen es):
- Gegeben: Kurt hat ein `scope=instance`-Dashboard angelegt
- Wenn: Franz ruft `GET /api/v1/dashboards` auf
- Dann: Kurts Dashboard erscheint in der Liste

**AC2** (personal-Dashboard: kein Datenleck):
- Gegeben: Franz hat ein `scope=personal`-Dashboard angelegt
- Wenn: Kurt ruft `GET /api/v1/dashboards` oder `GET /api/v1/dashboards/{franz-dashboard-id}` auf
- Dann: Franzs Dashboard erscheint weder in der Liste noch ist es per ID abrufbar (403)

**AC3** (personal-URL-Zugriff geblockt):
- Gegeben: Kurts hat die ID von Franzs personal-Dashboard (z.B. erraten oder aus Logs)
- Wenn: `GET /api/v1/dashboards/{id}/data` direkt aufgerufen
- Dann: HTTP 403

**AC4** (Scope-Upgrade):
- Gegeben: Franz hat ein personal-Dashboard; er erhält danach Dashboard-Schreibberechtigung
- Wenn: `PUT /api/v1/dashboards/{id}` mit scope=instance
- Dann: HTTP 200; Dashboard ab sofort für alle sichtbar

**AC5** (Downgrade blockiert wenn andere Nutzer):
- Gegeben: Kurt hat ein instance-Dashboard; 5 weitere Nutzer haben es aufgerufen
- Wenn: Kurt versucht PUT mit scope=personal
- Dann: HTTP 409 „Dashboard wird von anderen Nutzern verwendet; Downgrade nicht möglich"

## Abhängigkeiten

- Bezieht Authentifizierung aus: REQ-001–007 (UC-01 Login)
- Berechtigungsmodell: Rolle „Enterprise Architekt" = Dashboard-Schreibberechtigung (analog zu Katalog-Berechtigung)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; scope-Durchsetzung, URL-Zugriff-Schutz, Upgrade/Downgrade-Logik |
