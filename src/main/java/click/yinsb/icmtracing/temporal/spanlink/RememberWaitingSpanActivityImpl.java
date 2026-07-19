package click.yinsb.icmtracing.temporal.spanlink;

import click.yinsb.icmtracing.temporal.model.Constants;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ActivityImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class RememberWaitingSpanActivityImpl implements RememberWaitingSpanActivity {

    private final WaitingSpanContextRegistry registry;

    @Override
    public void remember(String workflowId, String traceId, String spanId) {
        registry.put(workflowId, new WaitingSpanContext(traceId, spanId));
        log.info("remembered waiting span context for workflowId={} traceId={} spanId={}",
                workflowId, traceId, spanId);
    }
}
