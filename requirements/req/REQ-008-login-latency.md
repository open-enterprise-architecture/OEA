---
id: REQ-008
title: Login-Latenz
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-008: Login-Latenz

## Aussage

Das System SOLL den Login-Vorgang (ab Einlösen des Authorization Codes bzw. Validierung des API-Keys bis zur Session-Bestätigung) innerhalb eines definierten Zeitbudgets abschließen.

## Begründung

UC-01, Realisierungs-Hinweise: Login ist ein hochfrequenter, latenzkritischer Pfad. Lange Login-Zeiten beeinträchtigen die tägliche Nutzung, insbesondere für SH-03 (Kurt), der das Tool täglich nutzt.

## Kontext

Gilt für den serverseitigen Anteil des Login-Vorgangs (Token-Einlösung, Person-/Rollen-Lookup, Session-Erstellung); die Zeit, die eine Person beim externen Identity-Provider selbst verbringt (Credential-Eingabe), ist nicht Teil dieses Requirements.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: performance

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Schwellwert (critical) | Scope |
|---|---|---|---|---|
| p95 Server-Verarbeitungszeit Login (Token-Einlösung bis Session-Bestätigung) | < 300ms | 500ms | 1000ms | bei bis zu 10.000 Personen und 1.000 Rollen im Repository |
| p95 API-Key-Validierung | < 100ms | 200ms | 500ms | bei bis zu 10.000 aktiven API-Keys |

**Beispiel**: "p95 Login-Verarbeitungszeit < 300ms bei 10.000 Personen im Repository"

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Repository mit 10.000 Personen und 1.000 Rollen
- Wenn: 95% der OIDC-Login-Vorgänge gemessen werden (serverseitiger Anteil)
- Dann: liegt die Verarbeitungszeit unter 300ms

**AC2**:
- Gegeben: ein Repository mit 10.000 aktiven API-Keys
- Wenn: 95% der API-Key-Validierungen gemessen werden
- Dann: liegt die Verarbeitungszeit unter 100ms

## Verifikationsmethode

- [x] Methode: test (automatisiert, Lasttest)
- [x] Test-Setup: Lasttest mit synthetischem Repository-Datensatz (10.000 Personen, 1.000 Rollen, 10.000 API-Keys)
- [x] Mess-Werkzeug: Last-/Performance-Test-Tool (konkrete Wahl: Solution-Design, nicht hier festzulegen)
- [x] Bestanden-Kriterium: p95-Werte gemäß Tabelle eingehalten
- [ ] In CI integriert: ja, als Performance-Gate sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-001, REQ-002
- **Folgewirkungen**: keine bekannt
- **Konflikte**: REQ-004, REQ-006 (zusätzliche Lookup-/Anti-Timing-Maßnahmen dürfen das Latenzbudget nicht sprengen)

## Risiken bei Nichterfüllung

- Risiko 1: Spürbar langsamer Login mindert tägliche Akzeptanz, besonders bei häufiger Nutzung – mittlerer Schweregrad

## Trade-offs

- vs. REQ-006 (konstante Antwortzeit gegen Timing-Seitenkanäle): künstliche Verzögerung zur Anti-Timing-Maßnahme darf das hier definierte Budget nicht überschreiten

## Realisierungs-Hinweise

- Rollenzuweisungen sollten gecacht werden, nicht bei jedem Login neu aus dem Repository aufgelöst (siehe UC-01 Realisierungs-Hinweise)
- Konkrete Zielwerte sind vorläufig (Walking-Skeleton-Annahme) und bei realer Lastmessung zu validieren

## Realisierung

- ADR(s): keine
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Zielwerte sind erste Schätzung für den Walking-Skeleton-Scope, keine validierten Produktions-SLAs. Bei Bedarf in Review anpassen.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |
