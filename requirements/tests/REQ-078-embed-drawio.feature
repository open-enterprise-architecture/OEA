# Ableitung aus: requirements/req/req-078-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (Playwright E2E für AC1–AC5, AC7) + manuell (AC6 Security-Review, AC8)

@functional @should @UC-09
Feature: REQ-078 – Draw.io-Diagramme im WYSIWYG-Editor einbetten

  Das System MUSS im WYSIWYG-Editor (Arc42, UC-09) das Einbetten von
  Draw.io-Diagrammen als Codeblock (`drawio`) ermöglichen. Der
  Codeblock enthält Draw.io-XML (`<mxGraphModel>`); das System MUSS
  dieses XML als SVG-Vorschau rendern.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Rendering aus Leseansicht – Arc42-Antwort mit `drawio`-Codeblock
    Given Arc42-Antwort mit `drawio`-Codeblock
    When  Nutzer öffnet die Antwort
    Then  Draw.io-Diagramm als SVG gerendert (nicht als XML-Text); korrekte Shapes und Connections sichtbar

  @AC2
  Scenario: AC2 – Editor-Modal öffnen – Nutzer ist im Bearbeitungsmodus der Arc42-Antwort
    Given Nutzer ist im Bearbeitungsmodus der Arc42-Antwort
    When  Klick auf „In Draw.io öffnen"
    Then  Modal öffnet sich mit dem vollen draw.io-Editor; vorhandenes XML ist geladen; Nutzer kann Shapes hinzufügen, verschieben, beschriften

  @AC3
  Scenario: AC3 – Änderungen übernehmen – Nutzer im draw.io-Editor speichert (Ctrl+S oder Speichern-Button)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer im draw.io-Editor speichert (Ctrl+S oder Speichern-Button)
    Then  Modal schliesst; aktualisiertes XML erscheint im `drawio`-Codeblock; SVG-Vorschau wird sofort aktualisiert

  @AC4
  Scenario: AC4 – Neues leeres Diagramm – Nutzer fügt einen leeren `drawio`-Codeblock ein und klickt „Öffnen"
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer fügt einen leeren `drawio`-Codeblock ein und klickt „Öffnen"
    Then  draw.io-Editor öffnet mit leerem Canvas; nach Speichern steht XML im Block

  @AC5
  Scenario: AC5 – Entity-Mention in Shape-Label – Nutzer bearbeitet ein Shape-Label im draw.io-Editor und tippt `[[CRM`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer bearbeitet ein Shape-Label im draw.io-Editor und tippt `[[CRM`
    Then  Autocomplete-Dropdown öffnet sich über dem draw.io-Modal; nach Auswahl wird `CRM-System` als reiner Text ins Shape-Label eingefügt

  @AC6
  Scenario: AC6 – Sicherheit — kein XSS – Draw.io-XML mit `<mxCell value="<script>alert(1)</script>"/>`
    Given Draw.io-XML mit `<mxCell value="<script>alert(1)</script>"/>`
    When  Rendering
    Then  Script-Tag wird entfernt oder escaped; kein JavaScript ausgeführt

  @AC7
  Scenario: AC7 – Web-Portal read-only – Nutzer im Web-Portal Arc42-Dokumentation öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer im Web-Portal Arc42-Dokumentation öffnet
    Then  Draw.io-Diagramm als SVG sichtbar; kein Editor-Button; kein postMessage-Bridge aktiv

  @AC8
  Scenario: AC8 – Offline-fähig – OEA-Instanz ohne Internetzugang; draw.io-Viewer-Library ist lokal mitgel...
    Given OEA-Instanz ohne Internetzugang; draw.io-Viewer-Library ist lokal mitgeliefert
    When  Nutzer öffnet Antwort mit `drawio`-Block
    Then  SVG-Rendering funktioniert ohne externe Anfragen; draw.io-Editor-Popup verwendet ebenfalls lokale draw.io-Installation (wenn konfiguriert)
