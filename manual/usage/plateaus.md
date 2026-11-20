---
title: Plateaus
covers:
  screens: [SCR-065, SCR-066]
  stories: [US-084, US-085, US-086, US-087, US-088, US-089, US-155, US-157]
---

# Plateaus

A plateau is a named, stable state of your architecture. The **baseline** is the architecture in production today. **Target** and **transition** plateaus describe planned states: a target state, and intermediate states on the way to it. [Solutions](./solutions) can work toward a plateau.

There is no switch for plateau mode: it starts when you create the first baseline. Solutions without a plateau keep working as before.

You need the permission `plateau:write` (for example the role `enterprise-architect`) to create and edit plateaus. To see them, `plateau:write`, `plateau:go-live` or `solution:write` is enough (see [Roles and permissions](../administration/roles-and-permissions)).

## The page Plateaus

Choose *Plateaus* in the header. The page shows:

- the **baseline**, highlighted, with the date since when it is valid;
- the **target and transition plateaus**, by planned date, each with its predecessor, its basis and the number of solutions that work toward it. Without one, the page says "No target plateau planned yet";
- the **realized plateaus**, collapsed, newest first. A plateau becomes realized when its successor goes live.

## Create the initial baseline

Without a baseline, the page says "No baseline plateau exists yet". Choose *Create initial baseline*, enter a name such as "Baseline 2026-Q2" and, optionally, a description and the date of the state, and choose *Create plateau*. There is only ever one baseline.

## Create a target or transition plateau

1. Choose *New plateau*.
2. Fill in the dialog:

   | Field | Meaning |
   |---|---|
   | Name | required, up to 255 characters, unique regardless of upper and lower case |
   | Type | *Target* or *Transition* |
   | Description | optional, up to 2000 characters |
   | Valid from | optional: the planned go-live date |
   | Predecessor | the plateau this one follows; the baseline is preselected. A realized plateau cannot be a predecessor |
   | Basis | what the solutions of this plateau start from (see below) |

3. Choose *Create plateau*.

### The basis {#basis}

The basis decides what a solution that works toward the plateau sees as its baseline:

- **Current catalog:** the catalog as it is now.
- **Content of the predecessor:** the predecessor plateau with the changes of its active solutions applied. An entity that a solution of the predecessor retires is marked *Retiring by …*, a new entity it proposes is marked *Proposed by …*, and changed names and values show their planned value. A proposed entity cannot be changed or retired yet, because it does not exist before its go-live. If the predecessor itself has the basis "content of the predecessor", its own predecessor counts as well.

When two solutions of the same plateau change the same field, the change made last counts.

## Edit a plateau

Choose *Edit* next to a plateau to change its name, description, date, predecessor or basis. The type changes only through the go-live. The predecessor is fixed as soon as a solution works toward the plateau.

A realized plateau is read-only: it has no *Edit*, and the API answers "Realized plateau is read-only".

## Take a plateau live {#go-live}

When the planned state is reached, the plateau goes live: it becomes the new baseline, the old baseline is realized, and the changes of its solutions reach the catalog. You need the permission `plateau:go-live` (for example the role `enterprise-architect`).

Only the plateau that follows the baseline directly can go live; it shows *Go live*. The dialog *Take plateau live* shows:

- which plateau becomes the baseline and which is realized;
- how many entities are created, changed and retired;
- the solutions that go live with the plateau;
- warnings, which do not stop the go-live: solutions not yet approved, and the open issues the go-live will leave.

Type the exact name of the plateau, then choose *Confirm go-live*. The go-live cannot be undone.

All active solutions of the plateau go live in one step, in the order in which their changes were last edited, each as a single [solution goes live](./solutions#go-live). If a field a solution changes was changed meanwhile, in the catalog or by a solution that goes live before it, the dialog lists the conflict at that solution; *Resolve* opens *Resolve conflicts*. Until every conflict is resolved, nothing goes live. A change of an entity that was deleted from the repository stops the go-live as well: the dialog lists it at its solution, and the solution has to remove the change or the entity has to be restored first. If anything fails during the go-live, nothing changes.

After the go-live:

- the plateau is the baseline from today, and the old baseline is realized until today and read-only;
- the solutions of the plateau are *Implemented*, and retired entities are deleted from the catalog;
- plateaus that followed the old baseline now follow the new one, and their solutions start from it.

## Through the API

`GET /api/v1/plateaus` lists the plateaus, `POST /api/v1/plateaus` creates one, and `GET` and `PUT /api/v1/plateaus/{id}` read and edit one. `GET /api/v1/plateaus/{id}/go-live` shows what the go-live would do, and `POST` with the name of the plateau takes it live. Creating, editing and the go-live are audited as `plateau.created`, `plateau.updated` and `plateau.went-live`.
