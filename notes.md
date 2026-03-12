# Temporal Workflow Heartbeat Notes

## What

这次改动引入了一个 **heartbeat span 机制**，用于给长时间运行的 Temporal workflow 持续补充一个很轻量的 tracing 信号。

它做的事情很简单：

- 当一个 main workflow 开始运行后，除了执行业务逻辑本身，还会每隔 `60s` 打一个名为 `heartbeat` 的 span
- 这个 `heartbeat` span 会挂在当前 workflow span 下面
- 一旦主业务执行完成，heartbeat 也随之停止

最终效果是：

- 在 Jaeger / New Relic 里，可以持续看到这个 workflow 还活着
- 即使 workflow 中间因为 `retry`、`sleep`、等待 `signal`、等待审批、等待外部系统等原因长时间没有业务 span，也不会那么容易被看起来“断开”



## Why

### 背景问题

我们遇到的问题不是 trace ID 丢了，而是 **New Relic 的 trace grouping 行为**。

现象是：

- 一个 workflow 明明还是同一个 trace ID
- 但如果中间超过大约 `90s` 没有新的 span
- New Relic 可能会把后面的 span 放到另一个 trace group 里显示

这会导致：

- 同一个长 workflow 在 UI 上看起来像被拆成了两段甚至多段
- 排查问题时很难一眼看出它们其实属于同一条长链路

### 为什么 Temporal workflow 特别容易踩这个问题

Temporal workflow 天生就容易出现长时间“安静”的窗口，比如：

- Activity 执行失败后进入 retry backoff
- workflow 主动 `Workflow.sleep(...)`
- child workflow 里 `Workflow.await(...)` 等待 signal
- 等待人工审批
- 等待外部系统回调

这些时间段里：

- workflow 还活着
- trace 也没结束
- 但可能没有新的 span 被产出

如果这个安静窗口超过了 New Relic 的 grouping threshold，就会出现 trace 被拆组的问题。

### 为什么固定用 60 秒

heartbeat 的目的就是在 “>90s 的空档” 出现之前，主动补一个 span。

所以这里固定使用 `60s`：

- 足够低于 `90s`
- 实现简单
- 避免再开放成可配置后被改成 `120s` 之类导致方案失效



## How

### 整体思路

实现思路分成 3 层：

1. `WorkflowHeartbeatSupport`
   负责在 workflow 侧调度 heartbeat
2. `HeartbeatActivity`
   负责真正创建 heartbeat span
3. `NrOtelConfig`
   负责过滤掉 Temporal 自己的 `RecordHeartbeat` activity spans，避免 UI 里重复

核心原则是：

- **workflow 代码里不直接创建 span**
- 只在 workflow 里调度一个 activity
- span 的创建放到 activity 里做

这是因为 Temporal workflow 代码运行在 deterministic sandbox 里，直接在 workflow 逻辑中做 tracing side effect 不合适。



## Workflow 侧怎么工作

### 1. 包一层 heartbeat support

main workflow 不需要知道 heartbeat 的细节，只要把业务逻辑包进去：

```java
@WorkflowImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class MainWorkflowImpl implements MainWorkflow {

    @Override
    public void runAsync(EventMessage eventMessage) {
        WorkflowHeartbeatSupport.runWithHeartbeat(() -> runMainFlow(eventMessage));
    }

    private void runMainFlow(EventMessage eventMessage) {
        // create Workflow Stubs
        ChildWorkflow001 stub1 = Workflow.newChildWorkflowStub(ChildWorkflow001.class);

        // start workflows
        stub1.run(eventMessage);
    }
}
```

这样做的好处是：

- heartbeat 是外围能力
- 业务逻辑本身几乎不变
- 以后别的 main workflow 也可以复用



### 2. heartbeat support 的核心逻辑

`WorkflowHeartbeatSupport` 做了几件事：

- 先捕获当前 workflow span 的 `traceId` 和 `spanId`
- 再启动真正的主业务逻辑
- 然后每 `60s` 调一次 `HeartbeatActivity`
- 如果主业务已经结束，就停止 heartbeat

代码如下：

