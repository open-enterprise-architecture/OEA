---
id: REQ-011
title: Username/Passwort-Login ohne zweiten Faktor (Minimal-Variante)
type: functional
priority: could
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
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-011: Username/Passwort-Login ohne zweiten Faktor (Minimal-Variante)

## Aussage

Das System KANN Personen die Anmeldung mit ausschließlich Username/Passwort ohne zweiten Faktor ermöglichen; diese Variante MUSS auf Instanz-Ebene explizit aktiviert werden und DARF NICHT der Standardzustand einer neu installierten OEA-Instanz sein.

## Begründung

Konzept §21.8 nennt diese Variante bewusst als "riskante Minimal-Variante" für den einfachsten Self-Hosting-Fall (z.B. Kurt als Single-User im KMU, der weder externen IdP noch Passkey/TOTP-Infrastruktur betreiben will). UC-01, Alternative A5. Da sie das schwächste Sicherheitsniveau aller Mechanismen bietet, ist sie als optionale, bewusst zu aktivierende Ausnahme modelliert, nicht als Default.

## Kontext

Betrifft ausschließlich Instanzen, die explizit auf einen zweiten Faktor und auf Passkey verzichten.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Username, Passwort

**Verarbeitung**:
- Prüfung des Passwort-Hashes gegen den gespeicherten Hash der Person
- Prüfung, ob diese Authentifizierungsvariante für die Instanz aktiviert ist; falls nicht, wird sie wie ein nicht-existierender Mechanismus behandelt

**Ausgaben**:
- Bei korrektem Passwort: Fortsetzung mit Person-/Rollen-Lookup (siehe REQ-001, Schritt 6)

**Fehlerfälle**:
- Falsches Passwort → generische Fehlermeldung (siehe REQ-006)
- Variante ist instanzweit deaktiviert → Mechanismus wird nicht angeboten

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Instanz, auf der diese Variante explizit aktiviert wurde, und eine Person mit korrektem Passwort
- Wenn: sie sich anmeldet
- Dann: erhält sie eine Session gemäß ihren aktiven Rollenzuweisungen

**AC2**:
- Gegeben: eine neu installierte, nicht konfigurierte OEA-Instanz
- Wenn: ihr Authentifizierungs-Setup geprüft wird
- Dann: ist diese Variante standardmäßig deaktiviert

**AC3**:
- Gegeben: jeder erfolgreiche oder fehlgeschlagene Login über diese Variante
- Wenn: der Audit-Log-Eintrag erzeugt wird (siehe REQ-005)
- Dann: ist im Eintrag erkennbar, dass kein zweiter Faktor verwendet wurde

## Verifikationsmethode

- [x] Methode: test (automatisiert) + inspection (Default-Konfiguration)
- [x] Test-Setup: Login-Versuche bei aktivierter und deaktivierter Variante; Prüfung der Default-Konfiguration einer frischen Installation
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls, Konfigurations-Inspektion
- [x] Bestanden-Kriterium: AC1–AC3 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-024 (Passwort-Enrollment muss vorher abgeschlossen sein)
- **Folgewirkungen**: keine bekannt
- **Konflikte**: [REQ-020](./REQ-020-erzwingung-zweiter-faktor.md) – ist die instanzweite Erzwingung eines zweiten Faktors aktiv, darf diese Variante für reguläre Personen nicht aktivierbar sein

## Risiken bei Nichterfüllung

- Risiko 1: Wäre diese Variante standardmäßig aktiv, würde jede OEA-Instanz ungewollt mit dem schwächsten Authentifizierungsniveau ausgeliefert – schwerwiegend
- Risiko 2: Ohne diese Option könnten Betreiber ohne Passkey/TOTP-Infrastruktur (z.B. Max im KMU) das Tool gar nicht ohne externen IdP betreiben

## Trade-offs

- vs. Sicherheit: explizit schwächer als REQ-009 (Passkey) und REQ-010 (TOTP); Existenz dieser Option ist ein bewusster Usability-/Self-Hosting-Kompromiss, kein empfohlener Standard

## Realisierungs-Hinweise

- Aktivierung sollte eine bewusste, mit einer Warnung versehene Konfigurationsentscheidung sein (z.B. beim Setup-Wizard explizit abgefragt, nicht versteckt in einer Config-Datei)
- Passwort-Hashing wie in REQ-010 (z.B. Argon2id), Rate-Limiting gegen Brute-Force

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-01 Login, Alternative A5. Priorität bewusst `could` statt `should`, da diese Variante nicht für den produktiven Standardfall vorgesehen ist, sondern für den niedrigschwelligsten Self-Hosting-Fall.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-01 |
