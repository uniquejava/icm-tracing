package click.yinsb.icmtracing.temporal.spanlink;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Infrastructure activity used by {@link HitlSpanLinkSupport} to persist waiting span context.
 * Not a business activity — stays with the spanlink package so Support stays decoupled from app workflows.
 */
@ActivityInterface
public interface RememberWaitingSpanActivity {

    @ActivityMethod
    void remember(String workflowId, String traceId, String spanId);
}
