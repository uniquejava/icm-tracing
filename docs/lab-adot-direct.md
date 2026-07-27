# Lab: Temporal + ADOT Java Agent → X-Ray (collector-less)

Contrast with the Collector path: [lab-adot-aws.md](./lab-adot-aws.md).

## Hypothesis

AWS’s **collector-less** path works for this Temporal Java worker: the **ADOT Java Agent**
signs OTLP/HTTP with SigV4 and posts spans directly to
`https://xray.{region}.amazonaws.com/v1/traces` — no local `amazon/aws-otel-collector`.

Official docs:

- [Exporting collector-less telemetry using ADOT SDK](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLP-UsingADOT.html)
- [CloudWatch OTLP endpoints](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPEndpoint.html)

## Why not plain OpenTelemetry Java?

Vanilla `OtlpHttpSpanExporter` → X-Ray OTLP URL returns **403** (needs SigV4).
ADOT Agent (or Collector + `sigv4auth`) signs requests. See
[opentelemetry-java#6979](https://github.com/open-telemetry/opentelemetry-java/issues/6979).

## Architecture

```
Java app + ADOT Java Agent (≥ 2.11.2)   service.name=adot-direct
        │  OTLP HTTP/protobuf + SigV4
        ▼
https://xray.{region}.amazonaws.com/v1/traces
        ▼
X-Ray ingest → aws/spans (when Transaction Search / CloudWatchLogs destination is on)
```

No Docker ADOT Collector. Metrics export is disabled in this lab (`OTEL_METRICS_EXPORTER=none`,
Micrometer OTLP off). Metrics via CW OTLP or Micrometer CloudWatch registry is a follow-up.

## Lab findings (validated)

| Topic | Finding |
|-------|---------|
| Traces path | **Works** — ADOT Agent → X-Ray OTLP → spans in `aws/spans` with `service.name=adot-direct` |
| Collector | **Not required** — do not run `docker compose` / `amazon/aws-otel-collector` for this lab |
| Dual SDK | Must **not** also build `OpenTelemetrySdk` → `:4317`. `OtelConfig` exposes `GlobalOpenTelemetry.get()` for Temporal Spring Boot |
| Auth | Host session keys via `aws configure export-credentials` in `./scripts/run-app-direct.sh` |
| App (JDK 25) | `mvn spring-boot:run -Dmaven.compiler.proc=full` (Lombok) |
| `service.name` | **`adot-direct`** (matches branch) via `spring.application.name` + `OTEL_RESOURCE_ATTRIBUTES` |
| Legacy X-Ray API | `get-trace-summaries` may still be empty if destination is `CloudWatchLogs` (same as `adot-aws`) |

## Run the lab

```shell
# 1) Temporal only (no Collector)
./scripts/startup-direct.sh

# 2) App + ADOT agent (exports session creds, sets OTEL_* for X-Ray OTLP)
./scripts/run-app-direct.sh

# 3) Trigger workflow (activity hits failing :8081 → retries in the trace)
./scripts/01normal.sh
```

Agent JAR (gitignored under `.tools/`):

```shell
mkdir -p .tools
curl -fL -o .tools/aws-opentelemetry-agent.jar \
  https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
```

## Pass / fail checklist

- [x] No ADOT Collector container required / running for export
- [x] App starts with `-javaagent:.tools/aws-opentelemetry-agent.jar` (agent `2.29.0-aws`)
- [x] CloudWatch log group `aws/spans` has spans with `service.name=adot-direct` (validated via Logs Insights `@message like /adot-direct/`)
- [x] Agent log: `Detected using AWS OTLP Endpoint: https://xray.eu-west-1.amazonaws.com/v1/traces`

### Interpreting results for the ADR

| Result | Implication |
|--------|-------------|
| Spans in `aws/spans` | Collector-less Option B path is **viable** for JVM Temporal workers (agent + IAM) |
| 403 on export | Wrong credentials / region, or not using ADOT agent (plain OTel lacks SigV4) |
| Local-only / no AWS spans | Agent env vars missing, or still exporting to Collector `:4317` via a second SDK |

## IAM sketch (lab)

Same as `adot-aws` for traces (`xray:PutTraceSegments`, CloudWatch Logs on `aws/spans`).
No EMF metrics log group needed for this lab’s default run.

## Contrast with `adot-aws`

| | `adot-aws` | `adot-direct` |
|--|------------|---------------|
| Path | App → ADOT **Collector** → `awsxray` / `awsemf` | App + ADOT **Java Agent** → X-Ray OTLP |
| Docker | Collector + Jaeger | Temporal only |
| Metrics | Micrometer → EMF | Off (follow-up) |
| Ops trade-off | Central collector, dual-view Jaeger | Fewer moving parts; agent on every JVM |

## Follow-ups

1. Metrics: CW metrics OTLP endpoint or Micrometer CloudWatch registry.
2. Logs: OTLP logs endpoint + log group when enabling `OTEL_LOGS_EXPORTER`.
3. Prod sampler: lower `OTEL_TRACES_SAMPLER_ARG` (lab uses `1.0`).
