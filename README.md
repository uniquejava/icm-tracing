# icm-tracing — `adot-aws` lab

Validate: **Temporal traces + metrics → ADOT Collector → AWS X-Ray / CloudWatch**.

中文版：[README_zh.md](./README_zh.md) · Lab 报告：[docs/lab-adot-aws.md](docs/lab-adot-aws.md) / [中文](docs/lab-adot-aws_zh.md)

**Next (collector-less):** branch `adot-direct` — see [AGENTS.md](./AGENTS.md) and [docs/next-steps-adot-direct.md](docs/next-steps-adot-direct.md).

This branch answers the assessment / ADR question for Option B: can a non-AgentCore
platform component (Temporal Java worker) ship telemetry to AWS via ADOT?

| Document | Relevance |
|----------|-----------|
| Observability backend assessment | Option B claims external/platform runtimes use ADOT → CloudWatch |
| ADR-011 (hybrid CW + NR) | This lab proves the CW path for Temporal |
| [AWS blog — AgentCore Observability](https://aws.amazon.com/blogs/machine-learning/build-trustworthy-ai-agents-with-amazon-bedrock-agentcore-observability/) | Same ADOT→CloudWatch idea, but GenAI/AgentCore-focused (Python `aws-opentelemetry-distro`) |

## Short answer

**Yes** for Temporal → X-Ray ingest (traces) and CloudWatch Metrics via EMF (metrics).

**Caveats**

1. The blog’s **GenAI Observability** dashboard expects GenAI semantic conventions.
   Temporal spans show as workflow/activity traces (X-Ray / Transaction Search),
   **not** as AgentCore GenAI sessions.
2. If the account’s X-Ray trace destination is **CloudWatchLogs** (Transaction Search),
   traces appear in log group **`aws/spans`**. Legacy `aws xray get-trace-summaries`
   may return empty — that is not a failed export.
3. The ADOT collector image does not use SSO profiles from a mounted `~/.aws` reliably.
   `./scripts/startup.sh` injects short-lived session keys via
   `aws configure export-credentials`.
4. `service.name` is **`adot-aws`** (matches this branch), from `spring.application.name`.

```
Java app (Temporal + Micrometer/OTel)  service.name=adot-aws
        │  OTLP :4317 traces / :4318 metrics
        ▼
ADOT Collector
        ├─ awsxray  → X-Ray (→ aws/spans when Transaction Search is on)
        ├─ awsemf   → CloudWatch Logs → Metrics (namespace ICMTracing/adot-aws)
        └─ otlp     → local Jaeger (dual-view)
```

## Prerequisites

- Java 25, Maven 3.9.x, Temporal CLI, Docker Compose (Colima/Docker)
- Host AWS profile that can call `xray:PutTraceSegments` and write CloudWatch Logs
  (session exportable with `aws configure export-credentials`)

## Configure

```shell
cp .env.example .env
# set AWS_REGION / AWS_PROFILE for your lab credentials
aws sts get-caller-identity
```

## Run the lab

```shell
# 1) Temporal + ADOT (exports session keys into the collector) + Jaeger
./scripts/startup.sh

# 2) app (JDK 25 needs annotation processing for Lombok)
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn spring-boot:run -Dmaven.compiler.proc=full

# 3) trigger workflow (activity hits failing :8081 → retries in the trace)
./scripts/01normal.sh
```

Session keys expire — re-run `./scripts/startup.sh` after SSO refresh.

```shell
docker compose logs -f otel-collector
```

## Where to look

| UI | URL / location |
|----|----------------|
| Temporal UI | http://localhost:8088 |
| Jaeger (local dual-view) | http://localhost:16686 service **`adot-aws`** |
| Traces in AWS | CloudWatch Transaction Search / `aws/spans` — filter service **`adot-aws`** |
| CloudWatch Metrics | Namespace `ICMTracing/Temporal` (`application=main`), log group `/icm-tracing/otel/metrics` |
| CloudWatch Dashboard | `./scripts/put-dashboard.sh` → [adot-aws-lab](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#dashboards:name=adot-aws-lab) |
| ADOT health | http://localhost:13133 |

Example CloudWatch trace (map + Temporal spans): see [docs/lab-adot-aws.md](docs/lab-adot-aws.md) or screenshot [`screenshots/05xray-traces.png`](screenshots/05xray-traces.png).

## Shutdown

```shell
./scripts/shutdown.sh
```

## Relation to other branches

Other branches demo NR/Datadog/Jaeger shapes (retry, HITL, heartbeat, span links).
This branch swaps the **backend path** to ADOT→AWS while keeping the same Temporal sample.
