package click.yinsb.icmtracing.temporal.activities;

import click.yinsb.icmtracing.temporal.model.Constants;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
@ActivityImpl(taskQueues = {Constants.ICM_TASK_QUEUE})
public class Activity001Impl implements Activity001 {

    private final RestTemplate restTemplate;

    @Override
    public void runActivity(EventMessage eventMessage) {
        log.info("runActivity called");
        //restTemplate.postForObject("http://localhost:8081/run-agent", eventMessage, Object.class);
    }
}
