# DoneIt — Task Views Specification

## 1. Purpose

DoneIt provides three primary ways to work with tasks:

- **List View** — fast task overview and management;
- **Kanban View** — visual workflow management;
- **Calendar View** — date-oriented planning and deadline visualization.

All three views operate on the **same Task entities**.

Views are presentation concepts only.

They must not introduce separate task entities, duplicate persisted data, or maintain independent task state.

A change made through one view must immediately affect the task as displayed in every other view.

---

# 2. Core principle

The system has one task model and multiple projections of that model.

```text
                         ┌── List View
                         │
Task Domain / Task API ──┼── Kanban View
                         │
                         └── Calendar View
```

Examples:

- completing a task in List View moves it to `DONE` in Kanban View;
- moving a task to `IN_PROGRESS` in Kanban changes its status everywhere;
- changing the planned date changes where the task appears in List and Calendar views;
- changing the deadline changes its deadline representation in Calendar View.

No view-specific copy of a task may exist.

---

# 3. Shared Task model

The views are based on the following conceptual model:

```text
Task
├── id
├── title
├── description
├── status
├── plannedForAt
├── deadlineAt?
├── createdAt
├── updatedAt
├── completedAt?
└── closedAt?
```

## 3.1 Status

Supported statuses:

```text
TODO
IN_PROGRESS
DONE
CLOSED
```

### TODO

The task exists but active work has not started.

### IN_PROGRESS

The task is currently being worked on.

### DONE

The task has been successfully completed.

### CLOSED

The task will not be completed or is no longer relevant.

`DONE` and `CLOSED` are terminal states for the normal daily workflow.

---

# 4. Shared date semantics

DoneIt distinguishes two fundamentally different task dates.

## 4.1 `plannedForAt`

Represents:

> When do I plan to work on this task?

This value determines the normal placement of the task in date-oriented views.

Example:

```text
plannedForAt = 2026-10-10 14:00
```

means that the task is planned for October 10 at 14:00.

Changing `plannedForAt` is considered **rescheduling the task**.

---

## 4.2 `deadlineAt`

Represents:

> By when must this task be completed?

The deadline is independent of the planned execution date.

Example:

```text
plannedForAt = 2026-10-10 14:00
deadlineAt   = 2026-10-15 18:00
```

means:

- the user intends to work on the task on October 10;
- the task must be completed no later than October 15.

Changing `plannedForAt` must not implicitly change `deadlineAt`.

---

# 5. Shared view context

List and Kanban operate primarily against a selected working date.

The UI should conceptually support:

```text
Selected date: 26 September 2026

[List] [Kanban] [Calendar]
```

Switching between List and Kanban must preserve the selected date whenever possible.

Calendar operates over a wider date range and may use its own currently displayed calendar period.

Future project filtering should be compatible with the same concept:

```text
Context
├── selected date / date range
├── project
├── user
└── other future filters

        ↓

[List] [Kanban] [Calendar]
```

Views must therefore not own filtering rules that belong to the application/domain query layer.

---

# 6. List View

## 6.1 Purpose

List View is optimized for:

- quick overview;
- rapid task creation;
- simple task management;
- reviewing all tasks planned for a particular day.

It should remain the simplest and most information-dense DoneIt view.

---

## 6.2 Default content

For a selected date, List View displays tasks whose:

```text
DATE(plannedForAt) = selectedDate
```

The main active section contains:

```text
TODO
IN_PROGRESS
```

tasks.

`DONE` and `CLOSED` tasks must not clutter the primary active list.

---

## 6.3 Completed section

Finished tasks should be placed into a separate section conceptually named:

```text
Completed
```

The section may contain:

- `DONE`;
- `CLOSED`.

The UI should visually distinguish successful completion from closure/cancellation.

The section may initially be collapsed by default.

---

## 6.4 Task actions

List View must support:

- create task;
- open task;
- edit task;
- mark as `DONE`;
- mark as `CLOSED`;
- reschedule task;
- change status where appropriate.

