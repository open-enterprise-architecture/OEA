## Purpose

Defines the instance setting that says which metatypes play data object, data flow, carries-data, data input and data output in the data lineage, and how packages fill it.

## ADDED Requirements

### Requirement: Lineage model setting

The instance SHALL keep which element metatype plays data object and which connection metatypes play data flow, carries-data, data input and data output; each part MAY be unset. `metamodel:write` SHALL set them with `If-Match`, not in `import-only`; an entity reader SHALL read them. Changes SHALL be audited as `metamodel.lineage-model-updated`; a deleted metatype SHALL unset its part.

Trace: US-064, US-065, REQ-062, owner decisions 2026-11-28

#### Scenario: Change a part

- **WHEN** a metamodel administrator sets the own type "Interface" as data flow
- **THEN** the setting names "Interface", and `metamodel.lineage-model-updated` is audited with the part

#### Scenario: Wrong kind

- **WHEN** an element metatype is set as carries-data, or a connection metatype as data object
- **THEN** the request is rejected with 422 naming the part, and nothing changes

#### Scenario: Carries-data that does not start at the data flow

- **GIVEN** the data flow is "Data Flow"
- **WHEN** a connection metatype whose allowed sources exclude "Data Flow" and its base types is set as carries-data, and then one with an empty source list
- **THEN** the first request is rejected with 422 naming `carriesData`, and the second is accepted

#### Scenario: Data output that does not reach the data object

- **GIVEN** the data object is "Data Object"
- **WHEN** a connection metatype whose allowed targets exclude "Data Object" is set as data output
- **THEN** the request is rejected with 422 naming `dataOutput`, and nothing changes

#### Scenario: Without metamodel:write

- **WHEN** a holder of `entity:read` only changes the setting
- **THEN** the request is rejected with 403, nothing is audited, and the holder can still read the setting

#### Scenario: Stale version and locked instance

- **WHEN** a save carries a replaced version, no `If-Match`, or the instance is `import-only`
- **THEN** it is rejected with 412, 428 or 403 `metamodel-locked`, and nothing changes

#### Scenario: Metatype deleted

- **WHEN** the metatype set as data flow is deleted
- **THEN** the data flow part is unset

#### Scenario: Unchanged write

- **WHEN** a metamodel administrator saves the setting without a change
- **THEN** the version stays, and nothing is audited

### Requirement: Lineage model in a package

A package MAY name its own metatypes per part in the section `lineageModel`; a metatype outside the package or of the wrong kind SHALL be rejected with 422 naming the part. The import SHALL fill only unset parts, list them in the result and audit them.

Trace: US-064, REQ-081 AC6, owner decisions 2026-11-28

#### Scenario: TOGAF and BPMN fill their parts

- **GIVEN** no part is set
- **WHEN** `oea-starter-togaf-classic` 1.5.0 and `oea-starter-bpmn` 1.2.0 are imported
- **THEN** data object, data flow and carries-data name the TOGAF types, data input and data output the BPMN data associations, the import results list the filled parts, and each filling is audited with the cause `package-import`

#### Scenario: A set part is kept

- **GIVEN** the instance set "Interface" as data flow
- **WHEN** `oea-starter-togaf-classic` 1.5.0 is imported
- **THEN** the data flow stays "Interface", and the other parts name the TOGAF types

#### Scenario: Metatype outside the package

- **WHEN** a package names a metatype of another package as data flow in `lineageModel`
- **THEN** the import is rejected with 422 naming `/lineageModel/dataFlow`, and nothing is stored
