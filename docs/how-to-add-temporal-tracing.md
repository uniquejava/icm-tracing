# How to Add Temporal Tracing to a Spring Boot 3.x Project

This guide is written for AI agents and contributors who need to add tracing to an existing Temporal + Spring Boot 3.x service. It assumes a Maven build, Java 21 or similar, and a project that already runs Temporal workflows.

## Goal

After these changes, the project should:

- emit HTTP and application spans from Spring Boot,
- emit Temporal workflow and activity spans,
- export traces through OTLP,
- optionally keep very long-running workflow traces visible with custom heartbeat spans.

## Minimum required changes

If an AI agent needs the smallest working tracing setup, do these first:

1. Add `spring-boot-starter-actuator`, `micrometer-tracing-bridge-otel`, and `opentelemetry-exporter-otlp` to `pom.xml`.
2. Set `spring.application.name` and `spring.temporal.*` in `src/main/resources/application.yaml`.
3. Add an `OpenTelemetry` bean that exports to `${OTEL_EXPORTER_OTLP_ENDPOINT}`.
4. Start an OTLP collector or point directly to an observability backend.
5. Trigger one endpoint that starts one workflow and confirm workflow/activity spans appear.

Do not add heartbeat spans unless the workflow is actually long-running or the backend splits traces after idle gaps.

## Repo adaptation checklist

Before patching a random repository, the agent should inspect and fill in these placeholders:

- Replace `com.example.app` with the real base package.
- Replace `your-service-name` with the real `spring.application.name`.
- Replace `your-task-queue` with the task queue used by the workflow workers.
- Confirm whether the repo already defines an `OpenTelemetry` or Micrometer tracing bean to avoid duplicate configuration.
- Confirm the actual workflow and activity package paths used by `workers-auto-discovery`.
- Confirm whether the project exports directly to Jaeger, New Relic, Tempo, or an OpenTelemetry Collector.
- If the repo already has custom Temporal worker config, merge changes there instead of adding parallel config.

A safe agent workflow is: inspect `pom.xml`, inspect `application.yaml` or `application.properties`, inspect Temporal config classes, then patch only the missing pieces.

## 1. Add Maven dependencies

Update `pom.xml` with the core tracing dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>io.temporal</groupId>
        <artifactId>temporal-spring-boot-starter</artifactId>
        <version>1.33.0</version>
    </dependency>

    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Notes:

- `temporal-spring-boot-starter` wires workers and clients into Spring Boot.
- `micrometer-tracing-bridge-otel` lets Spring Boot use OpenTelemetry as the tracing backend.
- `opentelemetry-exporter-otlp` exports spans to an OTLP collector or backend.

## 2. Add baseline Spring configuration

Create or update `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: your-service-name
  temporal:
    namespace: default
    connection:
      target: ${TEMPORAL_ADDRESS:127.0.0.1:7233}
    workers-auto-discovery:
      packages:
        - com.example.app.temporal.workflow
        - com.example.app.temporal.activities
```

Replace package names with the real workflow and activity packages.

## 3. Register an OpenTelemetry bean

If the project does not already create an `OpenTelemetry` bean, add one such as `src/main/java/.../config/OtelConfig.java`:

```java
package com.example.app.config;

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

@Configuration
public class OtelConfig {

    @Bean
    OpenTelemetry openTelemetry(
            @Value("${spring.application.name}") String applicationName,
            @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}") String otlpEndpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), applicationName)));

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
                                W3CBaggagePropagator.getInstance())))
                .build();
    }
}
```

Why this matters:

- sets `service.name` so traces are grouped correctly,
- exports via OTLP gRPC,
- keeps standard W3C trace propagation enabled.

## 4. Ensure HTTP spans carry useful route attributes

In some projects, HTTP spans may be missing route-level attributes. Add a small MVC interceptor config such as `HttpSpanAttributesConfig`:

```java
package com.example.app.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

@Configuration
public class HttpSpanAttributesConfig implements WebMvcConfigurer {

    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Span span = Span.current();
                if (!span.getSpanContext().isValid()) {
                    return true;
                }
                span.setAttribute(HTTP_REQUEST_METHOD, request.getMethod());
                Optional.ofNullable(resolveRoute(request)).ifPresent(route -> span.setAttribute(HTTP_ROUTE, route));
                return true;
            }
        });
    }

    @Nullable
    private static String resolveRoute(HttpServletRequest request) {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && !pattern.isEmpty()) {
            return pattern;
        }
        String servletPath = request.getServletPath();
        return (servletPath != null && !servletPath.isEmpty()) ? servletPath : null;
    }
}
```

This is optional, but it makes traces much easier to read.

## 5. Verify Temporal classes are discovered by Spring

A typical project structure looks like this:

```text
src/main/java/com/example/app/
├── config/
├── temporal/
│   ├── activities/
│   ├── workflow/
│   ├── client/
│   └── model/
└── web/
```

If workflow or activity implementations are outside the auto-discovery packages in `application.yaml`, tracing may appear incomplete because workers never start.

## 6. Add an OTLP collector or point directly to a backend

For local development, an OpenTelemetry Collector is the easiest option. Example `otel-collector.yml`:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  debug:
    verbosity: normal

processors:
  batch: {}

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [debug]
```

Start the collector, then point `OTEL_EXPORTER_OTLP_ENDPOINT` to it, usually `http://localhost:4317`.

## 7. Trigger one HTTP request and one workflow

Use an endpoint that starts a workflow. Confirm the trace contains:

