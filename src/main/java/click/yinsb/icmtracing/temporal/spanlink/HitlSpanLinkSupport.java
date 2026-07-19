package click.yinsb.icmtracing.temporal.spanlink;

import click.yinsb.icmtracing.temporal.activities.Activity001;
import io.opentelemetry.api.trace.Span;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Workflow-side helper for HITL / external-wait Span Links.
 * <p>
 * Call {@link #await(Supplier)} (or {@link #rememberCurrentSpan()} before a custom await)
 * so the waiting workflow span can later be linked from an external signal request.
 * Safe to reuse from any workflow that waits on a signal.
 */
public final class HitlSpanLinkSupport {

    private static final Logger log = Workflow.getLogger(HitlSpanLinkSupport.class.getName());

    private HitlSpanLinkSupport() {
    }

    /**
     * Remember the current workflow span, then {@link Workflow#await(Supplier)}.
     * Preferred one-liner for signal/HITL waits.
     */
    public static void await(Supplier<Boolean> condition) {
        rememberCurrentSpan();
        Workflow.await(condition);
    }

    /**
     * Remember the current workflow span, then await with timeout.
     *
     * @return {@code true} if the condition became true before timeout
     */
    public static boolean await(Duration timeout, Supplier<Boolean> condition) {
        rememberCurrentSpan();
        return Workflow.await(timeout, condition);
    }

    /**
     * Capture the current Temporal {@code RunWorkflow} span and store it for later linking.
     * Use this when you need a custom await pattern; otherwise prefer {@link #await(Supplier)}.
     */
    public static void rememberCurrentSpan() {
        String[] spanContext = Workflow.sideEffect(String[].class, () -> {
            var context = Span.current().getSpanContext();
            if (!context.isValid()) {
                return new String[]{"", ""};
            }
            return new String[]{context.getTraceId(), context.getSpanId()};
        });
        String traceId = spanContext[0];
        String spanId = spanContext[1];
        if (traceId.isBlank() || spanId.isBlank()) {
            log.warn("skip remembering waiting span context: missing OTel span on workflow thread");
            return;
        }
        String workflowId = Workflow.getInfo().getWorkflowId();
        log.info("remembering waiting span context workflowId={} traceId={} spanId={}",
                workflowId, traceId, spanId);
        activity001().rememberWaitingSpan(workflowId, traceId, spanId);
    }

    private static Activity001 activity001() {
        return Workflow.newActivityStub(
                Activity001.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(30))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setMaximumAttempts(3)
                                .build())
                        .build());
    }
}
