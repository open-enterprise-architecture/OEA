# Spec Delta

## MODIFIED Requirements

### Requirement: Mapping a property to a metatype

A principal with `metamodel:write` SHALL map an organization property definition to a metatype with a necessity (`mandatory`, `warning`, `optional`), a valid-from date and a sort order. A property already mapped in the base type chain of the metatype or its subtypes SHALL be rejected with 409, unless it is a further dated entry on the same metatype. Audited as `metamodel.mapping-created`.

Trace: UC-04 step 6, REQ-032 AC1, REQ-149, US-137

#### Scenario: Mandatory property for new entities

- **WHEN** a metamodel administrator maps `Trust Level` as mandatory to `SecurityZone` and a new `SecurityZone` is written without it
- **THEN** the new entity is rejected with 422 and code `MANDATORY_MISSING`

### Requirement: Marking entities without a new mandatory property

An entry making a property mandatory SHALL succeed regardless of existing entities and, once in effect, SHALL mark every entity of the metatype and its subtypes without a value, stating their number in the response or audit event. A mark SHALL be cleared when the entity gets a value or the property is removed or no longer mandatory.

Trace: REQ-149 AC1, owner decision 2026-10-31, owner decision 2026-10-31 (US-137)

#### Scenario: Existing entities marked

- **WHEN** a metamodel administrator maps `Trust Level` as mandatory to `SecurityZone`, which has five entities without a value
- **THEN** the mapping is stored, the response states five marked entities, and the five entities are marked as missing `Trust Level`

### Requirement: Extending imported and built-in metatypes

Organization property definitions SHALL be mapped to imported and built-in metatypes as well; the metatype stays unchanged. Package mappings SHALL NOT be changed or removed by administrators, but organization entries with a valid-from date MAY override their necessity.

Trace: owner decision 2026-10-30, ADR-002, owner decision 2026-10-31 (US-137)

#### Scenario: Own property on an imported metatype

- **WHEN** a metamodel administrator maps the organization property `Cost Center` as optional to the imported `Application Component`
- **THEN** reading `Application Component` lists `Status` from the package and `Cost Center` with `scope=organization`

### Requirement: Removing a mapping

A principal with `metamodel:write` SHALL remove an organization entry at any time. When the metatype then no longer has the property, its values SHALL be deleted from the entities of the metatype and its subtypes, each getting a new version. The removal SHALL be audited as `metamodel.mapping-deleted` with the number of affected entities.

Trace: REQ-032 AC4, owner decision 2026-10-31, US-137

#### Scenario: Mapping with values removed

- **WHEN** a metamodel administrator removes the mapping of `Cost Center` from `Application Component` while three entities have a value for it
- **THEN** the mapping is gone, the three entities have a new version without `Cost Center`, and an audit event `metamodel.mapping-deleted` names three entities

#### Scenario: Removing one of several entries

- **WHEN** a metamodel administrator removes the entry of 2026-07-01 that made `Version` mandatory for `Application Component`, while the entry of 2026-01-01 remains
- **THEN** the values of `Version` stay, `Version` is optional again and the marks of `Version` are cleared

## ADDED Requirements

### Requirement: Necessity by effective date

A principal with `metamodel:write` SHALL add an entry with a necessity and a valid-from date, also in the past, to a mapping of the metatype, including a package mapping; existing entries stay unchanged. The entry with the latest date not after today SHALL apply. A second entry with the same date SHALL be rejected with 409.

Trace: US-137 AC1, US-137 AC2, REQ-148, ADR-022, owner decision 2026-10-31

#### Scenario: Mandatory from a date

- **WHEN** `Version` is mapped as optional to `Application Component` since 2026-01-01 and a metamodel administrator adds an entry with necessity `mandatory` from 2026-07-01
- **THEN** both entries are stored, and from 2026-07-01 reading `Application Component` returns `Version` as mandatory

#### Scenario: Same date twice

- **WHEN** a metamodel administrator adds a second entry for `Version` on `Application Component` with the date 2026-07-01
- **THEN** the request is rejected with 409 and problem type `conflict`, and no entry is added

#### Scenario: History of the entries

- **WHEN** a metamodel administrator lists the mappings of `Application Component` with `includeHistory=true`
- **THEN** both entries of `Version` are returned with the states `past` and `active` or `active` and `future`

### Requirement: Entries taking effect

An entry SHALL take effect on its date without an administrator action, at the latest ten minutes after the start of the day (UTC); an entry dated today or earlier takes effect when created. Taking effect SHALL set or clear marks as for a new mapping and be audited as `metamodel.mapping-effective` with the number of marked entities.

Trace: REQ-148 AC1, REQ-149 AC1, owner decision 2026-10-31

#### Scenario: Entities marked on the date

- **WHEN** an entry makes `Version` mandatory for `Application Component` from tomorrow, and two entities have no value
- **THEN** the entities are not marked today, are marked once tomorrow begins, and an audit event `metamodel.mapping-effective` names two entities
