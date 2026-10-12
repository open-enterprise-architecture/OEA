---
id: REQ-029
title: Rollen-basierte 2FA-Ausnahme durch Betreiber konfigurieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-03
  business_objects:
    - person
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

# REQ-029: Rollen-basierte 2FA-Ausnahme durch Betreiber konfigurieren

## Aussage

Ist die instanzweite 2FA-Erzwingung aktiv (REQ-020), MUSS der Betreiber zusätzlich konfigurieren können, welche OEA-Rollen von dieser Pflicht ausgenommen sind; Personen, deren aktive Rolle(n) vollständig in der Ausnahmeliste enthalten sind, MÜSSEN REQ-020 ohne zweiten Faktor erfüllen können. Der System-Admin-Account (UC-02) ist von dieser Konfiguration unabhängig – er ist immer ausgenommen (REQ-020).

## Begründung

Administratoren einer OEA-Instanz benötigen häufig uneingeschränkten Zugang auch dann, wenn 2FA instanzweit erzwungen wird – etwa für initiale Einrichtung, Recovery-Szenarien oder Notfallzugriff. Eine starre "alle oder keine"-Erzwingung (REQ-020) zwingt den Betreiber, entweder für alle Personen 2FA zu erzwingen oder auf Erzwingung ganz zu verzichten. Die rollen-basierte Konfiguration erlaubt es, reguläre Nutzer zur 2FA zu verpflichten, während privilegierte Rollen (z.B. Instanz-Administratoren) bei Bedarf ausgenommen werden können. Adressiert SH-06s Anforderung an flexible, praxistaugliche Sicherheitskonfiguration.

## Kontext

Gilt nur, wenn REQ-020 aktiv ist (`enforce2FA=true`). Bei inaktiver Erzwingung ist REQ-029 bedeutungslos. Die Ausnahme gilt für OEA-interne Rollen (nicht für externe IdP-Claims). Mehrere Rollen können ausgenommen werden. Eine Person ist ausgenommen, wenn mindestens eine ihrer aktiven Rollen in der Ausnahmeliste steht. Der Ausnahmen-Check findet beim Login (UC-01) statt, direkt nach der Passwort-Prüfung und vor dem Required-Action-Check (UC-03).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben** (Konfiguration durch Betreiber im Admin-UI oder per Config-Datei):

| Regelname | Typ | Default | Beschreibung |
|---|---|---|---|
| `twoFactorExemptRoles` | string[] | `[]` | Liste der OEA-Rollen-IDs, die von der 2FA-Pflicht ausgenommen sind; leer = keine Ausnahme |

**Verarbeitung**:
- Nach erfolgreicher Passwort-Prüfung (UC-01 A5): prüfe, ob die Person eine Rolle hat, die in `twoFactorExemptRoles` liegt
- Falls ja: UC-03 Required Action wird nicht ausgelöst, auch wenn kein aktives TOTP/Passkey-Credential vorhanden ist; Login gewährt direkt Zugriff
- Falls nein: normaler Required-Action-Check (UC-03) gemäß REQ-020
- Ausnahme für System-Admin-Account (UC-02) ist hartcodiert; sie ist nicht über `twoFactorExemptRoles` konfiguriert und gilt unabhängig davon

**Ausgaben**:
- Konfiguration gespeichert; wirksam bei nächstem Login der betroffenen Personen

**Fehlerfälle**:
- Rollen-ID in `twoFactorExemptRoles` existiert nicht → Validierungsfehler beim Speichern; kein implizites Ignorieren unbekannter IDs
- Betreiber versucht, `twoFactorExemptRoles` zu setzen, ohne dass `enforce2FA=true` ist → zulässig (wird gespeichert, ist aber wirkungslos; Admin-UI gibt Hinweis)

## Akzeptanzkriterien

**AC1**:
- Gegeben: `enforce2FA=true` und `twoFactorExemptRoles=['administrator']`
- Wenn: eine Person mit Rolle `administrator` sich mit Passwort anmeldet und kein 2FA-Credential hat
- Dann: erhält sie direkt Zugriff, ohne UC-03 Required Action

**AC2**:
- Gegeben: dieselbe Konfiguration
- Wenn: eine Person ohne Rolle `administrator` sich mit Passwort anmeldet und kein 2FA-Credential hat
- Dann: wird UC-03 Required Action ausgelöst (Verhalten von REQ-020 unverändert)

**AC3**:
- Gegeben: `twoFactorExemptRoles=['administrator']` und `enforce2FA=true`
- Wenn: eine Person sowohl Rolle `administrator` als auch `editor` hat
- Dann: ist die Person ausgenommen (mindestens eine ausgenommene Rolle genügt)

**AC4**:
- Gegeben: `twoFactorExemptRoles` enthält eine nicht-existente Rollen-ID
- Wenn: der Betreiber speichern möchte
- Dann: wird die Konfiguration abgelehnt mit konkreter Fehlermeldung (unbekannte Rollen-ID)

**AC5**:
- Gegeben: System-Admin-Account aus UC-02 und beliebige `twoFactorExemptRoles`-Konfiguration
- Wenn: System-Admin-Account sich anmeldet
- Dann: ist er immer ausgenommen, unabhängig von dieser Konfiguration

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Instanz mit `enforce2FA=true`; Person mit ausgenommener Rolle + Person ohne ausgenommene Rolle; Login-Flow-Test
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls + Konfigurationsmodul
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-020 (2FA-Erzwingung; REQ-029 ist eine Erweiterung davon); OEA-Rollen-Modell muss existieren (Rollen-IDs referenzierbar)
- **Folgewirkungen**: UC-03 (Required Action wird für ausgenommene Personen nicht ausgelöst); UC-01 (Login-Flow prüft Ausnahme)
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne rollen-basierte Ausnahme kann der Betreiber 2FA nicht nur für reguläre Nutzer erzwingen, ohne auch Admins zu zwingen – entweder alles oder nichts; schränkt Praxistauglichkeit ein
- Risiko 2: Zu breite Ausnahmeliste (z.B. alle Rollen ausgenommen) hebelt REQ-020 effektiv aus – Betreiber-Verantwortung; keine technische Schutzmaßnahme dagegen

## Trade-offs

- Ausnahme auf Rollen-Basis (nicht auf Personen-Basis) ist bewusst: Ausnahmen durch OEA-Governance kontrollierbar (Rollenzuweisung), kein Ad-hoc-Ausschluss einzelner Personen.
- `twoFactorExemptRoles=[]` (Default) ergibt das ursprüngliche REQ-020-Verhalten: keine Ausnahmen für reguläre Personen.

## Realisierungs-Hinweise

- `twoFactorExemptRoles` als Teil der Instanz-Auth-Konfiguration (neben `enforce2FA` aus REQ-020)
- Check: beim Login nach Passwort-Prüfung; vor dem UC-03 Required-Action-Dispatch; O(1)-Lookup via Set-Intersection
- Admin-UI: Rollen-Dropdown statt Freitext-IDs; nur existierende Rollen wählbar → AC4 automatisch erfüllt

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus Betreiber-Anforderung (SH-06): Admin-Rollen müssen von der 2FA-Pflicht ausgenommen werden können, weil mehrere Admins existieren können und Admins in Recovery-/Einrichtungs-Szenarien uneingeschränkten Zugang benötigen. Die Konfiguration ist ein Sicherheits-Trade-off, den der Betreiber bewusst trifft.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
