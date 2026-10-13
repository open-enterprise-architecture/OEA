# /new-adr – Create a new ADR

Creates a new Architectural Decision Record in `adrs/`. The next free number is determined automatically.

## Arguments

`$ARGUMENTS` contains the short title of the ADR (optional).
Example: `/new-adr Logging strategy`

## Execution

1. **Determine the next free number**:
   ```bash
   ls adrs/ADR-*.md | sed 's/.*ADR-//' | sed 's/-.*//' | sort -n | tail -1
   ```
   Next number = highest number + 1, three digits with leading zeros (e.g. `ADR-019`).
   ADR numbers are **never reused** — not even for `rejected`.

2. **Derive the file name**:
   `adrs/ADR-NNN-shortname.md`
   - Short name from `$ARGUMENTS`: English, kebab-case, 2-4 words
   - Example: "Logging strategy" → `ADR-019-logging-strategy.md`

3. **Create the file** with the following skeleton:

   ```markdown
   # ADR-NNN: {{Title}}

   **Status**: draft
   **Date**: {{YYYY-MM-DD}}
   **Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
   **Consulted**: {{if applicable: Requirements Engineer, Business Engineer, …}}
   **Informed**: –

   ## Context and Problem

   {{What is the concrete problem this decision solves? Which situation forces the decision now?}}

   ## Decision Drivers

   - {{Driver 1}}
   - {{Driver 2}}

   ## Considered Options

   ### Option 1: {{Name}}

   - **Pro**: …
   - **Con**: …

   ### Option 2: {{Name}}

   - **Pro**: …
   - **Con**: …

   ## Decision

   We choose **Option N: {{Name}}**.

   **Rationale**: …

   ## Consequences

   ### Positive Consequences

   - …

   ### Negative Consequences / Trade-offs

   - …

   ## References

   **Related ADRs**: …
   **Concept**: …
   ```

4. **Mandatory checks**:
   - At least 2 options, each with pro/con?
   - Decision drivers explicit (no "because it is standard")?
   - Consequences (both directions) present?
   - Concept reference set?
   - `Deciders` set to the repository owner (Lukas Mathis)?

5. **Avoid anti-patterns**:
   - No ADR on stock — only when there is a concrete need for a decision
   - No ADR without documented alternatives
   - No ADR without consequences

6. **Output**:
   Path, number, title, status `draft`.

## Conventions

- `draft` → `proposed` → `accepted | rejected`; later possibly `superseded by ADR-NNN`
- Never reuse numbers
- Filename: English, kebab-case
