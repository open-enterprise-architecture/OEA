---
id: REQ-128
title: Admin-Override für Property-Sichtbarkeit
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - metamodel-configuration
    - role
    - entity
  stakeholders:
    - SH-03
    - SH-06
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-128: Admin-Override für Property-Sichtbarkeit

## Aussage

Das System MUSS einen konfigurierbaren **Admin-Override** bereitstellen: Nutzer mit einer als `overridesPropertyVisibility=true` markierten Systemrolle (Standard: `system-admin`) MÜSSEN alle Properties lesen können, unabhängig von `visibilityMode`. Der Override-Status MUSS im Audit-Log vermerkt werden, wenn ein eingeschränktes Property von einem Override-Nutzer gelesen wird. Die Liste der Override-Rollen MUSS im Metamodell-Editor konfigurierbar sein (EA-Manager kann weitere Rollen hinzufügen oder entfernen).

## Begründung

Ohne Admin-Override sind EA-Manager und System-Admins im Notfall (z.B. Fehleranalyse, Datenmigration) blind für Felder, die sie selbst eingeschränkt haben. Der Override ist explizit konfigurierbar statt hardcodiert, damit er organisationsspezifisch angepasst werden kann.

## Akzeptanzkriterien

**AC1** (Override wirksam):
- Wenn: Nutzer mit `system-admin`-Rolle eine Entität mit eingeschränkten Properties abruft
- Dann: Alle Werte werden zurückgegeben; `visibilityMode` hat keinen Effekt

**AC2** (Audit-Log-Eintrag):
- Wenn: Override-Nutzer ein `role-restricted` oder `connection-scoped` Feld liest
- Dann: Audit-Log enthält Eintrag mit Typ `property-override-access`, Nutzer-ID, Entitäts-ID und Property-Name

**AC3** (Override konfigurierbar):
- Wenn: Kurt im Metamodell-Editor die Override-Rollen-Liste bearbeitet und eine weitere Rolle hinzufügt
- Dann: Nutzer dieser Rolle erhalten ab sofort Override-Zugriff

**AC4** (Kein impliziter Override):
- Wenn: Nutzer hat `EA-Manager`-Rolle, aber `EA-Manager` ist nicht in der Override-Liste
- Dann: Sichtbarkeits-Einschränkungen greifen normal; kein automatischer Override durch Hierarchie-Annahme

## Abhängigkeiten

- **Voraussetzungen**: REQ-124 (Datenmodell), REQ-126 (Enforcement), REQ-005 (Audit-Log)
- **Folgewirkungen**: –

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
