---
id: REQ-019
title: Deaktivierbarkeit des lokalen System-Admin-Accounts bei aktivem Remote-Mapping
type: business-rule
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
  business_rules:
    - BR-02
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-019: Deaktivierbarkeit des lokalen System-Admin-Accounts bei aktivem Remote-Mapping

## Aussage

Sobald für eine Instanz mindestens ein funktionsfähiges Remote-Bootstrapping-Mapping (REQ-014) konfiguriert ist, SOLL der lokale System-Admin-Account (REQ-013) durch einen System-Admin gezielt deaktivierbar sein.

## Begründung

Organisationen mit etablierter IdP-Governance wollen nach Erstkonfiguration keinen zusätzlichen, außerhalb der zentralen Identity-Verwaltung liegenden Zugang offen lassen. Business Rule BR-02 aus `system-admin-account.md`; ADR-006, Negative Konsequenzen.

## Kontext

Betrifft den Zustand nach erfolgreichem Remote-Bootstrapping (UC-02, A1 oder A2) – das lokale Credential bleibt standardmäßig aktiv, bis es bewusst deaktiviert wird.

## Typ-spezifische Felder

### Bei type=business-rule

**Auslöser**: onUpdate (manuelle Aktion eines System-Admins)

**Betroffene Business Objects**: [system-admin-account](../../business-objects/system-admin-account.md)

**Formale Aussage** (vereinfacht):
```
if SystemAdminAccount.mode == "remote" and mapping.isFunctional:
    allow(deactivate(localSystemAdminAccount))
```

**Beispiel**: Nachdem Max ein funktionierendes Remote-Mapping auf eine Entra-ID-Gruppe eingerichtet hat, deaktiviert er den ursprünglichen lokalen Bootstrap-Account, um die Angriffsfläche zu reduzieren.

## Akzeptanzkriterien

**AC1**:
- Gegeben: ein aktiver lokaler System-Admin-Account und ein funktionsfähiges Remote-Mapping
- Wenn: ein System-Admin den lokalen Account deaktiviert
- Dann: ist eine Anmeldung über den lokalen Account danach nicht mehr möglich, während der Remote-Weg weiter funktioniert

**AC2**:
- Gegeben: kein funktionsfähiges Remote-Mapping konfiguriert
- Wenn: versucht wird, den lokalen Account zu deaktivieren
- Dann: warnt das System vor einem möglichen vollständigen Lockout, blockiert die Aktion aber nicht zwingend (Operator-Entscheidung)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Deaktivierung des lokalen Accounts mit und ohne funktionsfähiges Remote-Mapping
- [x] Mess-Werkzeug: Test-Suite des Auth-Moduls
- [x] Bestanden-Kriterium: AC1, AC2 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-013, REQ-014
- **Folgewirkungen**: keine bekannt
- **Konflikte**: erhöht das Risiko aus UC-02 Exception Flow E4 (vollständiger Lockout), falls unüberlegt deaktiviert – daher AC2 als Warnung statt Hard-Block

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Deaktivierungs-Option bleibt dauerhaft ein zusätzlicher, nicht über die zentrale IdP-Governance kontrollierbarer Zugang bestehen – Compliance-/Sicherheitsrisiko für Organisationen mit strikten Vorgaben

## Trade-offs

- vs. Lockout-Risiko (E4): unüberlegte Deaktivierung ohne funktionsfähigen Remote-Zugang kann die Instanz vollständig unadministrierbar machen – deshalb Warnung statt Verbot

## Realisierungs-Hinweise

- "Funktionsfähig" beim Remote-Mapping sollte sich nach Möglichkeit auf REQ-018 (Warnung bei leerem Claim) stützen, ist aber letztlich nicht zur Laufzeit hundertprozentig verifizierbar

## Realisierung

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-wahl.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Notizen

Abgeleitet aus UC-02, offene Frage zur BR-02-Umsetzung, und ADR-006, Folgeentscheidungen.

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft aus UC-02 |
