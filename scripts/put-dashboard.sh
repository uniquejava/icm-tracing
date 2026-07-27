#!/usr/bin/env bash
# Create/update the CloudWatch dashboard for lab metrics (namespace ICMTracing/Temporal, application=main).
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

REGION="${AWS_REGION:-eu-west-1}"
PROFILE="${AWS_PROFILE:-default}"
NAME="${CW_DASHBOARD_NAME:-adot-aws-lab}"
BODY="dashboards/adot-aws-main.json"

echo "==> put-dashboard name=${NAME} region=${REGION} profile=${PROFILE}"
aws cloudwatch put-dashboard \
  --profile "${PROFILE}" \
  --region "${REGION}" \
  --dashboard-name "${NAME}" \
  --dashboard-body "file://${BODY}" \
  --no-cli-pager

echo
echo "Open:"
echo "https://${REGION}.console.aws.amazon.com/cloudwatch/home?region=${REGION}#dashboards:name=${NAME}"
