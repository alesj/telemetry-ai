#!/usr/bin/env bash
set -euo pipefail

PROFILE="${1:-openai}"
SCORER="${2:-}"

export AI="$PROFILE"

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

mvn clean install -DskipTests -pl common
mvn clean test -pl ai -P"$MAVEN_PROFILE" -Dintegration.run=true -Dtest=FullIntegrationTest
