# Spec Delta

## MODIFIED Requirements

### Requirement: Organization mappings at a re-import

A re-import SHALL replace the package rows of its mappings and keep or remove the organization entries of each re-imported metatype – also those on package mappings – by its effective re-import setting: own value, else that of its base types, else the global setting (default `keep`). On a shared date the organization entry wins.

Trace: owner decisions 2026-10-30 and 2026-10-31, ADR-002, owner decision 2026-10-31 (US-137)

#### Scenario: Default keeps organization mappings

- **WHEN** the package is imported again after an administrator mapped the organization property `Cost Center` to one of its metatypes, with no re-import setting changed
- **THEN** the mappings of the package are replaced and the mapping of `Cost Center` stays

#### Scenario: Removal inherited from the base type

- **WHEN** the global setting is `keep`, an imported base type has the setting `remove`, and the package is imported again
- **THEN** the organization mappings of the base type and of its imported subtypes with the setting `inherit` are removed, and those of subtypes with `keep` stay

#### Scenario: Override of a package necessity kept

- **WHEN** an administrator made the package property `Status` mandatory for an imported metatype from 2026-07-01 and the package is imported again with the setting `keep`
- **THEN** the package rows of `Status` are replaced and the organization entry of 2026-07-01 stays in effect
