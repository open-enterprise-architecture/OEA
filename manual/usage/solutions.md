---
title: Solutions
covers:
  screens: [SCR-060, SCR-061, SCR-062]
  stories: [US-038, US-039, US-040, US-041, US-042, US-043, US-044]
---

# Solutions

A solution is the container of a change initiative, such as a new module, a replaced interface or a platform migration. It states in its **architecture vision** what the initiative wants to achieve and what it covers, so that everyone involved has the same picture. The solution also holds the planned changes to the landscape: the entities the initiative introduces, the ones it modifies and the ones it retires.

You need the permission `entity:read` (for example the role `viewer`) to see solutions. To create one, you need `solution:write`: the roles `solution-architect` and `enterprise-architect` hold it (see [Roles and permissions](../administration/roles-and-permissions)).

## Create a solution

1. Choose *Solutions* in the header, then *New solution*.
2. Fill in the dialog *New solution*:

   | Field | Meaning |
   |---|---|
   | Name | required, up to 255 characters; unique within the instance, regardless of upper and lower case and of spaces at the start and end |
   | Architecture vision | optional, up to 2000 characters: the goal, the scope and the rationale of the initiative. Line breaks are kept |

<Screenshot name="solution-create" alt="Dialog New solution with name and architecture vision" />

3. Choose *Create solution*. The page of the new solution opens.

If the name is already taken, the dialog says so, for example "Name 'ERP Extension Logistics' is already taken"; choose another name. Without `solution:write`, you see the solutions but not *New solution*.

A new solution has the status *Draft*, and you are its owner. It is created in **project mode**: without a baseline and target plateau. Plateau mode follows with the plateaus.

## View solutions

The page *Solutions* lists every solution with its status, owner and creation date, ordered by name:

<Screenshot name="solutions" alt="List of solutions" />

Open a solution to see its architecture vision, its changes and its baseline:

<Screenshot name="solution" alt="Page of a solution with owner, creation time, architecture vision, changes and baseline" />

The name, the vision and the status of a solution cannot be changed yet.

## Baseline

The area *Baseline* of a solution shows the current IT landscape the initiative builds on: every entity in the catalog today that is not deleted, with its name and metatype, ordered by name. Connections such as data flows are not listed. The baseline is read when you open the solution, so it always shows the catalog as it is now.

- **Search:** type a part of the name, up to 255 characters; upper and lower case do not matter.
- **Filter:** choose a metatype to see only its entities.
- **Pages:** the baseline shows 50 entities per page.

If the catalog holds no entities yet, the area says "No entities in the current landscape – you can only create new entities". If only your search or filter matches nothing, it says so instead.

## Changes

The area *Changes* lists what the initiative changes in the landscape: new entities first, then modified entities with their number of changes, then the entities it retires, each by name. It shows 50 changes and the total; *Load more* adds the next 50. With `solution:write` you add and remove changes in every solution, not only in your own.

- **Modify an entity:** choose *Modify* in its row of the baseline. The dialog *Modify entity* shows each field with its current value and an input for the new one: the name, the description and the properties. Enter only what changes; clearing a property removes its value. The current values are recorded as the values before the change when you save, and they stay as recorded even if the catalog changes later. A field you enter with its current value is not a change, and a modification needs at least one change. The entity appears in *Changes* as *Modified*, and its baseline row shows *Modified*.
- **Edit a modification:** choose *Edit* in its row of *Changes*. The dialog starts from the new values; what you save replaces them. A field that was already part of the modification keeps its recorded value before the change. A change of a restricted property you may not read stays as it is.
- **Retire an entity:** choose *Retire* in its row of the baseline. The entity appears in *Changes* as *Retiring*, and its baseline row shows *Retiring* instead of the action.
- **Create a new entity:** choose *New entity* and fill in the dialog: the metatype, a name of up to 255 characters, an optional description of up to 2000 characters, and the properties of the metatype. Properties marked as required need a value. The new entity is not written to the catalog yet: it enters the catalog with the go-live of the solution, and until then it is not part of the baseline.
- **Remove a change:** choose *Remove* in its row and confirm. A retired entity can then be retired again, for example after a mistake.

The rules:

