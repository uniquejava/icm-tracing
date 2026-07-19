package click.yinsb.icmtracing;

import click.yinsb.icmtracing.temporal.client.WorkflowClientService;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping
@RestController
@RequiredArgsConstructor
public class RequestController {
    private final WorkflowClientService workflowClientService;

    @PostMapping("/request")
    public void request(EventMessage eventMessage) {
        workflowClientService.start(eventMessage);
    }
}