```java
public final class WorkflowHeartbeatSupport {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60;
    private static final Duration HEARTBEAT_INTERVAL =
            Duration.ofSeconds(DEFAULT_HEARTBEAT_INTERVAL_SECONDS);

    private WorkflowHeartbeatSupport() {
    }

    public static void runWithHeartbeat(Functions.Proc mainWork) {
        // Keep the main workflow trace active during long waits such as retries, sleeps, or signal/approval waits.
        HeartbeatParentSpan parentSpan = captureParentSpan();
        HeartbeatActivity heartbeatActivity = newHeartbeatActivity();

        Promise<Void> workPromise = Async.procedure(mainWork);
        while (true) {
            Workflow.sleep(HEARTBEAT_INTERVAL);
            if (workPromise.isCompleted()) {
                break;
            }
            heartbeatActivity.recordHeartbeat(parentSpan.traceId(), parentSpan.spanId());
        }

        workPromise.get();
    }

    private static HeartbeatParentSpan captureParentSpan() {
        String[] spanContext = Workflow.sideEffect(String[].class, () -> {
            try {
                SpanContext currentSpanContext = Span.current().getSpanContext();
                if (currentSpanContext != null && currentSpanContext.isValid()) {
                    return new String[]{currentSpanContext.getTraceId(), currentSpanContext.getSpanId()};
                }
            } catch (Exception ignored) {
            }
            return new String[]{"", ""};
        });
        return new HeartbeatParentSpan(spanContext[0], spanContext[1]);
    }

    private static HeartbeatActivity newHeartbeatActivity() {
        return Workflow.newActivityStub(
                HeartbeatActivity.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build());
    }

    private record HeartbeatParentSpan(String traceId, String spanId) {
    }
}
```

### 关键点

这里最关键的是 `captureParentSpan()`：

- 它只捕获一次当前 workflow span 的上下文
- 后面的 heartbeat span 都会挂到这个 parent span 下面

这样在 trace 树里就会看到：

```text
RunWorkflow:MainWorkflow
├── heartbeat
├── heartbeat
├── heartbeat
└── RunWorkflow:ChildWorkflow001
```



## Activity 侧怎么工作

### 为什么要用 activity 创建 span

workflow 里不能直接安全地做这种 tracing side effect，所以真正的 span 创建放在 activity 里。

`HeartbeatActivity` 接口很简单：

```java
@ActivityInterface
public interface HeartbeatActivity {

    @ActivityMethod
    void recordHeartbeat(String traceId, String spanId);
}
```

实现如下：

```java
@Component
@Slf4j
@RequiredArgsConstructor
@ActivityImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class HeartbeatActivityImpl implements HeartbeatActivity {

    private final OpenTelemetry openTelemetry;

    @Override
    public void recordHeartbeat(String traceId, String spanId) {
        if (traceId == null || spanId == null || traceId.isEmpty() || spanId.isEmpty()) {
            log.debug("Skipping heartbeat: missing trace or span context");
            return;
        }
        try {
            SpanContext parentContext = SpanContext.createFromRemoteParent(
                    traceId,
                    spanId,
                    TraceFlags.getSampled(),
                    TraceState.getDefault());

            Tracer tracer = openTelemetry.getTracer("icm-tracing", "1.0");
            Span span = tracer.spanBuilder("heartbeat")
                    .setParent(Context.root().with(Span.wrap(parentContext)))
                    .startSpan();
            try {
                // no-op; span exists only to record heartbeat as child of workflow span
            } finally {
                span.end();
            }
        } catch (Exception e) {
            log.warn("Failed to record heartbeat span: {}", e.getMessage());
        }
    }
}
```

### 关键点

这里不是新开一条 trace，而是：

- 用 workflow 传进来的 `traceId` / `spanId`
- 重建一个 parent `SpanContext`
- 然后创建一个新的 `heartbeat` child span

所以 heartbeat 会和原 workflow trace 继续挂在一起。



## 为什么还要过滤 Temporal 自己的 spans

当 workflow 调用 `HeartbeatActivity.recordHeartbeat(...)` 时，Temporal 自己也会生成 activity spans，比如：

- `StartActivity:RecordHeartbeat`
- `RunActivity:RecordHeartbeat`

如果不处理，就会在 UI 上看到两类 heartbeat 相关 span：

1. 我们想要的自定义 `heartbeat`
2. Temporal 自动生成的 activity spans

这会造成噪音和重复。

所以我们在 exporter 侧做了一层过滤。



## New Relic 直连时怎么过滤

在当前项目里没有走 OTel Collector 的 `processors.filter`，而是直接在应用侧 exporter 做过滤。

