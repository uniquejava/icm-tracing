# Heartbeat Spans

This document describes the heartbeat span feature: why it exists, how it works, and how to configure it.

## Overview

The app emits **heartbeat** spans only as **children** of `RunWorkflow:MainWorkflow`. The child workflow (`RunWorkflow:ChildWorkflow001`) does not emit heartbeats.

A heartbeat span is created on a fixed interval (default 60 seconds) while the workflow is running. That gives observability tools (e.g. New Relic, Jaeger) a signal that the workflow is still active, which is especially useful for long-running workflows that would otherwise show little or no activity in a trace.

## How It Works

### Flow

1. When a workflow runs, it captures its own OpenTelemetry span context (trace ID and span ID) once via `Workflow.sideEffect()`. That context corresponds to the `RunWorkflow:…` span created by Temporal.
2. The workflow runs its main work (child workflow or activity) and a **timer loop** in parallel.
3. Every N seconds, the timer fires and the workflow calls a **heartbeat activity**, passing the captured trace ID and span ID.
4. The activity creates a new span named `"heartbeat"` with that context as its **parent**, then ends the span immediately. The span is exported via OTLP like any other span.
5. The loop stops when the main work completes; the workflow then finishes.

Because the parent context is the MainWorkflow span, each heartbeat appears as a direct child of `RunWorkflow:MainWorkflow` in the trace. The OTel collector filters out Temporal’s `StartActivity:RecordHeartbeat` / `RunActivity:RecordHeartbeat` spans so only these custom `heartbeat` spans are exported.

### Why an Activity?

Temporal workflow code runs in a deterministic sandbox. Spans cannot be created inside workflow logic (that would be non-deterministic and could break replay). So heartbeat spans are created inside an **activity**, which runs in the worker process with full access to OpenTelemetry. The workflow only passes the parent span context and schedules the activity.

### Trace Hierarchy

```
RunWorkflow:MainWorkflow
├── heartbeat
├── heartbeat
├── …
└── RunWorkflow:ChildWorkflow001
    └── RunActivity:...
```

## Configuration

| What | Where | Default |
|------|--------|---------|
| Heartbeat interval | `spring.temporal.heartbeat.interval-seconds` in `application.yaml` | 60 seconds |
| Per-run override | `EventMessage.heartbeatIntervalSeconds` (set by client or caller) | Uses config value if null |

The **WorkflowClientService** reads `spring.temporal.heartbeat.interval-seconds` and, when starting a workflow, sets `eventMessage.heartbeatIntervalSeconds` if it is not already set. Workflows use that value (or 60 if still null) for their timer loop.

Example in `application.yaml`:

```yaml
spring:
  temporal:
    heartbeat:
      interval-seconds: 60
```

Change `60` to any positive number of seconds to make heartbeats more or less frequent.

## OTel Collector: Dropping Temporal Activity Spans

Temporal’s OpenTelemetry integration creates a span for every activity execution (e.g. `StartActivity:RecordHeartbeat`, `RunActivity:RecordHeartbeat`). If we did not filter these, each heartbeat would appear twice in the trace: once as our custom `heartbeat` span (child of the workflow) and once as Temporal’s activity span. To avoid that duplication, the collector drops Temporal’s RecordHeartbeat activity spans so only the custom `heartbeat` spans are exported.

In `otel-collector.yml`, the **filter** processor is configured to drop spans whose name matches either of these:

| Span name (dropped) | Source |
|---------------------|--------|
| `StartActivity:RecordHeartbeat` | Temporal (activity start) |
| `RunActivity:RecordHeartbeat` | Temporal (activity run) |

Configuration (excerpt):

```yaml
processors:
  filter/drop_temporal_heartbeat_activity:
    error_mode: ignore
    traces:
      span:
        - 'name == "StartActivity:RecordHeartbeat"'
        - 'name == "RunActivity:RecordHeartbeat"'
```

This processor is included in both trace pipelines (`traces/jaeger` and `traces/nr`) before the batch processor. Spans that match either condition are dropped; all other spans (including our custom `heartbeat` spans) pass through. The filter uses OTTL (OpenTelemetry Transformation Language): when a span condition evaluates to true, that span is dropped.

## Components

| Component | Description |
|-----------|-------------|
| **HeartbeatActivity** | Interface with `recordHeartbeat(String traceId, String spanId)`. |
| **HeartbeatActivityImpl** | Implements the activity: builds an OTel `SpanContext` from the IDs, starts a span named `"heartbeat"` with that as parent, then ends it. Registered on the same task queue as other activities. |
| **EventMessage.heartbeatIntervalSeconds** | Optional integer; carries the interval (seconds) from the client into the workflows. |
| **MainWorkflowImpl** | Starts the child workflow and a parallel loop that calls the heartbeat activity every N seconds until the child completes. |
| **ChildWorkflow001Impl** | Runs the main activity only; it does not emit heartbeat spans. |

## Edge Cases

- **Missing span context**: If the workflow thread does not have OpenTelemetry context (e.g. no Temporal OTel interceptor), the side effect returns empty trace/span IDs. The activity then skips creating a span and logs at debug level.
- **Short workflows**: If the workflow finishes before the first interval, no heartbeat span is emitted for that run.
- **Failures**: If the heartbeat activity fails to create a span (e.g. invalid IDs), it logs a warning and does not fail the workflow.
