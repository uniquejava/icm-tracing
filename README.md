## Environment
- java 25
- maven 3.9.x
- temporal version 1.5.1 (Server 1.29.1, UI 2.42.1

check screenshots directory for known issues.

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

## Up & Running

```shell
# keys
export NR_ENDPOINT=https://otlp.nr-data.net:4317
export MY_NEW_RELIC_API_KEY=91xxxxxxxFFFFNRAL

# start temporal server, otel collector and jaeger
./scripts/startup.sh

# run app (Or start from your IDE)
mvn clean spring-boot:run

# trigger workflow
./scripts/01normal.sh
```
