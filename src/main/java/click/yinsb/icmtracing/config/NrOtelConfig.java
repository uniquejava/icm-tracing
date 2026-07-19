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
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.Collection;
import java.util.List;
import java.util.Set;

// Unused: collector path uses OtelConfig. Add @Configuration here and remove it from OtelConfig to switch.
public class NrOtelConfig {

    // The custom heartbeat span exists only to prevent New Relic from splitting one long-running Temporal
    // workflow into multiple trace groups after a quiet period. Filter the Temporal RecordHeartbeat activity
    // spans so reviewers see the intended heartbeat span once, not duplicated by Temporal activity spans.
    private static final Set<String> FILTERED_SPAN_NAMES = Set.of(
            "StartActivity:RecordHeartbeat",
            "RunActivity:RecordHeartbeat"
    );

    @Bean
    OpenTelemetry openTelemetry(
            @Value("${spring.application.name}") String applicationName,
            @Value("${NR_ENDPOINT:https://otlp.nr-data.net:4317}") String nrEndpoint,
            @Value("${MY_NEW_RELIC_API_KEY}") String newRelicApiKey) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), applicationName)));

        SpanExporter exporter = new FilteringSpanExporter(
                OtlpGrpcSpanExporter.builder()
                        .setEndpoint(nrEndpoint)
                        .addHeader("api-key", newRelicApiKey)
                        .build());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
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

    private static final class FilteringSpanExporter implements SpanExporter {

        private final SpanExporter delegate;

        private FilteringSpanExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            List<SpanData> filteredSpans = spans.stream()
                    .filter(span -> !FILTERED_SPAN_NAMES.contains(span.getName()))
                    .toList();
            return delegate.export(filteredSpans);
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }
    }
}
