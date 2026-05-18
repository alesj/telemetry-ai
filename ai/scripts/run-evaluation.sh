#!/bin/bash
set -e

# Run evaluation tests and track results over time
# Usage: ./scripts/run-evaluation.sh [--save-baseline] [--compare-baseline]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_FILE="$PROJECT_DIR/target/evaluation-results.log"
BASELINE_FILE="$PROJECT_DIR/target/evaluation-baseline.log"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo "🧪 Running Analysis Evaluation Tests..."
echo "Timestamp: $TIMESTAMP"
echo ""

# Run the evaluation tests
cd "$PROJECT_DIR"
mvn test -Dtest=AnalysisEvaluationTest -q

# Check if results file exists
if [ ! -f "$RESULTS_FILE" ]; then
    echo "❌ No results file found at $RESULTS_FILE"
    exit 1
fi

# Extract latest scores
LATEST_SCORES=$(tail -n 10 "$RESULTS_FILE" | grep "^\[" || echo "No scores found")

echo "📊 Latest Evaluation Results:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$LATEST_SCORES"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Calculate average score from latest run
AVERAGE=$(echo "$LATEST_SCORES" | grep -oP '\d+/100' | cut -d'/' -f1 | awk '{sum+=$1; count+=1} END {if(count>0) print int(sum/count); else print 0}')
echo "📈 Average Score: $AVERAGE/100"
echo ""

# Save baseline if requested
if [[ "$1" == "--save-baseline" ]]; then
    echo "💾 Saving current results as baseline..."
    cp "$RESULTS_FILE" "$BASELINE_FILE"
    echo "✅ Baseline saved to $BASELINE_FILE"
    echo ""
fi

# Compare with baseline if requested
if [[ "$1" == "--compare-baseline" ]] && [ -f "$BASELINE_FILE" ]; then
    echo "🔍 Comparing with baseline..."
    echo ""

    # Extract baseline scores
    BASELINE_SCORES=$(tail -n 10 "$BASELINE_FILE" | grep "^\[" || echo "")
    BASELINE_AVG=$(echo "$BASELINE_SCORES" | grep -oP '\d+/100' | cut -d'/' -f1 | awk '{sum+=$1; count+=1} END {if(count>0) print int(sum/count); else print 0}')

    echo "Baseline Average: $BASELINE_AVG/100"
    echo "Current Average:  $AVERAGE/100"

    DIFF=$((AVERAGE - BASELINE_AVG))

    if [ "$DIFF" -gt 0 ]; then
        echo "📈 Improvement: +$DIFF points ✅"
    elif [ "$DIFF" -lt 0 ]; then
        echo "📉 Regression: $DIFF points ⚠️"
        echo ""
        echo "❌ Quality regression detected!"
        exit 1
    else
        echo "➡️  No change"
    fi
    echo ""
fi

# Check if any tests failed
FAILURES=$(echo "$LATEST_SCORES" | grep -c "FAIL" || true)
if [ "$FAILURES" -gt 0 ]; then
    echo "❌ $FAILURES test case(s) failed"
    echo ""
    echo "Failed cases:"
    echo "$LATEST_SCORES" | grep "FAIL"
    echo ""
    exit 1
else
    echo "✅ All test cases passed"
fi

echo ""
echo "💡 Tip: Run with --save-baseline to set current results as baseline"
echo "💡 Tip: Run with --compare-baseline to check for regressions"
echo ""
echo "📁 Full results: $RESULTS_FILE"
