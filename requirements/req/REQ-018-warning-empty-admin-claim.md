---
id: REQ-018
title: Warnung bei leerem oder nicht-existierendem Remote-Admin-Claim
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - system-admin-account
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-018: Warnung bei leerem oder nicht-existierendem Remote-Admin-Claim

## Aussage

Das System SOLL beim Konfigurieren eines Remote-Bootstrapping-Mappings (REQ-014) warnen, wenn die angegebene Gruppe/Rolle beim Identity-Provider zum Konfigurationszeitpunkt prüfbar nicht existiert oder keine Mitglieder hat.

## Begründung

Ein Mapping auf eine leere oder nicht-existierende Gruppe würde dazu führen, dass niemand über diesen Weg System-Admin-Rechte erhält – ein Lockout-Risiko, falls dies der einzige konfigurierte Zugang ist. UC-02, Exception Flow E2.

## Kontext

Greift während der Konfiguration des Remote-Mappings (UC-02, A1, Schritt 2), nicht erst beim späteren Login-Versuch.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Konfigurierte Provider-, Claim-Typ- und Claim-Wert-Angabe für das Remote-Mapping

**Verarbeitung**:
- Soweit technisch möglich (abhängig von Provider-API-Zugriff): Prüfung, ob die angegebene Gruppe/Rolle existiert und mindestens ein Mitglied hat
- Anzeige einer Warnung, falls die Prüfung negativ ausfällt oder nicht durchführbar ist

**Ausgaben**:
- Warnung an den Operator (Max), verbunden mit dem Hinweis, den lokalen Bootstrapping-Weg (REQ-013) zusätzlich aktiv zu halten

**Fehlerfälle**:
- Provider-API für die Prüfung nicht erreichbar → Warnung wird als "Prüfung nicht möglich" ausgegeben, Konfiguration wird nicht blockiert (kein Hard-Stop)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Remote-Mapping auf eine zum Konfigurationszeitpunkt leere oder nicht existierende Gruppe
- Wenn: Max die Konfiguration speichert
- Dann: erhält er eine Warnung, dass dieses Mapping aktuell niemandem Zugriff verleiht

**AC2**:
- Gegeben: dieselbe Situation
- Wenn: Max die Warnung zur Kenntnis nimmt
- Dann: kann er die Konfiguration trotzdem speichern (kein Hard-Block), idealerweise mit aktivem lokalem Fallback (REQ-013)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Konfiguration eines Mappings gegen einen Test-IdP mit leerer/nicht-existierender Gruppe
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-014
- **Folgewirkungen**: keine bekannt
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Warnung könnte ein Operator versehentlich die Instanz so konfigurieren, dass niemand mehr System-Admin-Zugriff über den Remote-Weg erhält – mittlerer Schweregrad, abgefedert durch REQ-013 als Fallback

## Trade-offs

- vs. Implementierungsaufwand: eine verlässliche Prüfung erfordert Provider-spezifische API-Zugriffe (z.B. Microsoft Graph für Entra ID), die über reine OIDC-Standardkonformität hinausgehen

## Realisierungs-Hinweise

- Prüfung ist Best-Effort, kein verpflichtender Hard-Block, da nicht jeder Provider eine entsprechende Abfrage-API bereitstellt

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-02, Exception Flow E2.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-02 |
