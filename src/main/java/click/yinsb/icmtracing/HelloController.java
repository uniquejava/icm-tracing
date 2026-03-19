package click.yinsb.icmtracing;

import click.yinsb.icmtracing.temporal.client.WorkflowClientService;
import click.yinsb.icmtracing.temporal.model.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping
@RestController
@RequiredArgsConstructor
public class HelloController {
    private final WorkflowClientService workflowClientService;

    @PostMapping("/hello")
    public void hello(EventMessage eventMessage) {
        workflowClientService.start(eventMessage);
    }

    @GetMapping("/approve")
    public void approve(@RequestParam("workflowId") String workflowId) {
        workflowClientService.approve(workflowId);
    }
}
