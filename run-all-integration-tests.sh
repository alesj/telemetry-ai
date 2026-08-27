#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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
  echo " Running: AI=$ai SCORER=$scorer"
  echo "=========================================="
  if "$SCRIPT_DIR/run-integration-test.sh" "$ai" "$scorer"; then
    echo "PASSED: AI=$ai SCORER=$scorer"
  else
    echo "FAILED: AI=$ai SCORER=$scorer"
    FAILED+=("AI=$ai SCORER=$scorer")
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
