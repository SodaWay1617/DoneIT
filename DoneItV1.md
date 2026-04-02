# DoneIt V1 Implementation Plan

This document is a step-by-step checklist for the first MVP implementation.

It is intentionally practical:
- build only what is needed for MVP;
- keep architecture clean from the first commit;
- deliver vertical slices that can be verified quickly.

---

## 1. Phase goal

- [ ] Build the first usable version of DoneIt as a small single-user web application.
- [ ] Keep the implementation aligned with the accepted architecture and ADR decisions.
- [ ] Reach a point where daily task management is realistic without returning to the old tracker.

---

## 2. Phase 0 - Project bootstrap

### 2.1 Repository foundation
- [x] Create Spring Boot project
- [x] Configure Java 21
- [x] Configure Gradle or Maven
- [x] Add base package structure aligned with modular monolith direction
- [x] Add `.editorconfig` if desired for consistent formatting
- [x] Add basic `application.yml`

### 2.2 Dependencies
- [x] Add Spring Web / Spring MVC
- [x] Add Thymeleaf
- [x] Add Spring JDBC
- [x] Add PostgreSQL driver
- [x] Add Liquibase
- [x] Add validation support
- [x] Add Spring Boot Test
- [x] Add Testcontainers for PostgreSQL

### 2.3 Initial package layout
- [ ] Create `com.doneit.common`
- [ ] Create `com.doneit.user`
- [ ] Create `com.doneit.task`
- [ ] Create `com.doneit.config`
- [ ] Within business modules, separate `domain`, `application`, `infrastructure`, and `web` where applicable

### 2.4 Bootstrap done criteria
- [ ] Application starts locally
- [ ] Build runs successfully
- [ ] Empty app can render a basic page or health response
- [ ] Package structure matches architectural intent

---

## 3. Phase 1 - Database and runtime foundation

### 3.1 PostgreSQL and local runtime
- [ ] Add Dockerfile for application
- [ ] Add `docker-compose.yml` for app + PostgreSQL
- [ ] Define environment variables for DB connection
- [ ] Document local startup flow in `README.md`

### 3.2 Liquibase setup
- [ ] Add Liquibase changelog root file
- [ ] Ensure migrations run on application startup
- [ ] Verify migration execution against local PostgreSQL

### 3.3 Initial schema

#### `users`
- [ ] Create `users` table
- [ ] Add `id`
- [ ] Add `login`
- [ ] Add `password_hash`
- [ ] Add `display_name`
- [ ] Add `created_at`
- [ ] Add `updated_at`

#### `tasks`
- [ ] Create `tasks` table
- [ ] Add `id`
- [ ] Add `title`
- [ ] Add `description`
- [ ] Add `status`
- [ ] Add `planned_for_at`
- [ ] Add `deadline_at`
- [ ] Add `user_id`
- [ ] Add `created_at`
- [ ] Add `updated_at`
- [ ] Add `completed_at`
- [ ] Add `closed_at`

### 3.4 Data integrity
- [ ] Add foreign key from `tasks.user_id` to `users.id`
- [ ] Add required `NOT NULL` constraints where appropriate
- [ ] Make status storage explicit
- [ ] Add useful indexes for date queries

### 3.5 Seed strategy
- [ ] Define how the initial user is created
- [ ] Add a manual SQL seed or startup instruction
- [ ] Ensure password is stored hashed, not plain text

### 3.6 Foundation done criteria
- [ ] Docker Compose starts DB and app successfully
- [ ] Liquibase creates schema automatically
- [ ] Manual initial user can be created reliably
- [ ] Local setup has no hidden steps

---

## 4. Phase 2 - Core domain model

### 4.1 User model
- [ ] Create minimal user domain model
- [ ] Create repository contract for loading the active user
- [ ] Keep implementation simple for single-user MVP

### 4.2 Task domain model
- [ ] Create `TaskStatus` enum with `OPEN`, `DONE`, `CLOSED`
- [ ] Create task entity or aggregate model
- [ ] Model `planned_for_at` as first-class field
- [ ] Model `deadline_at` as optional separate field
- [ ] Add audit timestamps

