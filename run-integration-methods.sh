#!/usr/bin/env bash
set -euo pipefail

PROFILE="${1:-openai}"
SCORER="${2:-}"

shift 2 2>/dev/null || shift $# 2>/dev/null

VALID_METHODS=(
  analyzeNormalTraffic
  analyzeErrorTraffic
  analyzeLatency
  analyzeResourcePressure
  analyzeCascadingFailure
  analyzeLockContention
  analyzeIntermittentFailures
  analyzeNetworkPartition
  analyzeRequestFlood
  analyzeDeadlock
  examineSourceCode
  generateDashboard
)

if [ $# -eq 0 ]; then
  echo "Usage: $0 <ai-profile> [scorer] <method1> [method2] ..."
  echo ""
  echo "  ai-profile:  openai | grok | gemini | watsonx"
  echo "  scorer:       grok | (empty = openai default)"
  echo "  methods:      test method names from FullIntegrationTest"
  echo ""
  echo "Available methods:"
  for m in "${VALID_METHODS[@]}"; do
    echo "  $m"
  done
  echo ""
  echo "Examples:"
  echo "  $0 openai grok analyzeIntermittentFailures analyzeNetworkPartition"
  echo "  $0 grok '' analyzeRequestFlood"
  echo "  $0 openai '' analyzeNormalTraffic analyzeErrorTraffic analyzeLatency"
  exit 1
fi

VALIDATED=()
for method in "$@"; do
  found=false
  for valid in "${VALID_METHODS[@]}"; do
    if [ "$method" = "$valid" ]; then
      found=true
      break
    fi
  done
  if [ "$found" = true ]; then
    VALIDATED+=("$method")
  else
    echo "WARNING: skipping unknown method '$method'"
    echo "  Run '$0' with no methods to see available options."
  fi
done

if [ ${#VALIDATED[@]} -eq 0 ]; then
  echo "ERROR: no valid methods to run."
  exit 1
fi

METHODS=$(IFS=+; echo "${VALIDATED[*]}")

export AI="$PROFILE"

case "$PROFILE" in
  grok) MAVEN_PROFILE="openai" ;;
  *)    MAVEN_PROFILE="$PROFILE" ;;
esac

case "$SCORER" in
  grok)
    export SCORER_BASE_URL=https://api.x.ai/v1
    export SCORER_API_KEY="${GROK_API_KEY}"
    export SCORER_MODEL=grok-3-mini
    ;;
  watsonx)
    export SCORER_BASE_URL="${WATSONX_BASE_URL}"
    export SCORER_API_KEY="${WATSONX_API_KEY}"
    export SCORER_MODEL=ibm/granite-4-h-small
    ;;
esac

echo "=== Running FullIntegrationTest ==="
echo "  AI profile: $PROFILE"
echo "  Maven profile: $MAVEN_PROFILE"
echo "  Scorer: ${SCORER:-openai (default)}"
echo "  Methods: $METHODS"
echo ""

mvn clean test -pl ai -P"$MAVEN_PROFILE" -Dintegration.run=true \
  -Dtest="FullIntegrationTest#${METHODS}"
