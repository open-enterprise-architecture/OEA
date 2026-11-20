## MODIFIED Requirements

### Requirement: Diagrams page

The web app SHALL offer the page "Diagrams" (SCR-067) with the diagrams, their viewpoint and notation, a name filter, "Load more", and for holders of `entity:write` "Create diagram", "Rename" and "Delete" with a confirmation, and for holders of `entity:restore` "Show deleted" with "Restore". A diagram SHALL open its page with the canvas (SCR-021).

Trace: US-074, UC-10 step 1, owner decision 2026-11-19

#### Scenario: Create from the page

- **WHEN** Anna chooses "Create diagram", enters "Order to Cash" and the viewpoint "Process Model (BPMN 2.0)"
- **THEN** the diagram is created and its page opens

#### Scenario: Reader

- **WHEN** Franz, holding only `entity:read`, opens the page "Diagrams"
- **THEN** he sees the diagrams without "Create diagram", "Rename" and "Delete"

#### Scenario: No diagrams

- **WHEN** Anna opens the page "Diagrams" and there is none
- **THEN** it shows "No diagrams yet." and "Create diagram"
