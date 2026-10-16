# Spec: openspec metamodel/package-import
# Source material: requirements/tests/REQ-033-*.feature, REQ-114, REQ-184

@US-033 @UC-04 @metamodel
Feature: Import of metamodel packages

  A metamodel administrator imports a package of metatypes and properties in
  the portable JSON format. The package is checked before anything is stored;
  its content is marked as imported with the package as origin, a re-import
  updates it, content of others is never overwritten, and every import is
  audited with one batch ID.

  Background:
    Given a running instance with a metamodel administrator logged in through the IdP

  @REQ-033
  Scenario: Syntactically faulty package
    When the metamodel administrator imports a file that is not valid JSON
    Then the import is rejected with 422 naming line and column
    And no metatype of the package exists

  @REQ-184
  Scenario: Package violating the schema
    When the metamodel administrator imports the package "schema-violation" with a metatype without a name
    Then the import is rejected with 422 pointing to "/metatypes/0/name"
    And no metatype of the package "schema-violation" exists

  @REQ-184 @AC2
  Scenario: Package above the size limit
    When the metamodel administrator imports the package "too-large" with more than 64 KB
    Then the import is rejected with 413
    And no metatype of the package "too-large" exists

  Scenario: Unsupported package sections
    When the metamodel administrator imports the package "with-viewpoints" with translation bundles
    Then the import is rejected with 422 as unsupported content
    And no metatype of the package "with-viewpoints" exists

  @US-033 @AC1
  Scenario: Package with five metatypes
    When the metamodel administrator imports the package "five-types" with 5 metatypes
    Then 5 metatypes are created with scope "imported" and the origin "five-types" 1.0.0
    And the audit trail contains the package import and 5 metatype imports with the same batch ID

  @REQ-114
  Scenario: Re-import of the same package
    Given the metamodel administrator has imported the package "reimport" with 3 metatypes
    When the metamodel administrator imports the package "reimport" again with one metatype renamed
    Then the result reports 0 created and 1 updated metatypes
    And the package "reimport" has 3 metatypes

  @ADR-032
  Scenario: Import without permission
    When a person without role imports the package "no-permission" with 1 metatypes
    Then the import is rejected with 403
    And no metatype of the package "no-permission" exists

  @REQ-114
  Scenario: Name already used by another package
    Given the metamodel administrator has imported the package "first-owner" with 2 metatypes
    When the metamodel administrator imports the package "second-owner" whose second metatype is named "first-owner Type 2"
    Then the import is rejected with 409 listing "metatype first-owner Type 2" as conflict with another package
    And no metatype of the package "second-owner" exists