- one incoming HTTP server span,
- one workflow span such as `RunWorkflow:YourWorkflow`,
- activity spans such as `RunActivity:YourActivity`.

If the HTTP span exists but no workflow/activity spans appear, check Temporal worker registration first.

## 8. Optional: add heartbeat spans for long-running workflows

Some observability backends split long traces when there is a long quiet period. For workflows that wait on signals, approvals, retries, or timers, add a lightweight heartbeat pattern.

### 8.1 Create a heartbeat activity

```java
package com.example.app.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface HeartbeatActivity {

    @ActivityMethod
    void recordHeartbeat(String traceId, String spanId);
}
```

```java
package com.example.app.temporal.activities;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.temporal.spring.boot.ActivityImpl;

@ActivityImpl(taskQueues = "your-task-queue")
public class HeartbeatActivityImpl implements HeartbeatActivity {

    @Override
    public void recordHeartbeat(String traceId, String spanId) {
        if (traceId == null || traceId.isBlank() || spanId == null || spanId.isBlank()) {
            return;
        }

        SpanContext parent = SpanContext.createFromRemoteParent(
                traceId,
                spanId,
                TraceFlags.getSampled(),
                TraceState.getDefault());

        Span span = GlobalOpenTelemetry.getTracer("temporal-heartbeat")
                .spanBuilder("heartbeat")
                .setParent(Context.current().with(Span.wrap(parent)))
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.end();
    }
}
```

### 8.2 Create workflow-side support

```java
package com.example.app.temporal.workflow.support;

import com.example.app.temporal.activities.HeartbeatActivity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.Functions;

import java.time.Duration;

public final class WorkflowHeartbeatSupport {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(60);

    private WorkflowHeartbeatSupport() {
    }

    public static void runWithHeartbeat(Functions.Proc mainWork) {
        String[] parentSpan = Workflow.sideEffect(String[].class, () -> {
            SpanContext current = Span.current().getSpanContext();
            if (current != null && current.isValid()) {
                return new String[] { current.getTraceId(), current.getSpanId() };
            }
            return new String[] { "", "" };
        });

        HeartbeatActivity heartbeatActivity = Workflow.newActivityStub(
                HeartbeatActivity.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build());

        Promise<Void> workPromise = Async.procedure(mainWork);
        while (true) {
            boolean completed = Workflow.await(HEARTBEAT_INTERVAL, workPromise::isCompleted);
            if (completed) {
                break;
            }
            heartbeatActivity.recordHeartbeat(parentSpan[0], parentSpan[1]);
        }

        workPromise.get();
    }
}
```

### 8.3 Use the helper in a workflow

```java
@Override
public void run(MyRequest request) {
    WorkflowHeartbeatSupport.runWithHeartbeat(() -> runMainFlow(request));
}
```

Important:

- do not start spans directly inside workflow logic,
- create custom spans in an activity, not in deterministic workflow code,
- keep the heartbeat interval below the backend’s trace-gap threshold.

## 9. Optional: filter duplicate Temporal heartbeat activity spans

If heartbeat spans are added, the backend may also show Temporal’s own activity spans, for example `StartActivity:RecordHeartbeat` and `RunActivity:RecordHeartbeat`. If that is too noisy, filter them in the collector:

```yaml
processors:
  filter/drop_temporal_heartbeat_activity:
    error_mode: ignore
    traces:
      span:
        - 'name == "StartActivity:RecordHeartbeat"'
        - 'name == "RunActivity:RecordHeartbeat"'
  batch: {}

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [filter/drop_temporal_heartbeat_activity, batch]
      exporters: [debug]
```

## Agent execution strategy

When applying this guide to a real repository, prefer this order:

1. Inspect existing tracing and Temporal configuration before editing anything.
2. Reuse existing config classes if they already create beans for tracing, exporters, or interceptors.
3. Add the minimum viable tracing setup first.
4. Run one narrow validation path before adding optional heartbeat logic.
5. Add heartbeat support only for workflows with long waits, approvals, signals, or timers.

This keeps agent changes small, lowers merge risk, and avoids duplicating observability wiring that the repository may already have.

## 10. Final validation checklist

Before considering the work complete, verify all of the following:

- `mvn clean package` succeeds,
- the app starts with `mvn spring-boot:run` or equivalent,
- one HTTP request creates a server span,
- one workflow execution creates workflow and activity spans,
- `service.name` matches the application name,
- long-running workflows keep emitting visible heartbeat spans if that feature was added.

## Common mistakes

- Missing `OpenTelemetry` bean, so spans are never exported.
- Wrong Temporal auto-discovery packages, so workers do not register.
- No OTLP endpoint configured, so traces are generated but never shipped.
- Creating spans inside workflow code instead of inside an activity.
- Forgetting to set `service.name`, which makes traces hard to identify.

## Suggested file list

In a typical project, the final change set touches these files:

- `pom.xml`
- `src/main/resources/application.yaml`
- `src/main/java/.../config/OtelConfig.java`
- `src/main/java/.../config/HttpSpanAttributesConfig.java` (optional)
- `src/main/java/.../temporal/activities/HeartbeatActivity.java` (optional)
- `src/main/java/.../temporal/activities/HeartbeatActivityImpl.java` (optional)
- `src/main/java/.../temporal/workflow/support/WorkflowHeartbeatSupport.java` (optional)
- `otel-collector.yml` (optional for local development)

Use this document as a checklist: dependency changes first, configuration second, exporter wiring third, then optional long-running workflow enhancements.
