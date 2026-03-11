package click.yinsb.icmtracing.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity that records a "heartbeat" span as a child of the workflow span.
 * Used to emit periodic heartbeat spans under RunWorkflow:MainWorkflow and RunWorkflow:ChildWorkflow001.
 */
@ActivityInterface
public interface HeartbeatActivity {

    @ActivityMethod
    void recordHeartbeat(String traceId, String spanId);
}
