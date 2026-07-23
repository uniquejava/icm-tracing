package click.yinsb.icmtracing.temporal.spanlink;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process store of prior activity-attempt span contexts for Span Links.
 * <p>
 * Temporal owns the exponential-backoff retry loop across separate activity task
 * executions, so contexts must survive between attempts. This demo uses an
 * in-memory map (single worker). Multi-worker / multi-pod would need durable storage.
 */
@Component
public class RetryAttemptContextRegistry {

    private final ConcurrentHashMap<String, List<RetryAttemptContext>> byKey = new ConcurrentHashMap<>();

    public static String key(String workflowId, String activityId) {
        return workflowId + ":" + activityId;
    }

    public void append(String key, RetryAttemptContext context) {
        if (key == null || context == null || !context.isValid()) {
            return;
        }
        byKey.compute(key, (k, existing) -> {
            List<RetryAttemptContext> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.removeIf(c -> c.getAttempt() == context.getAttempt());
            next.add(context);
            next.sort((a, b) -> Integer.compare(a.getAttempt(), b.getAttempt()));
            return next;
        });
    }

    public List<RetryAttemptContext> getAll(String key) {
        List<RetryAttemptContext> list = byKey.get(key);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public void clear(String key) {
        if (key != null) {
            byKey.remove(key);
        }
    }
}
