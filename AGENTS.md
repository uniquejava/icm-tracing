# Agent notes — icm-tracing

Public GitHub repo. **Never commit AWS account IDs, ARNs, emails, access keys, or `.env`.**

## Branches (observability labs)

| Branch | Status | Purpose |
|--------|--------|---------|
| `main` + feature branches (`retry`, `hitl`, …) | Done | Temporal + OTel → Jaeger / New Relic / Datadog |
| [`adot-aws`](https://github.com/uniquejava/icm-tracing/tree/adot-aws) | **Done / pushed** | Temporal → **ADOT Collector** → X-Ray / CloudWatch EMF + custom CW dashboard |
| [`adot-direct`](https://github.com/uniquejava/icm-tracing/tree/adot-direct) | **Done** | Validate **collector-less** ADOT Java Agent → AWS managed OTLP endpoints |

Lab report: `docs/lab-adot-direct.md` / `_zh.md`. Run: `./scripts/startup-direct.sh` + `./scripts/run-app-direct.sh`.

---

## Reference: `adot-direct` (collector-less) checklist

### Goal

Prove AWS’s official **no local Collector** path:

App + **ADOT Java Agent (≥ 2.11.2)** → SigV4 OTLP HTTP →  
`https://xray.{region}.amazonaws.com/v1/traces` (spans land in `aws/spans` when Transaction Search is on).

Do **not** run `amazon/aws-otel-collector` / docker compose collector for this lab.

### Official docs

- [Exporting collector-less telemetry using ADOT SDK](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLP-UsingADOT.html)
- [CloudWatch OTLP endpoints](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPEndpoint.html)
- Prior lab (with Collector): `docs/lab-adot-aws.md` / `docs/lab-adot-aws_zh.md`

### Why not plain `OtlpHttpSpanExporter`?

Vanilla OpenTelemetry Java → X-Ray OTLP URL returns **403** (needs **SigV4**). ADOT Agent (or Collector + `sigv4auth`) signs requests. See [opentelemetry-java#6979](https://github.com/open-telemetry/opentelemetry-java/issues/6979).

### Implementation checklist

1. **Download agent** (gitignore the JAR):
   ```shell
   mkdir -p .tools
   curl -fL -o .tools/aws-opentelemetry-agent.jar \
     https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
   ```
   Add `.tools/` to `.gitignore`.

2. **Stop dual exporters** — current `OtelConfig` sends OTLP gRPC to `localhost:4317` (Collector). On this branch either:
   - Make `OtelConfig` expose `GlobalOpenTelemetry.get()` as the Spring `@Bean` for Temporal (agent configures the global SDK), **or**
   - Disable custom `BatchSpanProcessor` / OTLP-to-collector when running with the agent.
   Temporal Spring Boot needs an `OpenTelemetry` bean; the agent alone may not register one in Spring.

3. **Disable Micrometer OTLP → `:4318`** in `application.yaml` for this branch (no Collector). Official Java sample often sets `OTEL_METRICS_EXPORTER=none`. Metrics validation can be follow-up (CW metrics OTLP endpoint or Micrometer CloudWatch registry).

4. **`service.name`**: use **`adot-direct`** (match branch), via `spring.application.name` / `OTEL_RESOURCE_ATTRIBUTES`.

5. **Run scripts** (suggested):
   - `scripts/startup-direct.sh` — Temporal only (no docker ADOT collector).
   - `scripts/run-app-direct.sh` — export session creds + start Spring Boot with agent, e.g.:
     ```bash
     eval "$(aws configure export-credentials --profile "${AWS_PROFILE:-default}" --format env)"
     export AWS_REGION="${AWS_REGION:-eu-west-1}"
     export JAVA_TOOL_OPTIONS="-javaagent:$PWD/.tools/aws-opentelemetry-agent.jar"
     export OTEL_METRICS_EXPORTER=none
     export OTEL_LOGS_EXPORTER=none   # optional; enable later with logs endpoint + log group
     export OTEL_TRACES_EXPORTER=otlp
     export OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=http/protobuf
     export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="https://xray.${AWS_REGION}.amazonaws.com/v1/traces"
     export OTEL_TRACES_SAMPLER=parentbased_traceidratio
     export OTEL_TRACES_SAMPLER_ARG=1.0   # lab only; prod ~0.05
     export OTEL_RESOURCE_ATTRIBUTES="service.name=adot-direct,deployment.environment=adot-direct"
     export OTEL_AWS_APPLICATION_SIGNALS_ENABLED=false
     JAVA_HOME=$(/usr/libexec/java_home -v 25)
     mvn spring-boot:run -Dmaven.compiler.proc=full
     ```
   - Trigger: `./scripts/01normal.sh`

6. **Verify**
   - No Collector container required.
   - CloudWatch log group `aws/spans` has spans with `service.name=adot-direct`.
   - Transaction Search filter `service.name = "adot-direct"`.
   - Note: `get-trace-summaries` may still be empty if destination is `CloudWatchLogs` (same as `adot-aws` lab).

7. **Docs**: `docs/lab-adot-direct.md` + `docs/lab-adot-direct_zh.md`, update README branch table. Link contrast with Collector path (`adot-aws`).

8. **Commit / push** when green; no account IDs in docs/screenshots text.

### Local AWS / ops reminders (from `adot-aws` lab)

- Region used in lab: `eu-west-1`; profile via `.env` `AWS_PROFILE` (gitignored).
- Strip broken HTTP proxies when calling AWS CLI if STS fails (`env -i HOME=… PATH=… aws …`).
- Colima may need `colima start` before Docker; **this branch should not need Docker for AWS export**.
- JDK 25 + Lombok: `-Dmaven.compiler.proc=full`.
- Activity still calls failing `:8081` — retries in the trace are expected.

### Already validated on `adot-aws` (do not redo unless needed)

- Collector + session keys (`aws configure export-credentials`) → X-Ray PutTrace / `aws/spans` + EMF metrics `ICMTracing/Temporal`.
- Empty Compose `AWS_ACCESS_KEY_ID=` breaks SSO profile mount inside ADOT image.
- Dashboard: `./scripts/put-dashboard.sh` → `adot-aws-lab` from `dashboards/adot-aws-main.json`.

---

## Quick pointers

| Artifact | Path |
|----------|------|
| Collector lab report | `docs/lab-adot-aws.md`, `_zh.md` |
| Dashboard JSON | `dashboards/adot-aws-main.json` |
| Collector startup | `scripts/startup.sh` (export-credentials + force-recreate) |
| Assessment / ADR (outside repo) | `aaiplat/.../observability-backend-strategy.md`, `ADR-011-...` — Option B CW path |
