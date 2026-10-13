# Stakeholder: CIO – Corporate Group with a Mixed OEA Stack

**ID**: SH-05
**Type**: persona
**Relation to the Tool**: secondary
**Internal/External**: internal

## Short Description

CIO of a corporate group with several subsidiaries. At group level, EA tools from different vendors are in use (Ardoq, Avolution, LeanIX, Sparx) – each subsidiary has its own history. The CIO is not a direct user but sponsor and decision maker for tool strategy. He evaluates on the basis of reports, total cost of ownership, strategic fit, and compliance suitability.

## Role and Responsibility

- **Current role**: CIO of a corporate group with 1,000-10,000 employees
- **Organization/industry**: Corporate group with several business divisions or subsidiaries
- **Experience level**: high in strategic tool management, not a direct modeler
- **Main daily tasks**:
  - Strategic IT alignment
  - Supplier negotiations and tool strategy
  - Compliance responsibility (DORA, NIS2, KRITIS depending on the industry)
  - Reporting to the executive board and supervisory board
  - Consolidation initiatives across subsidiaries

## Relation to OEA

- **Usage frequency**: monthly to quarterly (consumes reports, does not model)
- **Main usage**: consume outputs (reports, dashboards, trend analyses)
- **Technical level**: medium (understands architecture concepts, but does not model)
- **Preferred interface**: generated PDF reports, dashboards, executive summaries

## Current Pain Points

- **Tool heterogeneity in the subsidiaries**: Ardoq, Avolution, LeanIX, Sparx – each subsidiary has made its own choice. The consolidated group view is manual.
- **OEA decisions repeat every 3-5 years**: Every switch costs migration effort and loss of acceptance. Vendor lock-in is real.
- **License costs scale non-linearly**: LeanIX and Avolution charge per user. Involving more users would promote adoption but is economically problematic.
- **Adoption falls short of the sales pitch**: Every EA tool promised "EA democratization". In reality, usually 5-15 people actively use the tool, others ignore it.
- **Compliance audits reveal the model-reality gap**: What is in the respective EA tool often does not match the actual landscape. Audits become embarrassing.
- **Reports are "pretty but not very meaningful"**: Colorful heatmaps and capability maps are shown to the board, but rarely used for decisions.
- **OSS alternatives have so far seemed unprofessional**: The board asks about vendor support, SLAs, audit suitability. OSS must be able to answer these questions.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Credibility as an OSS solution in a corporate group context | strategic | blocking |
| Strategic fit with the OSS/cloud-native/DevSecOps strategy | strategic | worrying |
| Total cost of ownership incl. adoption, training, maintenance | cost | blocking |
| Migration from existing tools feasible (multi-tool) | compatibility | blocking |
| Compliance-suitable (DORA, NIS2, KRITIS, ISO 27001) | compliance | blocking |
| Reportable at board level | usability | worrying |
| Risk profile (developer stability, support, SLAs) | strategic | worrying |
| Long-term vendor independence | strategic | worrying |
| Data sovereignty (particularly relevant for Switzerland, EU) | compliance | worrying |

## Success Criteria

- [ ] Consolidated view across subsidiaries possible (at least in perspective)
- [ ] TCO ≥40% lower than the current multi-tool landscape (over a 3-year horizon)
- [ ] Compliance reports automatically generatable (Art. 30 GDPR, ISMS asset inventory)
- [ ] Board accepts outputs in regular reporting format
- [ ] Vendor risk neutralized by OSS nature (forkability, community)
- [ ] Adoption ≥30% of eligible users (higher than current tool adoption rate)
- [ ] Audit trail meets regulatory requirements (DORA, NIS2 depending on the industry)

## Involved Use Cases

<!-- To be filled once use cases are defined -->

## Concept References

- [§17 ITSM Integration](../../concept/60-integrations/17-itsm-integration.md) – multi-tool mastership
- [§20 GRC/GDPR/ISMS Integration](../../concept/60-integrations/20-grc-gdpr-isms-integration.md) – compliance requirements
- [§21.8 Security, Audit Trail](../../concept/70-platform/21-api-architecture.md) – audit suitability
- [§22.11 Repository Changelog](../../concept/70-platform/22-analyzability.md) – board reports

## Notes

The CIO is the **sponsor persona**, not a user. He decides on adoption and funds the initiative, but does not model himself. His concerns are strategic and financial, not functional in the sense of the modelers.

**Multi-tool complexity as a constant**: Unlike the other personas, he does not have "one" existing tool, but four. Migration must be feasible step by step (subsidiary by subsidiary), not as a big bang.

**OSS credibility as the central lever**: His biggest reservation about OSS is not the tool itself, but the question "who is behind it?". Forkability, a clear license, a transparent roadmap, and ideally a commercial support partner (even if not necessarily used) are critical.

**Valuable for roadmap prioritization**: What convinces the CIO also convinces other CIOs. If the tool becomes adoptable for corporate groups, a large market opens up.

**Tension with modelers**: The CIO wants consistency, reports, strategic fit. Modelers want quick value, low threshold. Both worlds must be served by the tool without cannibalizing each other.
