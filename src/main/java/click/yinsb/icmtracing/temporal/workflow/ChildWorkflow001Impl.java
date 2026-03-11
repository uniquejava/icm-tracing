package click.yinsb.icmtracing.temporal.workflow;

import click.yinsb.icmtracing.temporal.activities.Activity001;
import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

@WorkflowImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class ChildWorkflow001Impl implements ChildWorkflow001 {

	private final Logger log = Workflow.getLogger(ChildWorkflow001.class.getName());

	private final Activity001 activity1 = Workflow.newActivityStub(Activity001.class,
			ActivityOptions.newBuilder()
					.setStartToCloseTimeout(Duration.ofMinutes(5))
					.setScheduleToCloseTimeout(Duration.ofMinutes(120))
					.setRetryOptions(RetryOptions.newBuilder()
										.setInitialInterval(Duration.ofSeconds(60))
										.setMaximumInterval(Duration.ofMinutes(5))
										.setBackoffCoefficient(2)
										.setMaximumAttempts(5)
										.build())
					.build());

	@Override
	public void run(EventMessage eventMessage) {
		log.info("run child workflow 001");
		activity1.runActivity(eventMessage);
	}
}
