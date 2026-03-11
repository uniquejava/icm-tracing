package click.yinsb.icmtracing.temporal.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {
    private String id;
    private String type;
    /** Heartbeat interval in seconds (default 60). Used by workflows to emit heartbeat spans. */
    private Integer heartbeatIntervalSeconds;
}
