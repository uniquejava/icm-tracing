## Environment

- Java 21
- Maven 3.9.x
- Spring Boot 3.5.x
- Temporal (Server 1.29.1, UI 2.42.1)

Check the `screenshots` directory for known issues.

## Tracing

- **OpenTelemetry**: The app exports traces via OTLP (gRPC) to the collector. HTTP server spans include `http.request.method` and `http.route` (OpenTelemetry semantic conventions).
- **Heartbeat spans**: Only **MainWorkflow** emits heartbeat spans. They appear as `heartbeat` children of `RunWorkflow:MainWorkflow`. The collector filters out Temporal’s `StartActivity:RecordHeartbeat` and `RunActivity:RecordHeartbeat` spans so only these custom heartbeats are shown. See [HEARTBEAT.md](HEARTBEAT.md) for details.
- **Collector** (`otel-collector.yml`): Receives OTLP, runs a filter (drops Temporal heartbeat activity spans), batches, and exports to Jaeger and New Relic.

## Up & Running

```shell
# Keys (required for New Relic export)
export NR_ENDPOINT=https://otlp.nr-data.net:4317
export MY_NEW_RELIC_API_KEY=your_api_key

# Start Temporal server, OTel collector, and Jaeger
./scripts/startup.sh

# Run the app (or start from your IDE)
mvn clean spring-boot:run

# Trigger a workflow
./scripts/01normal.sh
```

Traces are visible in Jaeger (http://localhost:16686) and, when configured, in New Relic.
