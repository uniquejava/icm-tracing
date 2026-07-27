# Lab：Temporal + ADOT Java Agent → X-Ray（无 Collector）

对照带 Collector 的路径：[lab-adot-aws_zh.md](./lab-adot-aws_zh.md)。

## 假设

AWS 官方的 **无本地 Collector** 路径对本 Temporal Java Worker 成立：用 **ADOT Java Agent**
对 OTLP/HTTP 做 SigV4 签名，直接 POST 到
`https://xray.{region}.amazonaws.com/v1/traces`，无需运行 `amazon/aws-otel-collector`。

官方文档：

- [使用 ADOT SDK 导出无 Collector 遥测](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLP-UsingADOT.html)
- [CloudWatch OTLP 端点](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPEndpoint.html)

## 为什么不能用原版 OpenTelemetry Java？

普通 `OtlpHttpSpanExporter` 打到 X-Ray OTLP URL 会 **403**（需要 SigV4）。
ADOT Agent（或 Collector + `sigv4auth`）负责签名。参见
[opentelemetry-java#6979](https://github.com/open-telemetry/opentelemetry-java/issues/6979)。

## 架构

```
Java 应用 + ADOT Java Agent（≥ 2.11.2）   service.name=adot-direct
        │  OTLP HTTP/protobuf + SigV4
        ▼
https://xray.{region}.amazonaws.com/v1/traces
        ▼
X-Ray 摄入 → aws/spans（Transaction Search / CloudWatchLogs destination 开启时）
```

本 Lab 不跑 Docker ADOT Collector。Metrics 默认关闭（`OTEL_METRICS_EXPORTER=none`，
Micrometer OTLP 关闭）。后续可用 CW Metrics OTLP 或 Micrometer CloudWatch registry。

## Lab 结论（已验证）

| 主题 | 结论 |
|------|------|
| Traces 路径 | **可用** — ADOT Agent → X-Ray OTLP → `aws/spans` 中 `service.name=adot-direct` |
| Collector | **不需要** — 本 Lab 不要起 `docker compose` / ADOT Collector |
| 双 SDK | **禁止**再自建 `OpenTelemetrySdk` → `:4317`。`OtelConfig` 暴露 `GlobalOpenTelemetry.get()` 给 Temporal Spring Boot |
| 鉴权 | `./scripts/run-app-direct.sh` 用 `aws configure export-credentials` 注入短期 Session |
| App（JDK 25） | `mvn spring-boot:run -Dmaven.compiler.proc=full`（Lombok） |
| `service.name` | **`adot-direct`**（与分支名一致），来自 `spring.application.name` + `OTEL_RESOURCE_ATTRIBUTES` |
| 旧版 X-Ray API | destination 为 `CloudWatchLogs` 时 `get-trace-summaries` 可能仍为空（与 `adot-aws` 相同） |

## 怎么跑

```shell
# 1) 只起 Temporal（无 Collector）
./scripts/startup-direct.sh

# 2) 应用 + ADOT Agent（导出 Session、设置 X-Ray OTLP 相关 OTEL_*）
./scripts/run-app-direct.sh

# 3) 触发工作流（Activity 打失败的 :8081 → trace 里可见重试）
./scripts/01normal.sh
```

Agent JAR（`.tools/` 已 gitignore）：

```shell
mkdir -p .tools
curl -fL -o .tools/aws-opentelemetry-agent.jar \
  https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
```

## 通过 / 失败清单

- [x] 导出不依赖 ADOT Collector 容器
- [x] 应用以 `-javaagent:.tools/aws-opentelemetry-agent.jar` 启动（agent `2.29.0-aws`）
- [x] CloudWatch 日志组 `aws/spans` 有 `service.name=adot-direct` 的 span（Logs Insights：`@message like /adot-direct/`）
- [x] Agent 日志出现：`Detected using AWS OTLP Endpoint: https://xray.eu-west-1.amazonaws.com/v1/traces`

### 对 ADR 的含义

| 结果 | 含义 |
|------|------|
| `aws/spans` 有 span | 无 Collector 的 Option B 路径对 JVM Temporal Worker **可行**（Agent + IAM） |
| 导出 403 | 凭证 / 区域错误，或没用 ADOT Agent（原版 OTel 无 SigV4） |
| 只有本地、无 AWS span | Agent 环境变量缺失，或仍有第二套 SDK 往 Collector `:4317` 导 |

## IAM 草图（Lab）

与 `adot-aws` 的 traces 权限相同（`xray:PutTraceSegments`、对 `aws/spans` 写 Logs）。
本 Lab 默认跑法不需要 EMF metrics 日志组。

## 与 `adot-aws` 对比

| | `adot-aws` | `adot-direct` |
|--|------------|---------------|
| 路径 | 应用 → ADOT **Collector** → `awsxray` / `awsemf` | 应用 + ADOT **Java Agent** → X-Ray OTLP |
| Docker | Collector + Jaeger | 仅 Temporal |
| Metrics | Micrometer → EMF | 关闭（后续） |
| 运维取舍 | 集中式 Collector、本地 Jaeger 对照 | 组件更少；每个 JVM 挂 Agent |

## 后续

1. Metrics：CW Metrics OTLP 或 Micrometer CloudWatch registry。
2. Logs：启用 `OTEL_LOGS_EXPORTER` 时配置 OTLP logs 端点与 log group。
3. 生产采样：降低 `OTEL_TRACES_SAMPLER_ARG`（Lab 用 `1.0`）。
