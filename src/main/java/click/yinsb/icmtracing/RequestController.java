package click.yinsb.icmtracing;

import click.yinsb.icmtracing.temporal.client.WorkflowClientService;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping
@RestController
@RequiredArgsConstructor
public class RequestController {
    private final WorkflowClientService workflowClientService;

    @PostMapping("/request")
    public void request(EventMessage eventMessage) {
        workflowClientService.start(eventMessage);
    }

    @PostMapping("/approve")
    public void approve(@RequestParam("workflowId") String workflowId) {
        workflowClientService.approve(workflowId);
    }
}
