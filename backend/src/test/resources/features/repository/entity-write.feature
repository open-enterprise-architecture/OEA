# Spec: openspec repository/entity-write
# Source material: requirements/req/REQ-149-property-validation-necessity.md; ADR-022, ADR-033

@US-141 @UC-22 @repository
Feature: Writing entities against the metamodel

  Entities are written against the metamodel: the metatype decides which
  properties exist, which values are valid and which are mandatory. Every
  change creates a new version with the previous values of the changed
  fields and is recorded in the audit trail.

  Background:
    Given an instance with the starter package, an enterprise architect and a viewer logged in through the IdP

  Scenario: Unknown metatype
    When the enterprise architect writes an entity "Unknown Type Entity" with an unknown metatype
    Then the item is rejected with the code "METATYPE_NOT_FOUND" for the field "metatypeId"

  Scenario: Metatype changed on update
    Given an "Application Component" "CRM System" with external ID "crm:changed-type" exists
    When the enterprise architect writes "CRM System" with external ID "crm:changed-type" as "Data Object"
    Then the item is rejected with the code "METATYPE_CHANGE_NOT_SUPPORTED" for the field "metatypeId"

  @REQ-149
  Scenario: Mandatory property missing
    When the enterprise architect writes a "Checked Type" "Without Version" without the property "Version"
    Then the item is rejected with the code "MANDATORY_MISSING" for the field "properties"

  @REQ-149 @AC2
  Scenario: Property with necessity warning missing
    When the enterprise architect writes a "Checked Type" "Without Owner" with the property "Version" "1.0"
    Then the item is created with a warning for the field "properties[Owner]"

  Scenario: Value outside the enum values
    When the enterprise architect writes an "Application Component" "ERP" with the property "Status" "unknown"
    Then the item is rejected with the code "INVALID_VALUE" for the field "properties[0].value"

  Scenario: Property not mapped to the metatype
    When the enterprise architect writes an "Application Component" "Intranet" with the property "Version" "2.0"
    Then the item is created with a warning with the code "PROPERTY_NOT_MAPPED"
    And "Intranet" has no value for "Version"

  Scenario: Entity changed
    Given an "Application Component" "Billing" with external ID "billing:1" and "Status" "active" exists
    When the enterprise architect writes "Billing Platform" with external ID "billing:1" and "Status" "retired"
    Then the entity "billing:1" has version 2
    And the history of version 2 of "billing:1" contains the previous name "Billing" and the previous "Status" "active" and nothing else

  Scenario: Unchanged write
    Given an "Application Component" "Archive" with external ID "archive:1" and "Status" "active" exists
    When the enterprise architect writes "Archive" with external ID "archive:1" and "Status" "active"
    Then the item is reported as "unchanged"
    And the entity "archive:1" has version 1 and one audit event

  @ADR-033
  Scenario: Created entity audited
    When the enterprise architect writes "CRM Suite" with external ID "crm:audited" and "Status" "active"
    Then the audit trail contains "entity.created" for "crm:audited" with the architect as actor, the new values "name=CRM Suite" and "Status=active" and a batch ID
    And the audit chain verifies

  @ADR-033
  Scenario: Write with agent headers
    When the enterprise architect writes "Scanner Result" with external ID "scan:agent" sending "X-Agent-Id" "repo-scanner" and "X-Agent-Run" "run-42"
    Then the audit event of "scan:agent" has the agent label "repo-scanner", the agent run "run-42" and no agent ID

  Scenario: Property with restricted visibility
    When the enterprise architect writes a "Checked Type" "Secretive" with the properties "Version" "1.0" and "Secret" "x"
    Then the item is rejected with the code "RESTRICTED_PROPERTY_NOT_SUPPORTED" for the field "properties[1].propertyDefId"
