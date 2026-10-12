---
id: REQ-093
title: Viewpoint-Löschung mit Diagramm-Warnung
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-12
  business_objects:
    - viewpoint
    - metamodel-configuration
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-093: Viewpoint-Löschung mit Diagramm-Warnung

## Aussage

Beim Löschen eines user-defined Viewpoints MUSS das System prüfen, ob Diagramme diesen Viewpoint referenzieren, und eine Warnung mit der Anzahl betroffener Diagramme anzeigen. Die Löschung wird nicht blockiert; betroffene Diagramme referenzieren nach der Löschung keinen gültigen Viewpoint mehr und erhalten in der UI einen entsprechenden Hinweis.

## Begründung

Viewpoints können in Produktion von vielen Diagrammen genutzt werden. Eine stille Löschung ohne Warnung würde Nutzer überraschen und verwaiste Diagramm-Referenzen unbemerkt erzeugen.

## Akzeptanzkriterien

**AC1** (Warnung bei genutztem Viewpoint):
- Wenn: ein Viewpoint gelöscht wird, der von 5 Diagrammen genutzt wird
- Dann: zeigt das System die Warnung "5 Diagramme werden betroffen"; nach Bestätigung wird gelöscht

**AC2** (Keine Warnung bei ungenutztem Viewpoint):
- Wenn: ein Viewpoint gelöscht wird, der von keinem Diagramm genutzt wird
- Dann: erfolgt eine direkte Bestätigung ohne Warnungsdialog

## Abhängigkeiten

- **Voraussetzungen**: REQ-090 (Viewpoint-Verwaltung)
- **Folgewirkungen**: betroffene Diagramme zeigen Hinweis auf fehlenden Viewpoint

## Realisierungs-Hinweise

- Pre-Delete-Check: COUNT der Diagramme mit Referenz auf diesen Viewpoint-ID
- Betroffene Diagramme erhalten `viewpointId=null` nach Löschung; UI zeigt Hinweisbanner
- Warnung als Modal-Dialog vor Ausführung der Löschung

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
