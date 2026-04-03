# DoneIt Architecture

## Purpose

This document defines the intended architecture of DoneIt for MVP and the immediate post-MVP evolution path.

The architecture must satisfy two goals:

1. keep the MVP small and easy to implement;
2. preserve a clean structure for future growth.

---

## 1. Architectural style

DoneIt is implemented as a modular monolith.

This means:

- one deployable application;
- one database;
- one codebase;
- explicit internal module boundaries;
- low coupling between modules;
- future extraction into services should remain possible, but is not a current requirement.

This choice is driven by simplicity, maintainability, and development speed.

---

## 2. Deployment model

### MVP deployment stages

#### Stage 1
- local machine
- Docker Compose
- PostgreSQL container
- application container

#### Stage 2
- small VPS
- Docker-based deployment
- PostgreSQL persisted with volume

No Kubernetes, no service mesh, no distributed system complexity.

---

## 3. High-level module structure

Suggested internal modules:

### `user`
Responsible for:
- loading the single active user;
- user persistence;
- future support for additional family members.

### `task`
Responsible for:
- task CRUD;
- task status changes;
- task rescheduling;
- bulk move logic;
- date-based queries;
- backlog queries and backlog separation.

### `web`
Responsible for:
- Thymeleaf controllers;
- HTML pages;
- form binding;
- user interaction flows.

### `common`
Responsible for:
- shared types;
- exceptions;
- utilities;
- time handling;
- validation helpers.

### Future modules
Reserved for later:
- `project`
- `subtask`
- `recurrence`
- `time-tracking`
- `analytics`
- `notification`
- `calendar`

---

## 4. Layered structure

Each module should respect a layered structure.

### Presentation layer
- Spring MVC controllers
- Thymeleaf models
- request/response DTOs

### Application layer
- use case orchestration
- transaction boundaries
- mapping between web DTOs and domain commands

### Domain layer
- domain entities
- business rules
- status transitions
- reschedule logic
- validation rules

### Persistence layer
- repositories
- plain SQL
- row mappers
- database access via Spring JDBC

---

## 5. Persistence strategy

DoneIt intentionally avoids ORM in MVP.

### Chosen approach
- plain SQL
- Spring JDBC or JdbcTemplate
- explicit SQL queries
- Liquibase migrations

### Reason
The project owner prefers direct control over SQL and schema evolution.

This also makes data flow explicit and easier to reason about in a compact project.

---

## 6. Database outline

### `users`
Stores manually created users.

Suggested fields:
- `id`
- `login`
- `password_hash`
- `display_name`
- `created_at`
- `updated_at`

### `tasks`
Stores primary task records.

Suggested fields:
- `id`
- `title`
- `description`
- `status`
- `planned_for_at` nullable for backlog tasks
- `deadline_at`
- `user_id`
- `created_at`
- `updated_at`
- `completed_at`
- `closed_at`

### Status values
- `OPEN`
- `DONE`
- `CLOSED`

---

## 7. Task model decisions

### Why `planned_for_at`
The system must support:
- tasks for a specific day;
- tasks at a specific time;
- far-future reminders like birthdays;
- tasks without a chosen date yet that live in backlog.

`planned_for_at` is therefore first-class, but optional for backlog tasks.

### Why `deadline_at`
Some tasks have a desired execution slot and an actual deadline that are not the same.

The domain should support this distinction early, even if the MVP UI uses it only lightly.

### Why backlog exists in MVP
Some tasks are low-priority reminders that are good to do someday, but not on a specific date yet.

The MVP should support this explicitly rather than forcing fake dates.

### Why no hard delete in MVP
The project prefers task closure over removal.
This preserves history and avoids accidental data loss.

---

## 8. Main MVP user flows

### Create task
User opens task creation page and submits:
- title
- description
- planned datetime or backlog placement
- optional deadline datetime

### Edit task
User updates existing task fields, including moving a task between dated schedule and backlog.

### Daily view
User opens:
- today view
- selected day view

### Backlog view
User opens a separate backlog block, section, or page with undated low-priority tasks.

### Complete task
User marks task as `DONE`.

### Close task
User marks task as `CLOSED`.

### Move task
User changes planned datetime for one task or removes the date to place the task into backlog.

### Bulk move unfinished tasks to tomorrow
System finds all `OPEN` tasks for the current day and shifts them to the next day.
Backlog tasks are not affected.

---

## 9. View model strategy

The MVP uses server-side rendering.

### Pages to implement
- login page
- today page
- selected date page
- task creation page
- task edit page
- backlog page, block, or collapsible section
- completed/closed tasks page or collapsible section

### Visual rules
- active dated tasks must be visually separate from completed/closed tasks;
- backlog tasks must be visually separate from dated tasks;
- overdue tasks must be highlighted;
- task statuses should be immediately visible;
- completed tasks should not clutter the primary daily flow.

---

## 10. REST API direction

Even though MVP uses Thymeleaf, the internal application structure should be compatible with future REST exposure.

Recommended approach:
- keep controllers thin;
- keep use cases in services/application layer;
- avoid business logic in templates or controllers;
- introduce DTOs explicitly.

A full public API is not required in MVP, but the design should not prevent one.

---

## 11. Time and timezone handling

### Business timezone
- MSK by default

### Recommended storage
- store timestamps in a consistent database format;
- convert through application-level time configuration if needed;
- avoid hidden timezone assumptions in templates.

The first version can remain relatively simple as long as the logic is explicit and tested.

---

## 12. Testing architecture

### Required tests
- unit tests for domain logic;
- integration tests for repositories;
- integration tests for web flows where valuable.

### Infrastructure
- Testcontainers for PostgreSQL
- Liquibase migration validation in test context

### Minimum critical test areas
- task creation
- backlog task creation without date
- task editing
- task completion
- task closure
- rescheduling
- moving tasks between schedule and backlog
- bulk move unfinished tasks to tomorrow
- daily filtering
- backlog filtering
- exclusion of done/closed tasks from active list

---

## 13. Non-goals for MVP

The architecture must not be bent around the following features yet:

- multi-tenant support;
- advanced RBAC;
- event-driven microservices;
- asynchronous notification infrastructure;
- recurring task engine;
- project/subtask graph beyond future backlog preparation;
- analytics warehouse;
- advanced frontend SPA concerns.

---

## 14. Evolution path

### After MVP
Likely next modules:
1. projects
2. project-level backlogs
3. subtasks
4. recurring tasks
5. second user
6. notifications
7. time tracking
8. analytics
9. calendar

### Architecture expectation
Each of these should be added as an internal module with clear boundaries rather than by scattering changes across the entire codebase.

---

## 15. Suggested package structure

```text
com.doneit
  |- common
  |- user
  |  |- domain
  |  |- application
  |  |- infrastructure
  |  \- web
  |- task
  |  |- domain
  |  |- application
  |  |- infrastructure
  |  \- web
  \- config
```