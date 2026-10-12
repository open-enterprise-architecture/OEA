---
id: REQ-104
title: Teilwiederherstellung dreistufiger Wizard
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

# REQ-104: Teilwiederherstellung dreistufiger Wizard

## Aussage

Das System MUSS einen dreistufigen Wizard für die selektive Wiederherstellung bereitstellen: Schritt 1 General Properties (`name`, `description`, `isLogical`), Schritt 2 Custom Properties (alle konfigurierten Properties aus `snapshot.propertyDefinitions`), Schritt 3 Verbindungsübersicht (informativ: geändert/hinzugekommen/entfernt). Jeder Schritt MUSS einzeln überspringbar sein. Schritte ohne Unterschiede zwischen Quelle und aktuellem Stand MÜSSEN automatisch übersprungen werden mit Hinweis.

## Begründung

Eine Vollwiederherstellung überschreibt korrekte Änderungen. Der Wizard ermöglicht chirurgische Korrekturen ohne Kollateralschäden und gibt Nutzern volle Kontrolle über die Wiederherstellungsoperationen.

## Akzeptanzkriterien

**AC1** (Quellversion vorausgefüllt):
- Wenn: der Wizard aus der Zeitlinie (UC-14) für Version v3 gestartet wird
- Dann: ist die Quellversion v3 vorausgefüllt

**AC2** (Automatisches Überspringen):
- Wenn: Schritt 1 keine Unterschiede in General Properties enthält
- Dann: wird Schritt 1 automatisch mit dem Hinweis "Keine Unterschiede in General Properties" übersprungen; Wizard wechselt direkt zu Schritt 2

**AC3** (Bestätigen-Button bei leerer Auswahl):
- Wenn: in allen Schritten keine Felder ausgewählt wurden
- Dann: ist der Bestätigen-Button deaktiviert (gemäss REQ-106)

## Abhängigkeiten

- **Voraussetzungen**: REQ-098 (Zeitlinie als Einstiegspunkt), REQ-105 (Feldauswahl), REQ-106 (Validierung)
- **Folgewirkungen**: REQ-102 (Audit-Snapshot), REQ-103 (atomare Transaktion)

## Realisierungs-Hinweise

- Wizard-Schritte mit Schritt-Indikator (1/3, 2/3, 3/3) und Überspringen-Link
- Diff-Berechnung für jeden Schritt separat; nur geänderte Felder laden
- Schritt 3 (Verbindungsübersicht) ist rein informativ; keine Auswahl nötig

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
