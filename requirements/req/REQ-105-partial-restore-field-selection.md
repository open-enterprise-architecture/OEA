---
id: REQ-105
title: Teilwiederherstellung selektive Feldauswahl
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-16
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-105: Teilwiederherstellung selektive Feldauswahl

## Aussage

Im Wizard MÜSSEN ausschliesslich Felder auswählbar sein, die sich zwischen Quelle und aktuellem Stand unterscheiden. Identische Felder MÜSSEN ausgegraut und nicht anwählbar sein. Properties, die in der Quellversion im Metamodell noch nicht existierten (nach späterer Erweiterung hinzugekommen), MÜSSEN als "(in Quellversion nicht vorhanden)" gekennzeichnet und nicht auswählbar sein.

## Begründung

Das versehentliche Auswählen identischer Felder wäre verwirrend. Properties, die in der Quellversion nicht existierten, können nicht sinnvoll wiederhergestellt werden, da kein Quellwert vorliegt.

## Akzeptanzkriterien

**AC1** (Identische Felder ausgegraut):
- Wenn: ein Feld in Quelle und aktuellem Stand identisch ist
- Dann: ist die Checkbox ausgegraut und nicht anklickbar

**AC2** (Neue Properties gekennzeichnet):
- Wenn: eine Property "cost-center" in der Quellversion noch nicht vorhanden war
- Dann: zeigt der Wizard "(in Quellversion nicht vorhanden)"; Checkbox nicht anwählbar

**AC3** (Vorschau bei Feldauswahl):
- Wenn: die Checkbox eines geänderten Feldes aktiviert wird
- Dann: wird der Wert aus der Quellversion als Vorschau angezeigt

## Abhängigkeiten

- **Voraussetzungen**: REQ-104 (Wizard), REQ-100 (Snapshots)
- **Folgewirkungen**: REQ-106 (restoredFields-Protokoll)

## Realisierungs-Hinweise

- Diff-Berechnung per Dot-Notation-Flattening beider Snapshots
- Felder mit `source=undefined` (nicht in Quell-Snapshot) als not-restorable markieren
- Vorschau-Wert direkt aus dem Snapshot-JSON der Quellversion lesen

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
