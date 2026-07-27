# Lab: Temporal + ADOT → CloudWatch / X-Ray

## Hypothesis

ADOT can forward Temporal OpenTelemetry **traces** and **metrics** from this Java
worker to AWS (X-Ray + CloudWatch), which is the technical premise behind
Assessment Option B for platform components (and the CW half of ADR-011 hybrid).

## What this lab is / is not

| Is | Is not |
|----|--------|
| OTLP from Temporal Java → ADOT Collector → `awsxray` / `awsemf` | AgentCore Runtime auto-instrumentation |
| Proof that Temporal *can* dual-write or move to CW | Proof that GenAI Observability dashboard will render Temporal spans |
| Evidence for “ADOT + AWS credentials + log group” onboarding cost | A production forwarding design (Firehose → NR, multi-account OAM, etc.) |

The [AWS blog](https://aws.amazon.com/blogs/machine-learning/build-trustworthy-ai-agents-with-amazon-bedrock-agentcore-observability/)
uses Python `aws-opentelemetry-distro` for **agents**. Temporal uses the generic
ADOT Collector exporters (`awsxray`, `awsemf`). Same family; different product surface.

## Lab findings (validated)

| Topic | Finding |
|-------|---------|
| Metrics path | **Works** — Micrometer OTLP → `awsemf` → `/icm-tracing/otel/metrics` → namespace `ICMTracing/Temporal` (existing lab data, `application=main`) |
| Dashboard | Optional: `./scripts/put-dashboard.sh` creates CloudWatch Dashboard `adot-aws-lab` from `dashboards/adot-aws-main.json` |
| Traces path | **Works** — OTLP → `awsxray` → `PutTraceSegments` with `UnprocessedTraceSegments: []` |
| Where to read traces | If X-Ray destination is **CloudWatchLogs**, use log group **`aws/spans`** / Transaction Search. Do **not** treat empty `get-trace-summaries` as export failure. |
| Collector auth | Do **not** rely on mounting `~/.aws` + `AWS_PROFILE` into the ADOT image. Inject session keys via `aws configure export-credentials` (`./scripts/startup.sh`). Empty `AWS_ACCESS_KEY_ID=` from Compose overrides the profile and breaks export. |
| App (JDK 25) | Use `mvn spring-boot:run -Dmaven.compiler.proc=full` so Lombok annotation processing runs. |
| `service.name` | Set to **`adot-aws`** (same as the git branch) via `spring.application.name`. |

### Example: CloudWatch / X-Ray trace (topology + Temporal spans)

After `./scripts/01normal.sh`, Transaction Search shows the HTTP entry (`/request`), parent/child workflows, and the failing activity retries. Trace map: **Client → adot-aws → remote HTTP**.

![CloudWatch X-Ray trace: adot-aws /request with Temporal workflow spans](../screenshots/05xray-traces.png)

> Screenshot captured while `service.name` was still `main`. After restarting the app on this branch it appears as **`adot-aws : /request`**.

### CloudWatch custom Dashboard (JSON + CLI)

Raw Metrics in the console are a flat list — fine for a quick check, weak for ops. Same idea as Grafana: keep a dashboard definition in Git and apply it with the AWS CLI.

**What we used (matches already-ingested lab data):**

| Item | Value |
|------|--------|
| Namespace | `ICMTracing/Temporal` |
| Filter | `application=main` (Micrometer tag from the earlier run) |
| Dashboard name | `adot-aws-lab` |
| Definition file | [`dashboards/adot-aws-main.json`](../dashboards/adot-aws-main.json) |
| Apply script | [`scripts/put-dashboard.sh`](../scripts/put-dashboard.sh) |

**1) Discover metric names / dimensions** (so `SEARCH(...)` braces match EMF cardinality):

