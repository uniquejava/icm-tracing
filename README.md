# icm-tracing (`retry`)

Demonstrates **activity retry** shapes in OpenTelemetry traces.

## Prerequisites

- Java 25
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `screenshots/` for known issues.

See the branch overview on [`main`](https://github.com/uniquejava/icm-tracing/tree/main#branches).

## Configure secrets

```shell
cp .env.example .env
# edit .env and set MY_NEW_RELIC_API_KEY to your New Relic ingest license key
```

`.env` is gitignored. Docker Compose loads it automatically for the OTel collector (Jaeger + New Relic export).

## Run locally

```shell
./scripts/startup.sh
mvn clean spring-boot:run

# trigger workflow — activity fails against :8081 and retries (more attempts than main)
./scripts/01normal.sh
```

## Where to look

| UI | URL |
|----|-----|
| Temporal UI | http://localhost:8088 |
| Jaeger | http://localhost:16686 |
| App | http://localhost:8080 |

## Shutdown

```shell
./scripts/shutdown.sh
```
