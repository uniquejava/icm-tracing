# icm-tracing (`heartbeat_hitl`)

HITL + periodic **heartbeat spans** (~80s). Approve with the **child** workflow id.

## Prerequisites

- Java 21
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `screenshots/` for known issues. See also [HEARTBEAT.md](HEARTBEAT.md).

See the branch overview on [`main`](https://github.com/uniquejava/icm-tracing/tree/main#branches).

## Tracing

- Same heartbeat idea as `heartbeat_retry` (custom `heartbeat` spans on MainWorkflow).
- Collector filters Temporal heartbeat activity spans; exports to **Jaeger** and **New Relic**.

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

# approve with the child workflow id
./scripts/02approve.sh '<childWorkflowId>'
# e.g. ./scripts/02approve.sh '1-child-001'
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
