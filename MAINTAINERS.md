# Maintainers

This file lists the maintainers and areas of responsibility for OEA.

## Concept

Maintainers are people with write access to the main repository and review authority over pull requests. They commit to developing the project in line with the [vision](business-analysis/vision.md) and the [guiding principles](concept/README.md).

Maintainer status does not confer any rights in the software. The sole copyright holder and licensor is Lukas Mathis (see [NOTICE](NOTICE)).

## Current Maintainers

| Area | Name | Contact |
|---|---|---|
| Lead maintainer / PO, copyright holder | Lukas Mathis (chasperli) | <luek.mathis@gmail.com> |
| Concept and documentation | TBD | TBD |
| Backend | TBD | TBD |
| Frontend | TBD | TBD |
| Security | TBD | TBD |
| Operations / deployment | TBD | TBD |

<!--
Format per entry:
| Area | Full name (GitHub/Gitea handle) | Email or contact channel |
-->

## Becoming a Maintainer

New maintainers are welcome. The path:

1. **Substantial contribution** over at least 6 months (code, docs, concept, reviews)
2. **Nomination** by an active maintainer in the internal maintainer channel
3. **Consensus** among the active maintainers (lazy consensus, at least 75% approval)
4. **Addition** to this file via PR
5. **Write access** to the repository

Expected commitments:

- Active participation at least monthly
- Responsibility for reviews in the area
- Adherence to the [Code of Conduct](CODE_OF_CONDUCT.md)
- Communication with the community

## Losing Maintainer Status

Status is lost through:

- 6+ months of inactivity without notice
- Serious violations of the Code of Conduct
- Own announcement of resignation

Inactive maintainers are moved to an "Emeritus" section but do not retain write access.

## Decision Making

OEA follows a **lazy consensus model**:

- Small changes (docs, bug fixes): one maintainer review is sufficient
- Medium changes (features, new dependencies): two reviews, 7 days for objections
- Large changes (architecture, tech stack, license): ADR required, consensus among active maintainers, 14 days for objections

In case of a tie: the lead maintainer / PO decides. License changes always require the consent of the copyright holder.

## Areas of Responsibility

### Concept and Documentation

- `concept/` and all Markdown documents in the repository
- Ensuring the concept paper stays in sync with the code
- Maintaining the CHANGELOG

### Backend

- `backend/` codebase
- API specs in `api/openapi.yaml`
- Persistence layer and migrations

### Frontend

- `frontend/` codebase
- UI components and design system integration
- A11y compliance

### Security

- Security reviews
- Handling of security reports (see [SECURITY.md](SECURITY.md))
- SBOM and dependency updates

### Operations / Deployment

- Container images and release pipeline
- Deployment documentation
- Backup and recovery concepts

## Emeritus (Inactive Maintainers)

<!-- Added when maintainers give up or lose their status. -->

(none yet)

## Contact

For questions about the maintainer structure: `maintainers@oea.org` (TBD)

For content discussions: GitHub/Gitea Discussions or the issue tracker.

For security reports: see [SECURITY.md](SECURITY.md).
