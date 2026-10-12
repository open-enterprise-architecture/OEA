---
id: REQ-130
title: Barrierefreiheit WCAG 2.1 Level AA
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-06
    - UC-07
  business_objects:
    - entity
    - catalog
    - dashboard
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-05
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-130: Barrierefreiheit WCAG 2.1 Level AA

## Aussage

Die OEA-Weboberfläche MUSS für alle Seiten ausserhalb des Diagramm-Canvas die Konformitätsstufe **WCAG 2.1 Level AA** erfüllen. Der Diagramm-Canvas (UC-05, UC-10) ist von der WCAG-AA-Pflicht ausgenommen und MUSS mindestens WCAG 2.1 Level A erfüllen; für den Canvas SOLL eine zugängliche Alternativdarstellung (Listenansicht der Entities und Connections) bereitgestellt werden.

**Konkrete Anforderungen (Auswahl)**:
- Alle interaktiven Elemente sind per Tastatur erreichbar und bedienbar (Fokus-Management)
- Farbkontrast: Mindest-Kontrastverhältnis 4,5:1 für normalen Text, 3:1 für Grossschrift und UI-Komponenten
- Alle Nicht-Text-Inhalte (Icons, Diagramme) haben Text-Alternativen (`aria-label`, `alt`)
- Fehlermeldungen benennen konkret das betroffene Feld und beschreiben die Korrekturmassnahme
- Keine rein farbbasierten Informationsträger (Statusindikator muss zusätzlich Icon oder Text haben)

## Begründung

OEA ist ein Open-Source-Tool mit dem Anspruch breiter Nutzbarkeit. Barrierefreiheit ist für öffentliche Vergaben (EU-Richtlinie 2016/2102) und für Grossunternehmen mit Diversity-Anforderungen relevant. Ausserdem verbessert WCAG-Konformität die Bedienbarkeit für alle Nutzer (z.B. Tastaturbedienung für Power-User).

## Kontext

**In Scope**: Alle Web-Portal-Seiten (Login, Katalog, Dashboard, Metamodell-Editor, Navigationsbaum, Entitäts-Detailseiten)

**Ausnahme**: Diagramm-Canvas (UC-05, UC-10) — komplexe grafische Editoren sind per WCAG Ausnahme-tatbestand (Konformitätsanforderung wird auf Level A reduziert); Alternativdarstellung als Liste SOLL bereitgestellt werden

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: accessibility

**Messbare Zielwerte**:

| Metrik | Zielwert | Scope |
|---|---|---|
| WCAG 2.1 Level AA Konformität | 0 kritische Violations | alle Seiten ausser Canvas |
| WCAG 2.1 Level A Konformität (Canvas) | 0 kritische Violations | Diagramm-Canvas |
| Automatisierter Axe-Scan (CI) | 0 Violations (Severity: critical, serious) | alle Seiten |
| Tastaturnavigation: alle primären Workflows | vollständig per Tastatur bedienbar | Web Portal |

## Akzeptanzkriterien

**AC1** (Automatisierter Scan grün):
- Wenn: Axe oder Lighthouse Accessibility-Scan gegen alle Web-Portal-Seiten läuft
- Dann: 0 Violations mit Severity `critical` oder `serious`

**AC2** (Tastaturnavigation Login):
- Wenn: Nutzer Tab-Taste verwendet ab Seitenaufruf
- Dann: Alle Login-Felder, Buttons und Links erreichbar; Fokus-Indikator sichtbar

**AC3** (Kontrast):
- Wenn: Kontrast-Check gegen Design-System-Tokens durchgeführt
- Dann: Alle Text-/Hintergrundkombinationen erfüllen 4,5:1 (normal) / 3:1 (Grossschrift)

**AC4** (Canvas-Alternativdarstellung):
- Wenn: Nutzer den Canvas öffnet
- Dann: Link/Button „Als Liste anzeigen" vorhanden; listet alle Entities und Connections als strukturierte Tabelle

## Verifikationsmethode

- [x] Methode: automated-scan (Axe-Core in CI via Playwright oder Cypress)
- [x] Methode: manual-test (Screenreader-Test mit NVDA/VoiceOver für Login und Katalog)
- [ ] In CI integriert: ja (Axe-Scan), nein (Screenreader-Test)

## Abhängigkeiten

- **Voraussetzungen**: Design-System mit Farb-Tokens und Fokus-Styles (UI-Design-Phase)
- **Folgewirkungen**: Canvas-Alternativdarstellung erfordert zusätzliche Implementierungszeit

## Risiken bei Nichterfüllung

- Ausschluss von öffentlichen Ausschreibungen (EU 2016/2102)
- Nutzer mit Sehbehinderung oder motorischen Einschränkungen können das Tool nicht nutzen

## Trade-offs

- Canvas WCAG AA wäre ideal, ist aber für komplexe grafische Editoren technisch sehr aufwändig (D3.js, Vue Flow haben eingeschränkte native Accessibility); Level A + Alternativdarstellung ist pragmatischer Kompromiss
- Automatisierter Scan deckt ~30–40 % der WCAG-Kriterien ab; manuelle Tests sind für vollständige Konformität nötig

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
