## ADDED Requirements

### Requirement: Own password rules

A person with a local password SHALL read the rules a new password must meet: the minimum and maximum length and the required character classes of the current policy. Reading them SHALL be allowed while the session must change its password. A principal without a local password SHALL get 403.

Trace: REQ-028, REQ-173 AC2, architecture review 2026-11 API-6, issue #262

#### Scenario: Rules of the current policy

- **WHEN** the minimum length of the policy is 14 and a person reads the own password rules
- **THEN** the rules name the minimum length 14, the maximum length 128 and the required character classes

#### Scenario: Allowed during a required change

- **WHEN** a person whose session must change its password reads the own password rules
- **THEN** the rules are returned

#### Scenario: Without a local password

- **WHEN** a person who logs in only through the identity provider reads the own password rules
- **THEN** the request is answered with 403

### Requirement: Password form checks the current rules

The page "Change password" (SCR-004) SHALL check a new password against the rules read from the server before it sends it, and SHALL name each unmet rule. The server SHALL still check every rule.

Trace: REQ-028, architecture review 2026-11 API-6

#### Scenario: Length boundaries of the form

- **WHEN** the minimum length is 14
- **THEN** the form accepts a password of 14 characters, and names the length rule for 13 characters

#### Scenario: Missing character class

- **WHEN** the policy requires a digit and the new password has none
- **THEN** the form names the digit rule and sends nothing
