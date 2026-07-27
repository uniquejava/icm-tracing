# icm-tracing — `adot-aws` Lab

验证：**Temporal traces + metrics → ADOT Collector → AWS X-Ray / CloudWatch**。

English：[README.md](./README.md) · Lab 报告：[docs/lab-adot-aws_zh.md](docs/lab-adot-aws_zh.md) / [EN](docs/lab-adot-aws.md)

本分支回答评估 / ADR 里 Option B 的问题：非 AgentCore 的平台组件（Temporal Java Worker）
能否用 ADOT 把遥测打到 AWS？

| 文档 | 相关性 |
|------|--------|
| Observability backend assessment | Option B：外部/平台 runtime 用 ADOT → CloudWatch |
| ADR-011（CW + NR 混合） | 本 Lab 证明 Temporal 的 CW 路径 |
| [AWS 博客 — AgentCore Observability](https://aws.amazon.com/blogs/machine-learning/build-trustworthy-ai-agents-with-amazon-bedrock-agentcore-observability/) | 同一 ADOT→CW 思路，但偏 GenAI / AgentCore（Python `aws-opentelemetry-distro`） |

## 简短结论

**可以**：Temporal → X-Ray 摄入（traces）+ CloudWatch Metrics（经 EMF）。

**注意**

1. 博客里的 **GenAI Observability** 面板期望 GenAI 语义约定。Temporal span 会出现在
   X-Ray / Transaction Search 的工作流轨迹里，**不会**变成 AgentCore GenAI session。
2. 若账号 X-Ray destination 是 **CloudWatchLogs**（Transaction Search），痕迹在日志组
   **`aws/spans`**。旧的 `aws xray get-trace-summaries` 可能为空——**不等于**导出失败。
3. ADOT Collector 镜像不宜依赖挂载 `~/.aws` + SSO profile。`./scripts/startup.sh` 会通过
   `aws configure export-credentials` 注入短期 Session。
4. `service.name` 为 **`adot-aws`**（与本分支名一致），来自 `spring.application.name`。

```
Java 应用 (Temporal + Micrometer/OTel)  service.name=adot-aws
        │  OTLP :4317 traces / :4318 metrics
        ▼
ADOT Collector
        ├─ awsxray  → X-Ray（Transaction Search 开启时 → aws/spans）
        ├─ awsemf   → CloudWatch Logs → Metrics（命名空间 ICMTracing/adot-aws）
        └─ otlp     → 本地 Jaeger（对照）
```

## 前置条件

- Java 25、Maven 3.9.x、Temporal CLI、Docker Compose（Colima/Docker）
- 本机 AWS profile 能调用 `xray:PutTraceSegments` 并写 CloudWatch Logs
  （可用 `aws configure export-credentials` 导出 Session）

## 配置

```shell
cp .env.example .env
# 按你的 Lab 凭证设置 AWS_REGION / AWS_PROFILE
aws sts get-caller-identity
```

## 跑 Lab

```shell
# 1) Temporal + ADOT（把 Session 注入 Collector）+ Jaeger
./scripts/startup.sh

# 2) 应用（JDK 25 需要打开 Lombok 注解处理）
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn spring-boot:run -Dmaven.compiler.proc=full

# 3) 触发 workflow（Activity 打失败的 :8081 → 重试会出现在 trace 里）
./scripts/01normal.sh
```

Session 过期后重跑 `./scripts/startup.sh`（SSO 刷新后同理）。

```shell
docker compose logs -f otel-collector
```

## 去哪看

| UI | 地址 |
|----|------|
| Temporal UI | http://localhost:8088 |
| Jaeger（本地对照） | http://localhost:16686，服务 **`adot-aws`** |
| AWS 上的 Trace | CloudWatch Transaction Search / `aws/spans`，过滤服务 **`adot-aws`** |
| CloudWatch Metrics | 命名空间 `ICMTracing/Temporal`（`application=main`），日志组 `/icm-tracing/otel/metrics` |
| CloudWatch Dashboard | `./scripts/put-dashboard.sh` → [adot-aws-lab](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#dashboards:name=adot-aws-lab) |
| ADOT health | http://localhost:13133 |

CloudWatch Trace 示例（拓扑 + Temporal spans）见 [docs/lab-adot-aws_zh.md](docs/lab-adot-aws_zh.md)
或截图 [`screenshots/05xray-traces.png`](screenshots/05xray-traces.png)。

## 关闭

```shell
./scripts/shutdown.sh
```

## 与其他分支的关系

其他分支演示 NR/Datadog/Jaeger 上的形态（retry、HITL、heartbeat、span link）。
本分支在相同 Temporal 样例上，把**后端路径**换成 ADOT→AWS。
