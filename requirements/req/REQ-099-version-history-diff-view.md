---
id: REQ-099
title: Versionshistorie feldweiser Diff
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-14
  business_objects:
    - entity
    - entity-version
    - person
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-099: Versionshistorie feldweiser Diff

## Aussage

Das System MUSS beim Anklicken einer Version einen feldweisen Diff zwischen dieser Version und ihrem Nachfolger anzeigen (Vorher/Nachher-Werte, farblich hervorgehoben: rot=entfernt, grün=hinzugefügt). Properties MÜSSEN als einzelne Felder verglichen werden (Dot-Notation), nicht als JSON-Blob. Das System MUSS ausserdem den Direkt-Vergleich zweier beliebig gewählter Versionen ermöglichen (kumulierter Diff).

## Begründung

Ein JSON-Blob-Diff ist für fachliche Nutzer unlesbar. Feldweiser Diff erlaubt schnelles Erkennen von Änderungen ohne technische Vorkenntnisse und macht die Zeitlinie für EA-Praktiker nutzbar.

## Akzeptanzkriterien

**AC1** (Nur geänderte Felder):
- Wenn: eine Version angeklickt wird
- Dann: zeigt der Diff nur die geänderten Felder; identische Felder sind ausgeblendet

**AC2** (Dot-Notation für Properties):
- Wenn: `properties.owner` geändert wurde
- Dann: erscheint der Eintrag als "properties.owner: 'Kurt' → 'Michael'"

**AC3** (Kumulierter Diff):
- Wenn: Version v2 und v7 für den Vergleich ausgewählt werden
- Dann: zeigt das System den kumulierten Diff über alle 5 Übergänge (v2→v3, …, v6→v7)

## Abhängigkeiten

- **Voraussetzungen**: REQ-098 (Zeitlinie), REQ-100 (Snapshots)
- **Folgewirkungen**: REQ-101 (Vollwiederherstellung startet aus Diff-Ansicht)

## Realisierungs-Hinweise

- Diff-Algorithmus: Deep-Compare zweier Snapshot-JSONs mit Dot-Notation-Flattening
- Farbliche Hervorhebung: CSS-Klassen für rot (removed) und grün (added)
- Kumulierter Diff: schrittweise Anwendung der Diff-Übergänge auf den Basis-Snapshot

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
