## ADDED Requirements

### Requirement: Stacked lanes

In a `bpmn2` diagram the editor SHALL stack the lanes nested in a pool or a lane: they fill its width right of a label band of 30 pt and lie from its top one below the other without gaps, in the order of their position; the height of a pool or lane with lanes SHALL be the sum of their heights. Nested lanes SHALL stack in the same way.

Trace: US-074 AC3, AC4, REQ-076 AC4, owner decision 2026-11-27

#### Scenario: Lane added to a pool

- **GIVEN** the pool "Customer" with the lane "Sales" of 100 pt height
- **WHEN** Anna drops a new "Lane" "Shipping" onto the lower half of "Sales"
- **THEN** "Shipping" is nested in "Customer" directly below "Sales" with the width of "Sales", and "Customer" grows by the height of "Shipping"

#### Scenario: First lane

- **GIVEN** the pool "Customer" of 200 pt height without lanes
- **WHEN** Anna drops a new "Lane" "Sales" into "Customer"
- **THEN** "Sales" lies right of the label band of "Customer" and is 200 pt high, and "Customer" keeps its height

#### Scenario: Nested lanes

- **WHEN** Anna drops two new lanes onto the label band of the lane "Sales"
- **THEN** they stack inside "Sales" right of its label band, and "Sales" is as high as both together

#### Scenario: Pool wider

- **WHEN** Anna makes the pool "Customer" 100 pt wider
- **THEN** every lane of "Customer", nested lanes included, becomes 100 pt wider

### Requirement: Resizing and moving lanes

A lane SHALL be at least 30 pt high and at least as high as its content. Changing its height SHALL shift the lanes below and their content and resize its pool. A lane dropped on the content area of another lane SHALL become its sibling, above or below it by the half; dropped on a label band it SHALL be nested there. Reordering within a pool SHALL not ask "New parent?". The layout SHALL be saved as shapes.

Trace: US-074 AC3, owner decision 2026-11-27

#### Scenario: Lane higher

- **GIVEN** "Customer" with the lanes "Sales" and "Shipping", and a task in "Shipping"
- **WHEN** Anna makes "Sales" 40 pt higher
- **THEN** "Shipping" and its task move 40 pt down, and "Customer" is 40 pt higher

#### Scenario: Minimum height

- **WHEN** Anna makes an empty lane 30 pt high, and then 29 pt
- **THEN** the lane is 30 pt high both times

#### Scenario: Not smaller than the content

- **GIVEN** "Sales" holds a task whose bottom lies 80 pt below the top of "Sales"
- **WHEN** Anna makes "Sales" 70 pt high
- **THEN** "Sales" stays 80 pt high

#### Scenario: Lanes reordered

- **WHEN** Anna drags "Shipping" onto the upper half of "Sales"
- **THEN** "Shipping" is the first lane, "Sales" the second, each keeps its tasks, and she is not asked for a new parent

#### Scenario: Task directly in the pool

- **GIVEN** "Customer" with the lanes "Sales" and "Shipping"
- **WHEN** Anna drops a task onto the label band of "Customer", reaching 20 pt below "Shipping"
- **THEN** the task is nested in "Customer", and "Shipping" grows by 20 pt, so that the lanes still fill "Customer"

#### Scenario: Layout restored

- **WHEN** Anna saves the stacked lanes and reloads the diagram
- **THEN** the pool, the lanes and their content are where she left them

### Requirement: Collapsed sub-processes

A shape of a sub-process SHALL be collapsed and expanded per diagram (`collapsed`, saved with the layout): collapsed, it SHALL be drawn at 90 × 60 pt with the "+" marker, its nested shapes and the edges and hierarchy lines touching them SHALL be hidden, and its stored size and the repository SHALL be unchanged. `collapsed` on a shape that is not a sub-process SHALL be rejected with 422.

Trace: US-074, REQ-076 AC8, owner decision 2026-11-27

#### Scenario: Collapse

- **GIVEN** the sub-process "Check credit" holds two tasks joined by a sequence flow
- **WHEN** Anna collapses "Check credit" and saves
- **THEN** it is drawn at 90 × 60 pt with "+", its tasks and their flow are hidden, the view returns it with `collapsed` true and its expanded size, and the tasks keep the parent "Check credit"

