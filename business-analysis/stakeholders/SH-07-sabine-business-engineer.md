# Stakeholder: Sabine – Senior Business Engineer in a Global Corporate Group

**ID**: SH-07
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Sabine is a senior business engineer / business architect in a globally operating corporate group. She models the business domain in a structured way: business objects, business capabilities, business rules, information flows. Her outputs are the foundation for use cases, requirements, and solution design. She thinks in formalized business models and brings structural clarity to complex corporate realities.

## Role and Responsibility

- **Current role**: Senior business engineer / business architect (10+ years of experience)
- **Organization/industry**: Global corporate group (>10,000 employees, several business divisions, international operations)
- **Experience level**: very high in business modeling, familiar with BIAN, ARIS, BPMN, ArchiMate
- **Main daily tasks**:
  - Model business objects and their relationships (Customer, Product, Order, Contract, etc.)
  - Capture business capabilities and their dependencies
  - Formalize business rules and align them with compliance
  - Create process maps and value chains
  - Handovers to solution architecture and requirements engineering
  - Group-wide harmonization of terms and structures
  - Stakeholder management across areas and countries

## Relation to OEA

- **Usage frequency**: daily
- **Main usage**: maintain (business model), review (cross-area consistency), generate (reports for management)
- **Technical level**: medium-high (can read Markdown, YAML, JSON Schema; does not program, but understands data structures)
- **Preferred interface**: UI primarily (for modeling and reviews), CLI/export for reports

## Current Pain Points

- **Missing structured business object modeling in many tools**: ArchiMate does have concepts, but no dedicated business object model. ARIS has it, but is proprietary and expensive.
- **Proliferation of terms in the corporate group**: What is called "Kunde" in Germany is "Account" in one subsidiary and "Customer Master" in another. Harmonization without tool support is laborious.
- **Business rules insufficiently formalized**: Business rules often exist only in people's heads or scattered across documentation. Compliance audits reveal gaps.
- **Handover between business and IT breaks**: Business models are translated for IT, translation losses are the rule. She wishes for the same source for both.
- **Business capabilities without clear dependencies**: Capability maps look nice, but the relationships between capabilities are rarely captured cleanly.
- **Versioned business models are missing**: Models change (M&A, restructurings, new business fields), but the history is rarely captured.
- **Cross-border consistency**: Business models in DE, US, APAC have structural commonalities but also differences. A tool that can express both is rare.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Structured business object modeling as first-class | functional | blocking |
| Versioned business models (plateaus, lifecycle) | functional | worrying |
| Business rules formalizable and verifiable | functional | worrying |
| Model business capabilities with relationships | functional | worrying |
| Cross-area consistency (e.g. term mapping) | functional | worrying |
| Seamless handover to use cases (business objects referenceable) | usability | blocking |
| Generation of reports for management | usability | worrying |
| Multilingualism (DE/EN minimum for a global corporate group) | usability | worrying |
| Compliance relation of business rules (GDPR, industry-specific) | compliance | worrying |

## Success Criteria

- [ ] Business object can be modeled in a structured way (attributes, types, relationships)
- [ ] Relationships between business objects are navigable and analyzable
- [ ] Business capabilities modelable with dependencies
- [ ] Business rules reference business objects and are verifiable
- [ ] Use cases can reference business objects directly
- [ ] Term mapping between corporate group subsidiaries representable
- [ ] Reports for management automatically generatable (capability heatmap, business object overview)
- [ ] Versioning of business models across plateaus
- [ ] Multilingualism at least for identifiers and descriptions

## Involved Use Cases

<!-- To be filled once use cases are defined -->

## Concept References

- [§7 Motivation, Stakeholders & Goals](../../concept/20-entities/07-motivation-stakeholders-goals.md)
- [§9 Process Architecture](../../concept/20-entities/09-process-architecture.md) – business processes
- [§13 Business–Technology Linking](../../concept/30-dynamics/13-business-technology-linking.md) – business-IT bridge

## Notes

Sabine is the **business structure-giver**. She delivers the domain model on which everything else builds: use cases refer to her business objects, requirements reference her business rules, the solution architect derives his specs from her domain structure.

**Senior and global**: She has enough experience to model even complex corporate realities. Her tool may be demanding, but must be structurally clean and consistent.

**Leverage**: Her work is a multiplier for all subsequent roles. A wrongly modeled business object propagates through use cases, requirements, and code. The investment in clean business modeling pays off at every subsequent level.

**Cross-cultural complexity**: In global corporate groups nothing is trivial. "Customer" in the USA has different mandatory fields than in Germany (GDPR!). Her tool must be able to express these variants without turning into complexity hell.

**Connection to CIO (SH-05)**: She provides the CIO with the structural basis for strategic decisions. Capability heatmaps, business object inventories, compliance relation of business logic – that is her output.

**Connection to Lukas (SH-02)**: Data modeling benefits directly from her work. When business objects are modeled cleanly, the translation into data entities is mechanical.

**Connection to Franz (SH-01)**: She provides him with the domain model as learning material. Junior architects understand a tool faster when the business models are present.

**Connection to Michael (SH-04)**: The solution architect draws on her business objects to structure components.
