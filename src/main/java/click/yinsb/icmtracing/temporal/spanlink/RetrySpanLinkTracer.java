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
 * Noise-reduction rules (vs linking every retry as a new root to all priors):
 * <ul>
 *   <li>Stay in the current Temporal trace while the gap since the previous attempt is
 *       under {@link #NEW_ROOT_AFTER_MS} (New Relic ~90s session budget)</li>
 *   <li>Only {@code setNoParent()} + {@code addLink} when that gap is exceeded</li>
 *   <li>Link only the immediate previous attempt ({@code retry_of}), not the full history</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrySpanLinkTracer {

    /**
     * Start a new root (and Span Link) only after this idle gap since the previous attempt.
     * Aligns with New Relic's ~90s trace-session behavior for long-running workflows.
     */
    static final long NEW_ROOT_AFTER_MS = 90_000L;

    private static final AttributeKey<String> LINK_RELATIONSHIP = AttributeKey.stringKey("link.relationship");
    private static final AttributeKey<Long> PREVIOUS_ATTEMPT = AttributeKey.longKey("previous.attempt_number");

    private final OpenTelemetry openTelemetry;
    private final RetryAttemptContextRegistry registry;

    /**
     * Run {@code action} under an attempt span.
     * <ul>
     *   <li>Short gap: child of the current Temporal {@code RunActivity} span (same trace)</li>
     *   <li>Gap ≥ {@link #NEW_ROOT_AFTER_MS}: new root with a single link to the previous attempt</li>
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
        RetryAttemptContext last = previous.isEmpty() ? null : previous.get(previous.size() - 1);

        long now = System.currentTimeMillis();
        long gapMs = last == null || last.getEndedAtEpochMs() <= 0
                ? 0L
                : Math.max(0L, now - last.getEndedAtEpochMs());
        boolean startNewTrace = attempt > 1 && last != null && gapMs >= NEW_ROOT_AFTER_MS;

        var spanBuilder = tracer.spanBuilder("activity.retry.attempt")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("workflow.id", workflowId)
                .setAttribute("temporal.activity.type", activityType)
                .setAttribute("retry.attempt_number", (long) attempt)
                .setAttribute("retry.is_retry", attempt > 1)
                .setAttribute("retry.previous_attempts", (long) previous.size())
                .setAttribute("retry.gap_ms", gapMs)
                .setAttribute("retry.new_trace", startNewTrace);

        if (startNewTrace) {
            spanBuilder.setNoParent();
            SpanContext linked = toSpanContext(last);
            if (linked.isValid()) {
                Attributes linkAttrs = Attributes.of(
                        LINK_RELATIONSHIP, "retry_of",
                        PREVIOUS_ATTEMPT, (long) last.getAttempt());
                spanBuilder.addLink(linked, linkAttrs);
                log.info("span-link attempt={} -> prior attempt={} gapMs={} traceId={} spanId={}",
                        attempt, last.getAttempt(), gapMs, last.getTraceId(), last.getSpanId());
            }
        } else if (attempt > 1 && last == null) {
            log.warn("retry attempt={} has no prior span contexts for key={}", attempt, registryKey);
        } else if (attempt > 1) {
            log.info("same-trace attempt={} gapMs={} (threshold={}ms); skip setNoParent/addLink",
                    attempt, gapMs, NEW_ROOT_AFTER_MS);
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
        registry.append(registryKey, new RetryAttemptContext(
                attempt, ctx.getTraceId(), ctx.getSpanId(), System.currentTimeMillis()));
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