#### Scenario: Expand

- **GIVEN** "Check credit" is collapsed
- **WHEN** Anna expands it
- **THEN** it has its expanded size again, and its tasks and their flow are shown

#### Scenario: Collapsed in another diagram only

- **GIVEN** "Check credit" is shown in two diagrams
- **WHEN** Anna collapses it in one of them
- **THEN** it stays expanded in the other

#### Scenario: Flag on another type

- **WHEN** an API call saves a task shape with `collapsed` true
- **THEN** the response is 422 naming the shape's `collapsed`, and nothing changes

#### Scenario: Collapsed in a lane

- **GIVEN** the sub-process "Check credit" of 270 × 150 pt in the lane "Sales"
- **WHEN** Anna collapses "Check credit" and makes "Sales" lower
- **THEN** "Sales" stays as high as the expanded "Check credit" requires, and the save is accepted

#### Scenario: Collapse audited

- **WHEN** Anna collapses "Check credit" and saves without other changes
- **THEN** `diagram.layout-saved` is audited with 1 changed shape

#### Scenario: Flow to a hidden shape

- **GIVEN** a sequence flow from a task outside "Check credit" to a task inside it
- **WHEN** Anna collapses "Check credit"
- **THEN** the flow is hidden and kept, and it is shown again after expanding

### Requirement: Vertical pools

The shape of a pool SHALL have an orientation per diagram (`orientation`, `horizontal` by default), changeable on the canvas and saved with the layout; in a vertical pool the label band SHALL lie at the top and its lanes, nested ones included, SHALL lie side by side from its left and fill its height, with the rules of stacked lanes exchanged. `vertical` on a shape that is not a pool SHALL be rejected with 422.

Trace: US-074 AC3, REQ-076 AC4, owner decision 2026-11-27

#### Scenario: Pool switched to vertical

- **GIVEN** the pool "Customer" with the lanes "Sales" (100 pt high) and "Shipping", and a task in "Sales"
- **WHEN** Anna switches "Customer" to vertical and saves
- **THEN** "Sales" and "Shipping" lie side by side in that order, "Sales" is 100 pt wide, its task is still in it, and the view returns `orientation` `vertical`

#### Scenario: Lane wider in a vertical pool

- **WHEN** Anna makes "Sales" in the vertical pool "Customer" 40 pt wider
- **THEN** "Shipping" and its content move 40 pt to the right, and "Customer" is 40 pt wider

#### Scenario: Orientation per diagram

- **WHEN** Anna switches "Customer" to vertical in one diagram
- **THEN** "Customer" stays horizontal in another diagram

#### Scenario: Orientation on another type

- **WHEN** an API call saves a lane shape with `orientation` `vertical`
- **THEN** the response is 422 naming the shape's `orientation`, and nothing changes

### Requirement: Insert lanes with icons

In a `bpmn2` diagram a selected lane SHALL offer holders of `entity:write` icons to insert a new lane directly above or below it in a horizontal pool, and left or right of it in a vertical one; the new lane SHALL be an entity of the lane's type with the parent of the lane, get the viewpoint's default extent and be stacked; dragging lanes SHALL stay possible.

Trace: US-074 AC3, owner decision 2026-11-27

#### Scenario: Lane inserted below

- **GIVEN** the horizontal pool "Customer" with the lanes "Sales" and "Shipping"
- **WHEN** Anna selects "Sales", chooses "Insert lane below" and names the lane "Billing"
- **THEN** "Billing" lies between "Sales" and "Shipping", has the parent "Customer" after the save, and "Customer" is higher by the default lane height

#### Scenario: Lane inserted left

- **WHEN** Anna selects a lane of a vertical pool and chooses "Insert lane left"
- **THEN** the new lane lies directly left of it, and the icons of a vertical pool are "Insert lane left" and "Insert lane right"

#### Scenario: Reader

- **WHEN** Franz, a reader, selects a lane
- **THEN** no insert icons are shown
