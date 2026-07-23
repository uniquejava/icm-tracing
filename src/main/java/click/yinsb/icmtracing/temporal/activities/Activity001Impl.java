package click.yinsb.icmtracing.temporal.activities;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import click.yinsb.icmtracing.temporal.spanlink.RetryAttemptContextRegistry;
import click.yinsb.icmtracing.temporal.spanlink.RetrySpanLinkTracer;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
@ActivityImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class Activity001Impl implements Activity001 {

    private final RestTemplate restTemplate;
    private final RetrySpanLinkTracer retrySpanLinkTracer;

    @Override
    public void runActivity(EventMessage eventMessage) {
        ActivityInfo info = Activity.getExecutionContext().getInfo();
        int attempt = info.getAttempt();
        String workflowId = info.getWorkflowId();
        String activityId = info.getActivityId();
        String activityType = info.getActivityType();
        String key = RetryAttemptContextRegistry.key(workflowId, activityId);

        log.info("runActivity attempt={} workflowId={} activityId={}", attempt, workflowId, activityId);

        // Temporal owns exponential backoff between attempts; we only instrument each execution.
        retrySpanLinkTracer.runAttempt(key, workflowId, activityType, attempt, () ->
                restTemplate.postForObject("http://localhost:8081/run-agent", eventMessage, Object.class));
    }

}
