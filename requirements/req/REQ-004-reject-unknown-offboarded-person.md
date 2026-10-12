---
id: REQ-004
title: Ablehnung der Session-Erstellung für unbekannte oder offboardete Personen
type: functional
priority: must
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
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organisation-rollen-personen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-004: Ablehnung der Session-Erstellung für unbekannte oder offboardete Personen

## Aussage

Das System MUSS die Session-Erstellung verweigern, wenn ein gültiges Identity-Token keiner aktiven [Person](../../business-objects/person.md) im Repository zugeordnet werden kann oder die zugeordnete Person den Lifecycle-Status `offboarded` hat.

## Begründung

Ein gültiges Identity-Token allein genügt nicht für Zugriff – die Person muss im EA-Repository als aktiv bekannt sein. Verhindert Zugriff durch ehemalige Mitarbeitende, deren Identity-Provider-Account ggf. noch nicht deprovisioniert wurde. UC-01, Exception Flow E2.

## Kontext

Person-Mastership liegt meist beim HR-System (Konzept §8.7); der `active`/`offboarded`-Status im Repository kann dem Identity-Provider-Status zeitlich vorauseilen oder nachlaufen.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Gültiges Identity-Token mit Identity-Claims (z.B. E-Mail, Subject-ID)

**Verarbeitung**:
- Abgleich der Claims gegen `Person.externalReference`
- Prüfung des Lifecycle-Status der gefundenen Person (`active` erforderlich)

**Ausgaben**:
- Bei Treffer mit `active`-Status: Session wird erstellt (siehe REQ-001)
- Bei keinem Treffer oder Status `offboarded`: Ablehnung

**Fehlerfälle**:
- Kein Treffer → Ablehnung, Audit-Log-Eintrag (siehe REQ-005)
- Treffer mit Status `offboarded` → Ablehnung, Audit-Log-Eintrag

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein gültiges Identity-Token, das keiner Person im Repository zugeordnet werden kann
- Wenn: der Login-Vorgang abgeschlossen wird
- Dann: wird keine Session erstellt

**AC2**:
- Gegeben: ein gültiges Identity-Token, dessen zugeordnete Person den Status `offboarded` hat
- Wenn: der Login-Vorgang abgeschlossen wird
- Dann: wird keine Session erstellt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Login-Versuch mit Token ohne Person-Zuordnung bzw. mit `offboarded`-Person
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-001
- **Folgewirkungen**: REQ-005 (Audit-Log)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Offboardete Personen könnten bei verzögerter IdP-Deprovisionierung weiter Zugriff auf das EA-Repository haben – schwerwiegend, insbesondere bei sensiblen Architektur-/Compliance-Daten

## Trade-offs

- vs. REQ-001 (Geschwindigkeit des Login-Flows): zusätzlicher Lookup-Schritt gegen das Person-Objekt verlängert die Login-Latenz minimal

## Realisierungs-Hinweise

- Lookup sollte über `externalReference` indiziert sein, um Login-Latenz gering zu halten (siehe Performance-Hinweis in UC-01)

## Realisierung

- ADR(s): keine
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Exception Flow E2.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |
