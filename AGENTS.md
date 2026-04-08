# Repository Guidelines

## Project Structure & Module Organization
This repository is a Java 21 Maven project built with Spring Boot and Temporal. Application code lives under `src/main/java/click/yinsb/icmtracing`, with HTTP entrypoints in the root package, tracing/configuration in `config`, and Temporal workflows, activities, clients, and shared models under `temporal`. Runtime config is in `src/main/resources` (`application.yaml`, `logback.xml`). Local infrastructure files sit at the repo root (`docker-compose.yml`, `otel-collector.yml`). Helper scripts for common demos live in `scripts/`, and example screenshots and troubleshooting artifacts live in `screenshots/` and `HEARTBEAT.md`.

## Build, Test, and Development Commands
- `mvn clean spring-boot:run` — build and run the app locally on port `8080`.
- `mvn clean package` — produce a fresh application build in `target/`.
- `mvn test` — run the test suite; add tests before relying on this in PRs.
- `./scripts/startup.sh` — start Temporal dev server, Jaeger, and the OTEL collector.
- `./scripts/shutdown.sh` — stop the Docker-based local stack.
- `./scripts/01normal.sh` / `./scripts/02approve.sh <workflowId>` — trigger the demo workflow endpoints.

## Coding Style & Naming Conventions
Follow existing Java style in `src/main/java`: 4-space indentation for Spring app/config classes and the repository’s prevailing style inside Temporal workflow classes. Keep package names lowercase, class names `PascalCase`, methods and fields `camelCase`, and constants `UPPER_SNAKE_CASE`. Prefer focused Spring configuration classes in `config` and keep Temporal types grouped by role (`workflow`, `activities`, `client`, `model`). Use Lombok only where it reduces boilerplate without hiding behavior.

## Testing Guidelines
There is currently no `src/test` tree; new behavior should include targeted unit or Spring integration tests under `src/test/java` mirroring production packages. Name test classes with the `*Test` suffix (for example, `WorkflowClientServiceTest`). Prioritize coverage for workflow orchestration, controller entrypoints, and tracing configuration changes.

## Commit & Pull Request Guidelines
Recent history favors short, imperative commits, often with Conventional Commit prefixes such as `fix:` and `feat:`. Keep commits scoped to one change. PRs should explain the behavioral impact, list validation steps (for example, `mvn test`, demo scripts run), and attach screenshots when trace output or UI behavior changes.

## Configuration & Observability Tips
Do not commit secrets. Set `NR_ENDPOINT` and `MY_NEW_RELIC_API_KEY` in your shell before exporting traces to New Relic. Use Jaeger at `http://localhost:16686` and Temporal UI at `http://localhost:8088` to verify local tracing flows.
