# Next steps — `adot-direct` (collector-less ADOT)

> Full agent handoff: [AGENTS.md](../AGENTS.md).  
> 中文摘要见下方。

## Status

| Item | State |
|------|--------|
| Branch `adot-aws` (Collector path) | Done, pushed |
| Branch `adot-direct` | Created from `adot-aws`; **implementation not started** |
| Goal | ADOT Java Agent → `https://xray.{region}.amazonaws.com/v1/traces` **without** local ADOT Collector |

## New session prompt (paste)

```
Continue icm-tracing branch adot-direct. Read AGENTS.md and docs/next-steps-adot-direct.md.
Implement and validate collector-less ADOT Java Agent → AWS X-Ray OTLP endpoint.
Do not commit AWS account IDs. Prefer completing the checklist in AGENTS.md end-to-end.
```

## Checklist (copy)

- [ ] `.gitignore` → `.tools/`
- [ ] Download `aws-opentelemetry-agent.jar` into `.tools/`
- [ ] Wire Temporal `OpenTelemetry` bean to agent (`GlobalOpenTelemetry`) / stop exporting to `:4317`
- [ ] Disable Micrometer OTLP to Collector
- [ ] `service.name=adot-direct`
- [ ] `scripts/startup-direct.sh` + `scripts/run-app-direct.sh`
- [ ] Run workflow; confirm `aws/spans` for `adot-direct`
- [ ] Docs `lab-adot-direct.md` + `_zh.md` + README
- [ ] Commit + push (no secrets)

---

## 中文摘要

上一轮在 `adot-aws` 已验证：**应用 → ADOT Collector → CloudWatch/X-Ray**。  
官方还支持 **无 Collector**：ADOT Java Agent 直连  
`https://xray.<region>.amazonaws.com/v1/traces`（SigV4）。  
分支 `adot-direct` 已建好，**代码尚未改**。新 session 请读根目录 `AGENTS.md`，按清单实现并验证后提交。
