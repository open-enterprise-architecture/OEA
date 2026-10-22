# AGENTS.md – Instructions for AI Agents (OpenCode, Claude Code)

This file codifies how work is done in this repository. OpenCode reads it automatically on startup; Claude Code reads it via `CLAUDE.md`. It is binding.

## Project Context

**Project**: OEA – Open Enterprise Architecture (open-source tool for the EA discipline)
**Language**: English for all documentation and code; communication with the repository owner in German (see [Language and Style Conventions](#language-and-style-conventions))
**License**: AGPL-3.0 (Community) / proprietary (Enterprise) – see `LICENSE`, `LICENSES/LICENSE-ENTERPRISE.md`, `LICENSES/CLA.md`
**Phase**: Implementation – walking skeleton (requirements phase completed, see `docs/walking-skeleton.md` and `docs/sprint-plan.md`)
**Vision**: see `business-analysis/vision.md`

## Repository Structure

```text
oea/
├── concept/              ← concept paper (v0.15+), 24 chapters + changelog
├── business-analysis/    ← vision, 7 stakeholder profiles, concerns
├── business-objects/     ← domain model: business objects, capabilities, business rules
├── requirements/
│   ├── use-cases/        ← goal-oriented UCs
│   ├── req/              ← atomic requirements (all types)
│   ├── user-stories/     ← granular backlog items
│   └── nfr/              ← legacy NFRs (can be migrated to req/)
├── adrs/                 ← Architectural Decision Records
├── openspec/             ← implementation specs and changes (OpenSpec, see openspec/README.md)
├── templates/            ← templates for stakeholder, BO, UC, REQ, US, NFR
├── docs/                 ← workflow, quick reference, anti-patterns
├── manual/               ← user manual (VitePress, English and German, ADR-045) – created with package S2c
├── scripts/              ← helper scripts (validation, generation)
├── .opencode/            ← OpenCode: commands, skills, agents (source of truth)
├── .claude/              ← Claude Code: derived only (symlinks, generated files)
├── .github/              ← issue templates, PR template, CI workflows
├── install-concept.sh    ← setup script for concept distribution
├── CLAUDE.md             ← Claude Code entry point, refers to this file
└── AGENTS.md             ← this file
```

Code is created with the walking skeleton in `backend/` (Java 25 / Spring Boot / Maven), `frontend/` (Vue 3) and `api/openapi.yaml` (spec-first contract), see ADR-027.

## Current Phase: Implementation (Walking Skeleton)

Implementation happens in OpenSpec changes (`openspec/README.md`), cut from the user stories of the sprint plan. API changes always go into `api/openapi.yaml` first (ADR-013, spec-first).

**Tests are written as part of the OpenSpec change that implements the behavior** – derived from the change's spec scenarios and listed in its `tasks.md`. No test suites are created ahead of the code.

**Completed approach of the requirements phase** (domain-model-first, still applies to new business functionality):

1. Write out the vision in `business-analysis/vision.md` (✓ done)
2. Create 7 stakeholder profiles in `business-analysis/stakeholders/` (✓ done)
3. Model first business objects with the business engineer (`business-objects/`)
4. Derive use cases in `requirements/use-cases/` (goal-oriented, not CRUD)
5. Derive requirements from UCs with the requirements engineer (`requirements/req/`, all 7 types)
6. Prioritize using MoSCoW
7. Break down user stories for MUST use cases
8. ADRs for blocking decisions (group A: ADR-001 to ADR-005)
9. Tech stack decisions (ADR-011 frontend, ADR-012 backend, ADR-013 API)
10. Walking skeleton definition (exactly one end-to-end use case)

**Do not start use cases without stakeholders** – this leads to generic functionality.
**Do not start use cases without business objects** – otherwise scenarios without references emerge.

## Language and Style Conventions

### Communication

- **German** in conversation with the repository owner (chat answers, questions, summaries)
- Everything written to the repository is **English** (see below)

### Documentation

- **English** for all new and changed documentation (Markdown, ADRs, OpenSpec artifacts, templates, commit messages, PR descriptions)
- The repository was fully translated to English in October 2026; new German content is not accepted – exception: the German locale of the user manual in `manual/de/` ([ADR-045](adrs/ADR-045-user-manual.md)), translated from the English source in the same pull request
- Established German domain terms may be kept where no precise English equivalent exists (e.g. "Bebauungsplan"); add a short English explanation on first use
- **Neutral, factual tone**; no second-person address in documentation
- **Markdown** as format, with frontmatter where useful
- **Diagrams**: notation still open (see §21.2.1 in the concept). Until then: describe descriptively, use inline Mermaid if needed

### Code

- **English** code identifiers (variables, functions, classes) and code comments (international, OSS-friendly)

## Git Conventions

### Commit Messages: Conventional Commits

Format: `<type>(<scope>): <subject>`

Examples:

- `feat(requirements): add UC-03 Bebauungsplan export`
- `docs(concept): clarify optional logical layer in §6.1.1`
- `fix(scripts): correct link validator path handling`
- `chore(adrs): add ADR-006 stub for persistence choice`

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`

### Branches

- `main` is always deployable / readable
- Feature branches: `feature/<short-description>`
- Docs branches: `docs/<short-description>`
- ADR branches: `adr/<NNN-short-name>`

### Pull Requests

Every PR needs:

- a Conventional-Commits-compliant title
- a description with context
- a link to the related use case, NFR or ADR
- passing CI (see quality gates)

## Quality Gates (CI)

Before every merge to `main`:

1. **Link validation**: `python3 scripts/validate_links.py` – must pass
2. **Screen inventory**: `python3 scripts/check_screens.py` – inventory, stories and images consistent (ADR-031)
3. **Traceability matrix**: `requirements/traceability.md` is generated – run `python3 scripts/generate_traceability.py` after changing UCs, REQs or USs (CI checks it with `--check`)
4. **Markdown lint** (once set up)
5. **ADR consistency**: every ADR referenced in `adrs/` exists
6. **Use case completeness**: every use case has a persona, acceptance criteria and a concept reference
7. **Third-party licenses**: `THIRD-PARTY-LICENSES.md` is generated – after adding or updating a dependency run `./mvnw license:add-third-party` (in `backend/`), `npm run build` (in `frontend/`) and `python3 scripts/generate_third_party_licenses.py`; CI checks with `--check` that every distributed component has a license allowed by ADR-043 and that the file is current

## Versioning

### Concept Paper

- Status in `concept/README.md` (format: `**Status:** Draft vX.Y`)
- On change: entry in `concept/CHANGELOG.md` (format: Keep a Changelog)
- Minor bump for new sections/chapters
- Patch bump for corrections

### ADRs

- Numbered sequentially (ADR-001, ADR-002, ...)
- Numbers are never reused, even if an ADR is marked `superseded`
- Status transitions: `draft → proposed → accepted | rejected`; later possibly `superseded by ADR-NNN`

### Use Cases / User Stories / NFRs

- Track status per file (see templates)
- On the status transition `accepted → realized`, record the implementation hash

## Agent Configuration: Single Source of Truth

OpenCode and Claude Code must always work with the same information. Every piece of configuration has exactly one source; everything in `.claude/` and `CLAUDE.md` is derived and never edited by hand.

| Information | Source of truth (edit here) | Claude Code receives it via |
|---|---|---|
| Working agreement, rules | `AGENTS.md` | `CLAUDE.md` contains only `@AGENTS.md` |
| Project slash commands | `.opencode/commands/*.md` | symlinks in `.claude/commands/` |
| Agent roles | `.opencode/agents/*.md` | generated `.claude/agents/*.md` (incl. permissions as binding instructions) |
| OpenSpec commands and skills | OpenSpec CLI + `openspec/config.yaml`, `openspec/schemas/` | `openspec update` regenerates both tool directories |

After changing a source:

```bash
python3 scripts/sync_agent_config.py   # refresh .claude/ (commands, agents, CLAUDE.md)
openspec update                        # only after an OpenSpec upgrade
```

CI runs `python3 scripts/sync_agent_config.py --check` and fails on any drift (changed, missing or stale files, differing OpenSpec versions). Tool-specific content in `CLAUDE.md` or hand-written files in `.claude/` are not allowed.

## Slash Commands

All commands below are available in both OpenCode and Claude Code.

### Project commands (source: `.opencode/commands/`)

See the individual files for details:

- `/new-stakeholder` – creates a stakeholder profile from the template
- `/new-business-object` – creates a business object from the template (before use cases)
- `/new-usecase` – creates a use case, checks stakeholder and business object references
- `/new-requirement` – creates an atomic requirement from the template (all 7 types)
- `/new-story` – creates a user story, checks the use case reference
- `/new-nfr` – creates an NFR, reminds of measurable target values (legacy – can be replaced by `/new-requirement` with `type: non-functional`)
- `/new-adr` – creates an ADR with the next free number
- `/trace-check` – checks link consistency of all artifacts
- `/release` – bumps the concept version and updates the CHANGELOG

### OpenSpec commands (OpenCode and Claude Code)

| Purpose | OpenCode | Claude Code |
|---|---|---|
| Explore | `/opsx-explore` | `/opsx:explore` |
| Propose a change | `/opsx-propose` | `/opsx:propose` |
| Implement a change | `/opsx-apply` | `/opsx:apply` |
| Revise a change | `/opsx-update` | `/opsx:update` |
| Sync delta specs | `/opsx-sync` | `/opsx:sync` |
| Archive a change | `/opsx-archive` | `/opsx:archive` |

These files and the matching skills in `.opencode/skills/` and `.claude/skills/` are generated by the OpenSpec CLI. Do not edit them by hand; refresh them with `openspec update` (see `openspec/README.md`).

## Agents (in `.opencode/agents/`)

Seven agent roles for vibe coding. In OpenCode, all can be used as a primary agent (agent switch in the TUI) and as a subagent (`@<agent-name>`); in Claude Code they are available as subagents (generated into `.claude/agents/`). Write access is restricted per role to its own directory scope – enforced in OpenCode, binding as instruction in Claude Code:

- `business-engineer.md` – domain model first (before UC)
- `solution-architect.md` – clarification, scope, specs, tickets
- `requirements-engineer.md` – atomic requirements from UCs
- `ui-designer.md` – clickable Vue prototypes (Storybook stories), design system
- `backend-engineer.md` – backend (type safety mandatory)
- `frontend-engineer.md` – frontend (TypeScript mandatory)
- `security-engineer.md` – cross-cutting security reviews

Workflow see `docs/workflow-example.md`.

## Git Access in Distrobox

In the Distrobox environment, git is not directly available and is provided via a symlink.

Setup (once per host):

```bash
mkdir -p ~/.distrobox/bin
ln -sf /run/host/var/lib/flatpak/runtime/org.freedesktop.Sdk/x86_64/25.08/*/files/libexec/git-core/git ~/.distrobox/bin/git
```

Usage:

```bash
export PATH="$HOME/.distrobox/bin:$PATH"
git --version
```

The Flatpak runtime version may change. If the symlink no longer works, locate the current git binary:

```bash
find /run/host/var/lib/flatpak/runtime/org.freedesktop.Sdk -name "git" -type f 2>/dev/null | head -1
```

## Key Principles for Working with AI Agents

### Before every change

1. Read existing files before creating new ones
2. Check concept references – changes to requirements often affect the concept
3. In case of conflict: ask rather than assume

### On concept changes

- ALWAYS update `concept/CHANGELOG.md`
- ALWAYS bump the status in `concept/README.md`
- ALWAYS run link validation
- ALWAYS check whether `concept/INDEX.md` needs updating

### On new use cases

- ALWAYS check the persona reference (stakeholder must exist)
- ALWAYS set at least one concept reference
- ALWAYS phrase acceptance criteria as testable statements

### On new NFRs

- ALWAYS require measurable target values (no "should be fast" statements)
- ALWAYS name the verification method
- ALWAYS state the scope (at which data volume the value applies)

### On new ADRs

- ALWAYS document all options with pros/cons
- ALWAYS make decision drivers explicit
- ALWAYS link affected concept chapters
- NEVER reuse an ADR number (not even for rejected ones)

### On Screen Changes

UI prototypes are Storybook stories with real Vue/Nuxt UI components (ADR-031, ADR-040). `docs/screens/SCREENS.md` is the binding screen inventory. It MUST be checked and updated on each of the following events:

| Event | Action |
|---|---|
| New screen identified | Add an entry to SCREENS.md: ID, name, platform, UC reference, priority; status `planned` |
| Screen story created (`frontend/src/screens/SCR-NNN-*.stories.ts`) | Set status to `prototype`; replace the legacy SVG links by the generated `SCR-NNN-light.png` / `-dark.png` and delete the legacy SVGs |
| Screen implemented in the application | Set status to `implemented` |
| Screen dropped or merged | Remove the entry and its story and images |

Screen images (`docs/screens/SCR-NNN-*.png`) are generated by CI from the stories and are never edited by hand. `python3 scripts/check_screens.py` checks inventory, stories and images.

### On Data Model Changes

The data model consists of two sources that must be kept in sync:

- `docs/data-model.puml` – PlantUML overall diagram (classes, attributes, relations, SQL constraints)
- `business-objects/<name>.md` – individual description per business object

**Triggers for a data model check** – on each of the following events it MUST be checked whether the data model is still current and needs adjustment:

| Event | What to check |
|---|---|
| New business object (`/new-business-object`) | Add the class to `data-model.puml`; enter attributes, types, relations |
| New requirement with data impact | Check the affected BO in `business-objects/<name>.md`; add the new attribute/relation to `data-model.puml` |
| New use case referencing a previously unknown BO | Create the BO, then extend the data model |
| New ADR with persistence impact (e.g. soft delete, audit log, new table) | Apply the consequences in `data-model.puml` |
| Changed business object (attribute renamed, type changed, relation removed) | Keep `data-model.puml` in sync; check existing REQs and USs for consistency |

**Concrete steps for every change:**

1. Open `docs/data-model.puml` and check whether the affected BO/attribute is already contained
2. If missing or outdated: add/correct the class, attributes (with type and visibility), relations and SQL constraints
3. If the corresponding `business-objects/<name>.md` does not know an attribute yet: add it there as well
4. Briefly note why the change was necessary (e.g. as a comment in `data-model.puml` or in the BO file under "Notes")

**Rule of thumb:** every attribute that appears in a REQ or a user story must also be visible in the data model – at the latest before the corresponding user story is marked `accepted`.

## Anti-Patterns

Avoid explicitly:

### Use Cases

- **CRUD use cases**: "Create ApplicationComponent" is not a use case but a function. Use cases are goal-oriented.
- **Use cases without a persona**: generic use cases lead to generic functionality.
- **Use cases without acceptance criteria**: without testability it is a wish, not a use case.

### NFRs

- **"Be performant"** without a number
- **NFR without a verification method**
- **NFR without a scope** (at which data volume?)

### ADRs

- **"We chose X because it is the standard"** – without documented alternatives
- **ADR without consequences** – every decision has trade-offs
- **ADR in advance** – ADRs should solve a concrete problem, not be created speculatively

### Concept

- **Fixing notation-specific examples** – see §21.2.1, notation is still open
- **Forced depth** – the logical layer is optional, this is a core principle

### Data Model

- **Creating a requirement, forgetting the data model** – every REQ/US with data impact must leave a trace in the data model
- **Letting `data-model.puml` and `business-objects/*.md` diverge** – both sources must be in sync; maintaining only one creates hidden contradictions
- **Describing attributes in requirements without typing them** – type, value range and mandatory status belong in the BO, not only in the prose of a REQ

### Tests

- **Writing test suites ahead of the code** – tests are created within the OpenSpec change that implements the behavior

## Definition of Done for the Requirements Phase

The requirements phase is complete when:

- [ ] Vision formulated and reviewed
- [ ] At least 3 stakeholder profiles with concerns
- [ ] At least 10 use cases, all prioritized (MoSCoW)
- [ ] At least 5 NFRs with measurable target values
- [ ] Group A ADRs (001 to 005) decided or deliberately deferred
- [ ] Walking skeleton use cases identified
- [ ] Traceability matrix maintained
- [ ] Trace check without warnings

Only then does the implementation phase start.

## Contact for Clarifications

For conceptual questions: repository owner Lukas Mathis (sole copyright holder and licensor, see `NOTICE`).
For structural questions about AGENTS.md: PR with rationale.
