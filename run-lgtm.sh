#!/usr/bin/env bash
set -euo pipefail

PORTS="${1:-}"

echo "=== Starting LGTM container ==="
docker run -d --name lgtm \
  -p 3000:3000 \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 3200:3200 \
  docker.io/grafana/otel-lgtm:0.24.0

trap 'echo "=== Stopping LGTM container ==="; docker rm -f lgtm' EXIT

echo "Waiting for LGTM to be ready..."
until curl -sf http://localhost:3000/api/health > /dev/null 2>&1; do
  sleep 1
done
echo "LGTM is ready."

echo ""
PORTS_ARG=""
if [ -n "$PORTS" ]; then
  PORTS_ARG="-Dapp.ports=$PORTS"
fi

echo "=== Starting AI module (lgtm profile${PORTS:+, app.ports=$PORTS}) ==="
./mvn.ai.sh quarkus:dev -Dquarkus.profile=lgtm,openai $PORTS_ARG
