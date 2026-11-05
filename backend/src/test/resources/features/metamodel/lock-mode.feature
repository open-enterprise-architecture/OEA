# Spec: openspec metamodel/lock-mode
# Source material: requirements/user-stories/US-035-metamodel-lock-mode.md, REQ-035, US-037 AC1

@US-035 @UC-04 @metamodel
Feature: Metamodel lock mode

  An organization that maintains its metamodel in a versioned repository locks the
  instance metamodel: in `import-only`, only package imports change it. Extensions of
  solutions have their own edit mode.

  Background:
    Given a running repository instance with a metamodel administrator

  Scenario: Instance locked
    When the metamodel administrator sets the instance configuration to "import-only"
    Then the configurations show the instance configuration as "import-only"
    And the audit trail names the metamodel administrator for the edit mode change

  @AC1
  Scenario: Creating a metatype in a locked instance
    Given the instance configuration is "import-only"
    When the metamodel administrator creates a metatype in the instance
    Then the request is rejected with 403 "metamodel-locked" and no metatype is created

  @AC2
  Scenario: Import in a locked instance
    Given the instance configuration is "import-only"
    When the metamodel administrator imports a package
    Then the package is imported as usual

  @US-037
  Scenario: Extension stays editable
    Given the instance configuration is "import-only" and an extension is "gui-and-import"
    When the architect creates a metatype in the extension
    Then the metatype is created
