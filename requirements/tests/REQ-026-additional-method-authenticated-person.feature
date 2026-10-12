# Ableitung aus: requirements/req/req-026-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-03
Feature: REQ-026 – Weitere Authentifizierungsmethode für eingeloggte Person einrichten

  Das System SOLL einer bereits authentifizierten Person ermöglichen,
  über die eigenen Sicherheitseinstellungen eine weitere lokale
  Authentifizierungsmethode einzurichten, ohne dafür ein
  Enrollment-Token zu benötigen; die Session MUSS dabei als
  Authentifizierungsnachweis dienen.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Kurt ist eingeloggt und hat noch keine TOTP-Methode eingerichtet
    Given Kurt ist eingeloggt und hat noch keine TOTP-Methode eingerichtet
    When  er in den Sicherheitseinstellungen TOTP hinzufügt und die Einrichtung abschließt
    Then  ist TOTP als zusätzliche Methode persistiert; kein Enrollment-Token wurde benötigt

  @AC2
  Scenario: AC2 – Kurt ist eingeloggt und möchte einen zweiten Passkey hinzufügen
    Given Kurt ist eingeloggt und möchte einen zweiten Passkey hinzufügen
    When  er die Passkey-Registrierung durchführt
    Then  ist der neue Public Key persistiert; beide Passkeys sind für den Login verwendbar

  @AC3
  Scenario: AC3 – Kurt hat bereits Passkey, TOTP und Passwort eingerichtet
    Given Kurt hat bereits Passkey, TOTP und Passwort eingerichtet
    When  er die Sicherheitseinstellungen öffnet
    Then  zeigt das System an, dass alle Methoden bereits eingerichtet sind (kein Fehler)

  @AC4
  Scenario: AC4 – eine Anfrage an den Enrollment-Endpunkt ohne gültige Session
    Given eine Anfrage an den Enrollment-Endpunkt ohne gültige Session
    When  die Anfrage eingeht
    Then  wird sie abgelehnt; Weiterleitung zum Login

  @AC5
  Scenario: AC5 – eine Anfrage ohne CSRF-Token (bei POST-Formular-basiertem Flow)
    Given eine Anfrage ohne CSRF-Token (bei POST-Formular-basiertem Flow)
    When  die Anfrage eingeht
    Then  wird sie mit einem entsprechenden Fehler abgelehnt
