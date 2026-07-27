#!/usr/bin/env bash
# Start Temporal only — no ADOT Collector / Jaeger docker stack.
# Traces export from the app via ADOT Java Agent (scripts/run-app-direct.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if ! curl -sf -o /dev/null http://127.0.0.1:8088 2>/dev/null; then
  echo "==> Starting Temporal dev server"
  nohup temporal server start-dev --ui-port 8088 >/tmp/temporal-dev.log 2>&1 &
  sleep 2
else
  echo "==> Temporal already up on :8088"
fi

echo
echo "Temporal UI:  http://localhost:8088"
echo "App:          ./scripts/run-app-direct.sh"
echo "Then:         ./scripts/01normal.sh"
echo
echo "No Collector required. Spans → CloudWatch aws/spans (service.name=adot-direct)."
