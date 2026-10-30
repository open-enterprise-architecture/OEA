# Spec Delta

## MODIFIED Requirements

### Requirement: Atomic import with scope and origin

A valid package SHALL be imported in one transaction: new metatypes and property definitions get `scope=imported` and the package as origin; a base type SHALL be a metatype of the package or a built-in one; content the same package imported before is updated; if any element cannot be imported, nothing is stored. Only a person with `metamodel:write` SHALL import packages.

Trace: US-033 AC1, REQ-114, ADR-002, ADR-032, owner decision 2026-10-31

#### Scenario: Package with five metatypes

- **WHEN** a person with `metamodel:write` imports a package with five new metatypes and their property definitions
- **THEN** all five metatypes exist with `scope=imported` and the package as origin, and the result reports five created metatypes

#### Scenario: Re-import of the same package

- **WHEN** the same package is imported again with a changed metatype name
- **THEN** the metatype is updated in place, no duplicate is created, and the result reports one updated metatype

#### Scenario: Import without permission

- **WHEN** a principal without `metamodel:write` imports a package
- **THEN** the request is rejected with 403 and nothing is stored

## ADDED Requirements

### Requirement: Organization mappings at a re-import

A re-import SHALL replace the mappings of the package and SHALL keep or remove the organization mappings of each re-imported metatype by its effective re-import setting: its own value `keep` or `remove`, else that of its base types, else the global setting (default `keep`). A removal SHALL delete values as removing a mapping does. Settings are changed with `metamodel:write`, audited.

Trace: owner decisions 2026-10-30 and 2026-10-31, ADR-002

#### Scenario: Default keeps organization mappings

- **WHEN** the package is imported again after an administrator mapped the organization property `Cost Center` to one of its metatypes, with no re-import setting changed
- **THEN** the mappings of the package are replaced and the mapping of `Cost Center` stays

#### Scenario: Removal inherited from the base type

- **WHEN** the global setting is `keep`, an imported base type has the setting `remove`, and the package is imported again
- **THEN** the organization mappings of the base type and of its imported subtypes with the setting `inherit` are removed, and those of subtypes with `keep` stay
