# Consolidation Notes

This document summarizes what is contained in the consolidated tar archive and which manual steps are still required. **Once it has been successfully adopted into your working repo, this file can be deleted.**

## What Is in the Archive

| Area | Content |
|---|---|
| Concept | Complete (v0.15, 24 chapters + changelog), 163 links validated |
| Vision | Written out in `business-analysis/vision.md` |
| Stakeholders | 7 personas (Franz, Lukas, Kurt, Michael, CIO, Max, Sabine) |
| Business Objects | Folder structure created, README with approach, empty for content |
| Requirements | UC, REQ, US, NFR folders with README, empty for content |
| ADRs | 5 group A stubs (ADR-001 to ADR-005), template |
| Templates | 6 templates (stakeholder, business object, use case, requirement, user story, NFR) |
| Agents | 7 agent definitions in `.claude/agents/` with workflow README |
| Slash commands | 9 commands in `.claude/commands/` |
| Docs | 3 onboarding documents (workflow example, quick reference, anti-patterns) |
| OSS standards | LICENSE, NOTICE, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, MAINTAINERS, README |
| Config | .editorconfig, .gitattributes, .markdownlint.json, .commitlintrc.json, .pre-commit-config.yaml |
| CI | 3 workflow files (docs/backend/frontend), issue and PR templates |
| Scripts | `install-concept.sh`, `scripts/validate_links.py` |

## Manual Steps Before Go-Live

### Mandatory
- [ ] Set up email addresses and replace placeholders:
  - `security@oea.org` (SECURITY.md)
  - `conduct@oea.org` (CODE_OF_CONDUCT.md)
  - `maintainers@oea.org` (MAINTAINERS.md)
- [ ] Reserve domain: `oea.org`, `oea.dev`, and/or `oea.ch`
- [ ] Configure Git remote (GitHub org, Gitea instance, or self-hosted)
- [ ] MAINTAINERS.md: fill the lead maintainer line with your name
- [ ] Update repository URL in `.github/ISSUE_TEMPLATE/config.yml`

### Recommended Before the First Feature
- [ ] Read through CLAUDE.md and adapt it to your understanding of the workflow
- [ ] Skim `docs/quick-reference.md` once
- [ ] First question to the Solution Architect: "What is the minimal walking skeleton use case?"

### Optional
- [ ] Design a logo (not strictly necessary for the first release)
- [ ] Trademark search for "OEA" / "Open Enterprise Architecture" in DE, EU, US (conflicts unlikely, but good practice)
- [ ] Set up Penpot workspace (for UI designer)

## What Is Deliberately Empty

These folders have a README with the approach, but are empty in terms of content:

- `business-objects/*.md` – You model them yourself (or with the Business Engineer)
- `requirements/use-cases/*.md` – You formulate them yourself (or with the Solution Architect)
- `requirements/req/*.md` – Requirements Engineer derives them from UCs
- `requirements/user-stories/*.md` – During implementation planning
- `requirements/nfr/*.md` – Legacy, possibly migrate to req/
- `business-analysis/concerns/*.md` – Cross-persona concerns as needed

This is not a deficiency – it is intentional. The real content comes from your head, not from a template.

## What Changed Compared to the Individual Files

What I consolidated:

- Stakeholder README extended with an overview table of all 7 personas with observations
- Templates README extended with the two new templates (business object, requirement)
- Agents README extended with Business Engineer and Requirements Engineer
- Workflow diagram in Agents README extended
- CLAUDE.md updated: OEA as project name, new folders, new slash commands, new agents, Apache 2.0 instead of TBD (superseded: the project is dual-licensed AGPL-3.0 / proprietary, see `NOTICE`)
- Vision written out in `business-analysis/vision.md` (was previously a placeholder)
- 2 new slash commands: `/new-business-object`, `/new-requirement`
- `business-objects/`, `requirements/req/` created with README
- `docs/` created with README
- `.github/` complete with issue templates, PR template, CI workflows
- `MIGRATION-NOTES.md` for the naming decision and possible future adjustments

## First Steps After Unpacking

```bash
tar xzf oea-complete.tar.gz
cd oea
git init
git add .
git commit -m "chore: initial repository skeleton with concept v0.15"

# First checks
python3 scripts/validate_links.py
# Should print: "Checked: 163 relative links in concept/" and "✓ All links valid"

# Install pre-commit (optional, but recommended)
pip install pre-commit
pre-commit install
```

Then open in Claude Code and model the first business object with the Business Engineer:

```
Please act as Business Engineer (.claude/agents/business-engineer.md) 
and model "Application Component" as the first business object.
Use templates/business-object.template.md.
```

Good luck.
