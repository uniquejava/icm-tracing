package click.yinsb.icmtracing.temporal.activities;

import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface Activity001 {

	@ActivityMethod
	void runActivity(EventMessage eventMessage);

	/**
	 * Store the waiting workflow span context so an external approve/signal
	 * can Span-Link back to this workflow trace.
	 */
	@ActivityMethod
	void rememberWaitingSpan(String workflowId, String traceId, String spanId);
}
