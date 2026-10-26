# ADR-045: Multilingual User Manual with VitePress on GitHub Pages

**Status**: accepted
**Date**: 2026-10-23
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Frontend Engineer
**Informed**: all contributors

## Context and Problem

OEA has project documentation for contributors (`docs/`, ADRs, requirements, OpenSpec specs), but no documentation for the people who install, administer and use the tool. Operator knowledge is spread over `docs/operations.md`, the walking-skeleton document and the archived changes; the API is described only by `api/openapi.yaml`, which still contains about 98 planned operations next to the implemented ones.

Before the first public release, operators and users need a complete manual: an introduction, setup and first steps, administration and usage, with every function described, the interface documented and screenshots that match the released version. Writing it at the end would mean reconstructing behavior from the code; it has to grow with every change from now on. The repository owner requires the manual in English and German, built with VitePress and published on GitHub Pages, generated as far as possible per release (owner request of 2026-10-23).

GitHub Pages of the repository currently publishes Storybook (ADR-031, `.github/workflows/screens.yml`); a repository has a single Pages site.

## Decision Drivers

- **Completeness that can be checked**: "every function is described" must be verifiable in CI, not by review alone
- **Two languages from the start**: English and German content with the same structure and no silently missing pages
- **Matches the released software**: one manual per release; screenshots and API reference generated from that release
- **Little manual work per release**: screenshots, API reference and publication are automated
- **Consistency with the stack**: Vue 3 / Vite (ADR-011, ADR-040), Markdown in the repository, static hosting
- **Open source and license policy**: tooling under licenses allowed by ADR-043, no external hosting service required
- **Prototype review stays possible**: ADR-031 relies on Storybook being reachable for stakeholders

## Considered Options

### Option 1: VitePress on GitHub Pages

- **Pro**: Vite and Vue like the frontend, so components and tooling are shared; Markdown with Vue components; built-in i18n through locales; fast static build; MIT license
- **Con**: no built-in versioning – versions must be built into subdirectories by the publication workflow; the API reference needs a plugin or a generated page

### Option 2: Docusaurus

- **Pro**: built-in versioning and i18n; widely used by OSS products
- **Con**: React-based – a second UI stack next to Vue; Vue components of the product cannot be reused; heavier build

### Option 3: MkDocs with Material theme

- **Pro**: mature; versioning with `mike`; strong search
- **Con**: Python toolchain next to Node and Java; i18n through plugins; Vue components cannot be embedded

### Option 4: Markdown in the repository or GitHub wiki

- **Pro**: no build step
- **Con**: no versioning per release, no generated API reference, no navigation for two languages, no screenshot pipeline; the wiki is outside the pull-request review

## Decision

We choose **Option 1: VitePress on GitHub Pages**.

**Rationale**: it is the requested tool, fits the Vue/Vite stack and needs no further runtime; the missing built-in versioning is solved once in the publication workflow.

Rules:

1. **Location and structure**: the manual lives in `manual/` (VitePress project) with English as root locale and German under `/de/`. Every locale has the same chapters: Introduction, Setup and first steps, Administration, Usage, API reference. Contributor documentation stays in `docs/`.
2. **Source language and tone**: English is the source; German is a maintained translation in the same pull request. A page exists in both languages or in neither. The manual addresses the reader directly – "you" in English, the informal "du" in German – unlike the rest of the repository documentation.
3. **Every function described**: each manual page declares in its frontmatter which screens (`SCR-NNN`) and user stories (`US-NNN`) it covers. A CI check fails if an implemented screen of `docs/screens/SCREENS.md` or a user story with status `in-progress`, `review`, `done` or `realized` ([ADR-046](./ADR-046-manual-coverage-story-statuses.md)) is not covered in both languages, or if the German page covers something different from the English page.
4. **API reference**: generated from `api/openapi.yaml` at build time; it shows only implemented operations (operations marked as planned, retrospective action A1, are left out) and the permissions of `x-oea-authorization`.
5. **Screenshots**: produced by Playwright walkthrough tests against the real stack (Compose environment with Keycloak and seed data), not by hand and not from Storybook. Each screenshot has a stable name referenced from the Markdown. The tests render every screenshot in each language the user interface offers; while the user interface is available only in English (ADR-025 not yet implemented in the frontend), the German manual shows the English screenshots and switches automatically once German is offered.
6. **Versions per release**: a release workflow on a version tag runs the walkthrough tests, builds the manual and publishes it under `/vX.Y/`; the site root shows the latest release, and a version selector lists the published versions. Published versions are kept; earlier versions are not rebuilt.
7. **One Pages site**: the manual is the site root; Storybook moves to `/storybook/` and is linked only from the contributor section (ADR-031 review purpose kept). The publication branch or artifact is written only by CI jobs with the least privileges of REQ-192.
8. **Definition of done**: every OpenSpec change that adds or changes user-visible behavior updates the manual in both languages and the walkthrough tests for its screenshots in the same change.

## Consequences

### Positive Consequences

- Operators and users get one place for installation, administration, usage and the API, matching their release
- Documentation grows with each change instead of being written before the release
- Screenshots and API reference cannot drift from the released software
- The coverage check makes "every function is described" testable

### Negative Consequences / Trade-offs

- Every user-visible change costs additional effort for two languages and the walkthrough tests
- The walkthrough tests need the full stack in CI (PostgreSQL, Keycloak, backend, frontend) and add run time to the release workflow
- Versioning needs custom workflow logic (publication branch or merged artifact) that VitePress does not provide
- `AGENTS.md` needs an exception: German content is allowed in `manual/de/` (all other repository content stays English)
- Moving Storybook to `/storybook/` changes the URL used for prototype reviews
- Until the user interface is translated, the German manual shows English screenshots

## References

**Concept Chapters**:

- [§21 API Architecture and Modularity](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:

- [ADR-011](./ADR-011-frontend-framework.md) (Vue 3), [ADR-013](./ADR-013-api-style.md) (contract as source of the API reference), [ADR-025](./ADR-025-i18n-strategy.md) (user-interface languages), [ADR-031](./ADR-031-ui-prototyping-storybook.md) (Storybook on Pages), [ADR-040](./ADR-040-ui-component-library-after-primevue-license-change.md) (Nuxt UI), [ADR-043](./ADR-043-third-party-license-policy.md) (license policy for the tooling)

**Use Cases / Requirements**:

- [REQ-196: Multilingual user manual](../requirements/req/REQ-196-user-manual.md)
- [REQ-192: Least privilege for CI/CD workflows](../requirements/req/REQ-192-ci-least-privilege.md)

## Notes

Owner decisions of 2026-10-23: VitePress on GitHub Pages; English and German; chapters introduction, setup and first steps, administration, usage and interface; every function described; generated per release as far as possible; screenshots from tests against the real stack; versions per release with the latest at the root; Storybook kept under `/storybook/`; screenshots in each language the user interface offers, English otherwise. Owner decision of 2026-10-23: the manual addresses the reader directly, in German with the informal "du".

Owner decisions of 2026-10-24 (change `s2c-manual-site`), clarifications within this decision: the coverage check of rule 3 includes user stories with status `review` (recorded in ADR-046 together with `done`); until the first release, the site root shows the manual of `main` with a notice that it describes the unreleased state; VitePress 2.0 (alpha, pinned exactly) instead of 1.6, whose Vite 5 development server has advisories without fix.

Common practice of larger OSS products was considered: they rarely publish Storybook as part of the user documentation and usually translate only the text of their manuals while keeping English screenshots, because localized screenshots are expensive to maintain by hand. OEA generates screenshots automatically, so localized screenshots cost only an additional test run per language.
