package click.yinsb.icmtracing.temporal.workflow.base;

import click.yinsb.icmtracing.temporal.workflow.ChildWorkflow001;
import click.yinsb.icmtracing.temporal.workflow.MainWorkflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class WorkflowTypeRegistry {

	private static final Map<String, Class<?>> REGISTRY = new HashMap<>();

	private WorkflowTypeRegistry() {
	}

	static {
		register(MainWorkflow.class);
		register(ChildWorkflow001.class);

	}

	private static void register(Class<?> workflowInterface) {
		register(workflowInterface.getSimpleName(), workflowInterface);
	}

	private static void register(String workflowType, Class<?> workflowInterface) {
		REGISTRY.put(workflowType, workflowInterface);
	}

	public static Optional<Class<?>> getWorkflowClass(String workflowType) {
		return Optional.ofNullable(REGISTRY.get(workflowType));
	}

}