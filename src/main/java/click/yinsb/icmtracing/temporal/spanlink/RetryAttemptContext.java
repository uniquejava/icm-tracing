package click.yinsb.icmtracing.temporal.spanlink;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Span context for one Temporal activity attempt, used to build OTel Span Links
 * from later retry segments back to earlier ones.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryAttemptContext {
    private int attempt;
    private String traceId;
    private String spanId;
    /** Wall-clock end of this attempt; used to decide whether the next retry needs a new trace. */
    private long endedAtEpochMs;

    public boolean isValid() {
        return attempt > 0
                && traceId != null && !traceId.isBlank()
                && spanId != null && !spanId.isBlank();
    }
}
