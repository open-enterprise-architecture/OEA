# /release – Bump the concept version and update the CHANGELOG

Creates a release entry in the CHANGELOG and updates the status in the concept README.

## Arguments

`$ARGUMENTS` optionally contains: `<bump-type> "<description>"`
- `bump-type`: `minor` (new chapter/section) | `patch` (corrections)
- Example: `/release minor "New chapter §24 Monitoring strategy"`
- Without arguments: ask interactively

## Execution

1. **Read the current version**:
   ```bash
   grep -m1 "Status:" concept/README.md
   ```
   Format: `**Status:** Draft vX.Y`

2. **Calculate the new version**:
   - `minor`: vX.Y(.Z) → vX.(Y+1) (new section/chapter)
   - `patch`: vX.Y.Z → vX.Y.(Z+1); a two-part vX.Y becomes vX.Y.1 (correction)
   - Check the current scheme: `vX.Y` (two-part) or `vX.Y.Z` (three-part)

3. **Update the CHANGELOG** (`concept/CHANGELOG.md`):
   Format: Keep a Changelog
   ```markdown
   ## [vX.Y] – YYYY-MM-DD

   ### Added / Changed / Fixed / Removed
   - {{Description of the changes}}
   ```
   - Insert the new entry **at the top** under `## [Unreleased]` (or replace the `[Unreleased]` block)
   - Date: today

4. **Update the status in `concept/README.md`**:
   ```
   **Status:** Draft vX.Y  →  **Status:** Draft vX.(Y+1)
   ```

5. **Check INDEX.md** (`concept/INDEX.md`):
   - Has the chapter structure changed? If so: adjust the INDEX.

6. **Run link validation**:
   ```bash
   python3 scripts/validate_links.py
   ```
   Must be green before the commit.

7. **Create the git commit**:
   ```
   chore(concept): Release vX.Y – <short description>
   ```

8. **Output**:
   Old version, new version, CHANGELOG entry preview.

## Conventions

- Minor bump: new sections, new chapters, extensions of content
- Patch bump: corrections, clarifications, typos
- No release without a CHANGELOG entry
- No release without green link validation
