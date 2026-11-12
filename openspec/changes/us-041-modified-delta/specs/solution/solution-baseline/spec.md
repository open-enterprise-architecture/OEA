# Delta for solution/solution-baseline

## ADDED Requirements

### Requirement: Read one entry of the baseline

A principal with `entity:read` SHALL read one entity of the baseline of a solution with its name, description, metatype, version and its property values. A value the reader may not read SHALL be redacted. An ID that is not an entity of the baseline (unknown, deleted or a connection) and an unknown solution SHALL answer 404.

Trace: US-041 AC2, REQ-039, owner decision 2026-11-12

#### Scenario: Entry with its values

- **GIVEN** `ERP-Core` is in the baseline with "Release" `v4` and "Users" `250`
- **WHEN** a solution architect reads the entry `ERP-Core` of the baseline
- **THEN** it holds the name, the description, the metatype, the version and "Release" `v4` and "Users" `250`

#### Scenario: Restricted value redacted

- **GIVEN** `ERP-Core` has a value of the `role-restricted` property "Cost"
- **WHEN** a principal without a read role of "Cost" reads the entry
- **THEN** "Cost" is redacted, without its value

#### Scenario: Not an entry of the baseline

- **WHEN** an unknown entity ID, a deleted entity or a data flow is read as an entry of the baseline
- **THEN** the response is 404

#### Scenario: Without entity:read

- **WHEN** a principal without `entity:read` reads an entry of the baseline
- **THEN** the response is 403
