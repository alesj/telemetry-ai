#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TEST_TYPE="${1:-chaos}"

CONFIGS=(
  "openai openai"
  "grok openai"
  "openai grok"
  "grok grok"
)

FAILED=()

for config in "${CONFIGS[@]}"; do
  read -r ai scorer <<< "$config"
  echo "=========================================="
  echo " Running: TYPE=$TEST_TYPE AI=$ai SCORER=$scorer"
  echo "=========================================="
  if "$SCRIPT_DIR/run-integration-test.sh" "$TEST_TYPE" "$ai" "$scorer"; then
    echo "PASSED: TYPE=$TEST_TYPE AI=$ai SCORER=$scorer"
  else
    echo "FAILED: TYPE=$TEST_TYPE AI=$ai SCORER=$scorer"
    FAILED+=("TYPE=$TEST_TYPE AI=$ai SCORER=$scorer")
  fi
  echo ""
done

echo "=========================================="
echo " Summary"
echo "=========================================="
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "All ${#CONFIGS[@]} configurations passed."
else
  echo "${#FAILED[@]} of ${#CONFIGS[@]} configurations failed:"
  for f in "${FAILED[@]}"; do
    echo "  - $f"
  done
  exit 1
fi
