# Ableitung aus: requirements/req/req-093-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-12
Feature: REQ-093 – Viewpoint-Löschung mit Diagramm-Warnung

  Beim Löschen eines user-defined Viewpoints MUSS das System prüfen, ob
  Diagramme diesen Viewpoint referenzieren, und eine Warnung mit der
  Anzahl betroffener Diagramme anzeigen. Die Löschung wird nicht
  blockiert; betroffene Diagramme referenzieren nach der Löschung
  keinen gültigen Viewpoint mehr und erhalten in der UI einen
  entsprechenden Hinweis.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Warnung bei genutztem Viewpoint – ein Viewpoint gelöscht wird, der von 5 Diagrammen genutzt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Viewpoint gelöscht wird, der von 5 Diagrammen genutzt wird
    Then  zeigt das System die Warnung "5 Diagramme werden betroffen"; nach Bestätigung wird gelöscht

  @AC2
  Scenario: AC2 – Keine Warnung bei ungenutztem Viewpoint – ein Viewpoint gelöscht wird, der von keinem Diagramm genutzt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Viewpoint gelöscht wird, der von keinem Diagramm genutzt wird
    Then  erfolgt eine direkte Bestätigung ohne Warnungsdialog
