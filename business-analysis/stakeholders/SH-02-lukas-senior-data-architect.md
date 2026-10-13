# Stakeholder: Lukas – Senior Data Architect in a Mid-Sized Company

**ID**: SH-02
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Lukas is an experienced data architect in a mid-sized company that uses Abacus from Avolution. He knows the strengths of professional EA tools but suffers from license costs, maintenance effort, and a lack of pipeline suitability. His professional focus is on data models, information flows, and data lineage.

## Role and Responsibility

- **Current role**: Senior data architect (5-10 years of EA experience, of which 3+ years Avolution)
- **Organization/industry**: Mid-sized company, presumably data-driven business (finance, insurance, logistics, manufacturing)
- **Experience level**: high (knows several EA tools, has seen model know-how)
- **Main daily tasks**:
  - Maintain data models (logical and physical)
  - Model information flows between systems
  - Lineage analyses for reporting and compliance
  - Align GDPR asset inventories with data protection
  - Data model governance across areas

## Relation to OEA

- **Usage frequency**: daily
- **Main usage**: maintain, review, generate reports
- **Technical level**: high (Git, SQL, occasionally Python for data analyses)
- **Preferred interface**: UI for detailed maintenance, CLI/API for reports and validations

## Current Pain Points

- **Avolution license costs under pressure to justify**: Every renewal raises the question of whether the depth is really needed. Management sees high costs without measurable ROI.
- **Maintenance effort concentrates knowledge in few people**: Avolution is powerful, but only 2-3 people in the company can really work with it. Single point of failure.
- **Data lineage ages quickly**: The models are clean initially, but schema changes in production systems do not flow back automatically. Lineage diagrams are outdated after 3 months.
- **Duplicate data maintenance for GDPR**: The GDPR inventory is maintained in a compliance tool. Lukas maintains similar structures in parallel in Avolution. Inconsistencies become visible during audits.
- **Proprietary data formats prevent pipeline integration**: Avolution models cannot be validated in CI/CD pipelines. Lukas wants automated consistency checks on every schema update.
- **Business unit does not understand the models**: The ERD diagrams from Avolution are flawless, but business unit stakeholders cannot read them. Translation costs him time in every workshop.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Deep data modeling (schema evolution, lineage, data quality) | functional | blocking |
| Migration from Avolution feasible (XMI/ArchiMate import) | compatibility | blocking |
| Versioned data models as a first-class concept | functional | worrying |
| Pipeline integration for automatic validation | functional | worrying |
| GDPR asset inventory as an integrated concept, not in parallel | compliance | worrying |
| Business-unit-suitable views of data models | usability | worrying |
| Credibility with external auditors | compliance | worrying |
| TCO savings compared to Avolution | cost | worrying |

## Success Criteria

- [ ] Existing Avolution data model migratable (>80% automatically, remainder manually)
- [ ] Schema changes in the production DB are detected in the CI pipeline and reconciled with the model
- [ ] GDPR record of processing activities generatable from the EA data model (Art. 30)
- [ ] Lineage diagrams automatically updatable
- [ ] Avolution can be replaced after 12-18 months with a positive business case
- [ ] Business unit understands the generated data flow maps without a translation workshop

## Involved Use Cases

| UC | Title | Role |
|---|---|---|
| [UC-08](../../requirements/use-cases/UC-08-model-data-lineage.md) | Model and analyze data flow map (data lineage) | Primary actor |

## Concept References

- [§10.7 Requirements Repository Aspects](../../concept/20-entities/10-cross-cutting.md)
- [§13 Business–Technology Linking](../../concept/30-dynamics/13-business-technology-linking.md) – lineage-relevant
- [§15 Schema Evolution](../../concept/40-extensibility/15-schema-evolution.md) – data model versioning
- [§20 GRC/GDPR/ISMS Integration](../../concept/60-integrations/20-grc-gdpr-isms-integration.md) – resolve Avolution/compliance duplicate maintenance
- [§22 Analyzability](../../concept/70-platform/22-analyzability.md) – pipeline validations

## Notes

Lukas is the **tough critic**. He has a professional tool in use and will compare sharply. If the tool does not convince him, it is not a mid-sized-company competitor for Avolution.

**Avolution benchmark**: Certain functions are solved very well in Avolution (schema versioning, reporting). An honest roadmap must name what is initially NOT achieved and where Avolution remains superior.

**Data focus**: The tool must not be so application-centric that data modeling is handled "on the side". Data architects are a persona of their own with their own needs.

**Valuable for roadmap prioritization**: Lukas's pain points often coincide with real-world compliance requirements (GDPR Art. 30, audit trail, lineage). What helps him also helps auditors and DPOs.
