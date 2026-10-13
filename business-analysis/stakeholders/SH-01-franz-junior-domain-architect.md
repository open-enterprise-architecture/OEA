# Stakeholder: Franz – Junior Domain Architect in a Corporate Group

**ID**: SH-01
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Franz is a junior domain architect in a corporate group who is building an architecture view for his domain (e.g. Finance, HR, Logistics) from scratch. He has academic EA knowledge but little practical experience, and is under pressure to deliver visible results quickly.

## Role and Responsibility

- **Current role**: Junior domain architect (1-3 years of experience)
- **Organization/industry**: Corporate group, industry-agnostic
- **Experience level**: low in EA tool usage, theoretically trained (TOGAF Foundation or comparable)
- **Main daily tasks**:
  - Inventory of the existing systems in his domain
  - Workshops with the business unit to clarify requirements
  - Sketch first domain target states
  - Ensure alignment with corporate EA guidelines

## Relation to OEA

- **Usage frequency**: daily
- **Main usage**: maintain (build up the model, document)
- **Technical level**: medium (basic Git knowledge present, but not a given)
- **Preferred interface**: UI primarily, CLI for routine tasks

## Current Pain Points

- **Existing corporate EA tools are oversized**: Sparx EA, LeanIX, or Avolution are too complex for a single domain. He only gets limited access, licenses are scarce.
- **TOGAF theory does not help with getting started**: He knows what a landscape map (Bebauungsplan) should be, but not how to create a sensible one for his domain.
- **Pressure to justify himself**: His added value is abstract – without visible results within 3-6 months, his position will be questioned.
- **No sandbox to experiment in**: License costs of existing tools make experimenting risky – he does not dare to "just model something".
- **Credibility is still lacking**: Experienced architects in the corporate group judge him by the quality of his outputs – these must look respectable.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Low entry barrier, quick first results | usability | blocking |
| Compatibility with corporate EA guidelines | compatibility | worrying |
| Learning support by the tool (defaults, examples, templates) | usability | worrying |
| Credible outputs in front of experienced architects | usability | worrying |
| Being able to experiment risk-free | cost | worrying |
| Safe modeling despite lack of experience (validations) | functional | worrying |

## Success Criteria

- [ ] First Application Component captured in <30 minutes, without reading documentation
- [ ] First presentable domain inventory within 1 week
- [ ] First landscape map (Bebauungsplan) draft for the steering committee within 1 month
- [ ] Corporate architects accept his outputs as formally correct
- [ ] He understands what he is doing – the tool explains concepts without patronizing him

## Involved Use Cases

<!-- To be filled once use cases are defined -->

## Concept References

- [§7 Motivation, Stakeholders & Goals](../../concept/20-entities/07-motivation-stakeholders-goals.md)
- [§6.1.1 Application vs. Technology](../../concept/20-entities/06-core-entity-types.md) – the classification discussed here must be tangible for Franz
- [§12.4 Progressive Disclosure](../../concept/30-dynamics/12-domain-views.md) – inventory view is his entry point

## Notes

Franz is methodologically valuable because he has **no benchmark for comparison**. He asks questions that other personas overlook as self-evident. If the tool works for Franz, it works for many EA career starters.

**Greenfield characteristic**: Unlike four other personas, Franz has no existing tools to migrate. His MVP success experience is therefore the most honest test of the low-threshold entry.

**Learning path responsibility**: The tool should not force him to know EA theory, but should give him incentives to learn it – e.g. through explanatory tooltips, good default schemas, glossary references.
