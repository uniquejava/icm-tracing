package click.yinsb.icmtracing.temporal.activities;

import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface Activity001 {

	@ActivityMethod
	void runActivity(EventMessage eventMessage);
}
