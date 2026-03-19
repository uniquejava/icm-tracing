package click.yinsb.icmtracing.temporal.client;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import click.yinsb.icmtracing.temporal.workflow.MainWorkflow;
import click.yinsb.icmtracing.temporal.workflow.base.WorkflowTypeRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowClientService {
    @Value("${spring.temporal.namespace}")
    private String namespace;

    private final WorkflowClient client;

    public void start(EventMessage eventMessage) {
        try {
            log.info("starting workflow");

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

    public void approve(String childWorkflowId) {
        Class<?> workflowClass = getWorkflowClass(childWorkflowId);
        Object stub = client.newWorkflowStub(workflowClass, childWorkflowId);
        WorkflowStub workflowStub = WorkflowStub.fromTyped(stub);
        workflowStub.signal("receiveExternalResponse", childWorkflowId);
    }

    private Class<?> getWorkflowClass(String childWorkflowId) {
        WorkflowServiceStubs serviceStubs = client.getWorkflowServiceStubs();
        DescribeWorkflowExecutionRequest request = DescribeWorkflowExecutionRequest.newBuilder().setNamespace(namespace)
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(childWorkflowId).build()).build();
        DescribeWorkflowExecutionResponse response = serviceStubs.blockingStub().describeWorkflowExecution(request);
        String workflowType = response.getWorkflowExecutionInfo().getType().getName();
        log.info("workflowType: {}", workflowType);
        Class<?> workflowClass = WorkflowTypeRegistry.getWorkflowClass(workflowType)
                .orElseThrow(() -> new IllegalStateException("Unknown workflowType: " + workflowType));
        log.info("workflowClass: {}", workflowClass);
        return workflowClass;
    }
}
