# Spec: openspec metamodel/change-history
# Source material: requirements/user-stories/US-034-audit-log-metamodel.md, REQ-034

@US-034 @UC-04 @metamodel
Feature: Metamodel change history

  Several administrators maintain the metamodel. Every change is written to the
  audit trail together with the change itself; metamodel administrators read these
  events with the names of the persons, filtered by kind, person, period and
  import batch.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1
  Scenario: Two administrators changed the metamodel
    Given Kurt created the metatype "SecurityZone" and Lukas deleted another metatype
    When Kurt lists the metamodel changes
    Then he sees both entries with the names of Kurt and Lukas, their times and the element names

  Scenario: Without metamodel administration
    When a person holding only viewer lists the metamodel changes
    Then the change list is rejected with 403

  @AC2
  Scenario: An import as one batch
    Given a package with 5 metatypes was imported
    When an administrator lists the metamodel changes of its batch
    Then the list shows the 5 imported metatypes with the same batch ID

  Scenario: Changes of one person
    Given Kurt created the metatype "SecurityZone" and Lukas deleted another metatype
    When an administrator filters the metamodel changes by Lukas
    Then only the changes made by Lukas are listed

  @AC3
  Scenario: Audit trail not writable
    Given the audit event of a change cannot be written
    When an administrator creates a metatype
    Then the request fails and no metatype is created
