# 为什么在 Temporal exponential backoff retry 下用 Span Link 不容易

表面上 OTel 只是 `setNoParent()` + `addLink(spanContext)`，但套到 Temporal 的 **exponential backoff retry** 上并不直接。  
对照同仓的 [`retry`](https://github.com/uniquejava/icm-tracing/tree/retry)（不手动加 Span Link）与本分支实测后，结论更清楚：**难，而且对「补齐 Temporal retry 可观测性」往往并不值得。**

## 难点

1. **Retry 循环不在你手里**  
   Temporal 服务端调度下一次 attempt：两次执行可能相隔数分钟，甚至换到另一台 worker。  
   公开文档里常见的「本地 `for` + `sleep`，在同一次调用栈里给每次 attempt 建 span 并 link」在这里用不了。

2. **SpanContext 必须跨 attempt 存活**  
   Attempt 1 的 `(traceId, spanId)` 要留给 Attempt N 做 link。  
   单 worker demo 可用内存 registry；多 worker / 多 pod 需要把 prior context 落到共享存储，否则后一次 attempt 拿不到要 link 的目标。

3. **NR 要的是「跨 trace 导航」，不是同 trace 里多挂一个 link**  
   New Relic 的 Span Links UI 按 **不同 Trace ID** 做 previous/next。  
   Temporal OpenTracing interceptor 默认会把后续 attempt 挂在同一条延续链上（往往还是同一个 `traceId`，`RunActivity` → `FOLLOWS_FROM` → `StartActivity`）。  
   只在这条延续链上 `addLink`，对 NR「跨 fragment / Trace group 跳转」帮助有限。  
   若强行迁就 NR：attempt ≥ 2 一律 `setNoParent()` 再 link 全部历史，会得到 **O(n²) 扇入 + 十几条碎 trace**，NR 更乱。

4. **和 HITL / 消息队列场景也不一样**  
   HITL approve、MQ consumer 天然是「新请求 / 新消费 = 新 root」，再 link 回上游即可。  
   Activity retry 是同一逻辑操作的第 N 次执行，中间只有 Temporal timer，没有现成的「新 HTTP 入口」——必须自己在 activity 里造 segment。

5. **方案本身的能力边界**（NR 也写明了）  
   Span Links **只负责关联导航**，不会把碎片 **合并成一条** end-to-end trace；  
   依赖「整条 workflow 一条 trace」的 latency 等指标，这条路仍然不支持。

## 本分支做法（降噪后的 demo）

1. 每次 attempt 记下 OTel `SpanContext` + 结束时间  
2. 距上次 &lt; ~90s → 仍作当前 `RunActivity` 的子 span（同 trace，不加 link）  
3. 距上次 ≥ ~90s → `setNoParent()` 新 root，并 **只** `addLink` 到上一次 attempt（`retry_of`）  
4. Retry 配置与 `retry` 分支一致：最多 12 次、初始间隔 2s、指数退避  

实测（优化后）：attempt 1–7 留在主 trace；8–12 各开新 root、各 1 条 link → 约 **6** 条 trace，不再 O(n²)。  
这只是「少拆一点」，**没有**解决 NR Trace groups 把一次业务拆成多组的问题。

## 实测：Jaeger 清楚，NR Groups 两边都怪

| | `retry`（不手动 link） | `retry_spanlink`（本分支） |
|--|--|--|
| Jaeger | **1** 条 trace，12 次 `RunActivity` 同树 | 主 trace + 若干 `activity.retry.attempt` 新 root |
| NR Trace groups（示例） | 仍拆成约 3 组：`POST /request`、`RunWorkflow:MainWorkflow`、短 `RunActivity` | 上述分裂仍在，外加多组 `activity.retry.attempt` |
| 典型时长错位 | 同一次跑：Jaeger 墙钟 ~1054s；NR 里 `POST` 常显示 ~126s，`RunWorkflow` ~1054s | 同左；短 attempt 组 ~数 ms |

要点：

- **结构以 Jaeger 为准。** 同一份 OTLP，`retry` 在 Jaeger 已是完整一棵树。  
- **NR Trace groups ≠ 一条分布式 trace。** 更像按「入口 / root span 名」聚类；再叠加超长 span、Temporal `FOLLOWS_FROM` / Start·Run、以及文档中的 ~90s session 行为，一次简单 workflow 也会显示成多个 group。  
- 因此：**`retry` 在 NR 里看起来不正常，并不能证明「缺少 Span Link」**；本分支再拆 root，Groups 往往更吵。

### `CHILD_OF` / `FOLLOWS_FROM` 不是 `SpanKind`

- `SpanKind`：`INTERNAL` / `SERVER` / `CLIENT` / …（角色）  
- `CHILD_OF` / `FOLLOWS_FROM`：OpenTracing **引用关系**（OTel 里大致对应 parent vs Span Link）  
Temporal OT 大量使用 `FOLLOWS_FROM`（Start→Run、StartActivity→各次 RunActivity），这是编排模型使然，不是 Kind 配错了。

## 结论：对 Temporal retry，手动 Span Link 多半是画蛇添足

1. **Temporal 已提供同 `traceId` 的 attempt 关联**（`FOLLOWS_FROM`）。要看清 12 次失败，Jaeger / 能按 `traceId` 打开的 UI 通常够用。  
2. **乱主要在 NR 展示**（Groups、长会话切段），不是「业务没打 link」。  
3. **为迁就「跨 trace 导航」去 `setNoParent()`**：等于先拆碎再缝——和「让 NR 好看」目标相反。降噪优化只能减轻，不能根治 Groups。  
4. **Span Link 仍有正当场景**：HITL、MQ、跨系统事后关联——那些 **本来就是新 root**。不要和 Temporal activity retry 混为一谈。  
5. **更值得做的是向 NR 提诉求**（见下），而不是在业务里普及 retry Span Link。

### 给 New Relic 提诉求时建议怎么写

- **现象**：Temporal Java + OTLP；一次 `/request` → Main/Child workflow → Activity 指数退避 retry；Jaeger **1** `traceId`；NR Trace groups 却出现多个并列组（如 `POST /request`、`RunWorkflow:MainWorkflow`、零散 `RunActivity`）。  
- **期望**：同一 W3C `traceId`（及明确的 parent / `FOLLOWS_FROM` 链）在列表与 Groups 中应表现为 **一个业务 trace，或可展开的同 trace 片段组**，而不是多个看似无关的 group。  
- **请对方澄清**：Groups 的 grouping key；对 OT `FOLLOWS_FROM`、超长 span / ~90s session 是否会改写或复制成多条 trace。  
- **诉求焦点**是「同一次 workflow 的关联展示」，不是「禁止任何超长切段」。即便回复「Groups ≠ 单条 DT」，仍应要求 **按 `trace.id` 合并或显式标注片段关系**。

## 已知限制（本分支实现）

- Span Links 只做导航，不合并 E2E latency。  
- Registry 为内存（单 worker demo）；多 worker 需持久化 prior `(traceId, spanId, endedAt)`。  
- Temporal 自带的 `RunActivity` 仍会出现在原延续 trace；手动 segment 是 `activity.retry.attempt`（双轨并存，增加噪音）。  
- 本分支适合作为 **「验证 NR 跨 fragment + 降噪策略」的 demo**，不建议当成 Temporal retry 的生产默认打法。

## References

- [New Relic – OpenTelemetry traces / Span links](https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-best-practices-traces/)（long-running workflows / ~90s）
- [New Relic – Trace details / Understanding span links](https://docs.newrelic.com/docs/distributed-tracing/ui-data/trace-details/)
- [OTel Java `SpanBuilder.setNoParent` + `addLink`](https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/trace/SpanBuilder.html)
- Retry link 模式（每次 attempt 一个 span，`retry_of`）：[OneUptime: Span Links](https://oneuptime.com/blog/post/2026-01-07-opentelemetry-span-links/view)
- 对照分支：[`retry`](https://github.com/uniquejava/icm-tracing/tree/retry)（无手动 Span Link）