The MVP may use buttons rather than drag-and-drop.

---

## 6.5 Bulk move

List View should provide:

```text
Move unfinished tasks to tomorrow
```

The operation applies only to:

```text
TODO
IN_PROGRESS
```

tasks planned for the selected day.

It must not move:

```text
DONE
CLOSED
```

tasks.

The operation changes `plannedForAt`.

It must not modify `deadlineAt`.

If the task contains a meaningful planned time, the time component should be preserved when changing the date unless explicitly changed by the user.

Example:

```text
2026-09-26 14:30
        ↓
2026-09-27 14:30
```

---

# 7. Kanban View

## 7.1 Purpose

Kanban View is optimized for managing the workflow of tasks during a day.

Unlike a traditional project Kanban board, the initial DoneIt Kanban is primarily **date-oriented**.

The user selects a day and sees the workflow state of tasks planned for that day.

---

## 7.2 Initial columns

Kanban uses:

```text
TODO
IN_PROGRESS
DONE
```

as its primary columns.

`CLOSED` should not require a permanent primary column.

Closed tasks may be:

- shown in a separate collapsed area;
- available through filtering/history;
- visually grouped with completed items while retaining their actual status.

The exact visual treatment may evolve, but `CLOSED` must remain semantically different from `DONE`.

---

## 7.3 Kanban dataset

For a selected date, the board displays tasks where:

```text
DATE(plannedForAt) = selectedDate
```

The same Task objects used by List View are displayed.

---

## 7.4 Drag-and-drop

Moving a card between Kanban columns changes `Task.status`.

Example:

```text
TODO
 ↓
IN_PROGRESS
```

results in:

```text
status = IN_PROGRESS
```

Moving:

```text
IN_PROGRESS
 ↓
DONE
```

results in:

```text
status = DONE
completedAt = current timestamp
```

The exact domain transition logic must be implemented in the application/domain layer, not in JavaScript or template code.

---

## 7.5 Closing a task

Closing is semantically different from completing.

A task may be explicitly changed to:

```text
CLOSED
```

This means:

> The task is intentionally abandoned, cancelled, or no longer required.

It must not be treated as successful completion in future analytics.

---

## 7.6 Kanban ordering

Manual ordering inside Kanban columns is not required for the initial implementation.

If drag-and-drop ordering is later introduced, ordering should become explicit persisted data rather than relying on incidental database ordering.

---

# 8. Calendar View

## 8.1 Purpose

Calendar View is optimized for:

- looking ahead;
- understanding future workload;
- seeing tasks assigned to particular days;
- seeing approaching deadlines;
- distinguishing planned execution from deadline constraints.

Calendar View uses both:

```text
plannedForAt
deadlineAt
```

but they have different visual semantics.

---

# 9. Calendar planned task rendering

A task must normally appear on the calendar according to:

```text
plannedForAt
```

Example:

```text
Task:
    title        = "Pay insurance"
    plannedForAt = 2026-10-10 14:00
    deadlineAt   = 2026-10-15 18:00
```

October 10 displays:

```text
┌─────────────────────┐
│ 14:00 Pay insurance │
└─────────────────────┘
```

This is the normal task representation.

It uses standard task styling.

---

# 10. Calendar deadline rendering

If a task has a deadline, the deadline must also be represented on the corresponding calendar day.

For:

```text
plannedForAt = 2026-10-10
deadlineAt   = 2026-10-15
```

the calendar conceptually contains:

```text
October 10

┌─────────────────────┐
│ Pay insurance       │
└─────────────────────┘


October 15

┌─────────────────────┐
│ Pay insurance       │  ← deadline representation
└─────────────────────┘
```

The October 15 representation must use **red deadline styling**.

The deadline representation is not a second Task.

It is a second calendar projection of the same Task.

---

# 11. Planned date and deadline on the same day

If:

```text
DATE(plannedForAt) == DATE(deadlineAt)
```

