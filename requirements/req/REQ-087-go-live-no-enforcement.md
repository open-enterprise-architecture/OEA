---
id: REQ-087
title: Go-Live ohne Enforcement durch Warnungen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-11
  business_objects:
    - plateau
    - solution
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-087: Go-Live ohne Enforcement durch Warnungen

## Aussage

Go-Live DARF NICHT durch nicht-implemented Solutions oder offene CEL-Rule-Violations blockiert werden. Diese Zustände MÜSSEN als Warnungen im Bestätigungsdialog angezeigt werden (Anzahl + Liste), blockieren die Ausführung jedoch nicht.

## Begründung

Freiheit ist der grösste Mehrwert. Betreiber können bewusst entscheiden, ein Plateau produktiv zu setzen, auch wenn nicht alle Solutions implementiert sind — z.B. bei Phasenmigration. Eine erzwungene Blockierung würde reale Übergangssituationen nicht abbilden.

## Akzeptanzkriterien

**AC1** (Nicht-implemented Solutions als Warnung):
- Wenn: bei Go-Live 3 nicht-implemented Solutions existieren
- Dann: zeigt der Dialog die Warnung "3 Solutions nicht implementiert"; Go-Live bleibt ausführbar

**AC2** (CEL-Violations als Warnung):
- Wenn: bei Go-Live CEL-Rule-Violations existieren
- Dann: zeigt der Dialog die Warnung "N Business-Rule-Violations"; Go-Live bleibt ausführbar

**AC3** (Kein Warnungsabschnitt ohne Warnungen):
- Wenn: bei Go-Live weder nicht-implemented Solutions noch CEL-Violations existieren
- Dann: wird kein Warnungsabschnitt im Dialog angezeigt

## Abhängigkeiten

- **Voraussetzungen**: REQ-086 (Bestätigungsdialog)
- **Folgewirkungen**: REQ-088 (Go-Live-Ausführung)

## Realisierungs-Hinweise

- Warnungen sind informativ; kein HTTP-Fehler-Status für diese Zustände
- Entscheidung liegt beim Nutzer; kein automatisches Blocking durch das System
- Warnungsliste: Solution-Namen und Violations als aufklappbare Liste im Dialog

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
