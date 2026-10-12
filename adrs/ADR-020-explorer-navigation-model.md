---
id: ADR-020
title: Explorer-Browser-Navigationsmodell im Native Client
status: proposed
date: 2026-06-28
deciders:
  - Lead Enterprise Architekt
  - Solution Architekt
categories:
  - UX
  - Navigation
  - Frontend
references:
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
    - concept/20-entities/12-domain-sichten.md
  requirements:
    - REQ-138
    - REQ-139
    - REQ-140
  related_adrs:
    - ADR-021
---

# ADR-020: Explorer-Browser-Navigationsmodell im Native Client

## Status

Proposed

**Entscheider**: [Kurt – Lead Enterprise Architekt](../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md) (SH-03), [Michael – Solution Architekt](../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) (SH-04)

## Kontext

Der Native Client benötigt einen Browser-Panel (linke Seitenleiste), der Nutzern schnelle Navigation durch das EA-Repository ermöglicht. Gleichzeitig existiert bereits ein manueller Navigationsbaum (TreeNode / UC-13), den der Lead Enterprise Architekt frei gestaltet.

Drei Navigationsmodelle stehen zur Wahl.

## Entscheidungstreiber

- Solution Architekten arbeiten primär innerhalb einer Solution — die Navigation soll diesen Kontext sofort widerspiegeln
- Jede Solution braucht dieselben sechs Grundkategorien; manuelle Anlage per TreeNode-CRUD wäre repetitiv
- Ein aggregierter "Aktueller Stand"-Knoten ist für Analysen und Reporting der wichtigste Einstiegspunkt
- Die manuelle Baumstruktur (UC-13) hat weiterhin Daseinsberechtigung für kundenspezifische Sichten

## Optionen

### Option A — Solution-zentriert (auto-generiert)

Solutions erscheinen als Hauptknoten. Darunter je sechs auto-generierte Grundkategorien (Komponenten, Verbindungen, Kataloge, Functional Building Blocks, Solution Building Blocks, Diagramme). Zusätzlich ein virtueller „Aktueller Stand"-Knoten, der alle implementierten Inhalte aggregiert. Solutions ohne Plateau-Mapping sind ausschliesslich im „Aktueller Stand"-Knoten sichtbar.

**Pro**: Direkter Arbeitskontext; kein manueller Aufwand für Grundstruktur; konsistente Navigation für alle Nutzer
**Contra**: Wenig Flexibilität für ungewöhnliche Organisationsformen; Solutions-Liste kann bei vielen Projekten lang werden

### Option B — Freie Baumstruktur (manuell, aktuell UC-13)

Der Lead EA Architekt organisiert Inhalte manuell in Ordnern (TreeNode). Keine Auto-Struktur; volle Freiheit.

**Pro**: Maximale Flexibilität; passt zu jeder Organisationsstruktur
**Contra**: Administrativer Aufwand; neue Solutions müssen jedes Mal manuell eingehängt werden; Konsistenz abhängig von Disziplin

### Option C — Entity-Typ-zentriert (nach ArchiMate-Schicht)

Inhalte nach ArchiMate-Schicht oder Entitätstyp organisiert (Anwendungsschicht, Technologieschicht, Geschäftsschicht…).

**Pro**: Klassisches ArchiMate-Browsing; vertraut für ArchiMate-Nutzer
**Contra**: Widerspricht dem Solution-Konzept; Solutions (thematische Einheiten) werden zerrissen; nicht TOGAF-konform

## Entscheidung

**Option A — Solution-zentriert** wird als Standard-Tab „Browser" implementiert.

Der Browser-Panel zeigt Solutions als primäre Navigationsebene. Jede Solution erhält automatisch die sechs Grundkategorien als Unterknoten. Ein virtueller „Aktueller Stand"-Knoten erscheint als erster Knoten und aggregiert alle realisierten Inhalte. Die manuelle Baumstruktur (Option B) bleibt als separater Tab „Architektur-Baum" im Browser-Panel erhalten — beide Tabs sind frei im Layout verschiebbar.

## Konsequenzen

- REQ-138, REQ-139, REQ-140, REQ-141, REQ-142 werden aus dieser Entscheidung abgeleitet
- UC-13 (Navigationsbaum verwalten) beschreibt weiterhin den zweiten Tab „Architektur-Baum"
- Der „Aktueller Stand"-Knoten ist read-only und wird aus dem Solution-Status und Plateau-Mapping berechnet — keine manuelle Pflege nötig
- Solutions mit `status=archived` erscheinen nicht im Browser (nur in der Solution-Verwaltung)
- Die Grundstruktur (6 Kategorien) ist nicht konfigurierbar auf Instanzebene; sie ist fester Bestandteil jeder Solution

## Betroffene Konzept-Kapitel

- §6 Kern-Entitätstypen
- §12 Domain-Sichten

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | Solution Architekt | Initial draft |
