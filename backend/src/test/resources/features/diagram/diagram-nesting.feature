# Spec: openspec diagram/diagram-nesting (us-074-diagram-nesting)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; US-135; UC-10; owner decisions 2026-11-20 and 2026-11-26
# The editor scenarios (the children question and its answers, "Show children" in the context menu, "New parent?",
# the palette drop into a container, the confirmation of removing a container, the read-only canvas) are covered by the
# frontend specs; this feature covers the API.

@US-074 @US-135 @UC-10 @diagram
Feature: Diagram nesting

  A shape may lie in a container shape of the same diagram. The children of an
  entity are listed for the editor, hierarchy lines show a child placed
  elsewhere, parent changes are saved with the diagram, and the setting "Ask
  for a new parent" is stored globally and per viewpoint.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And a nesting viewpoint "Landscape" allowing "Application Component" and "Data Object" and the connection type "Composition"
    And a nesting diagram "Order to Cash" in the nesting viewpoint "Landscape"
    And the nesting setting "Ask for a new parent" is reset

  # Requirement: Nested shapes

  Scenario: Nested shape saved
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft nests "Billing" in "SAP ERP" at 10, 10 with 90 x 30 pt
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting view of "Order to Cash" shows "Billing" nested in "SAP ERP"
    And the nesting entity "Billing" has no parent
    And the nesting entity "Billing" has the version 1

  Scenario Outline: Container invalid
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft shows "CRM" at 10, 10 with 90 x 30 pt
    And the draft names the container "<container>" for the shape of "<shape>"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "<field>"
    And the nesting view of "Order to Cash" has no shapes

    Examples:
      | container   | shape       | field                   |
      | unknown     | SAP ERP     | shapes[0].parentShapeId |
      | CRM         | SAP ERP     | shapes[0].parentShapeId |
      | CRM         | CRM         | shapes[1].parentShapeId |

  Scenario: Container cycle
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft shows "CRM" at 10, 10 with 90 x 30 pt
    And the draft names the container "CRM" for the shape of "SAP ERP"
    And the draft names the container "SAP ERP" for the shape of "CRM"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "shapes[0].parentShapeId"
    And the nesting view of "Order to Cash" has no shapes

  Scenario Outline: Containment boundary
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 100 x 100 pt
    And the draft nests "Billing" in "SAP ERP" at <x>, <y> with <width> x <height> pt
    When the enterprise architect saves the draft
    Then the nesting answer is <status>

    Examples:
      | x     | y  | width  | height | status |
      | 0     | 0  | 100    | 100    | 200    |
      | 10    | 20 | 90     | 80     | 200    |
      | 0     | 0  | 100.01 | 100    | 422    |
      | 0     | 0  | 100    | 100.01 | 422    |
      | -0.01 | 0  | 50     | 50     | 422    |
      | 0     | -0.01 | 50  | 50     | 422    |

  Scenario Outline: Nesting depth
    Given the nesting entity "Deep" of the metatype "Application Component" exists
    And the draft of "Order to Cash" is a chain of <levels> shapes of "Deep", each nested in the one before
    When the enterprise architect saves the draft
    Then the nesting answer is <status>

    Examples:
      | levels | status |
      | 10     | 200    |
      | 11     | 422    |

  Scenario: Container removed
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" exists
    And the nesting entity "Invoicing" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 400 x 200 pt
    And the draft nests "Billing" in "SAP ERP" at 10, 10 with 90 x 30 pt
    And the draft nests "Invoicing" in "SAP ERP" at 120, 10 with 90 x 30 pt
    And the enterprise architect has saved the draft
    When the draft of "Order to Cash" is emptied and saved
    Then the nesting answer is 200
    And the nesting view of "Order to Cash" has no shapes
    And the nesting entities "SAP ERP", "Billing" and "Invoicing" exist

  Scenario: Container of a deleted entity
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft nests "Billing" in "SAP ERP" at 10, 10 with 90 x 30 pt
    And the enterprise architect has saved the draft
    And the nesting entity "SAP ERP" is deleted
    When the viewer opens the nesting view of "Order to Cash"
    Then the nesting view of "Order to Cash" has no shape of "SAP ERP"
    And the nesting view of "Order to Cash" shows "Billing" without a container

  # Requirement: Children of a placed entity

  Scenario: Children listed
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Invoicing" of the metatype "Application Component" is a child of "SAP ERP"
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    When the viewer lists the children of "SAP ERP" in "Order to Cash"
    Then the nesting answer is 200
    And the children are "Billing" and "Invoicing", all allowed, not truncated

  Scenario: Only direct children
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the nesting entity "Dunning" of the metatype "Application Component" is a child of "Billing"
    When the viewer lists the children of "SAP ERP" in "Order to Cash"
    Then the children are "Billing", all allowed, not truncated
    And the child "Billing" has 1 child

  Scenario: Type not allowed
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "ERP Database" of the metatype "Technology Component" is a child of "SAP ERP"
    When the viewer lists the children of "SAP ERP" in "Order to Cash"
    Then the child "ERP Database" is not allowed

  Scenario Outline: Children list limits
    Given the nesting entity "Big ERP" of the metatype "Application Component" exists
    And the nesting entity "Big ERP" has <count> children
    When the viewer lists the children of "Big ERP" in "Order to Cash"
    Then the list has <items> items and truncated is <truncated>

    Examples:
      | count | items | truncated |
      | 500   | 500   | false     |
      | 501   | 500   | true      |

  Scenario Outline: Children of an unknown entity
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    When the viewer lists the children of <target> in "Order to Cash"
    Then the nesting answer is 404

    Examples:
      | target                |
      | an unknown entity     |
      | a deleted entity      |
      | a connection entity   |

  Scenario: Children of an unknown diagram
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    When the viewer lists the children of "SAP ERP" in an unknown diagram
    Then the nesting answer is 404

  Scenario: Children without entity:read
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    When the metamodel administrator lists the children of "SAP ERP" in "Order to Cash"
    Then the nesting answer is 403

  # Requirement: Hierarchy line to a child placed elsewhere

  Scenario: Hierarchy line
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft shows "Billing" at 300, 0 with 90 x 30 pt
    And the draft adds a hierarchy line from the shape of "SAP ERP" to the shape of "Billing"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting view of "Order to Cash" has a hierarchy line from "SAP ERP" to "Billing"
    And the nesting view of "Order to Cash" has 1 shape of "Billing"

  Scenario: Composition connection instead
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    When the viewer lists the children of "SAP ERP" in "Order to Cash"
    Then the child "Billing" has the composition connection of the pair, allowed by the viewpoint

  Scenario: Line without hierarchy
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft shows "CRM" at 300, 0 with 90 x 30 pt
    And the draft adds a hierarchy line from the shape of "SAP ERP" to the shape of "CRM"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "hierarchyLines[0]"
    And the nesting view of "Order to Cash" has no shapes

  Scenario: Line valid through a parent change
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft shows "CRM" at 300, 0 with 90 x 30 pt
    And the draft adds a hierarchy line from the shape of "SAP ERP" to the shape of "CRM"
    And the draft changes the parent of "CRM" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting view of "Order to Cash" has a hierarchy line from "SAP ERP" to "CRM"

  Scenario: Hierarchy changed later
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Cloud" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft shows "Billing" at 300, 0 with 90 x 30 pt
    And the draft adds a hierarchy line from the shape of "SAP ERP" to the shape of "Billing"
    And the enterprise architect has saved the draft
    When the nesting entity "Billing" is moved under "Cloud" in the browser
    Then the nesting view of "Order to Cash" has no hierarchy line

  # Requirement: New parent on moving a shape

  Scenario: New parent confirmed
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft nests "CRM" in "SAP ERP" at 10, 10 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "CRM" has the parent "SAP ERP" and the version 2
    And the audit log holds "entity.parent-changed" for the nesting entity "CRM"

  Scenario: New parent declined
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft nests "CRM" in "SAP ERP" at 10, 10 with 90 x 30 pt
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "CRM" has no parent

  Scenario: Moved out
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the draft of "Order to Cash" shows "Billing" at 0, 0 with 90 x 30 pt
    And the draft clears the parent of "Billing"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "Billing" has no parent

  Scenario: Parent change to the current parent
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the draft of "Order to Cash" shows "Billing" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "Billing" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "Billing" has the parent "SAP ERP" and the version 2

  Scenario: Parent change rejected for a cycle
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "Billing" of the metatype "Application Component" is a child of "SAP ERP"
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft shows "Billing" at 300, 0 with 90 x 30 pt
    And the draft changes the parent of "SAP ERP" to "Billing"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "parentChanges[0].parentEntityId"
    And the nesting entity "SAP ERP" has no parent

  Scenario: Parent change rejected for a stale version
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "CRM" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP" with the version 7
    When the enterprise architect saves the draft
    Then the nesting answer is 409
    And the nesting entity "CRM" has no parent

  Scenario: Parent change rejected for an open issue
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the nesting entity "CRM" has an open issue of a solution
    And the draft of "Order to Cash" shows "CRM" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 409
    And the nesting entity "CRM" has no parent

  Scenario Outline: Hierarchy depth of a parent change
    Given a chain of <levels> nesting entities named "Level" exists
    And the nesting entity "Leaf" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "Leaf" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "Leaf" to the last of the chain
    When the enterprise architect saves the draft
    Then the nesting answer is <status>

    Examples:
      | levels | status |
      | 9      | 200    |
      | 10     | 422    |

  Scenario: Without entity:write
    Given the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "CRM" at 0, 0 with 90 x 30 pt
    When the viewer saves the draft
    Then the nesting answer is 403

  # Requirement: New element in a container

  Scenario: New child
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 300 x 200 pt
    And the draft nests a new "Application Component" named "Payments" in "SAP ERP" with the parent "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "Payments" has the parent "SAP ERP" and the version 2
    And the nesting view of "Order to Cash" shows "Payments" nested in "SAP ERP"

  Scenario: New child of a new container
    Given the draft of "Order to Cash" shows a new "Application Component" named "Shop"
    And the draft nests a new "Application Component" named "Cart" in "Shop" with the new parent "Shop"
    When the enterprise architect saves the draft
    Then the nesting answer is 200
    And the nesting entity "Cart" has the parent "Shop" and the version 2
    And the audit log holds "entity.parent-changed" for the nesting entity "Cart"

  # Requirement: Setting "Ask for a new parent"

  Scenario Outline: Effective value
    Given the nesting setting "Ask for a new parent" is <global>
    And the nesting viewpoint "Landscape" stores the nesting setting <viewpoint>
    When the viewer opens the nesting view of "Order to Cash"
    Then the view carries askNewParent <effective>

    Examples:
      | global | viewpoint | effective |
      | true   | none      | true      |
      | false  | none      | false     |
      | false  | true      | true      |
      | true   | false     | false     |

  Scenario: Change the global value
    When the enterprise architect sets the nesting setting to false with the version read
    Then the nesting answer is 200
    And the nesting setting is false with the version 2
    And the audit log holds "diagram.settings-updated" with the old value true and the new value false

  Scenario: Same value
    When the enterprise architect sets the nesting setting to true with the version read
    Then the nesting answer is 200
    And the nesting setting is true with the version 1
    And the audit log holds no new "diagram.settings-updated" event

  Scenario Outline: Global value rejected
    When <who> sets the nesting setting to false <version>
    Then the nesting answer is <status>
    And the nesting setting is true with the version 1

    Examples:
      | who                        | version                  | status |
      | the viewer                 | with the version read    | 403    |
      | the enterprise architect   | with the version 7       | 412    |
      | the enterprise architect   | without a version        | 428    |

  Scenario: Read the global value
    When the viewer reads the nesting setting
    Then the nesting answer is 200
    And the nesting setting read is true with the version 1
    When the metamodel administrator reads the nesting setting
    Then the nesting answer is 403

  # Requirement: Saving nesting, lines and parents

  Scenario Outline: Limits of lines and parent changes
    When an API call saves <lines> hierarchy lines and <changes> parent changes in "Order to Cash"
    Then the nesting answer is <status>

    Examples:
      | lines | changes | status |
      | 4000  | 0       | 200    |
      | 4001  | 0       | 400    |
      | 0     | 500     | 200    |
      | 0     | 501     | 400    |

  Scenario Outline: Shape size boundaries
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    When an API call saves a container of "SAP ERP" of <width> x 10000 pt in "Order to Cash"
    Then the nesting answer is <status>

    Examples:
      | width    | status |
      | 10000    | 200    |
      | 10000.01 | 400    |

  Scenario: Parent change of an entity not shown
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "SAP ERP" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "parentChanges[0].entityId"

  Scenario: Entity twice in parent changes
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "CRM" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP"
    And the draft changes the parent of "CRM" to "SAP ERP"
    When the enterprise architect saves the draft
    Then the nesting answer is 422 naming "parentChanges[1].entityId"

  Scenario: Atomic save
    Given the nesting entity "SAP ERP" of the metatype "Application Component" exists
    And the nesting entity "CRM" of the metatype "Application Component" exists
    And the draft of "Order to Cash" shows "CRM" at 0, 0 with 90 x 30 pt
    And the draft changes the parent of "CRM" to "SAP ERP"
    And the draft names the container "unknown" for the shape of "CRM"
    When the enterprise architect saves the draft
    Then the nesting answer is 422
    And the nesting entity "CRM" has no parent
