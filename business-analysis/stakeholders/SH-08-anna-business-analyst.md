---
id: SH-08
name: Anna – Business Analyst
version: 0.1.0
status: draft
created: 2026-06-26
---

# Stakeholder: Anna – Business Analyst in a Mid-Sized Company

## Role and Responsibility

Anna is a senior business analyst at a mid-sized manufacturing company. She models business processes, elicits requirements, and is the interface between business units and IT. Her diagrams form the basis for automation and digitalization projects.

- **Experience level**: very high in BPMN 2.0 and EPC; good in ArchiMate basic concepts; not a UML specialist
- **Tool experience**: ARIS, Signavio (SAP), Microsoft Visio; occasionally Camunda Modeler
- **Way of working**: process-centric, close to the business units; models current processes (AS-IS) and target processes (TO-BE) in sprints; needs fast, intuitive tools
- **Context**: Often works without direct IT access; results must be readable for business users

## Goals

1. Model business processes in BPMN 2.0 directly in the EA tool — no separate process tool anymore
2. Assign roles and organizational units to lanes and pools — responsibilities at a glance
3. Assign persons concretely to a task for process ownership
4. Link process models to the EA model: which application supports this task?
5. Version processes and link them as target process with EA solutions

## Concerns

| Concern | Type | Importance |
|---|---|---|
| BPMN pool/lane structure on canvas | functional | critical |
| Role and OrgUnit assignment to lanes | functional | critical |
| Person assignment to tasks | functional | high |
| Link task → application (UC-05) | functional | high |
| Metamodel configurability of process types | functional | high |
| Readability for business users (no EA jargon) | usability | high |
| Export as PDF or BPMN 2.0 XML | functional | medium |
| Import from Camunda/Signavio | functional | low |

## Involvement

| UC | Role |
|---|---|
| [UC-10](../../requirements/use-cases/UC-10-model-business-processes.md) | Primary actor |
| [UC-04](../../requirements/use-cases/UC-04-configure-metamodel.md) | Consulted (configure process types) |
| [UC-05](../../requirements/use-cases/UC-05-describe-architecture-vision.md) | Consulted (link task → application) |

## Frustrations with Previous Tools

- ARIS/Signavio: Powerful, but far from the EA model; duplicate maintenance of application and process models
- Visio: No semantics; graphics only; no role/person linking with a living directory
- Camunda Modeler: Good for technical BPMN, but no EA perspective; no OrgUnit assignment

## Concept Reference

- [§9 Process Architecture](../../concept/20-entities/09-process-architecture.md)
- [§6 Core Entity Types](../../concept/20-entities/06-core-entity-types.md)
