# Derived from: requirements/req/req-034-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + inspection

@compliance @should @UC-04
Feature: REQ-034 – Audit log for metamodel changes

  The system MUST record every create, update, and delete operation on
  the `MetamodelConfiguration` (EntityTypeDefinition, Stereotype,
  ConstraintRule) in an audit log; each entry MUST contain at least:
  person ID of the changing person, timestamp (UTC), kind of change
  (create/update/delete), element type
  (entity-type/stereotype/constraint-rule), element name.

  @AC1
  Scenario: AC1 – Kurt creates a custom EntityType `SecurityZone` (basic flow UC-04)
    Given Kurt creates a custom EntityType `SecurityZone` (basic flow UC-04)
    When  the audit log is evaluated
    Then  it contains an entry with `eventType=metamodel.entity_type.created`, `elementName=SecurityZone`, Kurt's person ID, timestamp

  @AC2
  Scenario: AC2 – Kurt imports 3 EntityTypes via REQ-033
    Given Kurt imports 3 EntityTypes via REQ-033
    When  the import is completed
    Then  the audit log contains 3 entries, each with `importBatch=<same UUID>` – so the import is recognizable as a coherent operation

  @AC3
  Scenario: AC3 – Lukas (another architect) deletes a custom EntityType
    Given Lukas (another architect) deletes a custom EntityType
    When  the audit log is evaluated
    Then  the entry is attributed to Lukas' person ID (not Kurt's), `eventType=metamodel.entity_type.deleted`

  @AC4
  Scenario: AC4 – audit log infrastructure is temporarily unavailable
    Given audit log infrastructure is temporarily unavailable
    When  Kurt makes a change
    Then  the change is saved in the MetamodelConfiguration; the audit log entry is caught up asynchronously (no rollback of the change)
