## ADDED Requirements

### Requirement: Parallel-change setting on the metatype detail

The detail of a metatype SHALL show the switch "Show parallel changes in the baseline" with a short explanation. A principal with `metamodel:write` SHALL change it for every metatype, also built-in and imported ones and while the metamodel is locked; others SHALL see the value read-only.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Switching on a built-in metatype

- **WHEN** an administrator turns on the switch of the built-in metatype `Application Component`
- **THEN** the switch stays on after reloading the configuration

#### Scenario: Read-only without metamodel administration

- **WHEN** a person with `solution-metamodel:write` only opens the instance metatype `Application Component`
- **THEN** the setting is shown as text, without a switch
