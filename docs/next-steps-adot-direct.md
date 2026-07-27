# Next steps — `adot-direct` (collector-less ADOT)

> Full agent handoff: [AGENTS.md](../AGENTS.md).  
> Lab report: [lab-adot-direct.md](./lab-adot-direct.md) / [中文](./lab-adot-direct_zh.md).

## Status

| Item | State |
|------|--------|
| Branch `adot-aws` (Collector path) | Done, pushed |
| Branch `adot-direct` | **Implemented** — ADOT Java Agent → X-Ray OTLP (no Collector) |
| Goal | ADOT Java Agent → `https://xray.{region}.amazonaws.com/v1/traces` **without** local ADOT Collector |

## Checklist

- [x] `.gitignore` → `.tools/`
- [x] Download `aws-opentelemetry-agent.jar` into `.tools/`
- [x] Wire Temporal `OpenTelemetry` bean to agent (`GlobalOpenTelemetry`) / stop exporting to `:4317`
- [x] Disable Micrometer OTLP to Collector
- [x] `service.name=adot-direct`
- [x] `scripts/startup-direct.sh` + `scripts/run-app-direct.sh`
- [x] Run workflow; confirm `aws/spans` for `adot-direct`
- [x] Docs `lab-adot-direct.md` + `_zh.md` + README
- [x] Commit + push (no secrets)

## Run

```shell
# Agent JAR (use local proxy if GitHub is slow):
#   export https_proxy=http://127.0.0.1:7897
mkdir -p .tools
curl -fL -o .tools/aws-opentelemetry-agent.jar \
  https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar

./scripts/startup-direct.sh
./scripts/run-app-direct.sh
./scripts/01normal.sh
```

---

## 中文摘要

`adot-direct` 已实现官方 **无 Collector** 路径：ADOT Java Agent 直连
`https://xray.<region>.amazonaws.com/v1/traces`（SigV4）。详见 `lab-adot-direct_zh.md`。
下载 Agent JAR 若 GitHub 慢，可用本地代理（如 `https_proxy=http://127.0.0.1:7897`）。
