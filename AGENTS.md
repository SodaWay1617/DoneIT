# Repository Guidelines

## Project Structure & Module Organization

DoneIt is a Java 21 / Spring Boot monolith using JDBC, PostgreSQL, Thymeleaf, and Spring AI MCP. Feature packages live under `src/main/java/com/doneit/`. Keep domain rules in `domain`, use cases in `application`, persistence in `infrastructure`, and HTTP handlers in `web` where those layers exist.

Templates and CSS/JavaScript assets are in `src/main/resources/templates/` and `static/`. Translations use `messages.properties` and `messages_ru.properties`. Liquibase migrations belong in `db/changelog/changes/` and must be included in the master changelog. Tests mirror production packages under `src/test/java/`. Product specifications and operational notes live in `spec/`; utility scripts live in `scripts/`.

## Build, Test, and Development Commands

- `mvn spring-boot:run`: run locally against the configured PostgreSQL database.
- `mvn test`: run unit and integration tests; integration tests normally require Docker for Testcontainers.
- `mvn -Dtest=McpTaskInputTest test`: run a focused test class.
- `mvn package`: test and build the executable JAR in `target/`.
- `docker compose up -d --no-build`: start services using existing images.

Avoid unnecessary Docker builds or image downloads; disk space is limited. Reuse existing services when practical. External integration-test databases require `DONEIT_TEST_JDBC_URL` targeting a loopback database ending in `_test`, plus test credentials. Tests clear data: never point them at a working database.

## Coding Style & Naming Conventions

Follow `.editorconfig`: UTF-8, LF, four-space indentation, final newline. Use PascalCase classes, camelCase methods/fields, and uppercase enum constants. Follow nearby code and reuse existing services, components, and CSS variables. No dedicated formatter or linter is configured. Scope CSS changes to the affected page and maintain RU/EN translations.

## Testing Guidelines

Use JUnit 5, Spring Boot Test, MockMvc, AssertJ, and PostgreSQL Testcontainers. Name classes `*Test`. Cover business rules, access control, and failure paths for behavioral changes; no numerical coverage threshold is configured. For UI changes, verify desktop and mobile/iPhone PWA behavior. Run relevant tests first, then the full suite for substantial changes.

## Commit & Pull Request Guidelines

Use the existing style: `feat(mcp): ...`, `fix(navigation): ...`, `style(inbox): ...`, or `docs: ...`. Keep commits focused. PR descriptions should explain the resulting behavior, validation, and migration implications; link relevant specifications/issues and include screenshots for visual changes.

## Security & Domain Constraints

Keep credentials and `.mcp-local/` secrets out of Git. MCP is disabled by default; consult `spec/mcp_mvp_runbook.md` before enabling it. Preserve project visibility checks and recurring-task rules. Close tasks with `CLOSED` instead of physically deleting them; recurring tasks must not become incoming tasks or receive `TODO` status.
