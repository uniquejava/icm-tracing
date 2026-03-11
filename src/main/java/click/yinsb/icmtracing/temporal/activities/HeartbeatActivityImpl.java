package click.yinsb.icmtracing.temporal.activities;

import click.yinsb.icmtracing.temporal.model.Constants;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Emits a "heartbeat" span as a child of the workflow span so the trace shows
 * periodic activity. Temporal's StartActivity:RecordHeartbeat spans are filtered
 * out in the collector so only these custom heartbeat spans appear.
 */
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
