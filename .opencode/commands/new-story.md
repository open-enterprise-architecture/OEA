# /new-story – Create a new user story

Creates a user story in `requirements/user-stories/`. Checks the use case reference.

## Arguments

`$ARGUMENTS` optionally contains: `<UC-NN> <short title>`
Example: `/new-story UC-05 Create delta entity as node in diagram`

## Execution

1. **Determine the next free ID**:
   ```bash
   ls requirements/user-stories/US-*.md | sed 's/.*US-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next ID = highest number + 1 (e.g. `US-132`).

2. **Check the UC reference**:
   - UC-NN from `$ARGUMENTS` or ask
   - Verify: `ls requirements/use-cases/UC-NN-*.md`
   - If the UC does not exist: STOP — the UC must be created first

3. **Check the REQ reference**:
   - Which REQ does this story belong to? (at least one)
   - Verify: `ls requirements/req/REQ-NNN-*.md`
   - If the REQ does not exist: call `/new-requirement` first

4. **Clarify the persona**:
   - Read the `primary_actor` of the referenced UC → persona of the story
   - Story wording: "As [persona from UC] I want …"

5. **Create the file**:
   Copy `templates/user-story.template.md` to `requirements/user-stories/US-NNN-shortname.md`
   - Set `ID`, `Story Points`, `Status: backlog`, `Sprint: unassigned`
   - `Reference`: link UC-NN and REQ-NNN
   - Formulate at least 2 acceptance criteria (AC1, AC2)

6. **INVEST check**:
   - Independent: Does the story depend on another story that is not done yet?
   - Small: Can it be implemented in 1-3 days? (> 8 SP → split)
   - Testable: Are the ACs verifiable without interpretation?

7. **Estimate story points** (Fibonacci: 1, 2, 3, 5, 8, 13):
   - 1-2 SP: trivial UI adjustment or simple API endpoint
   - 3-5 SP: typical feature story with backend + frontend
   - 8 SP: complex story; consider whether a split makes sense
   - 13 SP: too large, must be split

8. **Output**:
   Path, ID, story points, UC and REQ reference.

## Conventions

- File name: `US-NNN-shortname.md` (three digits; short name English, kebab-case)
- Stories describe ONE task — no multi-feature in one story
- Fill in the Definition of Done checklist completely
