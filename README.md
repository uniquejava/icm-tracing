# icm-tracing — `adot-direct` lab

Validate: **Temporal traces → ADOT Java Agent → AWS X-Ray OTLP** (no local Collector).

中文版：[README_zh.md](./README_zh.md) · Lab 报告：[docs/lab-adot-direct.md](docs/lab-adot-direct.md) / [中文](docs/lab-adot-direct_zh.md)

**Collector path (contrast):** branch [`adot-aws`](https://github.com/uniquejava/icm-tracing/tree/adot-aws) — [docs/lab-adot-aws.md](docs/lab-adot-aws.md).

This branch answers the same assessment / ADR Option B question as `adot-aws`, using AWS’s
**collector-less** path: ADOT Agent signs OTLP with SigV4 to
`https://xray.{region}.amazonaws.com/v1/traces`.

| Document | Relevance |
|----------|-----------|
| Observability backend assessment | Option B — external/platform runtimes → CloudWatch via ADOT |
| ADR-011 (hybrid CW + NR) | Proves CW path for Temporal **without** a sidecar Collector |
| [CloudWatch — collector-less ADOT](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLP-UsingADOT.html) | Official Java agent → managed OTLP endpoints |

## Short answer

**Yes** for Temporal → X-Ray ingest (spans in **`aws/spans`** when Transaction Search is on).

**Caveats**

1. Vanilla OpenTelemetry Java → X-Ray OTLP returns **403** (needs SigV4). Use ADOT Agent
   (≥ 2.11.2), not plain `OtlpHttpSpanExporter`.
2. Do **not** also export to a local Collector (`:4317`). `OtelConfig` exposes
   `GlobalOpenTelemetry.get()` so Temporal Spring Boot shares the agent’s SDK.
3. Metrics are **off** in this lab (`OTEL_METRICS_EXPORTER=none`). See `adot-aws` for EMF.
4. If X-Ray destination is **CloudWatchLogs**, prefer `aws/spans` / Transaction Search —
   empty `get-trace-summaries` is not a failed export.
5. `service.name` is **`adot-direct`** (matches this branch).

```
Java app + ADOT Java Agent   service.name=adot-direct
        │  OTLP HTTP/protobuf + SigV4
        ▼
https://xray.{region}.amazonaws.com/v1/traces
        ▼
aws/spans (Transaction Search)
```

## Prerequisites

- Java 25, Maven 3.9.x, Temporal CLI
- Host AWS profile with `xray:PutTraceSegments` (+ Logs on `aws/spans`)
- ADOT agent JAR under `.tools/` (see below) — **no Docker Collector**

## Configure

```shell
cp .env.example .env
# set AWS_REGION / AWS_PROFILE for your lab credentials
aws sts get-caller-identity

mkdir -p .tools
curl -fL -o .tools/aws-opentelemetry-agent.jar \
  https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
```

## Run the lab

```shell
# 1) Temporal only
./scripts/startup-direct.sh

# 2) app + ADOT agent (session keys + OTEL_* for X-Ray)
./scripts/run-app-direct.sh

# 3) trigger workflow (activity hits failing :8081 → retries in the trace)
./scripts/01normal.sh
```

Session keys expire — re-run `./scripts/run-app-direct.sh` after SSO refresh.

## Where to look

| UI | URL / location |
|----|----------------|
| Temporal UI | http://localhost:8088 |
| Traces in AWS | CloudWatch Transaction Search / `aws/spans` — filter service **`adot-direct`** |

## Shutdown

Stop the Spring Boot process (Ctrl+C). Temporal: stop the `temporal server start-dev` process if you started it via the script. No `docker compose down` required for this lab’s export path.

## Relation to other branches

| Branch | Path |
|--------|------|
| `adot-aws` | App → ADOT **Collector** → X-Ray / EMF + Jaeger |
| **`adot-direct` (this)** | App + ADOT **Java Agent** → X-Ray OTLP (no Collector) |
| `main` / `retry` / `hitl` / … | Temporal demos → Jaeger / New Relic / Datadog |
