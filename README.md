# icm-tracing (`hitl_spanlink`)

Same HITL flow as `hitl`, plus an OTel **Span Link** from the approve request back to the waiting workflow span.

## Prerequisites

- Java 21
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `screenshots/` for known issues.

See the branch overview on [`main`](https://github.com/uniquejava/icm-tracing/tree/main#branches).

## Tracing

- HITL signal wait + `GET /approve`.
- Approve path creates a linked `hitl.approve` span (see `temporal/spanlink/`).
- Collector exports to **Jaeger** and **New Relic**.

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

./scripts/01normal.sh
./scripts/02approve.sh '<workflowId>'
# e.g. ./scripts/02approve.sh '1-child-001'
```

In Jaeger / New Relic, open the approve span and inspect its **span links** to the waiting workflow span.

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
