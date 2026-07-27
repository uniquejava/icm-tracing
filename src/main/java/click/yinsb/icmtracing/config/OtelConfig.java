package click.yinsb.icmtracing.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expose the ADOT Java Agent's global {@link OpenTelemetry} as a Spring bean.
 * Temporal Spring Boot needs an {@code OpenTelemetry} bean for workflow/activity spans;
 * the agent configures the SDK (SigV4 OTLP → X-Ray). Do not build a second
 * {@code OpenTelemetrySdk} that exports to a local Collector (:4317).
 */
@Configuration
public class OtelConfig {
    @Bean
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
