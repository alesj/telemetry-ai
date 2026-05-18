# Analysis Quality Evaluation Strategy

This document describes how to evaluate and improve the quality of AI-generated trace analysis over time.

## Evaluation Approaches

### 1. Automated Rule-Based Testing (Implemented)

**Location:** `src/test/java/io/quarkus/telemetry/ai/AnalysisEvaluationTest.java`

**How it works:**
- Define test cases with known issues (memory pressure, thread starvation, etc.)
- Run AI analysis on these test traces
- Programmatically check for required findings using string matching and regex
- Score each analysis on multiple dimensions (detection, correlation, root cause, recommendations)
- Track scores over time in `target/evaluation-results.log`

**Pros:**
- Fast and automated
- No API costs
- Easy to run in CI/CD
- Clear pass/fail criteria

**Cons:**
- Brittle (relies on specific keywords)
- Can't evaluate nuance or quality of explanations
- May miss valid alternative phrasings

**When to use:** Run before committing changes to detect regressions

---

### 2. LLM-as-Judge Evaluation

Use a separate LLM to evaluate the quality of analysis output.

**Implementation:**

```java
// Add to your project
public class LlmJudgeEvaluator {
    
    private final AiService judgeService; // Separate AI service with judge prompt
    
    public EvaluationScore evaluate(String analysis, String expectedFindings) {
        String judgePrompt = """
            You are evaluating the quality of a distributed tracing analysis.
            
            Expected Findings:
            %s
            
            Actual Analysis:
            %s
            
            Evaluate this analysis on a scale of 0-100 across these dimensions:
            
            1. **Completeness (0-25)**: Did it identify all critical issues?
            2. **Accuracy (0-25)**: Are the findings correct? Any false positives?
            3. **Correlation Quality (0-25)**: How well did it correlate metrics with issues?
            4. **Actionability (0-25)**: Are recommendations specific and actionable?
            
            Provide:
            - Score for each dimension
            - Total score (sum)
            - Brief justification for each score
            - List of missing or incorrect findings
            
            Format as JSON:
            {
              "completeness": { "score": <0-25>, "justification": "..." },
              "accuracy": { "score": <0-25>, "justification": "..." },
              "correlation": { "score": <0-25>, "justification": "..." },
              "actionability": { "score": <0-25>, "justification": "..." },
              "total": <0-100>,
              "missing": ["...", "..."],
              "incorrect": ["...", "..."]
            }
            """.formatted(expectedFindings, analysis);
        
        return parseJudgeResponse(judgeService.evaluate(judgePrompt));
    }
}
```

**Pros:**
- More nuanced evaluation
- Can assess explanation quality
- Handles alternative phrasings
- Can identify novel insights

**Cons:**
- API costs for evaluation
- Non-deterministic
- Slower than rule-based
- Needs validation (who judges the judge?)

**When to use:** Periodic deep evaluation of analysis quality

---

### 3. Regression Testing with Snapshots

Save analysis outputs as snapshots and compare after changes.

**Implementation:**

```bash
# Before making changes
./run-analysis.sh --trace-id test-001 > snapshots/baseline-test-001.txt

# After making changes
./run-analysis.sh --trace-id test-001 > snapshots/current-test-001.txt

# Compare
diff snapshots/baseline-test-001.txt snapshots/current-test-001.txt
```

**With LLM comparison:**
```java
String comparison = compareService.compare(baseline, current);
// Ask LLM: "Did the analysis improve, stay the same, or regress?"
```

**Pros:**
- Detects unintended changes
- Preserves good examples
- Easy to see what changed

**Cons:**
- Non-deterministic LLM output makes exact diffs noisy
- Requires manual review
- Snapshots can become outdated

**When to use:** Before major refactors or prompt changes

---

### 4. Human Expert Review (Gold Standard)

Periodic manual evaluation by domain experts.

**Process:**

1. **Sample Selection**: Randomly select 10-20 analyses from production
2. **Blind Review**: Experts evaluate without knowing which version generated it
3. **Rubric Scoring**:
   ```
   Accuracy:        [1-5] ⭐⭐⭐⭐⭐
   Completeness:    [1-5] ⭐⭐⭐⭐⭐
   Insight Quality: [1-5] ⭐⭐⭐⭐⭐
   Actionability:   [1-5] ⭐⭐⭐⭐⭐
   
   Would you trust this analysis? [Yes/No]
   Did it identify the root cause? [Yes/No/Partial]
   Would you follow the recommendations? [Yes/No]
   ```

4. **Track Over Time**: Store scores in database, track trends
5. **Inter-rater Reliability**: Multiple reviewers for subset of samples

**Pros:**
- Most accurate assessment
- Catches subtle quality issues
- Provides qualitative feedback

