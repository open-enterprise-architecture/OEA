# Contributing to OEA

Thank you for your interest in contributing to OEA. This document describes how to contribute effectively.

## Quick Overview

| You want to... | Read... |
|---|---|
| Understand what OEA is | [README.md](README.md) and [concept/README.md](concept/README.md) |
| Report a bug | [Issue template Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) |
| Propose a feature | [Issue template Feature Request](.github/ISSUE_TEMPLATE/feature_request.md) |
| Contribute code | This document, then a PR |
| Improve documentation | This document, then a PR |
| Report a security vulnerability | **NOT as an issue** – [SECURITY.md](SECURITY.md) |

## Prerequisites

Before contributing, please read:

- [README.md](README.md) – project overview
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) – code of conduct
- [AGENTS.md](AGENTS.md) – working method and conventions
- Relevant chapters from `concept/` depending on the contribution

## Working Method

OEA is developed as a **documentation-centric project**. Concept paper, use cases, requirements, and ADRs are created before the code and maintained together with the code.

We work with an **agent-driven workflow** (see `.opencode/agents/` and `docs/workflow-example.md`). Even if you do not use an AI assistant, the role distribution is relevant:

- **Solution Architect**: Clarifies requirements, defines specs
- **Business Engineer**: Models the business domain
- **Requirements Engineer**: Derives atomic requirements
- **UI Designer**: Clickable screen prototypes (Storybook) and design system
- **Backend Engineer / Frontend Engineer**: Implementation from specs
- **Security Engineer**: Cross-cutting security reviews

## Types of Contributions

### Bug Reports

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md). Important:

- Reproducible step-by-step procedure
- Expected vs. actual behavior
- Environment information (OS, version, browser)
- Logs (if available, without sensitive data)

### Feature Requests

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md). Describe:

- Which persona should the feature serve? (see `business-analysis/stakeholders/`)
- Which pain point is addressed?
- What would the ideal flow look like?

Features often begin as a use case (`requirements/use-cases/`) before they are implemented.

### Code Contributions

#### Preparation

1. Create an issue or have an existing issue assigned to you
2. For larger changes: discuss an ADR draft beforehand
3. Create a fork
4. Feature branch: `feature/<short-description>` or `fix/<short-description>`

#### Code Conventions

- **Language**: English for code identifiers, comments, documentation and commit messages
- **Type safety**: TypeScript strict in the frontend, type hints + mypy strict in the Python backend (or comparable depending on the language)
- **Tests**: Mandatory – unit tests, contract tests against OpenAPI specs
- **Specs first**: API changes are incorporated into the OpenAPI spec first
- **Secure defaults**: TLS on, AuthN required, audit log active

#### Conventional Commits

Format: `<type>(<scope>): <subject>`

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`

Examples:
- `feat(backend): add POST /api/v1/applications endpoint`
- `fix(frontend): correct validation in wizard step 2`
- `docs(concept): clarify optionality of the logical layer in §6.1.1`
- `chore(adrs): add ADR-006 for backend stack decision`

#### Pull Request

- Title follows Conventional Commits
- Description references the affected issue, use case, ADR
- Fill in the PR template
- CI must be green
- At least one maintainer review required
- For security-relevant changes: security review required

#### Definition of Done

- [ ] Code implements the acceptance criteria
- [ ] Tests written (unit, integration, E2E where applicable)
- [ ] Code review by a second person
- [ ] Linter and type checks green
- [ ] Documentation updated (README, concept, if affected)
- [ ] ADR created if architecture decision
- [ ] Commits conform to Conventional Commits
- [ ] Link validation green (`python3 scripts/validate_links.py`)

### Documentation Contributions

Documentation is equivalent to code. Maintenance is mandatory, not optional.

- Markdown files follow the style of existing documents
- Cross-references as relative links
- Examples in English
- For concept changes: update `concept/CHANGELOG.md`

### Concept Contributions

The concept paper is the foundation. Changes require care:

- Discussion beforehand (issue or discussion thread)
- For larger changes: ADR
- `concept/CHANGELOG.md` mandatory
- Version bump in `concept/README.md`
- Link validation must stay green

## Review Process

- Maintainers generally review within 5 business days
- Reviews are respectful, objective, constructive
- "Request changes" means concrete improvements, not mere rejection
- Discussions are held publicly (in the PR or the issue)

## License for Contributions

OEA is dual-licensed: **AGPL-3.0** (Community Edition) and a proprietary **Enterprise license**.
The licensor is Lukas Mathis as a private individual (see [NOTICE](NOTICE)).

By submitting a pull request, you accept the **Contributor License Agreement** (see [LICENSES/CLA.md](LICENSES/CLA.md)). It grants the licensor the right to use your contribution under both licenses — you retain your copyright.

Please read [LICENSES/CLA.md](LICENSES/CLA.md) before your first contribution.

## First Steps

If you have never contributed to OEA, look for issues with the label `good first issue` or `documentation`. These are deliberately small and well-scoped.

If you have questions: create an issue with the label `question` or start a discussion.

Thank you for making OEA better.
