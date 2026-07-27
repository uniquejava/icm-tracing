# icm-tracing — `adot-direct` Lab

验证：**Temporal traces → ADOT Java Agent → AWS X-Ray OTLP**（无本地 Collector）。

English：[README.md](./README.md) · Lab 报告：[docs/lab-adot-direct_zh.md](docs/lab-adot-direct_zh.md) / [EN](docs/lab-adot-direct.md)

**带 Collector 的对照：** 分支 [`adot-aws`](https://github.com/uniquejava/icm-tracing/tree/adot-aws) — [docs/lab-adot-aws_zh.md](docs/lab-adot-aws_zh.md)。

本分支用 AWS 官方的 **无 Collector** 路径回答与 `adot-aws` 相同的评估 / ADR Option B 问题：
ADOT Agent 对 OTLP 做 SigV4，直连 `https://xray.{region}.amazonaws.com/v1/traces`。

| 文档 | 相关性 |
|------|--------|
| Observability backend assessment | Option B：外部/平台 runtime 经 ADOT → CloudWatch |
| ADR-011（CW + NR 混合） | 证明 Temporal 的 CW 路径**无需** sidecar Collector |
| [CloudWatch — 无 Collector ADOT](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLP-UsingADOT.html) | 官方 Java Agent → 托管 OTLP 端点 |

## 简短结论

**可以**：Temporal → X-Ray 摄入（Transaction Search 开启时 span 在 **`aws/spans`**）。

**注意**

1. 原版 OpenTelemetry Java → X-Ray OTLP 会 **403**（需要 SigV4）。用 ADOT Agent
   （≥ 2.11.2），不要用普通 `OtlpHttpSpanExporter`。
2. **不要**再往本地 Collector（`:4317`）导。`OtelConfig` 暴露 `GlobalOpenTelemetry.get()`，
   让 Temporal Spring Boot 共用 Agent 的 SDK。
3. 本 Lab **关闭** Metrics（`OTEL_METRICS_EXPORTER=none`）。EMF 见 `adot-aws`。
4. 若 X-Ray destination 是 **CloudWatchLogs**，看 `aws/spans` / Transaction Search —
   `get-trace-summaries` 为空不等于导出失败。
5. `service.name` 为 **`adot-direct`**（与本分支名一致）。

```
Java 应用 + ADOT Java Agent   service.name=adot-direct
        │  OTLP HTTP/protobuf + SigV4
        ▼
https://xray.{region}.amazonaws.com/v1/traces
        ▼
aws/spans（Transaction Search）
```

## 前置条件

- Java 25、Maven 3.9.x、Temporal CLI
- 本机 AWS profile 能 `xray:PutTraceSegments`（以及对 `aws/spans` 写 Logs）
- `.tools/` 下的 ADOT Agent JAR（见下）— **不需要** Docker Collector

## 配置

```shell
cp .env.example .env
# 按你的 Lab 凭证设置 AWS_REGION / AWS_PROFILE
aws sts get-caller-identity

mkdir -p .tools
curl -fL -o .tools/aws-opentelemetry-agent.jar \
  https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
```

## 跑 Lab

```shell
# 1) 只起 Temporal
./scripts/startup-direct.sh

# 2) 应用 + ADOT Agent（Session + X-Ray 相关 OTEL_*）
./scripts/run-app-direct.sh

# 3) 触发工作流（Activity 打失败的 :8081 → trace 里可见重试）
./scripts/01normal.sh
```

Session 过期后先刷新 SSO，再重新跑 `./scripts/run-app-direct.sh`。

## 去哪看

| UI | 位置 |
|----|------|
| Temporal UI | http://localhost:8088 |
| AWS 上的 traces | CloudWatch Transaction Search / `aws/spans` — 过滤服务 **`adot-direct`** |

## 关闭

停掉 Spring Boot（Ctrl+C）。若用脚本起了 Temporal，再停掉 `temporal server start-dev`。
本 Lab 的导出路径不需要 `docker compose down`。

## 与其他分支的关系

| 分支 | 路径 |
|------|------|
| `adot-aws` | 应用 → ADOT **Collector** → X-Ray / EMF + Jaeger |
| **`adot-direct`（本分支）** | 应用 + ADOT **Java Agent** → X-Ray OTLP（无 Collector） |
| `main` / `retry` / `hitl` / … | Temporal 演示 → Jaeger / New Relic / Datadog |
