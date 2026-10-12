# Ableitung aus: requirements/req/req-130-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: automated-scan (Axe-Core in CI via Playwright oder Cypress)

@non-functional @performance @should @UC-01 @UC-06 @UC-07
Feature: REQ-130 – Barrierefreiheit WCAG 2.1 Level AA

  Die OEA-Weboberfläche MUSS für alle Seiten ausserhalb des
  Diagramm-Canvas die Konformitätsstufe **WCAG 2.1 Level AA** erfüllen.
  Der Diagramm-Canvas (UC-05, UC-10) ist von der WCAG-AA-Pflicht
  ausgenommen und MUSS mindestens WCAG 2.1 Level A erfüllen; für den
  Canvas SOLL eine zugängliche Alternativdarstellung (Listenansicht der
  Entities und Connections) bereitgestellt werden.

  @AC1
  Scenario: AC1 – Automatisierter Scan grün – Axe oder Lighthouse Accessibility-Scan gegen alle Web-Portal-Seiten läuft
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Axe oder Lighthouse Accessibility-Scan gegen alle Web-Portal-Seiten läuft
    Then  0 Violations mit Severity `critical` oder `serious`

  @AC2
  Scenario: AC2 – Tastaturnavigation Login – Nutzer Tab-Taste verwendet ab Seitenaufruf
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer Tab-Taste verwendet ab Seitenaufruf
    Then  Alle Login-Felder, Buttons und Links erreichbar; Fokus-Indikator sichtbar

  @AC3
  Scenario: AC3 – Kontrast – Kontrast-Check gegen Design-System-Tokens durchgeführt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Kontrast-Check gegen Design-System-Tokens durchgeführt
    Then  Alle Text-/Hintergrundkombinationen erfüllen 4,5:1 (normal) / 3:1 (Grossschrift)

  @AC4
  Scenario: AC4 – Canvas-Alternativdarstellung – Nutzer den Canvas öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer den Canvas öffnet
    Then  Link/Button „Als Liste anzeigen" vorhanden; listet alle Entities und Connections als strukturierte Tabelle