### 4.3 Domain rules
- [ ] Task can be created only with valid required data
- [ ] `DONE` transition is explicit
- [ ] `CLOSED` transition is explicit
- [ ] Hard delete is not supported
- [ ] Done and closed tasks are excluded from active list queries
- [ ] Bulk move logic targets only unfinished tasks

### 4.4 Domain done criteria
- [ ] Core task rules are represented in code, not templates
- [ ] Status model is explicit and easy to test
- [ ] Naming follows `planned_for_at` and `deadline_at`

---

## 5. Phase 3 - Persistence layer

### 5.1 User persistence
- [ ] Implement JDBC repository for user loading
- [ ] Add row mapper(s)
- [ ] Keep repository logic persistence-only

### 5.2 Task persistence
- [ ] Implement task create
- [ ] Implement task update
- [ ] Implement load by id
- [ ] Implement query for today
- [ ] Implement query by selected date
- [ ] Implement query for completed/closed tasks if shown separately
- [ ] Implement status update methods
- [ ] Implement reschedule method
- [ ] Implement bulk move unfinished tasks to tomorrow

### 5.3 SQL quality
- [ ] Keep SQL explicit and readable
- [ ] Avoid ORM abstractions
- [ ] Keep orchestration logic out of repositories

### 5.4 Persistence done criteria
- [ ] Repositories cover all MVP storage needs
- [ ] Daily filtering works through SQL queries
- [ ] Bulk move uses clear and testable persistence logic

---

## 6. Phase 4 - Application layer and use cases

### 6.1 Commands and DTOs
- [ ] Create request DTOs / command models for create task
- [ ] Create request DTOs / command models for edit task
- [ ] Create request DTOs / command models for reschedule
- [ ] Create view DTOs for daily screens

### 6.2 Use cases
- [ ] Implement create task use case
- [ ] Implement edit task use case
- [ ] Implement get tasks for today use case
- [ ] Implement get tasks for selected date use case
- [ ] Implement mark task as done use case
- [ ] Implement mark task as closed use case
- [ ] Implement move one task use case
- [ ] Implement bulk move unfinished tasks to tomorrow use case

### 6.3 Validation and transaction boundaries
- [ ] Validate incoming data before business logic
- [ ] Keep transaction boundaries in application services
- [ ] Keep controllers thin

### 6.4 Application layer done criteria
- [ ] All MVP actions are available as explicit use cases
- [ ] Business rules live in domain/application layers
- [ ] DTO boundaries are ready for possible future REST exposure

---

## 7. Phase 5 - Authentication for single-user MVP

### 7.1 Minimal auth scope
- [ ] Implement simple login page
- [ ] Authenticate against manually created user
- [ ] Use safe password hashing
- [ ] Keep auth flow intentionally minimal

### 7.2 Explicit non-goals
- [ ] No registration page
- [ ] No roles
- [ ] No JWT
- [ ] No OAuth
- [ ] No password reset flow

### 7.3 Auth done criteria
- [ ] Single user can sign in
- [ ] Password is never stored plain text
- [ ] Auth implementation does not introduce unnecessary complexity

---

## 8. Phase 6 - Web UI flows

### 8.1 Pages
- [ ] Implement login page
- [ ] Implement today page
- [ ] Implement selected date page
- [ ] Implement create task page
- [ ] Implement edit task page
- [ ] Implement completed/closed area as a page, block, or collapsible section

### 8.2 Task creation flow
- [ ] User can open create page
- [ ] User can submit title
- [ ] User can submit description
- [ ] User can submit planned datetime
- [ ] User can optionally submit deadline datetime
- [ ] Validation errors are shown clearly

### 8.3 Task editing flow
- [ ] User can open edit page
- [ ] User can update all editable task fields
- [ ] User can save changes successfully

### 8.4 Daily view flow
- [ ] Today view loads current day's tasks
- [ ] Selected date view loads tasks for chosen date
- [ ] Date picker exists
- [ ] Main active list shows only `OPEN` tasks

