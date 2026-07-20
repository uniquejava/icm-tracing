# icm-tracing (`hitl`)

**Human-in-the-loop**: child workflow waits on a Temporal signal; approve via HTTP.

## Prerequisites

- Java 21
- Maven 3.9.x
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal` on `PATH`)
- Docker / Docker Compose

check `screenshots/` for known issues.

See the branch overview on [`main`](https://github.com/uniquejava/icm-tracing/tree/main#branches).

## Tracing

- App exports OTLP (gRPC) to the local collector (`localhost:4317`).
- Collector fans out to **Jaeger** and **New Relic**.
- HTTP server spans include `http.request.method` and `http.route`.

## Configure secrets

```shell
cp .env.example .env
# edit .env: MY_NEW_RELIC_API_KEY, DD_API_KEY (and DD_SITE if needed)
```

`.env` is gitignored. Docker Compose loads it automatically for the OTel collector (Jaeger + New Relic + Datadog export).

## Run locally

```shell
./scripts/startup.sh
mvn clean spring-boot:run

# start workflow — child blocks waiting for approval
./scripts/01normal.sh

# approve (use the child workflow id, usually <parentId>-child-001)
./scripts/02approve.sh '<workflowId>'
# e.g. ./scripts/02approve.sh '1-child-001'
```

## Where to look

| UI | URL |
|----|-----|
| Temporal UI | http://localhost:8088 |
| Jaeger | http://localhost:16686 |
| New Relic | https://one.newrelic.com/ |
| Datadog APM | https://ap1.datadoghq.com/apm/traces |
| App | http://localhost:8080 |

## Shutdown

```shell
./scripts/shutdown.sh
```
