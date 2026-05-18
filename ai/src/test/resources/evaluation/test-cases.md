# Evaluation Test Cases

## Test Case 1: Memory Pressure Error
**Trace ID:** `test-oom-trace-001`
**Expected Findings:**
- ✅ MUST detect: HTTP 500 error
- ✅ MUST detect: OutOfMemoryError in logs
- ✅ MUST correlate: jvm_memory_used_bytes at >90% when error occurred
- ✅ MUST identify root cause: Memory exhaustion
- ✅ MUST recommend: Increase heap size or investigate memory leak
- ❌ MUST NOT: Blame CPU, network, or unrelated metrics

**Severity:** CRITICAL

---

## Test Case 2: Thread Starvation
**Trace ID:** `test-thread-starvation-002`
**Expected Findings:**
- ✅ MUST detect: Slow response time (>2s)
- ✅ MUST correlate: worker_pool_queue_size >0 with worker_pool_idle=0
- ✅ MUST identify root cause: Thread pool exhaustion
- ✅ MUST recommend: Increase worker thread pool size or reduce blocking operations
- ❌ MUST NOT: Blame memory or CPU if they're normal

**Severity:** HIGH

---

## Test Case 3: Downstream Service Error
**Trace ID:** `test-downstream-403-003`
**Expected Findings:**
- ✅ MUST detect: HTTP 500 at proxy, HTTP 403 from downstream service
- ✅ MUST identify: Error propagation from child span to parent
- ✅ MUST quote: Relevant log message about 403 Forbidden
- ✅ MUST correlate: System metrics normal (not a resource issue)
- ✅ MUST identify root cause: Authorization failure in downstream service
- ✅ MUST recommend: Check API credentials or permissions

**Severity:** HIGH

---

## Test Case 4: Normal Successful Request
**Trace ID:** `test-success-200-004`
**Expected Findings:**
- ✅ MUST detect: HTTP 200 success
- ✅ MUST report: Normal latency (<100ms)
- ✅ MUST report: System metrics healthy
- ✅ MUST NOT: Report false issues or warnings

**Severity:** LOW (no issues)

---

## Scoring Rubric

For each test case, score 0-100:
- **Detection (40 points)**: Did it identify all MUST-detect items?
- **Correlation (30 points)**: Did it correctly correlate metrics with issues?
- **Root Cause (20 points)**: Did it identify the correct root cause?
- **Recommendations (10 points)**: Are recommendations actionable and relevant?

**Bonus/Penalty:**
- +5: Exceptional insight not in expected findings
- -10: False positive (claimed issue that doesn't exist)
- -20: Wrong root cause identified

**Overall Pass Threshold:** 70/100
