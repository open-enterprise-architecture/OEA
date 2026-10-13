# /new-stakeholder – Create a new stakeholder profile

Creates a new stakeholder profile in `business-analysis/stakeholders/`.

## Arguments

`$ARGUMENTS` contains the name or role of the stakeholder (optional).

## Execution

1. **Determine the next free ID**:
   ```bash
   ls business-analysis/stakeholders/SH-*.md | sed 's/.*SH-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next ID = highest number found + 1, two digits with leading zero (e.g. `SH-10`).

2. **Create the profile from the template**:
   Copy `templates/stakeholder.template.md` to `business-analysis/stakeholders/SH-NN-shortname.md`
   - `NN` = next free ID
   - `shortname` = English, kebab-case, derived from `$ARGUMENTS`
   - Replace all `{{placeholders}}` with concrete values

3. **Validation**:
   - Does the profile have `ID`, `Type`, `Relation to the Tool`?
   - Are there at least 2 concerns in the table?
   - Is `Involved Use Cases` filled in or marked as a placeholder?

4. **Update the README**:
   Check whether `business-analysis/stakeholders/README.md` exists; if so, add the SH entry to the overview table.

5. **Output**:
   Path of the new file, ID, and a note on open fields.

## Conventions

- File name: `SH-NN-firstname-or-short-role.md`
- No real names of real persons without consent — use personas instead
- Concept references: §7 and §8 from the template are mandatory references
