package click.yinsb.icmtracing.temporal.spanlink;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Client-side helper: run a signal/approve action under a span that Span-Links
 * back to the workflow span previously stored by {@link HitlSpanLinkSupport}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlSpanLinkTracer {

    private final OpenTelemetry openTelemetry;
    private final WaitingSpanContextRegistry registry;

    /**
     * Create a linked span named {@code spanName}, run {@code action}, then clear the stored context.
     */
    public void runLinked(String workflowId, String spanName, Runnable action) {
        WaitingSpanContext waiting = registry.get(workflowId).orElse(null);
        Tracer tracer = openTelemetry.getTracer("spanlink_hitl");
        var spanBuilder = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("workflow.id", workflowId);

        if (waiting != null && waiting.isValid()) {
            SpanContext linked = SpanContext.createFromRemoteParent(
                    waiting.getTraceId(),
                    waiting.getSpanId(),
                    TraceFlags.getSampled(),
                    TraceState.getDefault());
            if (linked.isValid()) {
                // Links must be registered on the builder before the span starts.
                spanBuilder.addLink(linked);
                log.info("span-linked {} to workflow traceId={} spanId={}",
                        spanName, waiting.getTraceId(), waiting.getSpanId());
            } else {
                log.warn("waiting span context is invalid for workflowId={}: {}", workflowId, waiting);
            }
        } else {
            log.warn("no waiting span context available for workflowId={}", workflowId);
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            action.run();
        } finally {
            span.end();
            registry.remove(workflowId);
        }
    }
}
