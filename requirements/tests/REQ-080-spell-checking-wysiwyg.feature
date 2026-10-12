# Ableitung aus: requirements/req/req-080-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @could
Feature: REQ-080 – Rechtschreibungsprüfung im WYSIWYG-Editor

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Browser-native Basis – TipTap-Editor-Element hat `spellcheck="true"` gesetzt
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - TipTap-Editor-Element hat `spellcheck="true"` gesetzt
    # - Der Browser markiert falsch geschriebene Wörter mit roter Unterstreichung
    # - Rechtsklick → Browser-Korrekturvorschläge (Standardverhalten)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC2
  Scenario: AC2 – Sprache der Prüfung – LanguageTool aktiv: Prüfsprache folgt der in REQ-079 gewählten UI-Sprach...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  LanguageTool aktiv: Prüfsprache folgt der in REQ-079 gewählten UI-Sprache des Nutzers; explizit im Editor überschreibbar
    Then ist das erwartete Ergebnis eingetreten

  @AC3
  Scenario: AC3 – LanguageTool konfigurierbar – konfiguriert: Editor sendet Text an LanguageTool-API und zeigt Fehler mi...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  konfiguriert: Editor sendet Text an LanguageTool-API und zeigt Fehler mit farblicher Unterstreichung an
    Then ist das erwartete Ergebnis eingetreten

  @AC4
  Scenario: AC4 – Fehlertypen mit LanguageTool – Rechtschreibfehler: rote Unterstreichung
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Rechtschreibfehler: rote Unterstreichung
    # - Grammatikfehler: blaue Unterstreichung
    # - Stil-Hinweise: graue Unterstreichung (deaktivierbar per Nutzer-Einstellung)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC5
  Scenario: AC5 – Fallback bei Nicht-Erreichbarkeit – LanguageTool nicht erreichbar: kein Fehler in der UI; Browser-nativer Sp...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  LanguageTool nicht erreichbar: kein Fehler in der UI; Browser-nativer Spellcheck bleibt aktiv
    Then ist das erwartete Ergebnis eingetreten

  @AC6
  Scenario: AC6 – Offline / Air-gapped – Self-hosted LanguageTool (`languagetool/languagetool` Docker-Image, LGPL...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Self-hosted LanguageTool (`languagetool/languagetool` Docker-Image, LGPL) konfigurierbar; konsistent mit REQ-075
    # - Kein externer Dienst-Aufruf wenn `languageToolUrl` nicht gesetzt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage

  @AC7
  Scenario: AC7 – Deaktivierbar – Nutzer kann Rechtschreibprüfung im Editor über Toolbar-Button deaktivier...
    # TODO: AC ohne Standard-Struktur – manuell verfeinern
    # - Nutzer kann Rechtschreibprüfung im Editor über Toolbar-Button deaktivieren (Präferenz wird gespeichert)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then verhält sich das System gemäß der Aussage