the task must appear only once in that calendar cell.

The single representation uses **deadline/red styling**.

Example:

```text
plannedForAt = 2026-10-15 14:00
deadlineAt   = 2026-10-15 18:00
```

Calendar:

```text
October 15

┌─────────────────────┐
│ 14:00 Pay insurance │  ← red deadline styling
└─────────────────────┘
```

It must NOT render:

```text
Pay insurance
Pay insurance [deadline]
```

This deduplication is mandatory.

---

# 12. Calendar rendering algorithm

Conceptually:

```text
for each task:

    plannedDate = DATE(task.plannedForAt)
    deadlineDate = DATE(task.deadlineAt)

    if deadlineAt is null:
        render regular item on plannedDate

    else if plannedDate == deadlineDate:
        render one deadline-styled item on plannedDate

    else:
        render regular item on plannedDate
        render deadline-styled projection on deadlineDate
```

This logic belongs to calendar projection/presentation preparation.

It must not create additional persisted tasks.

---

# 13. Calendar event identity

Because one Task may produce two calendar items, calendar items must distinguish their projection type.

Conceptually:

```text
CalendarItem
├── taskId
├── occurrenceType
│   ├── PLANNED
│   └── DEADLINE
├── dateTime
├── title
├── taskStatus
└── deadlineHighlighted
```

`CalendarItem` is a read/view model.

It is not a persisted domain entity.

Example:

```text
Task #42

→ CalendarItem(taskId=42, occurrenceType=PLANNED)
→ CalendarItem(taskId=42, occurrenceType=DEADLINE)
```

Both items navigate to the same underlying Task.

---

# 14. Calendar interactions

## 14.1 Planned item interaction

A normal planned calendar item represents `plannedForAt`.

Future drag-and-drop of that item may change:

```text
plannedForAt
```

Example:

```text
October 10 → October 12
```

changes:

```text
plannedForAt = October 12
```

but does not change the deadline.

---

## 14.2 Deadline item interaction

A deadline representation represents `deadlineAt`.

Moving a planned task must never move the deadline automatically.

If deadline drag-and-drop is supported in the future, it must explicitly edit:

```text
deadlineAt
```

and be treated as a separate user action.

For the initial Calendar implementation, deadline items may remain non-draggable.

---

# 15. Calendar status behavior

Calendar items must still reflect the underlying Task status.

For example:

- `TODO` — normal active appearance;
- `IN_PROGRESS` — active/in-progress indication;
- `DONE` — completed appearance;
- `CLOSED` — closed appearance.

However, deadline semantics have higher visual priority.

If an active task is represented as a deadline item, deadline/red styling must remain clearly visible.

Exact visual design is intentionally left open.

---

# 16. Overdue semantics

A task is overdue when:

```text
deadlineAt < current time
AND
status NOT IN (DONE, CLOSED)
```

An overdue task must remain visually identifiable as overdue.

A completed or closed task must not be considered actively overdue.

Historical information may still preserve that it was completed after its deadline for future analytics.

---

# 17. Cross-view consistency requirements

The following behavior is mandatory.

### Scenario A — Complete from List

```text
List:
Task → DONE
```

Result:

```text
Kanban → task appears as DONE
Calendar → task reflects DONE state
```

---

### Scenario B — Start from Kanban

```text
Kanban:
TODO → IN_PROGRESS
```

Result:

```text
List → IN_PROGRESS
Calendar → IN_PROGRESS
```

---

### Scenario C — Reschedule

```text
plannedForAt:
September 26 → September 27
```

Result:

```text
List Sep 26 → task disappears
List Sep 27 → task appears

Kanban Sep 26 → task disappears
Kanban Sep 27 → task appears

Calendar → planned item moves to Sep 27
```

If the task has a deadline, its deadline item remains unchanged.

---

### Scenario D — Planned date reaches deadline date

Before:

```text
plannedForAt = October 10
deadlineAt   = October 15
```

