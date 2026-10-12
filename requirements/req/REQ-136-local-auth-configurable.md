---
id: REQ-136
title: Lokale Authentifizierung durch Betreiber konfigurierbar
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
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

# REQ-136: Lokale Authentifizierung durch Betreiber konfigurierbar

## Aussage

Der Betreiber **MUSS** pro OEA-Instanz konfigurieren können, ob die lokale Authentifizierung (Username/Passwort, Passkey, TOTP) als Anmeldeoption in der Benutzeroberfläche angeboten wird. OEA **DARF NICHT** eine Authentifizierungsmethode als empfohlen kennzeichnen oder vorauswählen — diese Entscheidung liegt ausschließlich beim Betreiber.

## Begründung

Ob lokale Authentifizierung für eine Organisation sinnvoll ist, hängt von deren Sicherheitsrichtlinien und IT-Infrastruktur ab. Unternehmen mit bestehendem Identity-Provider (Entra ID, Authentik) wollen ausschließlich OIDC anbieten; kleinere Betreiber ohne IdP benötigen lokale Authentifizierung. OEA als Hersteller trifft diese Entscheidung nicht — sie liegt beim Betreiber (SH-06). Eine eingebaute „empfohlen"-Kennzeichnung würde die Betreiber-Autonomie einschränken und falsche Erwartungen wecken.

## Kontext

Die Anforderung betrifft ausschließlich die Sichtbarkeit von Optionen in der UI (Login-Screen, Bootstrapping-Wizard). Die technische Implementierung lokaler Authentifizierungsmethoden bleibt unberührt — nur ob sie dem Endnutzer angeboten werden, ist konfigurierbar.

## Typ-spezifische Felder

**Eingaben**:
- Instanz-Konfiguration: `auth.localAuthEnabled: boolean` (Default: `true`)
- Instanz-Konfiguration: `auth.oidcEnabled: boolean` (Default: `false`, wird `true` sobald ein IdP konfiguriert ist)

**Verarbeitung**:
1. UI liest beim Start die Instanz-Konfiguration (via API oder eingebetteter Config)
2. `localAuthEnabled: false` → Felder für Username/Passwort, Passkey-Link werden nicht angezeigt; Bootstrapping-Wizard zeigt nur OIDC-Option
3. `localAuthEnabled: true` und `oidcEnabled: true` → beide Optionen werden gleichwertig angeboten, keine ist vormarkiert oder als „empfohlen" gekennzeichnet
4. `localAuthEnabled: true` und `oidcEnabled: false` → nur lokale Optionen anzeigen
5. Keiner der Provider-Namen oder Methodennamen darf mit einem „empfohlen"-Label versehen sein

**Ausgaben**:
- Login-Screen zeigt exakt die vom Betreiber freigegebenen Optionen
- Bootstrapping-Wizard zeigt exakt die vom Betreiber freigegebenen Bootstrapping-Modi

**Fehlerfälle**:
- `localAuthEnabled: false` und `oidcEnabled: false` gleichzeitig (kein Provider aktiv): System zeigt Fehlerzustand „Keine Authentifizierungsmethode konfiguriert" und blockiert den Login bis ein Betreiber eine Methode aktiviert

## Akzeptanzkriterien

**AC1** — Lokale Auth deaktiviert:
- Gegeben: `auth.localAuthEnabled = false`
- Wenn: Login-Screen geöffnet wird
- Dann: Kein Username/Passwort-Formular, kein Passkey-Link, keine lokale Option sichtbar

**AC2** — OIDC deaktiviert:
- Gegeben: `auth.oidcEnabled = false`
- Wenn: Login-Screen geöffnet wird
- Dann: Kein SSO-Button sichtbar; lokale Optionen werden angezeigt (sofern aktiviert)

**AC3** — Beide aktiv, keine Empfehlung:
- Gegeben: `auth.localAuthEnabled = true`, `auth.oidcEnabled = true`
- Wenn: Login-Screen geöffnet wird
- Dann: Beide Optionen werden gleichwertig angezeigt; keine Option trägt das Label „empfohlen", „Standard" oder ähnliches

**AC4** — Bootstrapping-Wizard folgt Config:
- Gegeben: `auth.localAuthEnabled = false`
- Wenn: Bootstrapping-Wizard geöffnet wird
- Dann: Nur OIDC-Bootstrapping-Option angeboten; lokaler Modus nicht anzeigbar

**AC5** — Kein aktiver Provider:
- Gegeben: `auth.localAuthEnabled = false` und `auth.oidcEnabled = false`
- Wenn: Login-Screen aufgerufen wird
- Dann: Fehlerzustand mit klarer Meldung für den Betreiber; kein normaler Login möglich

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Playwright-Tests mit verschiedenen Konfigurationskombinationen; Config-Mocking via API-Interceptor
- [ ] Bestanden-Kriterium: AC1–AC5 alle grün; kein „empfohlen"-Label im DOM bei gemischter Konfiguration
- [ ] In CI integriert: ja (nach Implementierung)

## Abhängigkeiten

- **Voraussetzungen**: ADR-006 (Auth-Stack-Wahl); Instanz-Konfigurationskonzept (noch kein eigener ADR)
- **Folgewirkungen**: REQ-137 (Auto-SSO) baut auf `oidcEnabled`-Flag auf
- **Konflikte**: keine

## Risiken bei Nichterfüllung

- Risiko 1: Betreiber mit reinem OIDC-Setup sieht unnötige lokale Auth-Optionen → Verwirrung und Support-Aufwand (mittel)
- Risiko 2: „empfohlen"-Label führt Betreiber in eine aus Sicherheitssicht ungeeignete Richtung → Vertrauensverlust (mittel)

## Realisierungs-Hinweise

- Konfiguration als Teil der Instanz-Konfiguration (Environment-Variable oder Admin-Config-Datei), nicht als Datenbank-Einstellung — muss vor dem ersten Login auswertbar sein
- Frontend liest Config einmalig beim App-Start; kein Nachladen während der Session nötig

## Realisierung

- ADR(s): ADR-006
- Implementiert durch: (noch offen)
- Tests: (noch offen)
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; Trennung Betreiber-Konfiguration von Hersteller-Default |
