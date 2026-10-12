# Ableitung aus: requirements/req/req-069-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-09
Feature: REQ-069 – Arc42-Dokumentation zu einer Entität bearbeiten und anzeigen

  Das System MUSS für jede ArchitectureEntity, deren EntityType
  mindestens einer Arc42ChapterCollection zugewiesen ist, einen Tab
  „Arc42 Dokumentation" anzeigen. Dieser Tab zeigt alle konfigurierten
  Fragen der zugewiesenen Collections und ermöglicht das Anlegen,
  Bearbeiten und Löschen von Arc42MetaObject-Antwort-Entitäten
  (REQ-068).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Tab sichtbar bei Collection-Zuweisung – EntityType `application-component` hat Collection `arc42-standard` zugew...
    Given EntityType `application-component` hat Collection `arc42-standard` zugewiesen
    When  Michael öffnet CRM-System-Entität (id=1)
    Then  Tab „Arc42 Dokumentation" sichtbar; 12 Fragen aufgelistet; alle zunächst unbeanwortet

  @AC2
  Scenario: AC2 – Tab fehlt ohne Zuweisung – EntityType `technology-component` hat keine Collection
    Given EntityType `technology-component` hat keine Collection
    When  Nutzer öffnet Technology-Component-Entität
    Then  Kein Arc42-Tab; kein Fehler

  @AC3
  Scenario: AC3 – Antwort anlegen – Michael klickt „Jetzt beantworten" bei Frage 1, schreibt Content, speichert
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Michael klickt „Jetzt beantworten" bei Frage 1, schreibt Content, speichert
    Then  arc42-meta-object-Entität angelegt (id=201); arc42-describes(source=201, target=1) angelegt; Frage 1 zeigt „✓"

  @AC4
  Scenario: AC4 – Antwort bearbeiten – Michael klickt „Bearbeiten" bei Frage 1 (id=201)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Michael klickt „Bearbeiten" bei Frage 1 (id=201)
    Then  WYSIWYG-Editor öffnet sich mit aktuellem Content; nach Speichern Entity 201 aktualisiert

  @AC5
  Scenario: AC5 – Fortschrittsindikator – 4 von 12 Fragen beantwortet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  4 von 12 Fragen beantwortet
    Then  Chip zeigt „4 / 12"; offene Fragen visuell unterschieden (Farbe oder Icon)

  @AC6
  Scenario: AC6 – Mehrere Collections – EntityType hat 2 Collections
    Given EntityType hat 2 Collections
    When  Arc42-Tab geöffnet
    Then  Beide Collections als separate Sektionen (oder Tabs); Fortschritt pro Collection separat

  @AC7
  Scenario: AC7 – Web Portal read-only – CIO öffnet Entität im Web Portal
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  CIO öffnet Entität im Web Portal
    Then  Arc42-Tab sichtbar; Inhalte mit gerenderten Diagrammen; kein „Beantworten"-Button

  @AC8
  Scenario: AC8 – Antwort löschen – Michael klickt „Löschen" bei beantworteter Frage
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Michael klickt „Löschen" bei beantworteter Frage
    Then  arc42-meta-object-Entität (201) und arc42-describes-Connection gelöscht; Frage wieder „unbeanwortet"
