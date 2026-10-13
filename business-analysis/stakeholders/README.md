# Stakeholders

Eight persona profiles as primary users and sponsors of OEA. Each profile follows a uniform schema.

## Overview

| ID | Name | Role | Organization | Tool Background |
|---|---|---|---|---|
| [SH-01](SH-01-franz-junior-domain-architect.md) | Franz | Junior Domain Architect | Corporate group | Greenfield |
| [SH-02](SH-02-lukas-senior-data-architect.md) | Lukas | Senior Data Architect | Mid-sized company | Avolution |
| [SH-03](SH-03-kurt-lead-enterprise-architect.md) | Kurt | Lead Enterprise Architect | SME | Wiki |
| [SH-04](SH-04-michael-solution-architect.md) | Michael | Solution Architect | Mid-sized company | Sparx |
| [SH-05](SH-05-cio-corporate-group.md) | CIO | CIO | Corporate group | Multi-tool (Ardoq, Avolution, LeanIX, Sparx) |
| [SH-06](SH-06-max-operator-sme.md) | Max | Operator | Regulated SME | Hybrid infrastructure, mixed stack |
| [SH-07](SH-07-sabine-business-engineer.md) | Sabine | Senior Business Engineer | Global corporate group | Structured business modeling |
| [SH-08](SH-08-anna-business-analyst.md) | Anna | Business Analyst / Process Owner | Mid-sized company | – |

## Characteristics of the Selection

The eight personas cover three orthogonal spectrums:

**Size classes**: SME (Kurt, Max), mid-sized company (Lukas, Michael, Anna), corporate group (Franz, CIO, Sabine).

**Experience levels**: Junior (Franz), Professional (Max), Senior (Lukas, Sabine), Lead (Kurt), Solution (Michael), C-level (CIO); additionally the business analyst (Anna).

**Tool backgrounds**: Greenfield, wiki-based, Avolution, Sparx, multi-tool corporate group, business-object-oriented, operations-focused.

## Recurring Patterns

Three patterns stood out while writing:

**First, migration is the only concern that blocks multiple personas**: Lukas (Avolution), Michael (Sparx), and CIO (multi-tool) have marked migration as `blocking`. This makes **migration suitability an implicit walking skeleton criterion**.

**Second, Max has the most `blocking` concerns** (eight): this is not a typo but the reality of regulated SMEs. Operational prerequisites in such environments are actually minimum requirements, not wishes.

**Third, the CIO is the only sponsor persona**: all others are direct users. This asymmetry mirrors the real world – tool decisions are made by sponsors but carried by users.

## Approach

For a new stakeholder: use the `/new-stakeholder` slash command, based on `../../templates/stakeholder.template.md`.

For persona adjustments: versioning in the frontmatter, document the change in concerns or pain points.
