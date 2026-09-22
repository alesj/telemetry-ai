#!/usr/bin/env bash
set -euo pipefail

TEST_TYPE="${1:-chaos}"
PROFILE="${2:-openai}"
SCORER="${3:-}"

shift 3 2>/dev/null || shift $# 2>/dev/null

case "$TEST_TYPE" in
  chaos)   TEST_CLASS="ChaosIntegrationTest" ;;
  db)      TEST_CLASS="DbIntegrationTest" ;;
  weather) TEST_CLASS="WeatherIntegrationTest" ;;
  *)
    echo "Unknown test type: $TEST_TYPE"
    echo "Usage: $0 <chaos|db|weather> <ai-profile> [scorer] <method1> [method2] ..."
    exit 1
    ;;
esac

CHAOS_METHODS=(
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

DB_METHODS=(
  analyzeNormalTraffic
  analyzeSlowQueries
  analyzePoolExhaustion
  analyzeDbOutage
)

WEATHER_METHODS=(
  analyzeWeatherNormal
  analyzeWeatherSlowApi
  analyzeWeatherApiOutage
)

case "$TEST_TYPE" in
  chaos)   VALID_METHODS=("${CHAOS_METHODS[@]}") ;;
  db)      VALID_METHODS=("${DB_METHODS[@]}") ;;
  weather) VALID_METHODS=("${WEATHER_METHODS[@]}") ;;
esac

if [ $# -eq 0 ]; then
  echo "Usage: $0 <chaos|ext> <ai-profile> [scorer] <method1> [method2] ..."
  echo ""
  echo "  test-type:   chaos | db | weather"
  echo "  ai-profile:  openai | grok | gemini | watsonx"
  echo "  scorer:      grok | (empty = openai default)"
  echo "  methods:     test method names from $TEST_CLASS"
  echo ""
  echo "Available methods for '$TEST_TYPE':"
  for m in "${VALID_METHODS[@]}"; do
    echo "  $m"
  done
  echo ""
  echo "Examples:"
  echo "  $0 chaos openai grok analyzeIntermittentFailures analyzeNetworkPartition"
  echo "  $0 chaos grok '' analyzeRequestFlood"
  echo "  $0 db openai '' analyzeNormalTraffic"
  echo "  $0 weather openai grok analyzeWeatherNormal"
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
    echo "WARNING: skipping unknown method '$method' for $TEST_TYPE"
    echo "  Run '$0 $TEST_TYPE' with no methods to see available options."
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

echo "=== Running $TEST_CLASS ==="
echo "  Test type: $TEST_TYPE"
echo "  AI profile: $PROFILE"
echo "  Scorer: ${SCORER:-openai (default)}"
echo "  Methods: $METHODS"
echo ""

./mvnw clean test -pl ai -P"$MAVEN_PROFILE" -Dintegration.run=true \
  -Dtest="${TEST_CLASS}#${METHODS}"
