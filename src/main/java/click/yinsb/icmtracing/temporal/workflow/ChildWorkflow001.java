
package click.yinsb.icmtracing.temporal.workflow;

import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ChildWorkflow001 {

	@WorkflowMethod
	void run(EventMessage eventMessage);


	@SignalMethod
	void receiveExternalResponse(String workflowId);
}
