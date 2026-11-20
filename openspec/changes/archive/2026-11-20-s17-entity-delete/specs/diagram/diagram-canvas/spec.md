## ADDED Requirements

### Requirement: Delete from repository on the canvas

A shape or edge of a stored entity SHALL offer "Delete from repository", in its context menu and with Ctrl+Delete, to holders of `entity:delete`. It SHALL show the deletion preview – children, connections and active solutions – ask for confirmation, delete the entity at once, outside the explicit save, and remove the shapes and edges of every deleted entity from the canvas. The other unsaved changes of the diagram SHALL stay.

Trace: REQ-146, US-074, owner decisions 2026-11-19 and 2026-11-20

#### Scenario: Deleted from the canvas

- **WHEN** Kurt chooses "Delete from repository" on the shape of "ERP" and confirms the warning "3 children, 4 connections, 1 active solution"
- **THEN** "ERP" and its cascade are deleted, their shapes and edges disappear from the canvas, and a shape Kurt moved before stays moved and unsaved

#### Scenario: Without entity:delete

- **WHEN** Anna, without `entity:delete`, opens the menu of a shape
- **THEN** it has no "Delete from repository"

#### Scenario: Changed meanwhile

- **GIVEN** someone changed "ERP" after Kurt opened the confirmation
- **WHEN** Kurt confirms
- **THEN** the dialog says that "ERP" was changed, offers to read the preview again, and nothing is deleted

#### Scenario: Restored later

- **GIVEN** "ERP" was deleted from the canvas of "Order to Cash"
- **WHEN** "ERP" is restored and Anna opens "Order to Cash"
- **THEN** its shapes and edges show again
