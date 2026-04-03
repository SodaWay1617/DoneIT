# DoneIt Project Rules

This document defines implementation rules for DoneIt.

The goal of these rules is to preserve code quality, architecture clarity, and future extensibility while keeping MVP development practical.

---

## 1. General principles

### Rule 1.1 - Build for real use
Every implemented feature must support an actual user need in the project roadmap.

### Rule 1.2 - Keep the MVP small
Do not expand MVP scope beyond agreed functionality.

### Rule 1.3 - Preserve future extensibility
Even in MVP, code should be structured so that projects, subtasks, recurrence, analytics, additional users, and project-level backlogs can be added without large-scale rewrites.

### Rule 1.4 - Prefer clarity over cleverness
Code should be explicit and readable.

### Rule 1.5 - Architecture matters
Even though this is a personal project, architectural shortcuts that create long-term pain should be avoided.

---

## 2. Codebase structure

### Rule 2.1 - Use modular separation
Code must be grouped by business module, not by technical type alone.

Preferred direction:
- `task`
- `user`
- `common`
- future modules as needed

### Rule 2.2 - Keep layers explicit
Each module should keep a visible separation between:
- web/presentation;
- application/use case orchestration;
- domain logic;
- persistence/infrastructure.

### Rule 2.3 - Do not place business logic in controllers
Controllers should orchestrate request handling only.

### Rule 2.4 - Do not place business logic in templates
Thymeleaf templates must stay presentational.

---

## 3. Domain rules

### Rule 3.1 - Task statuses are explicit
MVP task statuses:
- `OPEN`
- `DONE`
- `CLOSED`

### Rule 3.2 - No hard delete in MVP
Tasks are not deleted.
They are completed or closed.

### Rule 3.3 - Active view excludes completed items
The main daily list must not show `DONE` or `CLOSED` tasks.

### Rule 3.4 - Planned datetime is first-class
Tasks must support `planned_for_at` from the beginning.
For backlog tasks, `planned_for_at` may be absent.

### Rule 3.5 - Backlog is a separate concept
Undated low-priority reminder tasks belong to backlog.
Backlog tasks must be displayed separately from dated active tasks.

### Rule 3.6 - Deadline is a separate concept
If present, `deadline_at` must be treated as distinct from the planned execution datetime.

### Rule 3.7 - Design naming for future growth
Prefer neutral and extensible names such as:
- `planned_for_at`
- `deadline_at`

Avoid names that lock the model to the daily-list-only worldview.

---

## 4. Persistence rules

### Rule 4.1 - Use plain SQL
Use explicit SQL queries instead of ORM.

### Rule 4.2 - Every schema change must go through Liquibase
Do not modify schema manually without migration files.

### Rule 4.3 - SQL must remain readable
Queries must be formatted and understandable.

### Rule 4.4 - Repository responsibilities
Repositories handle persistence only.
They must not contain orchestration logic.

---

## 5. API and DTO rules

### Rule 5.1 - Use DTOs explicitly
Do not bind database entities directly to views.

### Rule 5.2 - Keep contracts stable
Even if the MVP UI is server-rendered, DTO and service boundaries should be clear enough to support future REST usage.

### Rule 5.3 - Validate inputs
Request data must be validated before reaching business logic.

---

## 6. UI rules

### Rule 6.1 - Focus on functional UI
The MVP does not require polished design, but it must be usable.

### Rule 6.2 - Highlight key states
The UI must visually distinguish:
- overdue tasks;
- tasks planned for today;
- backlog tasks without date;
- completed/closed tasks.

### Rule 6.3 - Completed items must not clutter the active list
Completed tasks should be placed into a separate block, section, or page.
Backlog tasks should also live in a separate block, section, or page instead of mixing with dated tasks.

### Rule 6.4 - Create and edit are separate flows
MVP uses a dedicated page or separate screen for task creation/editing.

---

## 7. Testing rules

### Rule 7.1 - Tests are mandatory
All MVP functionality must be covered by tests.

### Rule 7.2 - Test levels
The codebase must include:
- unit tests for business logic;
- integration tests for repositories;
- integration tests for main application flows where relevant.

### Rule 7.3 - Use Testcontainers for DB integration tests
Database-related tests must run against PostgreSQL via Testcontainers.

### Rule 7.4 - Test real user-critical flows first
Critical flows:
- create task;
- create backlog task without date;
- edit task;
- complete task;
- close task;
- move task;
- move task between dated schedule and backlog;
- bulk move unfinished tasks to tomorrow;
- date-based filtering;
- backlog filtering;
- hiding completed tasks from active list.

---

## 8. Docker and runtime rules

### Rule 8.1 - Application must run in Docker
The MVP must be runnable through Docker.

### Rule 8.2 - Local-first development
The system must be easy to run locally before VPS deployment.

### Rule 8.3 - Configuration must be explicit
Environment configuration must be clear and minimal.

---

## 9. Security rules for MVP

### Rule 9.1 - Keep auth simple
No registration, no roles, no OAuth, no JWT for MVP.

### Rule 9.2 - Passwords must still be stored safely
Even in a simple MVP, passwords must never be stored in plain text.

### Rule 9.3 - No fake complexity
Do not add security machinery that the current scope does not require.

---

## 10. Commit rules

### Rule 10.1 - Use structured commit messages
Preferred format:

```text
type(scope): message
```

Example:

task(mvp): add backlog tasks without planned date
task(mvp): add bulk move unfinished tasks to tomorrow
test(task): add integration coverage for daily and backlog filtering
infra(docker): add postgres container for local development

### Rule 10.2 - Keep commits focused
One commit should represent one coherent change.

---

## 11. Non-goal protection rules

The following must not be introduced into MVP unless explicitly approved for a later iteration:

- microservices;
- advanced RBAC;
- enterprise-grade permissions;
- chat/comments;
- file attachments;
- analytics dashboards;
- recurring execution engine;
- notification infrastructure;
- SPA complexity;
- import/export subsystem.

---

## 12. Definition of acceptable MVP code

Code is acceptable when it is:

- readable;
- tested;
- structured by modules;
- explicit in SQL and domain rules;
- easy to extend without major rewrites.

Code is not acceptable when it is:

- tightly coupled;
- controller-heavy;
- template-driven with hidden business rules;
- weakly tested;
- written in a way that blocks future modules.