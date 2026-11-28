## ADDED Requirements

### Requirement: Connections of connections one level deep

A new connection SHALL NOT have as source or target a connection whose own source or target is a connection; such a write through `createConnection` or the batch SHALL be rejected with 422 `CONNECTION_DEPTH_EXCEEDED` naming the endpoint, whatever the endpoint lists allow. Existing connections SHALL stay.

Trace: US-064 AC4, REQ-061, ADR-010

#### Scenario: Connection at a carrying connection

- **GIVEN** the "Carries Data" connection 103 from the data flow 5 to "Customer Master"
- **WHEN** an architect writes a "Realized By" connection, whose endpoint lists are empty, with the source 103
- **THEN** it is rejected with 422 `CONNECTION_DEPTH_EXCEEDED` naming the source, and nothing is stored

#### Scenario: Target at a carrying connection

- **WHEN** a batch writes a connection with an element as source and the connection 103 as target
- **THEN** the item is rejected with `CONNECTION_DEPTH_EXCEEDED` naming the target

#### Scenario: One level stays allowed

- **WHEN** an architect writes "Carries Data" from the data flow 5, whose endpoints are elements, to "Customer Master"
- **THEN** the connection is stored
