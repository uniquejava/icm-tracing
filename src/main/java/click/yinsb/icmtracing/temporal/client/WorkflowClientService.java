package click.yinsb.icmtracing.temporal.client;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import click.yinsb.icmtracing.temporal.workflow.MainWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowClientService {

    private final WorkflowClient client;

    @Value("${spring.temporal.heartbeat.interval-seconds:60}")
    private int heartbeatIntervalSeconds;

    public void start(EventMessage eventMessage) {
        try {
            log.info("starting workflow");
            if (eventMessage.getHeartbeatIntervalSeconds() == null) {
                eventMessage.setHeartbeatIntervalSeconds(heartbeatIntervalSeconds);
            }
            String workflowId = UUID.randomUUID().toString();

            WorkflowOptions options = WorkflowOptions.newBuilder().setTaskQueue(Constants.ICM_TASK_QUEUE)
                    .setWorkflowId(workflowId).build();

            MainWorkflow stub1 = client.newWorkflowStub(MainWorkflow.class, options);
            WorkflowClient.start(stub1::runAsync, eventMessage); // this call do not block

            log.info("workflow started: {}", workflowId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }
}
