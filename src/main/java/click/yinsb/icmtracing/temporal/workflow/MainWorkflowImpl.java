package click.yinsb.icmtracing.temporal.workflow;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import click.yinsb.icmtracing.temporal.workflow.support.WorkflowHeartbeatSupport;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class MainWorkflowImpl implements MainWorkflow {

	@Override
	public void runAsync(EventMessage eventMessage) {
		WorkflowHeartbeatSupport.runWithHeartbeat(
				() -> {
					ChildWorkflow001 stub1 = Workflow.newChildWorkflowStub(ChildWorkflow001.class);
					stub1.run(eventMessage);
				});
	}
}