```shell
aws cloudwatch list-metrics \
  --namespace ICMTracing/Temporal \
  --region eu-west-1 \
  --query 'Metrics[].MetricName' \
  --output text | tr '\t' '\n' | sort -u

aws cloudwatch list-metrics \
  --namespace ICMTracing/Temporal \
  --metric-name temporal_activity_execution_failed \
  --region eu-west-1 \
  --max-items 1
```

**2) Author the dashboard JSON** — widgets use CloudWatch `SEARCH` expressions so we do not hard-code every EMF dimension combination, for example:

```text
SEARCH('{ICMTracing/Temporal,application,lab} application="main" MetricName="process.cpu.usage"', 'Average', 60)
```

Widgets in this lab: Temporal activity latency / client requests / request latency, JVM memory, process CPU, plus a Logs table on `aws/spans` filtered to service `main`.

**3) Create or update the dashboard:**

```shell
# wrapper (reads AWS_REGION / AWS_PROFILE from .env)
./scripts/put-dashboard.sh

# equivalent one-liner
aws cloudwatch put-dashboard \
  --dashboard-name adot-aws-lab \
  --dashboard-body file://dashboards/adot-aws-main.json \
  --region eu-west-1
```

Successful apply returns `"DashboardValidationMessages": []`.

**4) Open:**

https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#dashboards:name=adot-aws-lab

After a workflow run (and EMF flush), the board looks like this:

![CloudWatch custom dashboard adot-aws-lab for application=main](../screenshots/06cloudwatch-custom-dashboard.png)

**Ops note:** editing the JSON and re-running `put-dashboard` overwrites the console copy (idempotent upsert by name). To pull a console-edited board back into Git: Dashboard → **Actions → View/edit source** → save into `dashboards/adot-aws-main.json`.

## Pass / fail checklist

Run `./scripts/startup.sh`, start the app, `./scripts/01normal.sh`, then:

- [ ] `docker compose logs otel-collector` shows no repeated `AccessDenied` / credential errors
- [ ] Jaeger shows service **`adot-aws`** with parent→child workflow + activity retry spans
- [ ] CloudWatch / Transaction Search shows a trace for service **`adot-aws`** (log group `aws/spans`, or X-Ray console)
- [ ] CloudWatch log group `/icm-tracing/otel/metrics` receives EMF log events
- [ ] CloudWatch Metrics namespace `ICMTracing/Temporal` has datapoints (or open Dashboard `adot-aws-lab`)

### Interpreting results for the ADR

| Result | Implication |
|--------|-------------|
| All checks pass | CW path is **technically viable** for Temporal; Option B / hybrid Layer-1 for Temporal is feasible |
| Traces OK, metrics missing | Check Micrometer OTLP URL (`:4318/v1/metrics`) and `awsemf` IAM / log group |
| Local Jaeger OK, AWS empty | Almost always collector credentials (re-run `./scripts/startup.sh`) or region mismatch in `otel-collector.yml` |
| Everything works but ops still prefer NR | Aligns with ADR-011: keep NR as L2 ops view; CW as L1 / Evaluations / optional dual-write |

## IAM sketch (lab)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords",
        "xray:GetSamplingRules",
        "xray:GetSamplingTargets"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams",
        "logs:DescribeLogGroups"
      ],
      "Resource": [
        "arn:aws:logs:*:*:log-group:/icm-tracing/otel/metrics*",
        "arn:aws:logs:*:*:log-group:aws/spans*"
      ]
    }
  ]
}
```

## Follow-ups (out of scope for this branch)

1. Dual-export: keep NR exporter alongside `awsxray` (ADR-011 hybrid for Temporal itself).
2. Compare Transaction Search / Application Signals UX vs NR APM for Temporal workflows.
3. Cost sample: X-Ray / `aws/spans` ingest + EMF volume for one workflow with activity retries.
4. Compare onboarding friction (AWS session export + ADOT) vs NR OTLP HTTPS + API key — feeds assessment “external runtime onboarding” (Temporal is AWS-friendly; managed SaaS runtimes may not be).
