package click.yinsb.icmtracing.temporal.spanlink;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Creates per-attempt spans for Temporal activity retries using OpenTelemetry Span Links.
 * <p>
 * Based on:
 * <ul>
 *   <li>New Relic: long-running workflows that exceed the ~90s trace session should
 *       connect later segments via Span Links (distinct segments, navigable previous/next)</li>
 *   <li>OTel Java: {@code setNoParent()} + {@code addLink(SpanContext)} starts a new root
 *       span in a new trace while preserving causal association</li>
 *   <li>Retry instrumentation pattern: each attempt is its own span; retries link back
 *       with {@code link.relationship=retry_of}</li>
 * </ul>
 * Temporal schedules retries outside the process, so attempt N cannot share a for-loop
 * with attempt 1; we persist prior SpanContexts in {@link RetryAttemptContextRegistry}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrySpanLinkTracer {

    private static final AttributeKey<String> LINK_RELATIONSHIP = AttributeKey.stringKey("link.relationship");
    private static final AttributeKey<Long> PREVIOUS_ATTEMPT = AttributeKey.longKey("previous.attempt_number");

    private final OpenTelemetry openTelemetry;
    private final RetryAttemptContextRegistry registry;

    /**
     * Run {@code action} under an attempt span.
     * <ul>
     *   <li>Attempt 1: child of the current Temporal {@code RunActivity} span (same trace)</li>
     *   <li>Attempt N&gt;1: new root span ({@code setNoParent}) with links to all prior attempts</li>
     * </ul>
     */
    public <T> T runAttempt(
            String registryKey,
            String workflowId,
            String activityType,
            int attempt,
            Supplier<T> action) {
        Tracer tracer = openTelemetry.getTracer("retry_spanlink");
        List<RetryAttemptContext> previous = registry.getAll(registryKey);

        var spanBuilder = tracer.spanBuilder("activity.retry.attempt")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("workflow.id", workflowId)
                .setAttribute("temporal.activity.type", activityType)
                .setAttribute("retry.attempt_number", (long) attempt)
                .setAttribute("retry.is_retry", attempt > 1)
                .setAttribute("retry.previous_attempts", (long) previous.size());

        if (attempt > 1) {
            // New Relic Span Links UI navigates across distinct traces — start a new root.
            spanBuilder.setNoParent();
            for (RetryAttemptContext prev : previous) {
                SpanContext linked = toSpanContext(prev);
                if (!linked.isValid()) {
                    continue;
                }
                Attributes linkAttrs = Attributes.of(
                        LINK_RELATIONSHIP, "retry_of",
                        PREVIOUS_ATTEMPT, (long) prev.getAttempt());
                spanBuilder.addLink(linked, linkAttrs);
                log.info("span-link attempt={} -> prior attempt={} traceId={} spanId={}",
                        attempt, prev.getAttempt(), prev.getTraceId(), prev.getSpanId());
            }
            if (previous.isEmpty()) {
                log.warn("retry attempt={} has no prior span contexts for key={}", attempt, registryKey);
            }
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            try {
                T result = action.get();
                span.setAttribute("retry.status", "success");
                span.setStatus(StatusCode.OK);
                remember(registryKey, attempt, span);
                registry.clear(registryKey);
                return result;
            } catch (RuntimeException e) {
                span.setAttribute("retry.status", "failed");
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage() == null ? "failed" : e.getMessage());
                remember(registryKey, attempt, span);
                throw e;
            } catch (Exception e) {
                span.setAttribute("retry.status", "failed");
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage() == null ? "failed" : e.getMessage());
                remember(registryKey, attempt, span);
                throw new RuntimeException(e);
            }
        } finally {
            span.end();
        }
    }

    public void runAttempt(
            String registryKey,
            String workflowId,
            String activityType,
            int attempt,
            Runnable action) {
        runAttempt(registryKey, workflowId, activityType, attempt, () -> {
            action.run();
            return null;
        });
    }

    private void remember(String registryKey, int attempt, Span span) {
        SpanContext ctx = span.getSpanContext();
        if (!ctx.isValid()) {
            log.warn("skip remembering attempt={}: invalid span context", attempt);
            return;
        }
        registry.append(registryKey, new RetryAttemptContext(attempt, ctx.getTraceId(), ctx.getSpanId()));
        log.info("remembered attempt={} key={} traceId={} spanId={}",
                attempt, registryKey, ctx.getTraceId(), ctx.getSpanId());
    }

    private static SpanContext toSpanContext(RetryAttemptContext prev) {
        return SpanContext.createFromRemoteParent(
                prev.getTraceId(),
                prev.getSpanId(),
                TraceFlags.getSampled(),
                TraceState.getDefault());
    }
}
