# icm-tracing

Temporal + OpenTelemetry tracing demos (local Jaeger and New Relic).

## Prerequisites

- Java 25
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `screenshots/` for known issues.

## Branches

Each branch is a Temporal + OpenTelemetry (New Relic) demo focused on one tracing concern:

| Branch | What it demonstrates |
|--------|----------------------|
| [main](https://github.com/uniquejava/icm-tracing/tree/main) | Baseline: parent→child workflow + failing activity retries. No HITL / heartbeat / span link. |
| [retry](https://github.com/uniquejava/icm-tracing/tree/retry) | Stronger **activity retry** (more attempts) so retry shapes are easy to see in traces. |
| [hitl](https://github.com/uniquejava/icm-tracing/tree/hitl) | **Human-in-the-loop**: child waits on a signal; `GET /approve` unblocks it. No heartbeat / span link. |
| [hitl_spanlink](https://github.com/uniquejava/icm-tracing/tree/hitl_spanlink) | Same HITL as `hitl`, plus an OTel **Span Link** from the approve request back to the waiting workflow span. |
| [heartbeat_retry](https://github.com/uniquejava/icm-tracing/tree/heartbeat_retry) | HITL + periodic **heartbeat spans** (~80s) so New Relic does not split one long workflow into multiple traces. |
| [heartbeat_hitl](https://github.com/uniquejava/icm-tracing/tree/heartbeat_hitl) | Same HITL + heartbeat idea as `heartbeat_retry`; approve typically uses the **child** workflow id. |

Conceptual relationship:

```
main ──► retry                         (retries)
  └──► hitl ──► hitl_spanlink          (approval → + span link)
         └──► heartbeat_hitl / heartbeat_retry  (approval → + heartbeat)
```

## Configure secrets

```shell
cp .env.example .env
# edit .env and set MY_NEW_RELIC_API_KEY to your New Relic ingest license key
```

`.env` is gitignored. Docker Compose loads it automatically for the OTel collector (Jaeger + New Relic export).

## Run locally (this branch: `main`)

```shell
# 1) infra: Temporal dev server + OTel collector + Jaeger
./scripts/startup.sh

# 2) app
mvn clean spring-boot:run

# 3) trigger a workflow (activity calls a failing :8081 endpoint → retries)
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
# also stop the Temporal dev server if still running (e.g. kill the nohup process)
```