- A solution holds one change per entity: the same entity cannot be retired twice or both modified and retired, and two new entities of the same metatype cannot have the same name (upper and lower case do not matter). Another solution can change the same entity; both then show a warning (see [Parallel changes](#parallel-changes)).
- A value must fit its property, for example a whole number for an integer property, and the value of a required property cannot be removed. If a value is rejected, the dialog shows why at the field.
- A restricted property can only be set if one of your roles may write it (see [Property visibility](../administration/property-visibility)). A connection-scoped property cannot be set for a new entity, because its connections do not exist yet; for an existing entity, the entity must be in your scope. Values you may not read appear as hidden.
- A solution can hold any number of changes.
- An implemented or archived solution is completed: it cannot be changed, and its changes stay as they are.

## Compare

*Compare* in the area *Changes* opens the comparison of the solution. For each change, it shows the entity as it is in the catalog now, the *current state*, next to how it will be after the solution, the *target state*. Entities without a change do not appear. On a narrow screen, the two states are shown one below the other.

<Screenshot name="solution-diff" alt="Comparison of a solution with new, modified and retiring entities" />

- **New:** the current state says "Does not exist yet"; the target state shows the new entity with its values.
- **Modified:** both states show every field with a value. The fields the solution changes are highlighted and marked as changing; the other fields are shown in a muted color and stay as they are.
- **Retiring:** the target state says "Out of service" and shows the entity struck through.
- **Changed in the catalog since capture:** a modification records the values before the change when you save it. If the catalog holds another value today, the current state shows today's value with this note. Check whether the modification still fits.
- **No longer in the catalog:** the entity was deleted after the change was captured; the current state is empty.

Values you may not read appear as hidden. The comparison is computed each time you open it; *Refresh* loads it again, for example after someone else captured a change. It shows 50 changes at a time; *Load more* adds the next 50.

## Parallel changes {#parallel-changes}

Several initiatives often run at the same time. When another active solution modifies or retires an entity that your solution modifies or retires too, OEA warns you, so that you can coordinate with its owner before the go-live. A solution is active until it is implemented or archived. New entities never conflict, because they do not exist in the catalog yet.

- **On the change:** its row in *Changes* says "Also changed in '…' (owner: …)" for each other solution; the name opens that solution. The comparison shows the same note on the card of the entity.
- **At the top of the solution:** a notice counts the changes that concern other active solutions.
- **When you save:** retiring or modifying an entity that another solution changes shows a notice with the names of the other solutions. Close it when you have read it; the warning on the change stays.
- **In the baseline:** an entity that another active solution changes is marked "Changed in … other solutions", with their names, before you capture anything. This marker appears only for the entity types for which your metamodel administrators switched it on (see [Parallel changes in the baseline](../administration/metamodel-editing#parallel-changes)).

The warning never blocks a change. It is computed each time you open the solution, so it disappears as soon as the other solution is implemented or archived, or removes its change.

## Through the API

```bash
curl "${auth[@]}" -d '{"name": "ERP Extension Logistics", "description": "Goal: warehouse logistics in the ERP."}' \
  "$OEA/solutions"
curl "${auth[@]}" "$OEA/solutions"
# $SOLUTION is the "id" of a solution from the list
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape?q=erp&pageSize=50"
# retire entity 42 of the baseline, then list and remove the changes
curl "${auth[@]}" -d '{"deltaType": "retiring", "entityId": 42}' "$OEA/solutions/$SOLUTION/deltas"
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/deltas?pageSize=50"
curl "${auth[@]}" -X DELETE "$OEA/solutions/$SOLUTION/deltas/$DELTA"
# modify entity 43: its current values, then the new value of a property
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape/43"
curl "${auth[@]}" -d '{"deltaType": "modified", "entityId": 43, "properties": [{"propertyDefId": "'"$RELEASE"'", "value": "v5"}]}' \
  "$OEA/solutions/$SOLUTION/deltas"
# the comparison of current and target state
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/diff?pageSize=50"
```

`POST /solutions` answers 201 with the solution, 422 naming `name` for a blank or taken name, and 403 without `solution:write`. Every creation is recorded in the audit log as `solution.created`.

`GET /solutions/{id}/landscape` answers one page of the baseline with the total and, if a further page exists, a `nextCursor`; send it as `cursor` together with `page` + 1 to load the next page. `metatypeId` filters by metatype.

`POST /solutions/{id}/deltas` captures a change: `{"deltaType": "retiring", "entityId": …}`, or `{"deltaType": "new", "metatypeId": …, "name": …, "description": …, "properties": [{"propertyDefId": …, "value": "…"}]}` with the values as text, as for an entity write. It answers 201 with the change, 422 with `invalidFields` and a message per field in `errors`, 403 for a restricted value you may not write, and 409 for a completed solution. Adding and removing a change is recorded in the audit log as `solution.delta-added` and `solution.delta-removed`, without the property values.

A modification is `{"deltaType": "modified", "entityId": …, "name": …, "description": …, "properties": […]}` with the new values only; the response carries `changes` with the recorded `before` and the new `after` value of each field. `PUT /solutions/{id}/deltas/{deltaId}` with `name`, `description` and `properties` replaces the changes of a modification and is recorded as `solution.delta-updated`. `GET /solutions/{id}/landscape/{entityId}` returns an entity of the baseline with its current values.

`GET /solutions/{id}/deltas` and `GET /solutions/{id}/diff` answer one page, with `page` and `pageSize` (1 to 200, default 50), the number of changes per type in `counts` and, if a further page exists, a `nextCursor`, which you send as `cursor` together with `page` + 1. Each entry of the diff carries the `fields` of the entity with their `current` and `target` value, `changed` for a field the solution changes and `catalogChanged` for a field whose recorded value differs from the catalog; `inCatalog` is false for a new entity and for one deleted since.

Every change and every entry of the comparison carries `conflicts`: the other active solutions that change the same entity, each with `solutionId`, `solutionName`, `status`, `ownerId`, `ownerName` and the `deltaType` of its change, ordered by name; the list is empty for a new entity. The responses of `POST` and `PUT` carry it as well. `conflicted` in the list of changes counts the changes with at least one conflict, over all pages. In the baseline, `parallelChanges` lists them for the entity types that show parallel changes, and is empty otherwise.
