---
id: REQ-006
title: Keine Preisgabe der Account-Existenz bei fehlgeschlagenem Login
type: constraint
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
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-006: Keine Preisgabe der Account-Existenz bei fehlgeschlagenem Login

## Aussage

Das System DARF NICHT durch unterschiedliche Fehlermeldungen oder Antwortzeiten erkennbar machen, ob eine angefragte Identität (E-Mail-Adresse, Subject-ID) im Repository existiert oder nicht.

## Begründung

Verhindert User-Enumeration-Angriffe, bei denen ein Angreifer durch systematisches Ausprobieren herausfindet, welche Identitäten im System bekannt sind. UC-01, Nachbedingung bei Misserfolg sowie Akzeptanzkriterien.

## Kontext

Betrifft insbesondere die Exception Flows E1 (Identity-Provider lehnt ab) und E2 (Person nicht auffindbar/offboarded) – beide müssen nach außen identisch wirken.

## Typ-spezifische Felder

### Bei type=constraint

**Art der Beschränkung**: technical

**Quelle**: Security-Best-Practice gegen User-Enumeration (OWASP); keine externe Norm zwingend vorgeschrieben, aber etablierter Standard

**Bindungsstärke**: hard (nicht umgehbar)

**Auswirkung bei Nichtbeachtung**: Angreifer können gültige Identitäten im System identifizieren und gezielt weiterangreifen (z.B. Phishing, Credential-Stuffing)

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein Login-Versuch mit nicht-existierender Identität
- Wenn: der Login fehlschlägt
- Dann: ist die nach außen sichtbare Fehlermeldung identisch zu der bei einer existierenden, aber falsch authentifizierten Identität

**AC2**:
- Gegeben: zwei Login-Fehlversuche, einmal mit existierender, einmal mit nicht-existierender Identität
- Wenn: die Antwortzeiten verglichen werden
- Dann: unterscheiden sie sich nicht in einer für Angreifer auswertbaren Weise (kein Timing-Seitenkanal)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Vergleich von Fehlermeldungs-Inhalt und Antwortzeit-Verteilung für existierende vs. nicht-existierende Identitäten
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls, ggf. Timing-Analyse-Skript
- [x] Bestanden-Kriterium: AC1 exakt, AC2 innerhalb tolerierter Varianz (Schwellwert in Realisierung festzulegen)
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-001, REQ-004
- **Folgewirkungen**: keine bekannt
- **Konflikte**: REQ-005 (internes Audit-Log darf/soll detaillierter sein als die nach außen sichtbare Meldung)

## Risiken bei Nichterfüllung

- Risiko 1: User-Enumeration ermöglicht gezielte Angriffe auf bekannte Mitarbeitende (Phishing, Credential-Stuffing) – schwerwiegend, besonders relevant für ein Tool mit sensiblen Architektur-/Compliance-Daten

## Trade-offs

- vs. Usability: generische Fehlermeldungen sind für legitime Nutzer weniger hilfreich beim Debuggen eigener Login-Probleme

## Realisierungs-Hinweise

- Konstante Antwortzeit (z.B. durch künstliche Verzögerung) kann nötig sein, um Timing-Seitenkanäle zu vermeiden

## Realisierung

- ADR(s): keine
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Akzeptanzkriterien und Exception Flow E1/E2.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |
