# DoneIt

DoneIt is a personal family task tracker designed to replace an existing Telegram-based solution with a simple web application.

## Project goal

The first goal is to migrate current tasks with minimal loss of comfort and functionality.

The second goal is to establish a clean architectural foundation that allows iterative growth into a more capable system with projects, subtasks, recurring tasks, analytics, reminders, reports, and calendar views.

## MVP goal

The MVP is intentionally small.

It must allow a single user to:

- create a task;
- create a backlog task without a date;
- edit a task;
- view tasks for today;
- view tasks for a selected date;
- view backlog tasks separately from dated tasks;
- move a task to another date/time;
- move a task into backlog by clearing its planned date;
- bulk move unfinished tasks to tomorrow;
- mark a task as completed;
- mark a task as closed/cancelled;
- keep completed tasks out of the main active list;
- store tasks far into the future, including birthdays and important reminders.

## Success criteria for MVP

The MVP is considered successful when the user fully migrates from the current tracker to DoneIt and uses DoneIt as the primary daily task management tool, including undated reminder-style tasks kept in backlog.

## Product context

DoneIt starts as a personal single-user system for a family context.

The first production scenario is simple:

- one manually created user;
- local development first;
- Docker-based deployment;
- later deployment to a small VPS.

## Planned growth after MVP

The following features are expected in future iterations:

- projects;
- project-level backlogs;
- subtasks;
- recurring tasks;
- second family user;
- estimated vs actual time tracking;
- analytics and overload reports;
- reminders and notifications;
- calendar view;
- richer UI.

## Tech stack

### Backend
- Java 21
- Spring Boot
- Spring JDBC / plain SQL
- Liquibase
- PostgreSQL

### Frontend
- Thymeleaf templates
- server-rendered pages for MVP

### Infrastructure
- Docker
- Docker Compose

### Testing
- JUnit 5
- Spring Boot Test
- Testcontainers

## Architectural direction

The application is a monolith for now, but should be built with low coupling and explicit module boundaries so that selected parts can later be extracted into separate services if needed.

## What is intentionally not included in MVP

The following features are explicitly out of scope for MVP:

- registration UI;
- roles and permissions system;
- JWT or OAuth;
- mobile app;
- Telegram integration;
- advanced calendar UI;
- recurring task execution;
- projects and subtasks beyond backlog planning for future design;
- analytics;
- reminders infrastructure;
- file attachments;
- comments;
- task ordering inside a day;
- import/export features.

## Task status model in MVP

MVP uses the following statuses:

- `OPEN` - active task
- `DONE` - completed task
- `CLOSED` - cancelled / no longer relevant

Completed and closed tasks must not appear in the main active task list.

## Date model

Tasks store optional planned execution datetime and optional deadline datetime.

This is intentional because:

- some tasks need a planned time;
- some tasks belong to backlog and intentionally have no planned date yet;
- some tasks may have a deadline separate from the execution slot;
- the future calendar model should be supported by the domain from the start.

Tasks without `planned_for_at` belong to backlog and must be displayed separately from dated tasks.

Suggested field names:

- `planned_for_at`
- `deadline_at`

## Manual user management

There is no registration flow in MVP.

The initial user is created manually in the database or via seed script.

## Initial delivery expectations for Codex agent

The first delivery should provide:

- runnable Spring Boot application;
- PostgreSQL integration;
- Liquibase migrations;
- Thymeleaf pages;
- Docker setup;
- tests with Testcontainers;
- basic CRUD for tasks;
- daily task views;
- separate backlog view or section for undated tasks;
- bulk move unfinished tasks to tomorrow.

## Notes for future frontend migration

The MVP uses server-side rendering, but the application should expose a clean internal application layer and REST endpoints where reasonable so that a future migration to a dedicated frontend is straightforward.