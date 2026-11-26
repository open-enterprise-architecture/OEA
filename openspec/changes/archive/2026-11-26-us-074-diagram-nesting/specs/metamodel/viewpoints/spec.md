## ADDED Requirements

### Requirement: Ask for a new parent per viewpoint

A viewpoint SHALL store `askNewParent` as true, false or null; null means the global setting applies. A write SHALL replace it like the other fields; an omitted value stores null. The package section `viewpoints` SHALL carry it for system-defined viewpoints, which stay read-only. SCR-031 SHALL show it as "Ask 'New parent?'" with "Global setting", "Ask" and "Do not ask".

Trace: owner decisions 2026-11-26

#### Scenario: Override stored

- **WHEN** Kurt sets "Ask" for "Cloud Security View"
- **THEN** the viewpoint returns `askNewParent` true with a new version, and `viewpoint.updated` names `askNewParent`

#### Scenario: Back to the global setting

- **WHEN** Kurt sets "Global setting" for "Cloud Security View"
- **THEN** the viewpoint returns `askNewParent` null

#### Scenario: Omitted value

- **GIVEN** "Cloud Security View" stores false
- **WHEN** an API call replaces the viewpoint without `askNewParent`
- **THEN** the viewpoint returns null, and the global setting applies

#### Scenario: From a package

- **WHEN** a package with a viewpoint carrying `askNewParent` false is imported
- **THEN** the system-defined viewpoint returns false, and it cannot be edited
