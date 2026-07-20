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
# edit .env: MY_NEW_RELIC_API_KEY, DD_API_KEY (and DD_SITE if needed)
```

`.env` is gitignored. Docker Compose loads it automatically for the OTel collector (Jaeger + New Relic + Datadog export).

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
| New Relic | https://one.newrelic.com/ |
| Datadog APM | https://ap1.datadoghq.com/apm/traces |
| App | http://localhost:8080 |

## Shutdown

```shell
./scripts/shutdown.sh
```
