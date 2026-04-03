# DoneIt MVP Checklist

This checklist defines the scope and acceptance criteria for the MVP.

---

# 1. Product scope

## 1.1 Core MVP goal
- [ ] The application allows migration away from the current tracker.
- [ ] The application supports daily task management with minimal comfort loss, including a separate backlog for undated reminders.
- [ ] The application establishes a foundation for future modules.

## 1.2 Out of scope
- [ ] No projects in MVP
- [ ] No subtasks in MVP
- [ ] No recurring task engine in MVP
- [ ] No analytics in MVP
- [ ] No reminders infrastructure in MVP
- [ ] No second user in MVP
- [ ] No advanced calendar in MVP
- [ ] No import/export in MVP
- [ ] No task deletion in MVP

---

# 2. Technical foundation

## 2.1 Project bootstrap
- [ ] Spring Boot project created
- [ ] Java 21 configured
- [ ] PostgreSQL configured
- [ ] Liquibase configured
- [ ] Thymeleaf configured
- [ ] Docker support added
- [ ] Docker Compose added for local app + DB startup

## 2.2 Persistence stack
- [ ] Plain SQL persistence selected and implemented
- [ ] Repository layer created
- [ ] No ORM introduced

## 2.3 Test stack
- [ ] JUnit 5 configured
- [ ] Spring Boot Test configured
- [ ] Testcontainers configured for PostgreSQL integration tests

---

# 3. Domain model

## 3.1 User
- [ ] Single-user model exists
- [ ] User is created manually in DB or seed script
- [ ] No registration flow implemented
- [ ] No roles implemented

## 3.2 Task entity
- [ ] Task has `title`
- [ ] Task has `description`
- [ ] Task may have `planned_for_at`
- [ ] Task may have `deadline_at`
- [ ] Task can exist without `planned_for_at` when stored in backlog
- [ ] Task has `status`
- [ ] Task has timestamps for creation/update
- [ ] Task supports future dates far ahead
- [ ] Task supports datetime, not just date

## 3.3 Task status model
- [ ] `OPEN` implemented
- [ ] `DONE` implemented
- [ ] `CLOSED` implemented

---

# 4. Database

## 4.1 Liquibase migrations
- [ ] Initial migration exists
- [ ] `users` table exists
- [ ] `tasks` table exists
- [ ] `planned_for_at` is nullable for backlog tasks
- [ ] Constraints are defined clearly
- [ ] Status storage strategy is explicit
- [ ] Migration runs automatically on startup

## 4.2 Seed data
- [ ] Seed strategy is defined
- [ ] Initial manual user can be inserted
- [ ] Local development startup instructions cover DB initialization

---

# 5. Web flows

## 5.1 Authentication
- [ ] Simple login flow implemented
- [ ] No registration page exists
- [ ] Password storage is not plain text

## 5.2 Task creation
- [ ] User can open create-task page
- [ ] User can submit task title
- [ ] User can submit task description
- [ ] User can submit planned datetime
- [ ] User can create a backlog task without planned datetime
- [ ] User can optionally submit deadline datetime
- [ ] Validation errors are shown correctly

## 5.3 Task editing
- [ ] User can open edit page
- [ ] User can change title
- [ ] User can change description
- [ ] User can change planned datetime
- [ ] User can clear planned datetime to move task into backlog
- [ ] User can change deadline datetime
- [ ] User can save changes successfully

## 5.4 Daily task view
- [ ] User can view tasks for today
- [ ] User can view tasks for a selected date
- [ ] User can view backlog tasks separately from dated tasks
- [ ] Date picker exists
- [ ] Active tasks are clearly displayed
- [ ] Overdue tasks are visually highlighted

## 5.5 Completion and closure
- [ ] User can mark a task as `DONE`
- [ ] User can mark a task as `CLOSED`
- [ ] Completed tasks do not appear in the main active list
- [ ] Closed tasks do not appear in the main active list