配置类如下：

```java
@Configuration
public class NrOtelConfig {

    // The custom heartbeat span exists only to prevent New Relic from splitting one long-running Temporal
    // workflow into multiple trace groups after a quiet period. Filter the Temporal RecordHeartbeat activity
    // spans so reviewers see the intended heartbeat span once, not duplicated by Temporal activity spans.
    private static final Set<String> FILTERED_SPAN_NAMES = Set.of(
            "StartActivity:RecordHeartbeat",
            "RunActivity:RecordHeartbeat"
    );

    @Bean
    OpenTelemetry openTelemetry(
            @Value("${spring.application.name}") String applicationName,
            @Value("${NR_ENDPOINT:https://otlp.nr-data.net:4317}") String nrEndpoint,
            @Value("${MY_NEW_RELIC_API_KEY}") String newRelicApiKey) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), applicationName)));

        SpanExporter exporter = new FilteringSpanExporter(
                OtlpGrpcSpanExporter.builder()
                        .setEndpoint(nrEndpoint)
                        .addHeader("api-key", newRelicApiKey)
                        .build());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(),
                                W3CBaggagePropagator.getInstance()
                        )))
                .build();
    }

    private static final class FilteringSpanExporter implements SpanExporter {

        private final SpanExporter delegate;

        private FilteringSpanExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            List<SpanData> filteredSpans = spans.stream()
                    .filter(span -> !FILTERED_SPAN_NAMES.contains(span.getName()))
                    .toList();
            return delegate.export(filteredSpans);
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }
    }
}
```

### 重点

这里不是把 trace 丢掉，而只是把那两个 Temporal activity span 名称过滤掉。

保留下来的仍然有：

- workflow spans
- 业务 activity spans
- 自定义 `heartbeat` span



## 我们是怎么一步步引入的

### 第一步：先验证方案能不能工作

一开始先在 demo 项目里做最直接版本：

- main workflow 每 60 秒调用一次 heartbeat activity
- heartbeat activity 里创建一个 child span
- 观察 Jaeger / New Relic 里的表现

目标是先证明：

- trace 不会因为长时间空档而明显断开
- heartbeat span 能正确挂在 workflow span 下

### 第二步：把 heartbeat 逻辑抽离

验证通过以后，把与业务无关的 heartbeat 逻辑抽到 `WorkflowHeartbeatSupport`：

- workflow 只负责自己的业务主流程
- heartbeat 变成通用能力

这样后面新的 main workflow 只要这样接一下就行：

```java
WorkflowHeartbeatSupport.runWithHeartbeat(() -> runMainFlow(eventMessage));
```

### 第三步：固定 heartbeat interval

后面又把 interval 固定成了 `60s`，没有继续保留可配置能力。

原因很现实：

- 这个机制本来就是为了规避 `~90s` 的 gap
- 如果有人把 interval 改成 `120s`
- 整个方案就可能直接失效

所以这里故意保持简单且保守。

### 第四步：推广到真正的 main workflows

在正式项目里，不是给所有 workflow 全量加 heartbeat，而是优先加到入口级 main workflows：

- `CreditLimitChangeWorkflow`
- `DocumentedCreditDecisionWorkflow`
- `CreditExposureThresholdWorkflow`
- `PaymentBehaviourChangedWorkflow`

原因是这些 workflow 最值得保护：

- 它们生命周期更长
- 更可能经历 retry backoff / signal wait / approval wait
- 更容易在 New Relic UI 上出现 trace split

同时，这种做法也能把改动面控制住，不会把 heartbeat 散落到所有叶子 workflow 里。



## 这个方案的边界

### 它解决什么

- 长 workflow 的 trace 在 New Relic 中被拆组的问题
- UI 上看起来“前半段一组，后半段另一组”的问题
- 没有业务 span 的长静默期导致的可观测性断层

### 它不解决什么

- 不改变 Temporal retry 机制本身
- 不改变 trace ID 生成逻辑
- 不保证 New Relic 内部实现未来不会变化
- 不替代真正的 product-side fix

所以本质上这是一个 **pragmatic workaround**。



## 一句话总结

这次引入 heartbeat，不是为了增加业务功能，而是为了让 **长时间运行但中间会安静很久的 Temporal workflow**，在 New Relic 里仍然尽量表现为 **一条连续、可读的 trace**。

