#!/usr/bin/env bash
set -euo pipefail

TEST_TYPE="${1:-chaos}"
PROFILE="${2:-openai}"
SCORER="${3:-}"

export AI="$PROFILE"

case "$TEST_TYPE" in
  chaos) TEST_CLASS="ChaosIntegrationTest" ;;
  db)    TEST_CLASS="DbIntegrationTest" ;;
  *)
    echo "Unknown test type: $TEST_TYPE"
    echo "Usage: $0 <chaos|db> [ai-profile] [scorer]"
    exit 1
    ;;
esac

# Map AI profile to Maven profile (grok reuses openai's dependency)
case "$PROFILE" in
  grok) MAVEN_PROFILE="openai" ;;
  *)    MAVEN_PROFILE="$PROFILE" ;;
esac

# Configure scorer model (defaults to OpenAI via application.properties if unset)
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
echo ""

mvn clean test -pl ai -P"$MAVEN_PROFILE" -Dintegration.run=true -Dtest="$TEST_CLASS"
