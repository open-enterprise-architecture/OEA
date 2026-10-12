# Ableitung aus: requirements/req/req-024-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-03
Feature: REQ-024 – Initiales Passwort durch Administrator setzen

  Das System MUSS einem Administrator ermöglichen, beim Anlegen einer
  Person für lokale Authentifizierung ein initiales Passwort zu setzen;
  dieses Passwort MUSS sofort nach dem Setzen als sicherer Hash
  (argon2id oder bcrypt) persistiert werden und MUSS dem Administrator
  genau einmal im Klartext angezeigt werden, danach nicht mehr abrufbar
  sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Administrator legt eine Person an und setzt ein initiales Passwort
    Given Administrator legt eine Person an und setzt ein initiales Passwort
    When  die Anfrage erfolgreich verarbeitet wird
    Then  ist der Passwort-Hash als `LocalCredential (type=password)` persistiert; der Klartext wird genau einmal angezeigt

  @AC2
  Scenario: AC2 – das initiale Passwort wurde gesetzt und angezeigt
    Given das initiale Passwort wurde gesetzt und angezeigt
    When  der Administrator die Admin-UI erneut öffnet oder die API erneut aufruft
    Then  ist der Passwort-Klartext nicht mehr abrufbar (nur Hash in der DB)

  @AC3
  Scenario: AC3 – das initiale Passwort ist gesetzt
    Given das initiale Passwort ist gesetzt
    When  die Person sich mit diesem Passwort anmeldet (UC-01 A5)
    Then  gelingt der Login und UC-03 (Required Action) wird ausgelöst, falls 2FA erzwungen ist

  @AC4
  Scenario: AC4 – das System verarbeitet die Passwort-Anfrage
    Given das System verarbeitet die Passwort-Anfrage
    When  Server-Logs geschrieben werden
    Then  ist der Passwort-Klartext nicht in den Logs enthalten

  @AC5
  Scenario: AC5 – System generiert automatisch ein Passwort
    Given System generiert automatisch ein Passwort
    When  es generiert wird
    Then  hat es mindestens 16 Zeichen und ausreichend Entropie (Groß/Klein/Ziffern/Sonderzeichen)
