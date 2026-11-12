---
title: Solutions
covers:
  screens: [SCR-060, SCR-061]
  stories: [US-038, US-039]
---

# Solutions

A solution is the container of a change initiative, such as a new module, a replaced interface or a platform migration. It states in its **architecture vision** what the initiative wants to achieve and what it covers, so that everyone involved has the same picture. Later the solution also holds the planned changes to the landscape.

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

Open a solution to see its architecture vision and its baseline:

<Screenshot name="solution" alt="Page of a solution with owner, creation time, architecture vision and baseline" />

Solutions cannot be changed or archived yet.

## Baseline

The area *Baseline* of a solution shows the current IT landscape the initiative builds on: every entity in the catalog today that is not deleted, with its name and metatype, ordered by name. Connections such as data flows are not listed. The baseline is read when you open the solution, so it always shows the catalog as it is now.

- **Search:** type a part of the name, up to 255 characters; upper and lower case do not matter.
- **Filter:** choose a metatype to see only its entities.
- **Pages:** the baseline shows 50 entities per page.

If the catalog holds no entities yet, the area says "No entities in the current landscape – you can only create new entities". If only your search or filter matches nothing, it says so instead.

## Through the API

```bash
curl "${auth[@]}" -d '{"name": "ERP Extension Logistics", "description": "Goal: warehouse logistics in the ERP."}' \
  "$OEA/solutions"
curl "${auth[@]}" "$OEA/solutions"
# $SOLUTION is the "id" of a solution from the list
curl "${auth[@]}" "$OEA/solutions/$SOLUTION/landscape?q=erp&pageSize=50"
```

`POST /solutions` answers 201 with the solution, 422 naming `name` for a blank or taken name, and 403 without `solution:write`. Every creation is recorded in the audit log as `solution.created`.

`GET /solutions/{id}/landscape` answers one page of the baseline with the total and, if a further page exists, a `nextCursor`; send it as `cursor` together with `page` + 1 to load the next page. `metatypeId` filters by metatype.
