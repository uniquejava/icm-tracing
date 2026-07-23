# 为什么在 Temporal exponential backoff retry 下用 Span Link 不容易

表面上 OTel 只是 `setNoParent()` + `addLink(spanContext)`，但套到 Temporal 的 **exponential backoff retry** 上并不直接。

## 难点

1. **Retry 循环不在你手里**  
   Temporal 服务端调度下一次 attempt：两次执行可能相隔数分钟，甚至换到另一台 worker。  
   公开文档里常见的「本地 `for` + `sleep`，在同一次调用栈里给每次 attempt 建 span 并 link」在这里用不了。

2. **SpanContext 必须跨 attempt 存活**  
   Attempt 1 的 `(traceId, spanId)` 要留给 Attempt N 做 link。  
   单 worker demo 可用内存 registry；多 worker / 多 pod 需要把 prior context 落到共享存储，否则后一次 attempt 拿不到要 link 的目标。

3. **NR 要的是「跨 trace 导航」，不是同 trace 里多挂一个 link**  
   New Relic 的 Span Links UI 按 **不同 Trace ID** 做 previous/next。  
   Temporal OpenTracing/OTel interceptor 默认会把后续 attempt 挂在同一条延续的 parent 上（往往还是同一个 `traceId`）。  
   只在这条延续链上 `addLink`，对 NR「跨 fragment / trace group 跳转」帮助有限。  
   因此本 demo 在 attempt ≥ 2 时显式 `setNoParent()` 开新 root，再 `addLink` 回先前 attempts（`link.relationship=retry_of`）。

4. **和 HITL / 消息队列场景也不一样**  
   HITL approve、MQ consumer 天然是「新请求 / 新消费 = 新 root」，再 link 回上游即可。  
   Activity retry 是同一逻辑操作的第 N 次执行，中间只有 Temporal timer，没有现成的「新 HTTP 入口」给你挂 link——必须自己在 activity 代码里造 segment。

5. **方案本身的能力边界**（NR 也写明了）  
   Span Links **只负责关联导航**，不会把碎片 **合并成一条** end-to-end trace；  
   依赖「整条 workflow 一条 trace」的 latency 等指标，这条路仍然不支持。

## 本分支做法

1. Attempt 1：记下当前 OTel `SpanContext`
2. Attempt N：`setNoParent()` 新建 root，并对先前 attempts `addLink`（`retry_of`）
3. Retry 配置与 `retry` 分支一致：最多 12 次、初始间隔 2s、指数退避（后期间隔会超过 ~90s，便于观察 NR session 断开）

## 已知限制

- Span Links 只做导航，不合并 E2E latency。
- Registry 为内存（单 worker demo）；多 worker 需持久化 prior `(traceId, spanId)`。
- Temporal 自带的 `RunActivity` 仍可能出现在原延续 trace 里；显式可点的 linked segment 是 `activity.retry.attempt`。

## References

- [New Relic – OpenTelemetry traces / Span links](https://docs.newrelic.com/docs/opentelemetry/best-practices/opentelemetry-best-practices-traces/)（long-running workflows / ~90s）
- [New Relic – Trace details / Understanding span links](https://docs.newrelic.com/docs/distributed-tracing/ui-data/trace-details/)
- [OTel Java `SpanBuilder.setNoParent` + `addLink`](https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/trace/SpanBuilder.html)
- Retry link 模式（每次 attempt 一个 span，`retry_of`）：[OneUptime: Span Links](https://oneuptime.com/blog/post/2026-01-07-opentelemetry-span-links/view)
