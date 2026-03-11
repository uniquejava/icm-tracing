package click.yinsb.icmtracing.temporal.workflow;

import click.yinsb.icmtracing.temporal.activities.HeartbeatActivity;
import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class MainWorkflowImpl implements MainWorkflow {

	private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60;

	// Heartbeat activity stub (short timeout; activity is a no-op)
	private final HeartbeatActivity heartbeatActivity = Workflow.newActivityStub(HeartbeatActivity.class,
			ActivityOptions.newBuilder()
					.setStartToCloseTimeout(Duration.ofSeconds(10))
					.build());

	@Override
	public void runAsync(EventMessage eventMessage) {
		int intervalSeconds = eventMessage.getHeartbeatIntervalSeconds() != null
				? eventMessage.getHeartbeatIntervalSeconds()
				: DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
		Duration interval = Duration.ofSeconds(intervalSeconds);

		// Capture workflow span context once (for heartbeat child spans)
		String[] spanContext = Workflow.sideEffect(String[].class, () -> {
			try {
				SpanContext ctx = Span.current().getSpanContext();
				if (ctx != null && ctx.isValid()) {
					return new String[]{ctx.getTraceId(), ctx.getSpanId()};
				}
			} catch (Exception ignored) {
			}
			return new String[]{"", ""};
		});
		String traceId = spanContext[0];
		String spanId = spanContext[1];

		// Run child workflow and heartbeat loop in parallel
		Promise<Void> childPromise = Async.procedure(() -> {
			ChildWorkflow001 stub1 = Workflow.newChildWorkflowStub(ChildWorkflow001.class);
			stub1.run(eventMessage);
		});

		while (true) {
			Workflow.sleep(interval);
			if (childPromise.isCompleted()) {
				break;
			}
			heartbeatActivity.recordHeartbeat(traceId, spanId);
		}

		childPromise.get();
	}
}
