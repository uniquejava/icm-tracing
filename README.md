# icm-tracing (`heartbeat_retry`)

HITL + periodic **heartbeat spans** (~80s) so New Relic does not split one long-running workflow into multiple traces after a quiet period.

## Prerequisites

- Java 21
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `docs/screenshots/` for known issues. See also [HEARTBEAT.md](HEARTBEAT.md).

See the branch overview on [`main`](https://github.com/uniquejava/icm-tracing/tree/main#branches).

## Tracing

- Only **MainWorkflow** emits custom `heartbeat` spans (via `WorkflowHeartbeatSupport`).
- Collector drops Temporal's `StartActivity:RecordHeartbeat` / `RunActivity:RecordHeartbeat` so only custom heartbeats remain.
- Collector exports to **Jaeger** and **New Relic**.

## Configure secrets

```shell
cp .env.example .env
# edit .env and set MY_NEW_RELIC_API_KEY to your New Relic ingest license key
```

`.env` is gitignored. Docker Compose loads it automatically for the OTel collector (Jaeger + New Relic export).

## Run locally

```shell
./scripts/01start-temporal-server.sh
# or: ./scripts/02start-app.sh
mvn clean spring-boot:run

./scripts/03trigger-workflow.sh

# approve — this branch accepts parent or child workflow id
./scripts/04approve-workflow.sh '<workflowId>'
```

Leave the workflow waiting long enough (e.g. >90s) to observe heartbeat spans before approving.

## Where to look

| UI | URL |
|----|-----|
| Temporal UI | http://localhost:8088 |
| Jaeger | http://localhost:16686 |
| App | http://localhost:8080 |

## Shutdown

```shell
./scripts/05shutdown.sh
```
