package click.yinsb.icmtracing.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Traces go OTLP/gRPC → ADOT Collector (:4317) → awsxray (+ local Jaeger).
 * Metrics are exported separately via Micrometer OTLP
 * ({@code management.otlp.metrics.export.url} → :4318/v1/metrics → awsemf).
 * Temporal Spring Boot picks up this {@link OpenTelemetry} bean for workflow/activity spans.
 */
@Configuration
public class OtelConfig {
    @Bean
    OpenTelemetry openTelemetry(
            @Value("${spring.application.name}") String applicationName,
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String otlpEndpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), applicationName,
                        AttributeKey.stringKey("service.namespace"), "icm-tracing",
                        AttributeKey.stringKey("deployment.environment"), "adot-aws")));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                        .setEndpoint(otlpEndpoint)
                                        .build())
                        .build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(),
                                W3CBaggagePropagator.getInstance()
                        )))
                .build();
    }

}
