# Stakeholder: Michael – Solution Architect in a Mid-Sized Company

**ID**: SH-04
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Michael is a solution architect in a mid-sized company that uses Sparx Enterprise Architect. He works close to projects and models concrete solutions with UML, BPMN, and ArchiMate. Sparx is powerful, but for his pipeline- and code-review-oriented way of working it is a foreign body.

## Role and Responsibility

- **Current role**: Solution architect (5-8 years of experience, several of them with Sparx)
- **Organization/industry**: Mid-sized company, often software-driven industry
- **Experience level**: high in UML/BPMN/ArchiMate modeling, high in software engineering
- **Main daily tasks**:
  - Solution design for concrete projects
  - Create sequence and component diagrams
  - Document interface contracts
  - Code reviews with architecture relevance
  - Mentoring junior developers on design questions

## Relation to OEA

- **Usage frequency**: daily (during active projects)
- **Main usage**: maintain (model), export (for documentation and reviews)
- **Technical level**: very high (Git, IDE integration, CI/CD workflows)
- **Preferred interface**: CLI with editor integration, UI only where absolutely necessary

## Current Pain Points

- **Sparx is not Git-capable**: The `.eap` file is binary, cannot be diffed, and cannot be reviewed in pull requests. XMI export is a makeshift workaround.
- **Knowledge monopoly due to tool complexity**: Sparx knowledge is rare, so Michael becomes the bottleneck for every model change. Reviews do not take place because others do not want to open Sparx.
- **License costs scale with users**: Not every solution architect will get Sparx, so a silo emerges. The business unit cannot access it anyway.
- **Visual quality lags behind modern tools**: Sparx diagrams look technically correct, but stakeholders respond noticeably better to modern tools.
- **Diagram updates do not propagate automatically**: An architecture change requires manually updating many diagrams. Inconsistencies are the rule.
- **Sequence diagrams are his daily bread**: Sparx is powerful here, but maintenance remains cumbersome. PlantUML would almost be better for him, but it is not integrated into OEA.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Git-native working (pull requests, diffs, CI) | functional | blocking |
| Usable UML and sequence diagrams | functional | blocking |
| Migration from Sparx (XMI import) feasible | compatibility | blocking |
| Business unit can read models without tool license | usability | worrying |
| Visual quality for stakeholder presentations | usability | worrying |
| BPMN modeling as an integrated concept | functional | worrying |
| Interface contracts versionable in pipelines | functional | worrying |
| Skill transferability to junior developers | usability | worrying |

## Success Criteria

- [ ] Architecture models reviewable in PRs (diff visible, comments possible)
- [ ] Sequence diagrams generatable from repository data, of comparable quality to Sparx or PlantUML
- [ ] >70% of the existing Sparx model automatically migratable
- [ ] Junior developers can read models without tool license and without prior EA experience
- [ ] BPMN processes modelable or importable with a clear mapping convention
- [ ] Inconsistencies between model and code automatically detectable (CI validation)

## Involved Use Cases

<!-- To be filled once use cases are defined -->

## Concept References

- [§6 Core Entity Types](../../concept/20-entities/06-core-entity-types.md) – BPMN integration relevant
- [§9 Process Architecture](../../concept/20-entities/09-process-architecture.md) – BPMN as adapter module
- [§13 Business–Technology Linking](../../concept/30-dynamics/13-business-technology-linking.md)
- [§21 API Architecture and Modularity](../../concept/70-platform/21-api-architecture.md) – pipeline suitability
- [§21.2.1 Visualization Strategy](../../concept/70-platform/21-api-architecture.md) – sequence diagrams as a use case

## Notes

Michael is the **developer-affine architect**. He expects the same workflow quality from the tool as from modern dev tools (Git, IDE, CI/CD). Sparx feels like a foreign body to him.

**Sequence diagrams as a killer feature**: Here the concept decision "PlantUML for sequence diagrams" from §21.2.1 is directly relevant for Michael. If sequence diagrams are worse than in Sparx or PlantUML standalone, that is a knockout criterion.

**Sparx benchmark**: Sparx can do a lot, but is not good in every aspect. Pipeline suitability and Git workflow are clear wins for the new tool – layout control and UML richness are the critical risks of loss.

**Bridge to developers**: If the tool works for Michael, it is also attractive for developers who are supposed to read architecture documentation. This is an important multiplier.
