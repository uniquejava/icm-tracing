#!/usr/bin/env bash
# Start Temporal (if needed) + ADOT collector + Jaeger.
# ADOT runs distroless and does not reliably use SSO profiles from a mounted
# ~/.aws — we inject short-lived session keys from the host profile instead.
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

echo "==> Exporting session credentials from profile '${PROFILE}' (region ${REGION})"
eval "$(aws configure export-credentials --profile "${PROFILE}" --format env)"
# Empty AWS_ACCESS_KEY_ID in compose used to override the profile; do not pass AWS_PROFILE into the container.
unset AWS_PROFILE

if ! curl -sf -o /dev/null http://127.0.0.1:8088 2>/dev/null; then
  echo "==> Starting Temporal dev server"
  nohup temporal server start-dev --ui-port 8088 >/tmp/temporal-dev.log 2>&1 &
  sleep 2
else
  echo "==> Temporal already up on :8088"
fi

export AWS_REGION="${REGION}"
echo "==> Starting ADOT collector + Jaeger (force-recreate collector so session keys apply)"
docker compose up -d --force-recreate otel-collector
docker compose up -d

echo
echo "Temporal UI:  http://localhost:8088"
echo "Jaeger:       http://localhost:16686"
echo "ADOT health:  http://localhost:13133"
echo "Logs:         docker compose logs -f otel-collector"
echo
echo "App:  JAVA_HOME=\$(/usr/libexec/java_home -v 25) mvn spring-boot:run -Dmaven.compiler.proc=full"
echo "Then: ./scripts/01normal.sh"
echo "Note: session keys expire — re-run this script (or export-credentials + compose up -d --force-recreate otel-collector) after refresh."
