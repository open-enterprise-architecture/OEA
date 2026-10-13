# /new-usecase – Create a new use case

Creates a new use case in `requirements/use-cases/`. Checks the stakeholder and business object references.

## Arguments

`$ARGUMENTS` contains the title of the use case (optional).

## Execution

1. **Determine the next free ID**:
   ```bash
   ls requirements/use-cases/UC-*.md | sed 's/.*UC-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next ID = highest number + 1, two digits (e.g. `UC-22`).

2. **Clarify the stakeholder reference** (ask before creating if it cannot be derived from `$ARGUMENTS`):
   - Who is the `primary_actor`? → must exist as `SH-NN` in `business-analysis/stakeholders/`
   - Verify: `ls business-analysis/stakeholders/SH-NN-*.md`

3. **Clarify the business object reference**:
   - Which business objects does this UC use? → must exist in `business-objects/`
   - Verify: `ls business-objects/<identifier>.md`
   - If a BO is missing: STOP — call `/new-business-object` first

4. **Create the file**:
   Copy `templates/use-case.template.md` to `requirements/use-cases/UC-NN-shortname.md`
   - Set `id`, `title`, `primary_actor`, `created` (today), `status: draft`
   - Replace all `{{placeholders}}`
   - Set at least one concept reference (§ reference in the concept directory)

5. **Anti-pattern check**:
   - Is the title goal-oriented (NOT: "create X", "edit X")?
   - Does the UC have real acceptance criteria (not just placeholders)?
   - Does the UC have a story formulation ("As … I want … so that …")?

6. **Output**:
   Path, ID, primary_actor, referenced BOs, and a note on open fields.

## Conventions

- File name: `UC-NN-shortname.md` (short name: English, kebab-case, 2-4 words)
- Avoid CRUD use cases (no "create entity" without a goal context)
- At least 1 concept reference is mandatory
