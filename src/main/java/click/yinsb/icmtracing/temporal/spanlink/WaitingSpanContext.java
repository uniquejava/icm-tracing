package click.yinsb.icmtracing.temporal.spanlink;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trace/span ids of a workflow span waiting for an external (HITL) signal.
 * Used to create an OpenTelemetry Span Link from the signal/approve request
 * back to the original workflow trace.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitingSpanContext {
    private String traceId;
    private String spanId;

    public boolean isValid() {
        return traceId != null && !traceId.isBlank()
                && spanId != null && !spanId.isBlank();
    }
}
