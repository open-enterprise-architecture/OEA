---
identifier: {{english-kebab-case-id}}
name_de: {{German Name}}
name_en: {{English Name}}
version: 0.1.0
status: draft  # draft | reviewed | approved | deprecated
maturity: initial  # initial | developing | stable | mature
owner_role: {{role ID or role designation}}
aliases:
  - {{synonym or designation in another area}}
related_capabilities:
  - {{capability-id}}
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - external: {{optional: link to external standard such as BIAN, ISO}}
---

# Business Object: {{Name}}

<!--
Usage: One business object per file, modeled in a structured way.
File name: business-objects/<identifier>.md
Identifier language-neutral in english-kebab-case (e.g. customer, sales-order, master-contract)
-->

## Definition

A concise definition in one to two sentences. What exactly is the business object? What distinguishes it from similar objects?

## Description

Extended description: context, business purpose, typical lifetime.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| {{name}} | string \| integer \| decimal \| boolean \| date \| datetime \| enum \| reference \| composite | required \| optional | {{default value}} | {{e.g. max 255, regex...}} | {{meaning}} |
| | | | | | |

**Notation**:
- `reference` → points to another business object, with `target: <object-id>`
- `enum` → list the value set, e.g. `[active, inactive, archived]`
- `composite` → embedded structure, break down in a separate table

## Relationships

Relationships to other business objects:

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| {{relationship name}} | [{{object-id}}](./{{object-id}}.md) | 1:1 \| 1:n \| n:m | yes \| no | {{meaning}} |
| | | | | |

**Notes**:
- Relationship direction is semantically important (e.g. "Order belongsTo Customer")
- For n:m: model a separate connection object if applicable

## Lifecycle

States the business object can pass through:

```
{{initial-state}} → {{state-1}} → {{state-2}} → {{terminal-state}}
                       ↓
                   {{alternative-state}}
```

**States**:
- `{{state}}`: meaning, how it is reached, what is allowed in this state
- ...

**Transitions**:
- `{{from}} → {{to}}`: trigger, permission, consequences

## Business Rules

Business rules that apply to this business object:

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-{{NN}} | {{e.g. "Customer with personalDataCategory MUST have legalBasis"}} | {{onCreate, onUpdate}} | {{GDPR Art. 6}} |
| | | | |

For complex rules: separate file in `business-objects/rules/BR-NN-name.md` and link it here.

## Examples

Concrete examples help with understanding:

**Example 1**: {{name}}
```yaml
{{attribute1}}: {{value1}}
{{attribute2}}: {{value2}}
```

**Example 2**: {{name}}
```yaml
{{...}}
```

## Term Mapping

If the object is designated differently in different business areas:

| Area | Designation | Note |
|---|---|---|
| {{DACH}} | {{designation DE}} | |
| {{US}} | {{designation EN}} | |
| {{APAC}} | {{designation}} | {{semantic deviations, if any}} |

## Distinction from Similar Objects

What this object is **not**, compared to related ones:

- **NOT** {{related object 1}}: because ...
- **NOT** {{related object 2}}: because ...

## Usage in Use Cases

<!-- Maintained automatically or manually: which UCs use this business object? -->

- [UC-NN: Title](../requirements/use-cases/UC-NN-kurzname.md)

## Concept Reference

- [§{{N}} {{Chapter}}](../concept/...)

## Notes

Free text for modeling decisions, open questions, discussions.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | YYYY-MM-DD | Business Engineer | Initial draft |
