# Stakeholder: Kurt – Lead Enterprise Architect in an SME

**ID**: SH-03
**Type**: persona
**Relation to the Tool**: primary
**Internal/External**: internal

## Short Description

Kurt is an experienced lead enterprise architect in a small mid-sized company. He is the only EA expert in the house; others have so far handled the topic with Confluence/wiki pages and freehand diagrams. He knows commercial EA tools from previous positions but deliberately rejects them for his current setting.

## Role and Responsibility

- **Current role**: Lead enterprise architect (10+ years of experience, has seen several tools in previous jobs)
- **Organization/industry**: SME (50-250 employees), often industry-specific and product-oriented
- **Experience level**: high in EA methodology, pragmatic in tool usage
- **Main daily tasks**:
  - Architecture reviews for projects and initiatives
  - Supplier selection with architecture assessment
  - Compliance preparation (ISO 27001-light, occasional audits)
  - Strategy support for executive management
  - Onboarding new employees into the knowledge wiki

## Relation to OEA

- **Usage frequency**: daily (for maintenance), frequent for consultation
- **Main usage**: maintain, review, generate reports for executive management
- **Technical level**: high (Git-proficient, can read and adapt scripts)
- **Preferred interface**: CLI and Markdown editor, UI only for diagrams

## Current Pain Points

- **Wiki is flexible but unstructured**: Confluence pages have no consistency, cross-references constantly break, the same information exists in several versions.
- **Diagrams become outdated immediately**: Drawio diagrams are embedded and never updated because manual maintenance is too laborious.
- **No structured ADR workflow**: Architecture decisions are scattered across meeting minutes, emails, and wiki pages. In later discussions the rationale is lost.
- **Audit reports are cobbled together**: Before every audit, Kurt spends two weeks assembling a coherent picture from the wiki. This is a waste of time.
- **Commercial EA tools are not an option**: License costs do not fit the SME budget. Tool complexity is oversized. Vendor lock-in is unacceptable ("I will still know my wiki data in 10 years, an Avolution file maybe not").
- **Knowledge is concentrated in him**: If Kurt leaves or is ill, nobody else has the overview. This worries him and executive management.

## Concerns

| Concern | Category | Severity |
|---|---|---|
| Low maintenance overhead (he is alone) | usability | blocking |
| Open formats, no vendor lock-in | compatibility | blocking |
| Migration from wiki must be feasible | compatibility | worrying |
| Structured ADR workflow, without heavyweight | functional | worrying |
| Audit reports automatically generatable | functional | worrying |
| Knowledge transferability to successors | usability | worrying |
| Affordable (= free in the base variant) | cost | blocking |

## Success Criteria

- [ ] Existing wiki content can be migrated gradually over several weeks (no big-bang migration)
- [ ] ADR workflow integrable into the existing Conventional Commits / Git PR practice
- [ ] Audit preparation time halved
- [ ] Successor can get up to speed in 1 week (documentation as code)
- [ ] Executive management understands outputs (reports, roadmaps) without EA training
- [ ] Tool runs on own infrastructure, without cloud obligation

## Involved Use Cases

- [UC-01: Login](../../requirements/use-cases/UC-01-login.md) – primary actor
- [UC-02: System Admin Bootstrapping](../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) – other stakeholder (in the single-user SME case often also the operator)

## Concept References

- [§5 Principles, Standards & ADRs](../../concept/10-foundations/05-principles-standards-adrs.md) – ADR workflow is central
- [§12.4 Progressive Disclosure](../../concept/30-dynamics/12-domain-views.md) – must remain operable alone
- [§22 Analyzability](../../concept/70-platform/22-analyzability.md) – generate audit reports
- [§21 API Architecture](../../concept/70-platform/21-api-architecture.md) – self-hosting suitability

## Notes

Kurt is the **self-hoster and skeptic**. He has seen all EA tools and deliberately did not adopt them. This makes him the toughest test case for the OSS promises.

**Wiki background**: His benchmark is not Sparx or Avolution, but Confluence/Markdown/Drawio. The bar for low-threshold entry is correspondingly high.

**Single-user reality**: In an SME, Kurt is often alone. Features designed for team workflows (PR reviews, ARB approvals) must also work sensibly for him in single-user mode.

**Valuable for OSS adoption**: If Kurt chooses the tool and is satisfied with it, he will recommend it. SME architects often have influential networks. The size of the OSS community depends on personas like Kurt.