## 5.6 Rescheduling
- [ ] User can move one task to another datetime
- [ ] User can move one task into backlog by removing planned datetime
- [ ] User can bulk move unfinished tasks to tomorrow
- [ ] Bulk move affects only intended tasks
- [ ] Backlog tasks are not moved by bulk action
- [ ] Completed and closed tasks are not moved by bulk action

---

# 6. UI behavior

## 6.1 MVP usability
- [ ] Main screen is usable without design polish
- [ ] Create task flow is separate and clear
- [ ] Edit task flow is separate and clear
- [ ] Main view is not cluttered by completed tasks

## 6.2 Visual distinction
- [ ] Today tasks are easy to identify
- [ ] Overdue tasks are easy to identify
- [ ] Backlog tasks are clearly separated from dated tasks
- [ ] Completed/closed tasks are visually separated or hidden from the main list

## 6.3 Completed bucket
- [ ] Completed tasks are available in a separate view, block, or collapsible section
- [ ] Backlog tasks are available in a separate view, block, or collapsible section
- [ ] Main active list contains only dated `OPEN` tasks

---

# 7. API and code structure

## 7.1 Layering
- [ ] Controllers are thin
- [ ] Business logic is not placed in controllers
- [ ] Business logic is not placed in templates
- [ ] Application layer exists
- [ ] Domain layer exists
- [ ] Persistence layer exists

## 7.2 DTO usage
- [ ] Request DTOs exist
- [ ] Response/view DTOs exist
- [ ] No direct DB row exposure to templates

## 7.3 REST-friendly structure
- [ ] Internal design does not prevent future REST API expansion

---

# 8. Testing acceptance

## 8.1 Unit tests
- [ ] Task creation rules tested
- [ ] Backlog task creation rules tested
- [ ] Task status transition rules tested
- [ ] Task move/reschedule logic tested
- [ ] Move-to-backlog logic tested
- [ ] Bulk move logic tested

## 8.2 Integration tests
- [ ] Repository integration tests run against PostgreSQL via Testcontainers
- [ ] Liquibase migrations validated in test environment
- [ ] Task persistence and retrieval tested
- [ ] Date filtering tested
- [ ] Backlog filtering tested
- [ ] Status filtering tested

## 8.3 Web/application flow tests
- [ ] Create task flow tested
- [ ] Create backlog task flow tested
- [ ] Edit task flow tested
- [ ] Complete task flow tested
- [ ] Close task flow tested
- [ ] Daily view tested
- [ ] Backlog view tested
- [ ] Bulk move unfinished tasks tested

---

# 9. Docker acceptance

## 9.1 Local run
- [ ] Application starts locally through Docker Compose
- [ ] PostgreSQL starts correctly
- [ ] Liquibase migrations apply on startup
- [ ] Application connects to DB successfully

## 9.2 Reproducibility
- [ ] A new environment can run the app without manual hidden steps

---

# 10. Manual acceptance testing

## 10.1 Migration readiness
- [ ] User can manually recreate current tasks
- [ ] User can create future reminders such as birthdays
- [ ] User can keep undated reminder-style tasks in backlog
- [ ] User can manage daily tasks without fallback to the old tracker

## 10.2 Core manual flows
- [ ] Create a task for today
- [ ] Create a task for a future date
- [ ] Create a task with a specific time
- [ ] Create a backlog task without date
- [ ] Edit a task
- [ ] Move a task into backlog
- [ ] Mark a task as done
- [ ] Mark a task as closed
- [ ] Move a task to another date
- [ ] Bulk move unfinished tasks to tomorrow
- [ ] Open backlog section and verify separation rules
- [ ] Open completed tasks section and verify visibility rules

---

# 11. Definition of Done for MVP

The MVP is done when:

- [ ] all MVP checklist items marked as required are completed;
- [ ] the application runs in Docker;
- [ ] tests pass;
- [ ] task migration is practically possible;
- [ ] daily use in place of the current tracker is realistic;
- [ ] no critical blocker remains for switching to DoneIt as the primary tracker.