## MODIFIED Requirements

### Requirement: Element catalog

`listNotationElements` SHALL return the elements of the notations with key, notation, label and kind (element or relationship), without sizes; it SHALL need `entity:read` or `viewpoint:manage`. The catalog SHALL contain the elements of ArchiMate 3.2, the UML component and class elements, one element per BPMN type of REQ-076 and the generic elements.

Trace: US-091 AC1, REQ-091, REQ-076, owner decisions 2026-11-17 to 2026-11-27

#### Scenario: ArchiMate elements

- **WHEN** Kurt reads the catalog
- **THEN** it contains `archimate:ApplicationComponent` as an element of `archimate3` and `archimate:Flow` as a relationship

#### Scenario: BPMN elements

- **WHEN** Kurt reads the catalog
- **THEN** it contains `bpmn:UserTask`, `bpmn:ExclusiveGateway`, `bpmn:MessageStartEvent` and `bpmn:Group` as elements of `bpmn2` and `bpmn:SequenceFlow` as a relationship, and the existing keys such as `bpmn:Task` and `bpmn:Gateway` stay
