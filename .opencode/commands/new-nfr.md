# /new-nfr – Create a new NFR (legacy)

> **Note**: This command is legacy. Prefer creating new non-functional requirements with
> `/new-requirement` and setting `type: non-functional` — then the requirement ends up
> in `requirements/req/` with uniform tracking. `/new-nfr` writes to `requirements/nfr/`
> and is meant for older NFRs in the legacy format.

## Arguments

`$ARGUMENTS` contains the title of the NFR (optional).

## Execution

1. **Determine the next free ID**:
   ```bash
   ls requirements/nfr/NFR-*.md 2>/dev/null | sed 's/.*NFR-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next ID = highest number + 1, two digits (e.g. `NFR-06`).

2. **Clarify the category**:
   `performance` | `scalability` | `reliability` | `availability` | `security` |
   `maintainability` | `usability` | `portability` | `compatibility` | `cost` | `sustainability`

3. **Create the file**:
   Copy `templates/nfr.template.md` to `requirements/nfr/NFR-NN-shortname.md`
   - Set `ID`, `Category`, `Priority`, `Status: proposed`
   - Formulate the requirement in one sentence

4. **Mandatory checks** (without these the NFR is not accepted):
   - [ ] **Measurable target value** with unit and threshold in the table?
   - [ ] **Scope** given (for which data volume / load profile does the value apply)?
   - [ ] **Verification method** concrete (tool, test setup, pass criterion)?
   - No "should be performant" statements — always concrete numbers

5. **Stakeholder reference**: at least one SH who requested this NFR or is affected by it.

6. **Output**:
   Path, ID, category, summary of the target value.

## Conventions

- File name: `NFR-NN-shortname.md` (two digits; short name English, kebab-case)
- An NFR without measurable target values is a wish, not a requirement
- For new projects: prefer `/new-requirement type=non-functional`
