## MODIFIED Requirements

### Requirement: Writes rejected in import-only

While a configuration is `import-only`, every write on its metatypes, mappings and – for the instance – property definitions, enum values, categories and labels SHALL be rejected with 403 and problem type `metamodel-locked`, regardless of the caller's permissions; nothing SHALL change. Package imports, re-import settings, the setting "Show parallel changes in the baseline" and the edit mode SHALL stay possible.

Trace: US-035 AC1, AC2, REQ-035, BR-06, US-044, owner decision 2026-11-13

#### Scenario: Creating a metatype in a locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator creates a metatype
- **THEN** the request is rejected with 403 `metamodel-locked` and no metatype is created

#### Scenario: Import in a locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator imports a package
- **THEN** the package is imported as usual

#### Scenario: Extension stays editable

- **GIVEN** the instance configuration is `import-only` and an extension is `gui-and-import`
- **WHEN** an architect creates a metatype in the extension
- **THEN** the metatype is created

#### Scenario: Parallel-change setting in a locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator turns on "Show parallel changes in the baseline" for `Application Component`
- **THEN** the setting is saved
