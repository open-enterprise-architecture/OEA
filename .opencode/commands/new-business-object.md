# /new-business-object – Create a new business object

Creates a new business object in `business-objects/`. **Model before use cases.**

## Arguments

`$ARGUMENTS` contains the English name of the business object (optional); a German name can be added for `name_de`.

## Execution

1. **Derive the identifier**:
   - English, kebab-case, from `$ARGUMENTS` (e.g. "Anwendungskomponente" → `application-component`)
   - Make sure no BO with this identifier already exists:
     ```bash
     ls business-objects/*.md | grep -i "<identifier>"
     ```

2. **Create the file**:
   Copy `templates/business-object.template.md` to `business-objects/<identifier>.md`
   - Fill in all `{{placeholders}}`
   - Set `identifier`, `name_de`, `name_en`, `version: 0.1.0`, `status: draft`
   - Set `owner_role` to a suitable stakeholder role

3. **Validation**:
   - Do all referenced business objects in `Relationships` exist?
   - Does the BO have at least 3 attributes?
   - Is there a `Definition` in one sentence?
   - Does it have at least one concept reference?

4. **Check delimitation**:
   Read similar existing BOs in `business-objects/` and make sure no duplication arises.

5. **Output**:
   Path of the new file, identifier, and a note on open relationships or concept references.

## Conventions

- File name: `<english-kebab-case-identifier>.md`
- Identifier language-neutral (not `anwendungs-komponente`, but `application-component`)
- Do not create a BO without a definition sentence
- Leave `Business Rules` empty if none are known yet — do not enter a BR-NN placeholder
