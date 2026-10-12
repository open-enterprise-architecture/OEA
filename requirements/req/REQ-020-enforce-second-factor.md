---
id: REQ-020
title: Instanzweite Erzwingung eines zweiten Faktors für reguläre Logins
type: business-rule
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

# REQ-020: Instanzweite Erzwingung eines zweiten Faktors für reguläre Logins

## Aussage

Der Betreiber MUSS bei der Inbetriebnahme einer Instanz festlegen können, dass sich alle Personen ausschließlich mit einem zweiten Faktor anmelden dürfen (Passkey oder Username/Passwort mit TOTP, siehe REQ-009/REQ-010); ist diese Erzwingung aktiv, DARF die Minimal-Variante ohne zweiten Faktor (REQ-011) für reguläre Personen NICHT nutzbar sein. Diese Erzwingung gilt NICHT für den [System-Admin-Account](../../business-objects/system-admin-account.md) aus UC-02.

## Begründung

Organisationen mit höheren Sicherheitsanforderungen wollen schon bei der Inbetriebnahme ausschließen können, dass Personen sich jemals mit reinem Passwort ohne zweiten Faktor anmelden. Diese Entscheidung muss vom Betreiber (SH-06) zentral und instanzweit getroffen werden können, statt sie jeder Person einzeln zu überlassen.

Der explizite Ausschluss des System-Admin-Accounts ist notwendig, weil dieser laut [UC-02](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) und [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md) bewusst außerhalb des regulären Person-/Role-Modells steht und dem Bootstrapping-Zweck dient (Henne-Ei-Problem); er wird über eigene Mechanismen abgesichert (REQ-017: sichere Token-Übergabe), nicht über diese Richtlinie.

## Kontext

Betrifft die Instanz-Konfiguration aus UC-01 (reguläre Personen-Logins: REQ-001 OIDC, REQ-009 Passkey, REQ-010 TOTP, REQ-011 Minimal-Variante). Betrifft nicht UC-02 (System-Admin-Bootstrapping).

## Typ-spezifische Felder

### Bei type=business-rule

**Auslöser**: onCreate/onUpdate der Instanz-Authentifizierungs-Konfiguration; onLogin (Durchsetzung)

**Betroffene Business Objects**: [person](../../business-objects/person.md) (betroffen), [system-admin-account](../../business-objects/system-admin-account.md) (explizit NICHT betroffen)

**Formale Aussage** (vereinfacht):
```
if instanceConfig.enforceSecondFactor == true:
    disallow(REQ-011 for any Person login)
    require(login.factorCount >= 2) for OIDC-Logins (z.B. via amr-/acr-Claim-Prüfung, falls vom IdP bereitgestellt)
# gilt nicht für SystemAdminAccount-Logins (UC-02)
```

**Beispiel**: Ein Betreiber mit erhöhten Sicherheitsanforderungen aktiviert bei der Inbetriebnahme "zweiter Faktor erzwingen". Danach kann REQ-011 (Passwort ohne TOTP) für reguläre Personen nicht mehr aktiviert werden, selbst wenn ein Admin es versucht. Der System-Admin-Account aus dem Bootstrapping bleibt davon unberührt.

## Akzeptanzkriterien

**AC1**:
- Gegeben: eine Instanz, bei der "zweiter Faktor erzwingen" bei der Inbetriebnahme aktiviert wurde
- Wenn: versucht wird, REQ-011 (Username/Passwort ohne zweiten Faktor) für reguläre Personen zu aktivieren
- Dann: wird dies abgelehnt, solange die Erzwingung aktiv ist

**AC2**:
- Gegeben: dieselbe Instanz mit aktiver Erzwingung
- Wenn: der System-Admin-Account aus UC-02 sich anmeldet (lokal oder remote)
- Dann: ist diese Anmeldung von der Erzwingung unberührt und funktioniert wie in UC-02 spezifiziert

**AC3**:
- Gegeben: eine Instanz ohne aktivierte Erzwingung (Default)
- Wenn: das Authentifizierungs-Setup geprüft wird
- Dann: ist REQ-011 weiterhin gemäß seiner eigenen Bedingungen aktivierbar (kein Default-Zwang)

## Verifikationsmethode

- [x] Methode: test (automatisiert) + inspection (Default-Konfiguration)
- [x] Test-Setup: Instanz mit aktivierter Erzwingung, Versuch REQ-011 zu aktivieren; System-Admin-Login parallel prüfen
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls, Konfigurations-Inspektion
- [x] Bestanden-Kriterium: AC1–AC3 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-009, REQ-010, REQ-011 (alle drei lokalen Mechanismen müssen existieren, damit diese Regel etwas einzuschränken hat)
- **Folgewirkungen**: schränkt REQ-011 ein, sobald aktiv
- **Erweiterung**: REQ-029 (rollen-basierte Ausnahme konfigurieren – Betreiber kann Rollen von der 2FA-Pflicht ausnehmen)
- **Konflikte**: REQ-013/REQ-014 (System-Admin-Bootstrapping) sind bewusst von dieser Regel ausgenommen – siehe Begründung

## Risiken bei Nichterfüllung

- Risiko 1: Ohne instanzweite Erzwingungs-Option könnten sicherheitsbewusste Betreiber nicht verhindern, dass schwache Passwort-only-Logins für reguläre Personen aktiv bleiben
- Risiko 2: Würde die Erzwingung versehentlich auch den System-Admin-Account betreffen, könnte das Bootstrapping (UC-02) blockiert werden, bevor überhaupt ein zweiter Faktor registriert werden kann – daher der explizite Ausschluss

## Trade-offs

- vs. REQ-011: schränkt dessen Nutzbarkeit ein, sobald Erzwingung aktiv ist – bewusster Zielkonflikt, von Betreiber-Seite gewollt

## Realisierungs-Hinweise

- Durchsetzung bei OIDC-Logins (REQ-001) ist nur möglich, soweit der IdP MFA-Informationen bereitstellt (z.B. `amr`/`acr`-Claims gemäß OIDC Core); ist das nicht der Fall, kann OEA die Erzwingung für OIDC-Logins nicht technisch garantieren, sondern muss sich auf die Governance des IdP verlassen – diese Einschränkung sollte dem Betreiber bei Aktivierung kommuniziert werden
- Konfiguration sollte Teil derselben Instanz-Authentifizierungs-Konfiguration sein wie REQ-011/REQ-012

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Der Ausschluss des System-Admin-Accounts ist eine explizite fachliche Entscheidung (nicht aus UC-01/UC-02 selbst ableitbar) und wurde direkt vom Repository-Inhaber vorgegeben.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft |
