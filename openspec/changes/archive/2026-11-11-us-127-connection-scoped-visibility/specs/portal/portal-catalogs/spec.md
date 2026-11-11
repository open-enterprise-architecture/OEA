# Delta for portal/portal-catalogs

## ADDED Requirements

### Requirement: Connection-scoped values in the portal

The portal query SHALL show connection-scoped values per row like the catalog query: only for entities in the reader's scope.

Trace: REQ-127, REQ-160

#### Scenario: Portal reader with a linked account

- **GIVEN** Petra's account is linked and her element is assigned to "ERP Warehouse"
- **WHEN** she queries a shared catalog with a column on `securityClassification`
- **THEN** only "ERP Warehouse" shows its classification
