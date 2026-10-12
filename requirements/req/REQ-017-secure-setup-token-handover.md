---
id: REQ-017
title: Sichere Übergabe des Setup-Tokens
type: constraint
priority: must
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

# REQ-017: Sichere Übergabe des Setup-Tokens

## Aussage

Das initiale Setup-Token bzw. das Aufforderungs-Interface zur Passwortvergabe DARF NICHT über einen Kanal ausgeliefert werden, der ohne vorherigen privilegierten Zugriff auf die Instanz (z.B. Server-/Container-Zugriff) erreichbar ist.

## Begründung

Da zum Bootstrapping-Zeitpunkt noch kein RBAC greift, ist der Übergabe-Kanal des Setup-Tokens der einzige Schutzmechanismus gegen unbefugte Übernahme des ersten System-Admin-Zugangs. UC-02, Realisierungs-Hinweise.

## Kontext

Betrifft REQ-013 (lokales Bootstrapping): die Art, wie Max das Setup-Token oder die Passwort-Aufforderung erhält.

## Typ-spezifische Felder

### Bei type=constraint

**Art der Beschränkung**: technical

**Quelle**: Security-Best-Practice (Prinzip: initialer Bootstrap-Zugang nur über Kanäle, die bereits Infrastruktur-Zugriff voraussetzen)

**Bindungsstärke**: hard (nicht umgehbar)

**Auswirkung bei Nichtbeachtung**: Ein über das offene Netz erreichbares Setup-Interface ohne vorherige Authentifizierung wäre ein direkter Übernahme-Vektor für die gesamte Instanz

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine frisch installierte Instanz
- Wenn: das Setup-Token generiert wird
- Dann: ist es ausschließlich über einen Kanal abrufbar, der bereits privilegierten Zugriff voraussetzt (z.B. CLI-Ausgabe beim Start, lokale Logdatei, Container-Exec)

**AC2**:
- Gegeben: das initiale Setup-Webinterface (falls UI-basiert)
- Wenn: es ohne gültiges Setup-Token aufgerufen wird
- Dann: verweigert es die Passwortvergabe

## Verifikationsmethode

- [x] Methode: test (automatisiert) + inspection
- [x] Test-Setup: Prüfung, dass das Setup-Token nicht über ungeschützte Netzwerk-Endpunkte abrufbar ist
- [x] Mess-Werkzeug: Security-Test-Suite / manuelle Inspektion bei Implementierung
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-013
- **Folgewirkungen**: keine bekannt
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Offen erreichbares Setup-Interface ermöglicht jedem Netzwerk-Teilnehmer die Übernahme der Instanz vor deren eigentlichem Betreiber – kritisch

## Trade-offs

- vs. Usability: CLI-/Server-Zugriff als Voraussetzung erhöht die Einstiegshürde leicht gegenüber einem rein Web-basierten Setup, ist aber sicherheitstechnisch notwendig

## Realisierungs-Hinweise

- Konkreter Kanal (CLI-Stdout, temporäre Datei mit restriktiven Rechten, einmalige Setup-URL mit kurzer Gültigkeit) ist Solution-Design, nicht Teil dieses Requirements (siehe UC-02, offene Fragen)

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-02, Realisierungs-Hinweise und offener Frage zur Setup-Token-Übergabe.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-02 |
