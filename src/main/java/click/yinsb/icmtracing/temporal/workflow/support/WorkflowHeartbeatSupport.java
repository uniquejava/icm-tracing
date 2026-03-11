package click.yinsb.icmtracing.temporal.workflow.support;

import click.yinsb.icmtracing.temporal.activities.HeartbeatActivity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.Functions;

import java.time.Duration;

/**
 * Reusable workflow-side support for emitting heartbeat spans while long-running work is in progress.
 */
public final class WorkflowHeartbeatSupport {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(DEFAULT_HEARTBEAT_INTERVAL_SECONDS);

    private WorkflowHeartbeatSupport() {
    }

    public static void runWithHeartbeat(Functions.Proc mainWork) {
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
