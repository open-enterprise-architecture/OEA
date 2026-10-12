---
id: REQ-012
title: Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung
type: data
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

# REQ-012: Konfigurierbare Token-Lebensdauer bei lokaler Authentifizierung

## Aussage

Bei lokaler Authentifizierung (Passkey, Username/Passwort, mit oder ohne TOTP – siehe REQ-009–REQ-011) MUSS die Gültigkeitsdauer von Access-Token (Authorization Token) und Refresh-Token instanzweit konfigurierbar sein.

## Begründung

Im Gegensatz zu externen Identity-Providern (Entra ID, Authentik), bei denen die Token-Gültigkeit vom IdP vorgegeben wird (siehe REQ-001, AC4), gibt es bei lokaler Authentifizierung keine externe Instanz, die diese Werte festlegt. OEA selbst muss daher sinnvolle, konfigurierbare Defaults bereitstellen, da unterschiedliche Betreiber (z.B. SH-03 Single-User-KMU vs. sicherheitsbewusstere Organisationen) unterschiedliche Sicherheits-/Usability-Abwägungen treffen.

## Kontext

Betrifft ausschließlich Sessions, die über die lokalen Authentifizierungsmechanismen aus REQ-009 (Passkey), REQ-010 (Username/Passwort + TOTP) und REQ-011 (Username/Passwort ohne zweiten Faktor) entstehen.

## Typ-spezifische Felder

### Bei type=data

**Betroffene Datenstruktur**: Instanz-weite Authentifizierungs-Konfiguration (nicht Person- oder Role-Objekt)

**Datentyp**: composite (zwei Werte: `accessTokenLifetime`, `refreshTokenLifetime`, jeweils Dauer)

**Constraints**:
- Pflichtfeld: ja, beide Werte müssen gesetzt sein (mit sinnvollem Default, falls nicht explizit konfiguriert)
- Format: ISO 8601 Duration (z.B. `PT15M` für 15 Minuten) oder gleichwertige Konfigurationsnotation
- Wertebereich: `accessTokenLifetime` < `refreshTokenLifetime`; beide > 0
- Eindeutigkeit: instanzweit einheitlich (nicht pro Person konfigurierbar)

**Default-Wert**: vorläufig `accessTokenLifetime: PT15M`, `refreshTokenLifetime: P7D` (Walking-Skeleton-Annahme, bei Bedarf in Review anzupassen)

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Instanz mit lokal konfigurierter Access-Token-Lebensdauer von 15 Minuten
- Wenn: eine Person sich über einen lokalen Mechanismus (Passkey, Username/Passwort) anmeldet
- Dann: läuft das Access-Token nach 15 Minuten ab

**AC2**:
- Gegeben: ein abgelaufenes Access-Token, aber ein noch gültiges Refresh-Token aus lokaler Authentifizierung
- Wenn: ein Refresh angefordert wird
- Dann: wird ein neues Access-Token mit der konfigurierten Lebensdauer ausgestellt

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Konfiguration unterschiedlicher Lebensdauer-Werte, Prüfung von Ablauf- und Refresh-Verhalten
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-009, REQ-010, REQ-011 (mindestens einer der lokalen Mechanismen muss existieren)
- **Folgewirkungen**: keine bekannt
- **Konflikte**: REQ-001 AC4 (für externe IdP gilt die gegenteilige Regel: keine eigene Lebensdauer-Festlegung durch OEA)

## Risiken bei Nichterfüllung

- Risiko 1: Feste, nicht konfigurierbare Lebensdauer könnte für manche Betreiber zu kurz (Frustration) oder zu lang (Sicherheitsrisiko bei gestohlenen Tokens) sein

## Trade-offs

- vs. Sicherheit: längere Lebensdauer erhöht Usability, aber auch das Risiko bei Token-Diebstahl – Betreiber müssen diese Abwägung selbst treffen können, OEA gibt nur sinnvolle Defaults vor

## Realisierungs-Hinweise

- Konfiguration sollte Teil der instanzweiten Auth-Konfiguration sein (gleiche Stelle wie Aktivierung von REQ-011)
- Gilt nicht für extern ausgestellte Token (REQ-001, AC4) – dort bestimmt der IdP

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Löst die in UC-01 offene Frage "Wie lange ist eine Session gültig, und wie wird Refresh gehandhabt?" für den lokalen Authentifizierungsfall; für externe IdPs ist die Frage durch REQ-001 AC4 beantwortet (IdP-seitig vorgegeben).

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft, klärt Token-Lebensdauer getrennt für externe IdP (REQ-001) und lokale Authentifizierung |