### 8.5 Status actions
- [ ] User can mark task as `DONE`
- [ ] User can mark task as `CLOSED`
- [ ] Completed and closed tasks are separated from active list

### 8.6 Rescheduling actions
- [ ] User can move one task to another datetime
- [ ] User can bulk move unfinished tasks to tomorrow

### 8.7 UI done criteria
- [ ] Main flows are usable without design polish
- [ ] Create and edit are separate, clear screens
- [ ] Daily work can be performed without confusion

---

## 9. Phase 7 - Visual behavior and usability polish

### 9.1 Required visual distinctions
- [ ] Overdue tasks are highlighted
- [ ] Today tasks are easy to identify
- [ ] Completed and closed tasks do not clutter the active flow
- [ ] Status is visible at a glance

### 9.2 Lightweight polish only
- [ ] Keep templates functional and simple
- [ ] Avoid business logic in templates
- [ ] Prefer clarity over visual complexity

### 9.3 Usability done criteria
- [ ] The main page is comfortable for daily use
- [ ] Important task states are hard to miss

---

## 10. Phase 8 - Automated tests

### 10.1 Unit tests
- [ ] Test task creation rules
- [ ] Test status transitions
- [ ] Test reschedule logic
- [ ] Test bulk move rules

### 10.2 Repository integration tests
- [ ] Run PostgreSQL through Testcontainers
- [ ] Validate Liquibase migrations in test environment
- [ ] Test task persistence
- [ ] Test retrieval by date
- [ ] Test exclusion of `DONE` and `CLOSED` from active list
- [ ] Test bulk move persistence behavior

### 10.3 Web or application flow tests
- [ ] Test create task flow
- [ ] Test edit task flow
- [ ] Test complete task flow
- [ ] Test close task flow
- [ ] Test today view flow
- [ ] Test selected date flow
- [ ] Test bulk move unfinished tasks flow

### 10.4 Testing done criteria
- [ ] Critical MVP flows are covered
- [ ] Test suite is stable and repeatable
- [ ] Refactoring can be done with confidence

---

## 11. Phase 9 - Manual verification and migration readiness

### 11.1 Manual verification checklist
- [ ] Create task for today
- [ ] Create task for future date
- [ ] Create task with specific time
- [ ] Create task with deadline
- [ ] Edit existing task
- [ ] Mark task as done
- [ ] Mark task as closed
- [ ] Move task to another date/time
- [ ] Bulk move unfinished tasks to tomorrow
- [ ] Verify completed tasks are not shown in active list

### 11.2 Migration readiness
- [ ] Recreate current real tasks manually in DoneIt
- [ ] Add long-term reminders like birthdays
- [ ] Use the app as the primary daily tracker for trial period

### 11.3 Final readiness criteria
- [ ] No critical blocker remains for daily use
- [ ] Docker startup works on clean environment
- [ ] Tests pass
- [ ] MVP scope is complete without accidental extra features

---

## 12. Recommended delivery order

- [ ] Step 1: Bootstrap Spring Boot project and package structure
- [ ] Step 2: Add PostgreSQL, Docker, and Liquibase
- [ ] Step 3: Implement schema and single-user seed path
- [ ] Step 4: Implement task domain and JDBC repositories
- [ ] Step 5: Implement application use cases and DTOs
- [ ] Step 6: Implement minimal login
- [ ] Step 7: Implement today view and selected date view
- [ ] Step 8: Implement create and edit task flows
- [ ] Step 9: Implement done/closed actions and completed bucket
- [ ] Step 10: Implement move task and bulk move to tomorrow
- [ ] Step 11: Add tests for critical flows
- [ ] Step 12: Run manual verification and prepare real migration

---

## 13. Strict anti-scope reminder

Do not add these in V1 unless the scope is explicitly changed:

- [ ] No projects
- [ ] No subtasks
- [ ] No recurring task engine
- [ ] No reminders infrastructure
- [ ] No Telegram integration
- [ ] No advanced calendar UI
- [ ] No SPA frontend
- [ ] No ORM
- [ ] No microservices
- [ ] No hard delete
