# DoneIt Architecture Decision Record

This file captures the main architectural decisions accepted for the project.

---

## ADR-001 — Use a web application instead of Telegram-based workflow

### Status
Accepted

### Context
The previous task workflow relied on Telegram. Telegram became inconvenient due to slowdowns and poor usability in the target environment. The existing tracker also lacks active development and misses important features.

### Decision
DoneIt will be built as a web application.

### Consequences
#### Positive
- no dependency on Telegram performance;
- better UX for dates, forms, and future analytics;
- easier long-term product growth.

#### Negative
- requires independent hosting and deployment;
- requires own authentication and persistence layer.

---

## ADR-002 — Start with a single-user MVP

### Status
Accepted

### Context
The first goal is migration from the current tracker with minimal friction. Multi-user support is needed later, but not required for initial adoption.

### Decision
MVP supports only one manually created user.

### Consequences
#### Positive
- reduced complexity;
- faster implementation;
- simpler data model and authentication.

#### Negative
- future family collaboration is postponed;
- some domain design must anticipate user expansion.

---

## ADR-003 — Build as a modular monolith

### Status
Accepted

### Context
The project should remain simple to develop and deploy, but the internal structure should be clean enough to support future extraction of modules if needed.

### Decision
DoneIt will be implemented as a modular monolith with explicit internal module boundaries.

### Consequences
#### Positive
- fast development;
- simple operations;
- clean boundaries for future growth.

#### Negative
- extraction into services later still requires work;
- discipline is needed to avoid accidental coupling.

---

## ADR-004 — Use Thymeleaf and server-side rendering for MVP

### Status
Accepted

### Context
The project owner wants a simple MVP without investing early in a dedicated frontend application. At the same time, the architecture should not block future migration to a richer frontend.

### Decision
MVP UI will be server-rendered with Thymeleaf templates.

### Consequences
#### Positive
- fast implementation;
- minimal frontend complexity;
- lower barrier for MVP delivery.

#### Negative
- limited interactivity compared to SPA;
- frontend migration later will require UI replacement work.

---

## ADR-005 — Use PostgreSQL as the primary database

### Status
Accepted

### Context
The project owner is familiar with PostgreSQL and intends to run the system locally first and later on a small VPS.

### Decision
DoneIt will use PostgreSQL.

### Consequences
#### Positive
- known and reliable relational database;
- good fit for structured task data;
- easy Docker-based local development.

#### Negative
- none significant for current scope.

---

## ADR-006 — Use plain SQL instead of ORM

### Status
Accepted

### Context
The project owner prefers explicit SQL queries and direct control over schema and query behavior.

### Decision
Persistence will use plain SQL through Spring JDBC or equivalent lightweight DB access tools. ORM is excluded from MVP.

### Consequences
#### Positive
- transparent queries;
- full SQL control;
- easier performance reasoning.

#### Negative
- more manual mapping;
- more repository boilerplate.

---

## ADR-007 — Use Liquibase for schema migrations

### Status
Accepted

### Context
Database schema must be versioned and applied automatically on startup.

### Decision
Liquibase will manage database schema migrations.

### Consequences
#### Positive
- repeatable schema evolution;
- automated startup migration flow;
- good fit for Docker and tests.

#### Negative
- migration discipline required;
- migration mistakes affect startup.

---

## ADR-008 — Support both planned execution datetime and deadline datetime

### Status
Accepted

### Context
Some tasks are associated with a desired execution moment, while others also have a separate deadline. The future system is expected to include richer scheduling and calendar behavior.

### Decision
The task model will include:
- `planned_for_at`
- `deadline_at`

### Consequences
#### Positive
- supports current and future scheduling use cases;
- avoids future schema redesign for obvious date use cases.

#### Negative
- slightly more complex domain model from the start;
- MVP UI must be careful not to confuse these fields.

---

## ADR-009 — Do not implement task deletion in MVP

### Status
Accepted

### Context
The project owner wants simple handling of finished and irrelevant tasks without risking accidental data loss.

### Decision
Tasks will not be hard-deleted in MVP.
Instead, they will transition to:
- `DONE`
- `CLOSED`

### Consequences
#### Positive
- safer data handling;
- preserves task history;
- simple user mental model.

#### Negative
- database retains historical data;
- future archive views become necessary.

---

## ADR-010 — Completed tasks must not appear in the primary active list

### Status
Accepted

### Context
The daily workflow should remain focused and uncluttered.

### Decision
Tasks with status `DONE` or `CLOSED` must be hidden from the main active task list and shown separately if needed.

### Consequences
#### Positive
- cleaner daily view;
- better focus on active work.

#### Negative
- requires separate completed-task view or grouping.

---

## ADR-011 — Bulk move unfinished tasks to tomorrow

### Status
Accepted

### Context
The user explicitly needs a quick way to reschedule unfinished daily tasks.

### Decision
MVP will provide a bulk action that moves all unfinished tasks for the current day to the next day.

### Consequences
#### Positive
- matches a real daily workflow;
- saves repetitive manual rescheduling effort.

#### Negative
- requires careful definition of which tasks qualify for bulk move;
- needs solid tests to avoid accidental movement of wrong tasks.

---

## ADR-012 — No registration, no roles, no advanced auth in MVP

### Status
Accepted

### Context
The system is initially personal and single-user. Registration, roles, JWT, OAuth, password reset, and logout are unnecessary at this stage.

### Decision
Authentication remains minimal:
- no registration UI;
- no roles;
- no advanced auth flows.

### Consequences
#### Positive
- lower complexity;
- faster delivery.

#### Negative
- later security work required when multi-user support arrives.

---

## ADR-013 — Test quality is mandatory from the start

### Status
Accepted

### Context
The owner wants tests to be mandatory for MVP.

### Decision
MVP must include:
- unit tests;
- integration tests;
- Testcontainers-based database tests.

### Consequences
#### Positive
- safer refactoring;
- strong foundation for iterative development.

#### Negative
- slightly slower initial implementation.

---

## ADR-014 — Develop iteratively in small increments

### Status
Accepted

### Context
This is a long-lived soul project developed in free time. The goal is not to rush a complete system but to migrate first and extend later.

### Decision
The project will grow iteratively, starting with a very small MVP.

### Consequences
#### Positive
- sustainable development pace;
- reduced risk of overengineering;
- faster path to real usage.

#### Negative
- some future features are intentionally postponed;
- temporary simplicity may look incomplete by design.