**Cons:**
- Expensive (expert time)
- Slow
- Subjective

**When to use:** Quarterly reviews, after major changes

---

### 5. Outcome-Based Metrics (Real World)

Track actual usage outcomes.

**Metrics to collect:**

```sql
CREATE TABLE analysis_outcomes (
    trace_id VARCHAR,
    analysis_timestamp TIMESTAMP,
    identified_root_cause BOOLEAN,
    time_to_resolution_minutes INTEGER,
    user_rating INTEGER, -- 1-5 stars
    followed_recommendations BOOLEAN,
    additional_investigation_needed BOOLEAN,
    false_positive BOOLEAN,
    notes TEXT
);
```

**Analysis:**
- % of analyses that correctly identified root cause
- Average time to resolution when following recommendations
- User satisfaction scores
- False positive rate

**Implementation:**
Add feedback mechanism to your UI:
```
[Was this analysis helpful?] 
👍 Yes, found the issue | 👎 No, wrong analysis | 💡 Partially helpful

If helpful: [How long to resolve?] __ minutes
If not helpful: [What was wrong?] ___
```

**Pros:**
- Measures real impact
- Direct user feedback
- Identifies blind spots

**Cons:**
- Requires production deployment
- Delayed feedback
- Confounded by other factors

**When to use:** Continuous monitoring in production

---

## Recommended Evaluation Workflow

### Daily (Automated)
1. Run `AnalysisEvaluationTest` on every commit
2. Fail build if scores drop below threshold
3. Log scores to track trends

### Weekly
1. Review evaluation logs for patterns
2. Add new test cases for recently discovered issues
3. Update expected findings as system improves

### Monthly
1. Run LLM-as-judge on 50-100 analyses
2. Compare current month vs previous month
3. Identify areas for improvement

### Quarterly
1. Human expert review of sample analyses
2. Update rubrics and test cases
3. Revisit evaluation criteria

---

## Continuous Improvement Process

```
┌─────────────────┐
│ Make Change     │
│ (Prompt, Code)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Run Automated   │
│ Tests           │◄────── Fail: Revert or Fix
└────────┬────────┘
         │ Pass
         ▼
┌─────────────────┐
│ Manual Review   │
│ of Sample       │
└────────┬────────┘
         │ Looks Good
         ▼
┌─────────────────┐
│ Deploy          │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Monitor Real    │
│ Outcomes        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Iterate         │
└─────────────────┘
```

---

## Metrics Dashboard

Track these over time (chart in Grafana/similar):

```
Analysis Quality Metrics:
├── Automated Test Score: 85/100 ↑ +5
├── LLM Judge Score: 78/100 ↓ -2
├── Human Expert Rating: 4.2/5 ↑ +0.1
├── Root Cause Detection Rate: 87% ↑ +3%
├── False Positive Rate: 5% ↓ -1%
└── User Satisfaction: 4.5/5 ↑ +0.3

Coverage:
├── Test Cases: 12
├── Monthly LLM Evals: 100
├── Expert Reviews: 20
└── Production Feedback: 450
```

---

## Adding New Test Cases

When you encounter a production issue the AI missed:

1. **Capture the trace**: Save trace ID, logs, metrics snapshot
2. **Document expected findings**: What SHOULD have been detected?
3. **Create test case**: Add to `test-cases.md`
4. **Implement test**: Add method to `AnalysisEvaluationTest`
5. **Verify failure**: Confirm current system fails the test
6. **Fix and verify**: Improve prompt/code, ensure test passes
7. **Prevent regression**: Test runs on every commit

---

## Example: Tracking Improvement Over Time

```
# evaluation-results.log

[2026-05-01T10:00:00] Memory Pressure: 75/100 (PASS)
[2026-05-01T10:01:00] Thread Starvation: 65/100 (FAIL)
[2026-05-01T10:02:00] Downstream Error: 80/100 (PASS)

[2026-05-08T14:30:00] Memory Pressure: 78/100 (PASS) ✓ +3
[2026-05-08T14:31:00] Thread Starvation: 72/100 (PASS) ✓ +7 [FIX APPLIED]
[2026-05-08T14:32:00] Downstream Error: 82/100 (PASS) ✓ +2

[2026-05-15T09:15:00] Memory Pressure: 85/100 (PASS) ✓ +7
[2026-05-15T09:16:00] Thread Starvation: 88/100 (PASS) ✓ +16
[2026-05-15T09:17:00] Downstream Error: 85/100 (PASS) ✓ +3
[2026-05-15T09:18:00] GC Pause Detection: 70/100 (PASS) [NEW TEST]
```

Trend: **Improving** 📈
Average score: 75 → 77 → 82 (+9% improvement)
