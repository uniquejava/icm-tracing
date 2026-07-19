package click.yinsb.icmtracing.temporal.spanlink;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process store of workflow span contexts waiting for HITL / external signals.
 * Written by {@link RememberWaitingSpanActivity} before {@code Workflow.await};
 * read by {@link HitlSpanLinkTracer} when signaling.
 */
@Component
public class WaitingSpanContextRegistry {

    private final ConcurrentHashMap<String, WaitingSpanContext> byWorkflowId = new ConcurrentHashMap<>();

    public void put(String workflowId, WaitingSpanContext context) {
        if (workflowId == null || context == null || !context.isValid()) {
            return;
        }
        byWorkflowId.put(workflowId, context);
    }

    public Optional<WaitingSpanContext> get(String workflowId) {
        return Optional.ofNullable(byWorkflowId.get(workflowId));
    }

    public void remove(String workflowId) {
        if (workflowId != null) {
            byWorkflowId.remove(workflowId);
        }
    }
}
