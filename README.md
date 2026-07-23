# icm-tracing (`retry_spanlink`)

Demonstrates **Span Links** on Temporal activity retries (exponential backoff), so New Relic can navigate trace fragments after ~90s inactivity gaps.

See [docs/why-spanlink-hard-with-retry.md](docs/why-spanlink-hard-with-retry.md) for why this is non-trivial under Temporal-owned retries.

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

# trigger workflow — activity fails against :8081 and retries (12 attempts, exponential from 2s)
./scripts/01normal.sh
```

Watch logs for `span-link attempt=N -> prior attempt=...`. In Jaeger / New Relic, open an `activity.retry.attempt` span (attempt ≥ 2) and inspect **Span links**.

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
