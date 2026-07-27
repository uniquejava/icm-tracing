#!/usr/bin/env bash
# Run Spring Boot with ADOT Java Agent → X-Ray OTLP (SigV4), no local Collector.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

PROFILE="${AWS_PROFILE:-default}"
REGION="${AWS_REGION:-eu-west-1}"
AGENT_JAR="${PWD}/.tools/aws-opentelemetry-agent.jar"

if [[ ! -f "${AGENT_JAR}" ]]; then
  echo "Missing ${AGENT_JAR}"
  echo "Download with:"
  echo "  mkdir -p .tools && curl -fL -o .tools/aws-opentelemetry-agent.jar \\"
  echo "    https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar"
  exit 1
fi

echo "==> Exporting session credentials from profile '${PROFILE}' (region ${REGION})"
eval "$(aws configure export-credentials --profile "${PROFILE}" --format env)"

export AWS_REGION="${REGION}"
export AWS_DEFAULT_REGION="${REGION}"

export JAVA_TOOL_OPTIONS="-javaagent:${AGENT_JAR}"
export OTEL_METRICS_EXPORTER=none
export OTEL_LOGS_EXPORTER=none
export OTEL_TRACES_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=http/protobuf
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="https://xray.${REGION}.amazonaws.com/v1/traces"
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_TRACES_SAMPLER_ARG=1.0
export OTEL_RESOURCE_ATTRIBUTES="service.name=adot-direct,deployment.environment=adot-direct"
export OTEL_AWS_APPLICATION_SIGNALS_ENABLED=false

JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export JAVA_HOME

echo "==> Starting app (ADOT agent → ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT})"
exec mvn spring-boot:run -Dmaven.compiler.proc=full
