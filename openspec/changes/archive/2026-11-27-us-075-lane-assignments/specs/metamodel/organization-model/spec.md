## Purpose

Defines the instance setting that says which metatypes and connection types play the parts organizational unit, role and person, and how a package fills it, so that diagrams and objects can name who performs and who is responsible.

## ADDED Requirements

### Requirement: Organization model setting

The instance SHALL keep which element metatypes play organizational unit, role and person, and which connection metatypes play belongs-to, performs-role and responsible; each part MAY be unset. `metamodel:write` SHALL set them with `If-Match`, not in `import-only`; an entity reader SHALL read them. A change SHALL be audited as `metamodel.organization-model-updated`.

Trace: US-075, US-076, REQ-077, owner decisions 2026-11-27

#### Scenario: Change a part

- **WHEN** a metamodel administrator sets "Business Actor" as person
- **THEN** the setting names "Business Actor", and `metamodel.organization-model-updated` is audited with the part

#### Scenario: Wrong kind

- **WHEN** a connection metatype is set as role, or an element metatype as responsible
- **THEN** the request is rejected with 422 naming the part, and nothing changes

#### Scenario: Connection that does not reach the part

- **GIVEN** the organizational unit is "Organizational Unit"
- **WHEN** a connection metatype whose allowed targets exclude "Organizational Unit" and its base types is set as belongs-to, and then one with an empty target list
- **THEN** the first request is rejected with 422 naming `belongsTo`, and the second is accepted

#### Scenario: Stale version

- **WHEN** a metamodel administrator saves with a version another change has replaced, and then without `If-Match`
- **THEN** the first request is rejected with 412, the second with 428, and nothing changes

#### Scenario: Without metamodel:write

- **WHEN** a holder of `entity:write` only changes the setting
- **THEN** the request is rejected with 403, and the holder can still read the setting

#### Scenario: Locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator changes a part
- **THEN** the request is rejected with 403 `metamodel-locked`, and nothing changes

#### Scenario: Metatype deleted

- **WHEN** the metatype set as role is deleted
- **THEN** the role part is unset

### Requirement: Organization model in a package

A package MAY name its own metatypes per part in the section `organizationModel`; a metatype outside the package or of the wrong kind SHALL be rejected with 422 naming the part. The import SHALL fill only the parts that are unset, list them in the result, and audit them; it SHALL NOT overwrite a set part.

Trace: US-075, US-076, REQ-077, owner decisions 2026-11-27

#### Scenario: The import fills the setting

- **GIVEN** no part of the organization model is set
- **WHEN** `oea-starter-organization` is imported
- **THEN** all six parts name the package's types, and the import result lists the six parts

#### Scenario: The import keeps a set part

- **GIVEN** the instance set its own type "Department" as organizational unit
- **WHEN** `oea-starter-organization` is imported
- **THEN** the organizational unit stays "Department", and the other five parts name the package's types

#### Scenario: Metatype outside the package

- **WHEN** a package names a metatype of another package as role in `organizationModel`
- **THEN** the import is rejected with 422 naming `/organizationModel/role`, and nothing is stored
