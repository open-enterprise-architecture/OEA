# Spec Delta

## MODIFIED Requirements

### Requirement: Reading the metatypes

A principal with `entity:read` or `metamodel:write` SHALL list the metatypes of the instance and read one metatype, each with name, abbreviation, scope, origin package and version, its base type, its own and effective re-import setting, and its own and inherited property mappings in effect today with scope, property definition, data type, enum values and necessity.

Trace: UC-04 (step 2), ADR-002, ADR-022, ADR-032, owner decision 2026-10-31, US-137

#### Scenario: Metatypes after the starter import

- **WHEN** a person with `entity:read` lists the metatypes after `oea-starter-togaf-classic` was imported
- **THEN** seven metatypes are returned with `scope=imported` and origin `oea-starter-togaf-classic`, and `Application Component` maps the property `Status` with its four values

#### Scenario: Reading without permission

- **WHEN** a person without any role lists the metatypes
- **THEN** the request is rejected with 403