Calendar renders two items.

After rescheduling:

```text
plannedForAt = October 15
deadlineAt   = October 15
```

Calendar must render exactly one item with deadline styling.

---

# 18. Query/API design guidance

Views should consume task data through shared application services/query interfaces.

Avoid implementations such as:

```text
ListTaskRepository
KanbanTaskRepository
CalendarTaskRepository
```

when they duplicate the same task persistence logic.

Prefer:

```text
TaskRepository
        ↓
TaskQueryService
        ↓
 ┌──────┼───────┐
List  Kanban  Calendar
```

Calendar-specific projection logic may live in a dedicated query/projection service because Calendar can generate multiple display items from one Task.

For example:

```text
TaskQueryService
CalendarProjectionService
```

This is acceptable because the latter transforms tasks for presentation rather than owning separate task state.

---

# 19. Future project support

Views must be designed so that future project filtering does not require redesign.

Future usage should support scenarios such as:

```text
Project: Home
View: List
```

```text
Project: Home
View: Kanban
```

```text
Project: Home
View: Calendar
```

Likewise, when a second family user is introduced:

```text
Project: Home
User: Katya
View: Calendar
```

should conceptually remain the same query + projection system.

Do not hard-code views around the assumption that the selected date is the only possible filter forever.

---

# 20. Future subtask support

Tasks and subtasks will eventually use the same view infrastructure.

The initial implementation must not introduce assumptions that make subtasks impossible to represent later.

However, no subtask-specific implementation is required now.

---

# 21. Implementation order

Views should be implemented incrementally.

## Phase 1 — List View

Implement:

- selected date;
- active task list;
- create;
- edit;
- complete;
- close;
- reschedule;
- completed section;
- bulk move to tomorrow.

This phase must already be usable as the primary DoneIt task tracker.

---

## Phase 2 — Kanban View

Implement:

- selected date;
- `TODO`;
- `IN_PROGRESS`;
- `DONE`;
- status transitions;
- drag-and-drop between workflow columns.

Kanban must use the existing Task domain and application services.

---

## Phase 3 — Calendar View

Implement:

- calendar date grid;
- planned task rendering;
- deadline rendering;
- red deadline styling;
- planned/deadline deduplication;
- navigation from calendar item to Task.

Calendar drag-and-drop may be implemented after the initial Calendar View if needed.

---

# 22. Acceptance scenarios

## List

- [ ] User can select a date.
- [ ] User sees tasks planned for that date.
- [ ] User can create a task.
- [ ] User can edit a task.
- [ ] User can complete a task.
- [ ] User can close a task.
- [ ] User can reschedule a task.
- [ ] User can move unfinished tasks to tomorrow.
- [ ] DONE/CLOSED tasks do not clutter the active list.

## Kanban

- [ ] Kanban shows tasks for the selected date.
- [ ] TODO tasks appear in TODO.
- [ ] IN_PROGRESS tasks appear in IN_PROGRESS.
- [ ] DONE tasks appear in DONE.
- [ ] Dragging between columns changes Task status.
- [ ] Changes are immediately reflected in List View.
- [ ] CLOSED remains semantically distinct from DONE.

## Calendar

- [ ] Task appears normally on `plannedForAt` date.
- [ ] Deadline appears in red on `deadlineAt` date.
- [ ] Planned and deadline representations refer to the same Task.
- [ ] If planned date and deadline date differ, both representations are shown.
- [ ] If planned date and deadline date are equal, exactly one red representation is shown.
- [ ] Rescheduling planned date does not modify deadline.
- [ ] Completed/closed tasks are not considered actively overdue.
- [ ] Clicking either calendar representation opens the same Task.

---

# 23. Core invariant

The most important invariant of the entire view system is:

> **List, Kanban, and Calendar are different ways to observe and manipulate the same task state.**

A feature implemented through one view must not create a parallel model that can diverge from the others.

The Task domain is the source of truth.