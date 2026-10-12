# Ableitung aus: requirements/req/req-030-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @should @UC-03
Feature: REQ-030 – Mehrere aktive TOTP-Credentials pro Person

  Das System MUSS es einer Person erlauben, gleichzeitig mehrere aktive
  TOTP-Credentials (`LocalCredential.type=totp`) zu hinterlegen; beim
  Login (REQ-010) MUSS ein gültiger TOTP-Code aus irgendeinem der
  aktiven TOTP-Credentials der Person akzeptiert werden; jedes
  TOTP-Credential MUSS ein nutzer-vergebbares Label tragen, damit die
  Person ihre Credentials voneinander unterscheiden kann.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Person hat zwei aktive TOTP-Credentials (unterschiedliche Secrets, unter...
    Given Person hat zwei aktive TOTP-Credentials (unterschiedliche Secrets, unterschiedliche Geräte)
    When  ein gültiger TOTP-Code aus dem zweiten Credential eingegeben wird
    Then  ist der Login erfolgreich; `lastUsedAt` des zweiten Credentials wird aktualisiert

  @AC2
  Scenario: AC2 – Person hat ein bestehendes aktives TOTP-Credential
    Given Person hat ein bestehendes aktives TOTP-Credential
    When  sie über UC-03 A3 ein weiteres TOTP-Credential einrichtet
    Then  hat sie danach zwei aktive TOTP-Credentials; das erste ist nicht revoked

  @AC3
  Scenario: AC3 – zwei aktive TOTP-Credentials
    Given zwei aktive TOTP-Credentials
    When  die Person ihre Sicherheitseinstellungen aufruft
    Then  werden beide Credentials mit ihrem Label und `createdAt`/`lastUsedAt` angezeigt

  @AC4
  Scenario: AC4 – Person richtet ein TOTP ohne Label ein
    Given Person richtet ein TOTP ohne Label ein
    When  das Credential angelegt wird
    Then  erhält es ein sinnvolles Default-Label (z.B. "TOTP-Authenticator" + Datum)

  @AC5
  Scenario: AC5 – zwei aktive TOTP-Credentials
    Given zwei aktive TOTP-Credentials
    When  ein ungültiger Code (keinem der Credentials zuordenbar) eingegeben wird
    Then  schlägt der Login fehl; keine Information darüber, welches Credential nicht getroffen wurde (kein Leak)
