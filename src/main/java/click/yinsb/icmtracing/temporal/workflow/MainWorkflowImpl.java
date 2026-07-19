package click.yinsb.icmtracing.temporal.workflow;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WorkflowImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class MainWorkflowImpl implements MainWorkflow {

	@Override
	public void runAsync(EventMessage eventMessage) {
		runMainFlow(eventMessage);
	}

	private void runMainFlow(EventMessage eventMessage) {
		String parentWorkflowId = Workflow.getInfo().getWorkflowId();
		String childWorkflowId = parentWorkflowId + "-child-001";

		ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
				.setWorkflowId(childWorkflowId)
				.build();

		eventMessage.setWorkflowId(childWorkflowId);

		log.info("child workflow id: {}", childWorkflowId);

		ChildWorkflow001 stub1 = Workflow.newChildWorkflowStub(ChildWorkflow001.class, options);
		// sync call
		stub1.run(eventMessage);
	}
}
