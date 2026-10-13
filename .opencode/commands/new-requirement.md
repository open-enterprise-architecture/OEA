# /new-requirement – Create a new atomic requirement

Creates an atomic requirement in `requirements/req/`. Supports all 7 types.

## Arguments

`$ARGUMENTS` optionally contains: `<UC-NN> <type> <short title>`
Example: `/new-requirement UC-05 functional Place entity in diagram`

## Allowed types

`functional` | `non-functional` | `constraint` | `business-rule` | `data` | `interface` | `compliance`

## Execution

1. **Determine the next free ID**:
   ```bash
   ls requirements/req/REQ-*.md | sed 's/.*REQ-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next ID = highest number + 1, three digits (e.g. `REQ-135`).

2. **Check the UC reference**:
   - UC-NN from `$ARGUMENTS` or ask
   - Verify: `ls requirements/use-cases/UC-NN-*.md`
   - If the UC does not exist: STOP

3. **Determine the type**:
   - `type` from `$ARGUMENTS` or ask
   - For `non-functional`: measurable target values are mandatory (no "should be fast" statements)
   - For `compliance`: regulation and concrete article are mandatory

4. **Create the file**:
   Copy `templates/requirement.template.md` to `requirements/req/REQ-NNN-shortname.md`
   - Frontmatter: `id`, `title`, `type`, `priority`, `status: proposed`, `created` (today)
   - Fill `references.use_cases` with UC-NN
   - Keep only the block matching the type under "Type-Specific Fields", remove the rest
   - Formulate at least one acceptance criterion (AC1)

5. **Mandatory checks**:
   - Does the statement contain an RFC 2119 keyword (MUST / SHOULD / MUST NOT / MAY)?
   - Verification method given?
   - For `non-functional`: scope given?

6. **Output**:
   Path, ID, type, UC reference, and a note on mandatory fields that are still open.

## Conventions

- File name: `REQ-NNN-shortname.md` (three digits; short name English, kebab-case)
- One requirement = one statement. Multiple statements → multiple REQs
- No technology specifications in the statement (solution design ≠ requirement)
