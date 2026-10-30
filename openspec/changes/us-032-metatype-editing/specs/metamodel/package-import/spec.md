# Spec Delta

## MODIFIED Requirements

### Requirement: Atomic import with scope and origin

A valid package SHALL be imported in one transaction: new metatypes and property definitions get `scope=imported` and the package as origin; content the same package imported before is updated, keeping organization mappings; if any element cannot be imported, nothing is stored. Only a person with `metamodel:write` SHALL import packages.

Trace: US-033 AC1, REQ-114, ADR-002, ADR-032, owner decision 2026-10-30

#### Scenario: Package with five metatypes

- **WHEN** a person with `metamodel:write` imports a package with five new metatypes and their property definitions
- **THEN** all five metatypes exist with `scope=imported` and the package as origin, and the result reports five created metatypes

#### Scenario: Re-import of the same package

- **WHEN** the same package is imported again with a changed metatype name
- **THEN** the metatype is updated in place, no duplicate is created, and the result reports one updated metatype

#### Scenario: Import without permission

- **WHEN** a principal without `metamodel:write` imports a package
- **THEN** the request is rejected with 403 and nothing is stored

#### Scenario: Re-import keeps organization mappings

- **WHEN** the package is imported again after an administrator mapped the organization property `Cost Center` to one of its metatypes
- **THEN** the mappings of the package are replaced and the mapping of `Cost Center` stays
