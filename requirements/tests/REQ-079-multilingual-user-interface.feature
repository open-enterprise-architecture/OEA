# Ableitung aus: requirements/req/req-079-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should
Feature: REQ-079 – Mehrsprachige Benutzeroberfläche (i18n)

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Default Englisch – Eine frisch aufgesetzte OEA-Instanz zeigt die UI vollständig auf Englisc...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Eine frisch aufgesetzte OEA-Instanz zeigt die UI vollständig auf Englisch ohne weitere Konfiguration
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC2
  Scenario: AC2 – Sprache wechseln – Admin eine weitere Sprache aktiviert hat
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin eine weitere Sprache aktiviert hat
    Then  kann jeder Nutzer in den Profil-Einstellungen diese Sprache wählen

  @AC3
  Scenario: AC3 – EntityType-Anzeigename mehrsprachig – EntityTypeDefinition unterstützt `displayName: { en: "...", de: "..." }`...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - EntityTypeDefinition unterstützt `displayName: { en: "...", de: "..." }` als Sprach-Map
    # - UI zeigt immer den Anzeigenamen in der aktiven Nutzersprache; Fallback: Englisch; zweiter Fallback: technischer Identifier
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC4
  Scenario: AC4 – Fallback bei fehlender Übersetzung – Kein leerer String in der UI: fehlt ein Key in der aktiven Sprache, ersc...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Kein leerer String in der UI: fehlt ein Key in der aktiven Sprache, erscheint der englische Text (kein `undefined`)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC5
  Scenario: AC5 – Sprach-Pakete importierbar – Admin kann JSON-Sprach-Pakete über die Admin-UI hochladen und aktivieren
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Admin kann JSON-Sprach-Pakete über die Admin-UI hochladen und aktivieren
    # - Format: flaches JSON `{ "entity.create": "Entität anlegen", ... }`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC6
  Scenario: AC6 – Instanz-Default konfigurierbar – Beim Bootstrapping (UC-02) wählbar: welche Sprache ist die Instanz-Stand...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Beim Bootstrapping (UC-02) wählbar: welche Sprache ist die Instanz-Standard-Sprache (Fallback für Nutzer ohne eigene Präferenz)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC7
  Scenario: AC7 – Offline-fähig – Sprach-Pakete werden bei App-Start geladen und gecacht; kein externer CD...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Sprach-Pakete werden bei App-Start geladen und gecacht; kein externer CDN-Zugriff zur Laufzeit (REQ-075)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage
